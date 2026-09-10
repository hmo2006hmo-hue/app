package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/** Stage 21: unified, format-aware book installation. */
object LibraryBookManager {
    data class InstallResult(val file: File, val format: String)

    private val installLocks = ConcurrentHashMap<String, Mutex>()

    // Compiling a Regex is relatively expensive (pattern parsing/NFA build).
    // These patterns were previously re-created on every call for every book
    // in the catalog (290 books), which meant hundreds of fresh Regex
    // compilations every time the catalog was read - including on every
    // debounced keystroke while searching. Hoisting them to constants means
    // the pattern is compiled once per process instead of once per book per call.
    private val SQLITE_DB_FILE_PATTERN = Regex("book_[A-Za-z0-9_-]+\\.db")
    private val EXTERNAL_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,120}")

    // reconcile() performs a full recursive filesystem walk plus a per-book
    // validation pass over the entire catalog. It exists to self-heal legacy
    // plaintext databases and delete stale partial downloads left over from a
    // previous crash/interrupted install. None of that changes between one
    // catalog read and the next unless install()/delete() runs, so repeating
    // it on every getBooks() call (including on every search-as-you-type
    // lookup) is pure wasted I/O and CPU. Running it once per process is
    // sufficient for its self-healing purpose.
    @Volatile private var reconciledOnce = false

    /** Reconciles known installed books and removes only stale partial files.
     *  Unknown files are preserved to avoid accidental user data loss.
     *  Only performs the actual scan once per process (see [reconciledOnce]);
     *  subsequent calls are a no-op fast path.
     */
    suspend fun reconcile(context: Context, books: List<LibraryCatalogRepository.Book>): Int {
        if (reconciledOnce) return 0
        return withContext(Dispatchers.IO) {
            if (reconciledOnce) return@withContext 0
            var cleaned = 0
            val root = File(context.filesDir, "library/books")
            root.mkdirs()
            root.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".part")) {
                    if (file.delete()) cleaned++
                }
            }
            for (book in books) {
                when (book.format.lowercase()) {
                    "sqlite" -> {
                        if (!book.dbFile.matches(SQLITE_DB_FILE_PATTERN)) continue
                        val encrypted = BookStorage.encryptedFile(context, book.dbFile)
                        val plaintext = File(context.filesDir, "$ROOT_DIR/${book.dbFile}")
                        if (encrypted.exists() && !BookCrypto.isEncrypted(encrypted)) {
                            if (encrypted.delete()) cleaned++
                        }
                        // One-time migration for older installations: encrypt any legacy plaintext DB.
                        if (plaintext.isFile) {
                            if (!encrypted.exists()) {
                                runCatching {
                                    val part = File(context.filesDir, "$ROOT_DIR/${book.dbFile}.enc.part")
                                    part.delete()
                                    BookCrypto.encryptFile(plaintext, part)
                                    if (!part.renameTo(encrypted)) part.delete()
                                }
                            }
                            if (encrypted.isFile && BookCrypto.isEncrypted(encrypted) && plaintext.delete()) cleaned++
                        }
                    }
                    "json" -> {
                        val file = File(context.filesDir, "$ROOT_DIR/${safeExternalId(book.externalId)}/book.json")
                        if (file.exists() && !isValidJson(file)) {
                            if (file.delete()) cleaned++
                        }
                    }
                }
            }
            reconciledOnce = true
            cleaned
        }
    }

    suspend fun install(
        context: Context,
        book: LibraryCatalogRepository.Book,
        onProgress: ((Float) -> Unit)? = null
    ): InstallResult = withContext(Dispatchers.IO) {
        val key = book.externalId
        val lock = installLocks.computeIfAbsent(key) { Mutex() }
        lock.withLock {
            when (book.format.lowercase()) {
                "sqlite" -> installSqlite(context, book, onProgress)
                "json" -> installJson(context, book, onProgress)
                else -> error("صيغة كتاب غير مدعومة: ${book.format}")
            }
        }
    }

    private suspend fun installSqlite(
        context: Context,
        book: LibraryCatalogRepository.Book,
        onProgress: ((Float) -> Unit)? = null
    ): InstallResult {
        require(book.dbFile.matches(SQLITE_DB_FILE_PATTERN)) { "اسم قاعدة الكتاب غير صالح" }
        // Library SQLite books are deliberately never bundled in the APK.
        // Every installation comes from the verified catalog URL and is then
        // validated and encrypted before it becomes the installed file.
        require(!book.downloadUrl.isNullOrBlank()) { "رابط تنزيل SQLite غير متوفر لهذا الكتاب" }
        require(!book.sha256.isNullOrBlank()) { "بصمة SHA-256 غير متوفرة لهذا الكتاب" }
        val file = DownloadRepository.downloadBook(context, book, onProgress).file
        return InstallResult(file, "sqlite")
    }

    private suspend fun installJson(
        context: Context,
        book: LibraryCatalogRepository.Book,
        onProgress: ((Float) -> Unit)? = null
    ): InstallResult = withContext(Dispatchers.IO) {
        val urlText = book.downloadUrl?.trim().orEmpty()
        require(urlText.isNotBlank()) { "رابط تنزيل JSON غير متوفر لهذا الكتاب" }

        val dir = File(context.filesDir, "library/books/${safeExternalId(book.externalId)}").apply { mkdirs() }
        val target = File(dir, "book.json")
        val part = File(dir, "book.json.part")
        var lastError: Throwable? = null

        for (candidate in candidateUrls(urlText)) {
            try {
                if (part.exists() && !part.delete()) error("تعذر تنظيف التنزيل السابق")
                val url = URL(candidate)
                require(url.protocol.equals("https", ignoreCase = true)) { "يُسمح فقط بروابط HTTPS" }
                require(url.host.isNotBlank()) { "مضيف رابط التنزيل غير صالح" }

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    useCaches = false
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "QuranStudy-Native/1.0")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    connection.connect()
                    if (!connection.url.protocol.equals("https", ignoreCase = true)) throw IOException("تنزيل مرفوض: إعادة التوجيه إلى رابط غير HTTPS")
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("فشل تنزيل الكتاب: HTTP $code")

                    val expectedBytes = book.sizeBytes?.takeIf { it >= 0L }
                        ?: connection.contentLengthLong.takeIf { it > 0L }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var lastReportedPercent = -1
                    BufferedInputStream(connection.inputStream, 32 * 1024).use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (n == 0) continue
                                output.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                total += n
                                if (expectedBytes != null && total > expectedBytes) throw IOException("حجم الملف أكبر من المتوقع")
                                if (onProgress != null && expectedBytes != null && expectedBytes > 0L) {
                                    val fraction = (total.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f)
                                    val percent = (fraction * 100f).toInt()
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        onProgress(fraction)
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    if (expectedBytes != null && total != expectedBytes) throw IOException("حجم الملف غير مطابق: expected=$expectedBytes actual=$total")

                    val expectedSha = book.sha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (expectedSha != null && actualSha != expectedSha) throw IOException("SHA-256 غير مطابق للكتاب")

                    val jsonText = part.readText(Charsets.UTF_8)
                    require(jsonText.length >= 20 && JSONObject(jsonText).length() > 0) { "الملف المحمل ليس JSON صالحًا" }
                    installAtomically(part, target)
                    return@withContext InstallResult(target, "json")
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
                part.delete()
            }
        }
        throw (lastError ?: IOException("تعذر تنزيل الكتاب"))
    }

    private fun candidateUrls(value: String): List<String> {
        val clean = value.trim()
        val out = linkedSetOf(clean)
        val match = Regex("https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.*)").matchEntire(clean)
        if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val branch = match.groupValues[3]
            val path = match.groupValues[4]
            out += "https://github.com/$owner/$repo/raw/refs/heads/$branch/$path"
        }
        return out.toList()
    }

    fun installedFile(context: Context, book: LibraryCatalogRepository.Book): File? {
        return when (book.format.lowercase()) {
            "sqlite" -> if (book.dbFile.matches(SQLITE_DB_FILE_PATTERN)) {
                BookStorage.encryptedFile(context, book.dbFile).takeIf {
                    it.isFile && BookCrypto.isEncrypted(it)
                }
            } else null
            "json" -> if (book.externalId.matches(EXTERNAL_ID_PATTERN)) {
                File(context.filesDir, "$ROOT_DIR/${book.externalId}/book.json").takeIf {
                    it.isFile && it.length() > 20L && isValidJson(it)
                }
            } else null
            else -> null
        }
    }

    fun isInstalled(context: Context, book: LibraryCatalogRepository.Book): Boolean =
        installedFile(context, book) != null

    suspend fun delete(context: Context, book: LibraryCatalogRepository.Book): Boolean =
        withContext(Dispatchers.IO) {
            when (book.format.lowercase()) {
                "sqlite" -> BookRepository.deleteBook(context, book.dbFile)
                "json" -> {
                    val dir = File(context.filesDir, "library/books/${safeExternalId(book.externalId)}")
                    val target = File(dir, "book.json")
                    val part = File(dir, "book.json.part")
                    var deleted = false
                    for (file in listOf(target, part)) {
                        if (file.exists()) {
                            if (!file.delete()) error("تعذر حذف ملف الكتاب: ${file.name}")
                            deleted = true
                        }
                    }
                    if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) dir.delete()
                    deleted
                }
                else -> false
            }
        }

    private const val ROOT_DIR = "library/books"

    private fun installAtomically(part: File, target: File) {
        val backup = File(target.parentFile, "${target.name}.bak")
        if (backup.exists() && !backup.delete()) error("تعذر تنظيف النسخة الاحتياطية القديمة")

        var backedUp = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) error("تعذر حفظ الكتاب الحالي مؤقتًا")
                backedUp = true
            }
            if (!part.renameTo(target)) {
                if (backedUp) backup.renameTo(target)
                error("تعذر تثبيت الكتاب")
            }
            if (backup.exists() && !backup.delete()) {
                // The new file is already installed and valid; failing to remove only
                // the backup should not make the installation fail.
            }
        } catch (t: Throwable) {
            if (target.exists() && backup.exists()) target.delete()
            if (backedUp && backup.exists()) backup.renameTo(target)
            throw t
        } finally {
            if (part.exists()) part.delete()
            if (backup.exists() && target.exists()) backup.delete()
        }
    }

    private fun isValidSqlite(file: File): Boolean =
        runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

    private fun isValidJson(file: File): Boolean =
        runCatching {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            obj.has("pages") || obj.has("book") || obj.has("chapters") || obj.has("title")
        }.getOrDefault(false)

    private fun safeExternalId(value: String): String {
        require(value.matches(EXTERNAL_ID_PATTERN)) {
            "معرف الكتاب غير صالح"
        }
        return value
    }

    fun sqliteFile(context: Context, book: LibraryCatalogRepository.Book): File =
        BookStorage.encryptedFile(context, book.dbFile)
}

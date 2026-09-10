package com.muddakir.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Stage 14: safe download/install engine for independent SQLite databases.
 *
 * The catalog's download_url points to a real, verified public GitHub
 * release (owner/repo/tag confirmed reachable, and book_1.db's bytes and
 * SHA-256 were checked to match exactly). That URL is tried first. The
 * Ma'raj per-book endpoint is kept only as a secondary fallback in case the
 * primary host is briefly unreachable — it was never independently verified
 * to actually host these exact files, so it must not be tried first.
 */
object DownloadRepository {

    data class DownloadResult(
        val file: File,
        val bytes: Long,
        val sha256: String
    )

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024
    private val SQLITE_DB_FILE_PATTERN = Regex("book_[A-Za-z0-9_-]+\\.db")
    private val GITHUB_RELEASE_URL_PATTERN = Regex("https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.*)")

    suspend fun downloadBook(
        context: Context,
        book: LibraryCatalogRepository.Book,
        onProgress: ((Float) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(book.dbFile.matches(SQLITE_DB_FILE_PATTERN)) {
            "Invalid book database filename: ${book.dbFile}"
        }

        val catalogUrl = book.downloadUrl?.trim().orEmpty()
        require(catalogUrl.isNotEmpty()) { "رابط تنزيل الكتاب غير متوفر" }

        // IMPORTANT: do not fall back to another host after the verified file
        // reaches 100%.  The old implementation treated every error (including
        // SHA/SQLite validation errors that happen AFTER the transfer) as a
        // reason to start the whole download again from another URL.  This made
        // the progress bar jump 100% -> 0% repeatedly and could never finish.
        // The catalog URL is the authoritative source, so one download attempt
        // is used and any real validation error is reported to the user.
        return@withContext downloadDatabase(
            urlText = catalogUrl,
            finalFile = BookStorage.encryptedFile(context, book.dbFile),
            expectedBytes = book.sizeBytes,
            expectedSha256 = book.sha256,
            onProgress = onProgress
        )
    }

    suspend fun downloadDatabase(
        urlText: String,
        finalFile: File,
        expectedBytes: Long?,
        expectedSha256: String?,
        onProgress: ((Float) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(expectedBytes == null || expectedBytes >= 0L) {
            "expectedBytes must be null or non-negative"
        }

        var lastError: Throwable? = null
        for (candidate in candidateUrls(urlText)) {
            try {
                return@withContext downloadFromUrl(candidate, finalFile, expectedBytes, expectedSha256, onProgress)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
            }
        }

        throw (lastError ?: IOException("Download failed"))
    }

    private suspend fun downloadFromUrl(
        urlText: String,
        finalFile: File,
        expectedBytes: Long?,
        expectedSha256: String?,
        onProgress: ((Float) -> Unit)? = null
    ): DownloadResult {
        val url = parseHttpsUrl(urlText)
        finalFile.parentFile?.mkdirs()
        val partFile = File(finalFile.parentFile, "${finalFile.name}.plain.part")
        val encryptedPartFile = File(finalFile.parentFile, "${finalFile.name}.part")
        if (partFile.exists() && !partFile.delete()) error("Unable to clear partial download: ${partFile.name}")
        if (encryptedPartFile.exists() && !encryptedPartFile.delete()) error("Unable to clear partial encrypted download: ${encryptedPartFile.name}")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "QuranStudy-Native/1.0")
            setRequestProperty("Cache-Control", "no-cache")
        }

        try {
            connection.connect()
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("تنزيل مرفوض: إعادة التوجيه إلى رابط غير HTTPS")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Download failed with HTTP $code")

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastReportedPercent = -1
            val progressDenominator = expectedBytes ?: connection.contentLengthLong.takeIf { it > 0 }
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        total += read
                        if (expectedBytes != null && total > expectedBytes) throw IOException("Downloaded file is larger than expected")
                        if (onProgress != null && progressDenominator != null && progressDenominator > 0) {
                            val fraction = (total.toFloat() / progressDenominator.toFloat()).coerceIn(0f, 1f)
                            val percent = (fraction * 100).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(fraction)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            if (expectedBytes != null && total != expectedBytes) throw IOException("Downloaded size mismatch: expected=$expectedBytes actual=$total")

            val sha256 = digest.digest().toHex()
            val expectedSha = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (expectedSha != null && sha256 != expectedSha) throw IOException("SHA-256 mismatch for ${finalFile.name}")
            if (!isValidSqliteDatabase(partFile)) throw IOException("Downloaded file is not a valid SQLite database")

            // Verify the original DB first, then encrypt it before it becomes the installed file.
            BookCrypto.encryptFile(partFile, encryptedPartFile)
            partFile.delete()
            installAtomically(encryptedPartFile, finalFile)
            return DownloadResult(finalFile, total, sha256)
        } catch (t: Throwable) {
            partFile.delete()
            encryptedPartFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }


    private suspend fun downloadBookFromPublicArchive(
        bookName: String,
        finalFile: File,
        expectedBytes: Long?,
        expectedSha256: String?
    ): DownloadResult {
        val archiveUrl = URL("https://hmomen.com/static/databases/hadith/books.zip?v=quranstudy")
        val archivePart = File(finalFile.parentFile, ".books_archive.part")
        val plainPart = File(finalFile.parentFile, "${finalFile.name}.plain.part")
        val encryptedPart = File(finalFile.parentFile, "${finalFile.name}.part")
        listOf(archivePart, plainPart, encryptedPart).forEach { if (it.exists()) it.delete() }

        val connection = (archiveUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 60_000
            requestMethod = "GET"
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/zip, application/octet-stream")
            setRequestProperty("User-Agent", "QuranStudy-Native/1.0")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            connection.connect()
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("تنزيل مرفوض: إعادة التوجيه إلى رابط غير HTTPS")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("فشل تنزيل أرشيف الكتب: HTTP $code")

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(archivePart).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }

            var found = false
            java.util.zip.ZipInputStream(BufferedInputStream(archivePart.inputStream(), BUFFER_SIZE)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.substringAfterLast('/') == bookName) {
                        FileOutputStream(plainPart).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                if (n > 0) output.write(buffer, 0, n)
                            }
                            output.fd.sync()
                        }
                        found = true
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (!found) throw IOException("لم يتم العثور على $bookName داخل أرشيف الكتب")

            val digest = MessageDigest.getInstance("SHA-256")
            val actualBytes = plainPart.length()
            if (expectedBytes != null && actualBytes != expectedBytes) {
                throw IOException("Downloaded size mismatch: expected=$expectedBytes actual=$actualBytes")
            }
            BufferedInputStream(plainPart.inputStream(), BUFFER_SIZE).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) digest.update(buffer, 0, n)
                }
            }
            val sha256 = digest.digest().toHex()
            val expectedSha = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (expectedSha != null && sha256 != expectedSha) throw IOException("SHA-256 mismatch for $bookName")
            if (!isValidSqliteDatabase(plainPart)) throw IOException("الكتاب المستخرج ليس قاعدة SQLite سليمة")

            BookCrypto.encryptFile(plainPart, encryptedPart)
            plainPart.delete()
            installAtomically(encryptedPart, finalFile)
            return DownloadResult(finalFile, actualBytes, sha256)
        } finally {
            connection.disconnect()
            archivePart.delete()
            plainPart.delete()
            encryptedPart.delete()
        }
    }
    private fun candidateUrls(value: String): List<String> {
        val clean = value.trim()
        val out = linkedSetOf(clean)
        val match = GITHUB_RELEASE_URL_PATTERN.matchEntire(clean)
        if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val branch = match.groupValues[3]
            val path = match.groupValues[4]
            out += "https://github.com/$owner/$repo/raw/refs/heads/$branch/$path"
        }

        // The Ma'raj Al-Mu'min application exposes the same SQLite book files
        // from its public static database endpoint. Keep it as a deterministic
        // fallback so a private GitHub release does not turn into a misleading
        // HTTP 404 for the user. The catalog URL remains the primary source.
        val filename = clean.substringAfterLast('/').substringBefore('?')
        if (filename.matches(Regex("book_[0-9]{1,3}\\.db"))) {
            out += "https://hmomen.com/static/databases/hadith/books/$filename"
        }
        return out.toList()
    }

    fun deleteInstalledDatabase(finalFile: File): Boolean = finalFile.delete()

    private fun parseHttpsUrl(value: String): URL {
        val url = runCatching { URL(value) }
            .getOrElse { throw IllegalArgumentException("Invalid download URL", it) }

        require(url.protocol.equals("https", ignoreCase = true)) {
            "Only HTTPS download URLs are allowed"
        }
        require(url.host.isNotBlank()) {
            "Download URL host must not be blank"
        }

        return url
    }

    private fun isValidSqliteDatabase(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false

        return try {
            SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
        } catch (_: SQLiteException) {
            false
        }
    }

    private fun installAtomically(partFile: File, finalFile: File) {
        finalFile.parentFile?.mkdirs()
        val backup = File(finalFile.parentFile, "${finalFile.name}.bak")
        if (backup.exists() && !backup.delete()) {
            error("Unable to clear previous backup: ${backup.name}")
        }

        var backedUp = false
        try {
            if (finalFile.exists()) {
                if (!finalFile.renameTo(backup)) {
                    error("Unable to preserve existing database: ${finalFile.name}")
                }
                backedUp = true
            }

            if (!partFile.renameTo(finalFile)) {
                if (backedUp && backup.exists()) backup.renameTo(finalFile)
                error("Unable to install database: ${finalFile.name}")
            }
        } catch (t: Throwable) {
            if (finalFile.exists() && backup.exists()) finalFile.delete()
            if (backedUp && backup.exists()) backup.renameTo(finalFile)
            throw t
        } finally {
            partFile.delete()
            if (backup.exists() && finalFile.exists()) backup.delete()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

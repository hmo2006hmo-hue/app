package com.muddakir.quran

import android.content.Context
import android.database.Cursor
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

/**
 * Dedicated tafsir catalog/download/reader boundary.
 *
 * The five original tafsir databases are kept outside the APK and downloaded
 * on demand from the public source URLs. The reader discovers the real schema
 * of each SQLite database instead of hard-coding one query for all files.
 */
object TafsirRepository {
    data class CatalogItem(
        val id: Int,
        val fileName: String,
        val title: String,
        val author: String,
        val downloadUrl: String,
        val estimatedSizeBytes: Long? = null
    )

    data class AyahTafsir(
        val surahNumber: Int,
        val surahName: String,
        val ayahNumber: Int,
        val type: String,
        val author: String,
        val text: String,
        val tafsirId: Int
    )

    data class SearchResult(
        val surahNumber: Int,
        val surahName: String,
        val ayahNumber: Int,
        val type: String,
        val author: String,
        val text: String,
        val snippet: String,
        val tafsirId: Int
    )

    private data class Schema(
        val table: String,
        val surahColumn: String?,
        val ayahColumn: String?,
        val startAyahColumn: String?,
        val endAyahColumn: String?,
        val textColumn: String,
        val globalAyahColumn: String? = null,
        val compositeAyahColumn: String? = null
    )

    private const val PREFS = "tafsir_prefs"
    private const val KEY_SELECTED = "selected_tafsir_id"
    private const val ROOT_DIR = "library/tafsirs"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val BUFFER_SIZE = 64 * 1024

    private val SURAH_AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
        5, 4, 5, 6
    )

    private val catalog = listOf(
        CatalogItem(1, "Tafsir_001.db", "تفسير القرآن العظيم (تفسير ابن كثير)", "الإمام الحافظ ابن كثير", "https://github.com/hmo2006hmo-hue/explanation/releases/download/explanation_1v/Tafsir_001.db", 26_500_096L),
        CatalogItem(2, "Tafsir_002.db", "تيسير الكريم الرحمن في تفسير كلام المنان (تفسير السعدي)", "الشيخ عبد الرحمن بن ناصر السعدي", "https://github.com/hmo2006hmo-hue/explanation/releases/download/explanation_1v/Tafsir_002.db", 27_807_744L),
        CatalogItem(4, "Tafsir_004.db", "الجامع لأحكام القرآن (تفسير القرطبي)", "الإمام الأندلسي القرطبي", "https://github.com/hmo2006hmo-hue/explanation/releases/download/explanation_1v/Tafsir_004.db", 16_486_400L),
        CatalogItem(5, "Tafsir_005.db", "جامع البيان عن تأويل آي القرآن (تفسير الطبري)", "الإمام محمد بن جرير الطبري", "https://github.com/hmo2006hmo-hue/explanation/releases/download/explanation_1v/Tafsir_005.db", 29_389_824L),
        CatalogItem(6, "Tafsir_006.db", "التفسير الميسر", "نخبة من العلماء (مجمع الملك فهد)", "https://github.com/hmo2006hmo-hue/explanation/releases/download/explanation_1v/Tafsir_006.db", 16_565_248L)
    )

    fun catalog(): List<CatalogItem> = catalog

    fun catalogItem(id: Int): CatalogItem? = catalog.firstOrNull { it.id == id }

    fun selectedId(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SELECTED, -1)
            .takeIf { it > 0 }

    fun select(context: Context, id: Int) {
        require(catalogItem(id) != null) { "معرف التفسير غير موجود" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SELECTED, id).apply()
    }

    fun installedFile(context: Context, item: CatalogItem): File? =
        File(context.filesDir, "$ROOT_DIR/${safeFile(item.fileName)}")
            .takeIf { it.isFile && it.length() > 0L && isValidSqlite(it) }

    fun isInstalled(context: Context, item: CatalogItem): Boolean = installedFile(context, item) != null

    fun installedItems(context: Context): List<CatalogItem> = catalog.filter { isInstalled(context, it) }

    suspend fun install(context: Context, item: CatalogItem): File = withContext(Dispatchers.IO) {
        require(item.downloadUrl.startsWith("https://", ignoreCase = true)) { "رابط التفسير يجب أن يكون HTTPS" }
        val dir = File(context.filesDir, ROOT_DIR).apply { mkdirs() }
        val target = File(dir, safeFile(item.fileName))
        val part = File(dir, "${target.name}.part")
        if (part.exists()) part.delete()

        val connection = (URL(item.downloadUrl).openConnection() as HttpURLConnection).apply {
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
                throw IOException("تنزيل التفسير مرفوض: الرابط النهائي ليس HTTPS")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("فشل تنزيل التفسير: HTTP $code")

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }
            if (item.estimatedSizeBytes != null && part.length() != item.estimatedSizeBytes) {
                throw IOException("حجم التفسير غير مطابق: المتوقع=${item.estimatedSizeBytes} الفعلي=${part.length()}")
            }
            if (!isValidSqlite(part)) throw IOException("الملف المحمل ليس قاعدة SQLite سليمة")
            atomicReplace(part, target)
            select(context, item.id)
            target
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    suspend fun delete(context: Context, item: CatalogItem): Boolean = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "$ROOT_DIR/${safeFile(item.fileName)}")
        val deleted = !target.exists() || target.delete()
        if (selectedId(context) == item.id) {
            val fallback = installedItems(context).firstOrNull { it.id != item.id }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (fallback != null) prefs.edit().putInt(KEY_SELECTED, fallback.id).apply()
            else prefs.edit().remove(KEY_SELECTED).apply()
        }
        deleted
    }

    /** Returns the selected installed tafsir, installing the default only when needed. */
    suspend fun get(context: Context, surah: Int, ayah: Int): AyahTafsir? = withContext(Dispatchers.IO) {
        if (surah !in 1..114 || ayah <= 0) return@withContext null
        var installed = installedItems(context)
        if (installed.isEmpty()) {
            val default = catalogItem(6) ?: return@withContext null
            install(context, default)
            installed = installedItems(context)
        }

        // Prefer the user's selected tafsir, but do not make a perfectly valid
        // installed source unusable just because an older/variant DB schema
        // needs a different lookup strategy.
        val selected = selectedId(context)
        val ordered = buildList {
            installed.firstOrNull { it.id == selected }?.let(::add)
            installed.filter { it.id != selected }.forEach(::add)
        }
        ordered.firstNotNullOfOrNull { item -> queryItem(context, item, surah, ayah) }
    }

    /**
     * Reads the requested ayah from one specific tafsir database. The caller
     * is responsible for downloading the source first when it is not installed.
     */
    suspend fun get(
        context: Context,
        surah: Int,
        ayah: Int,
        tafsirId: Int
    ): AyahTafsir? = withContext(Dispatchers.IO) {
        if (surah !in 1..114 || ayah <= 0) return@withContext null
        val item = catalogItem(tafsirId) ?: return@withContext null
        queryItem(context, item, surah, ayah)
    }

    suspend fun search(context: Context, query: String, limit: Int = 120, offset: Int = 0): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val out = ArrayList<SearchResult>()
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.take(8)
        for (item in installedItems(context)) {
            if (out.size >= limit + offset) break
            installedFile(context, item)?.let { file ->
                runCatching {
                    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        val schema = discoverSchema(db) ?: return@use
                        val text = schema.textColumn
                        val clauses = terms.map { "$text LIKE ?" }
                        val args = terms.map { "%$it%" }.toTypedArray()
                        val sql = buildString {
                            append("SELECT * FROM ").append(qi(schema.table)).append(" WHERE ")
                            append(clauses.joinToString(" AND "))
                            append(" LIMIT ?")
                        }
                        val cursor = db.rawQuery(sql, args + arrayOf((limit + offset).toString()))
                        cursor.use {
                            val surahIdx = schema.surahColumn?.let(it::getColumnIndex)
                            val ayahIdx = schema.ayahColumn?.let(it::getColumnIndex)
                            val startIdx = schema.startAyahColumn?.let(it::getColumnIndex)
                            val endIdx = schema.endAyahColumn?.let(it::getColumnIndex)
                            val textIdx = it.getColumnIndex(schema.textColumn)
                            while (it.moveToNext() && out.size < limit + offset) {
                                val s = if (surahIdx != null && surahIdx >= 0) it.getIntOrNull(surahIdx) ?: 0 else 0
                                val a = if (ayahIdx != null && ayahIdx >= 0) it.getIntOrNull(ayahIdx) ?: 0 else (if (startIdx != null && startIdx >= 0) it.getIntOrNull(startIdx) ?: 0 else 0)
                                val body = if (textIdx >= 0) it.getString(textIdx).orEmpty() else ""
                                if (s in 1..114 && body.isNotBlank()) {
                                    out += SearchResult(s, surahName(s), a, "tafsir", item.author, body, snippet(body, q), item.id)
                                }
                            }
                        }
                    }
                }
            }
        }
        out.drop(offset.coerceAtMost(out.size)).take(limit)
    }

    /** Ensures at least one tafsir is installed; the default is the concise Al-Muyassar. */
    suspend fun ensureInstalled(context: Context): File? = withContext(Dispatchers.IO) {
        installedItems(context).firstOrNull()?.let { installedFile(context, it) }
            ?: run {
                val default = catalogItem(6) ?: return@withContext null
                install(context, default)
            }
    }

    private fun queryItem(context: Context, item: CatalogItem, surah: Int, ayah: Int): AyahTafsir? {
        val file = installedFile(context, item) ?: return null
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val schema = discoverSchema(db) ?: return@use null
                readMatchingRow(db, schema, surah, ayah, item)
            }
        }.getOrNull()
    }

    private fun readMatchingRow(
        db: SQLiteDatabase,
        schema: Schema,
        surah: Int,
        ayah: Int,
        item: CatalogItem
    ): AyahTafsir? {
        val table = qi(schema.table)
        val text = qi(schema.textColumn)

        fun rowValue(cursor: Cursor, index: Int, fallback: Int): Int {
            return if (index >= 0 && !cursor.isNull(index)) {
                cursor.getString(index).toIntOrNull() ?: fallback
            } else {
                fallback
            }
        }

        fun read(
            sql: String,
            args: Array<String>,
            rowSurah: Int = surah,
            rowAyah: Int = ayah,
            surahIndex: Int = -1,
            ayahIndex: Int = -1
        ): AyahTafsir? {
            db.rawQuery(sql, args).use { cursor ->
                if (!cursor.moveToFirst()) return null
                val body = cursor.getString(0).orEmpty().trim()
                if (body.isBlank()) return null
                val actualSurah = rowValue(cursor, surahIndex, rowSurah).coerceIn(1, 114)
                val actualAyah = rowValue(cursor, ayahIndex, rowAyah).coerceAtLeast(1)
                return AyahTafsir(
                    actualSurah,
                    surahName(actualSurah),
                    actualAyah,
                    "tafsir",
                    item.author,
                    body,
                    item.id
                )
            }
        }

        val surahCandidates = listOf(surah, surah - 1).filter { it > 0 }.distinct()
        val ayahCandidates = listOf(ayah, ayah - 1, ayah + 1).filter { it > 0 }.distinct()

        fun tryNamedPair(surahColumn: String, ayahColumn: String): AyahTafsir? {
            if (surahColumn == ayahColumn) return null
            val sql = "SELECT $text, ${qi(surahColumn)}, ${qi(ayahColumn)} FROM $table " +
                "WHERE ${qi(surahColumn)} = ? AND ${qi(ayahColumn)} = ? LIMIT 1"
            for (sValue in surahCandidates) {
                for (aValue in ayahCandidates) {
                    read(sql, arrayOf(sValue.toString(), aValue.toString()), surah, ayah, 1, 2)?.let {
                        return if (sValue != surah || aValue != ayah) {
                            it.copy(surahNumber = surah, surahName = surahName(surah), ayahNumber = ayah)
                        } else {
                            it
                        }
                    }
                }
            }
            return null
        }

        schema.surahColumn?.let { surahColumn ->
            schema.ayahColumn?.let { ayahColumn ->
                tryNamedPair(surahColumn, ayahColumn)?.let { return it }
            }

            schema.startAyahColumn?.let { startColumn ->
                val start = qi(startColumn)
                val end = schema.endAyahColumn?.let(::qi)
                val sql = if (end != null) {
                    "SELECT $text, ${qi(surahColumn)}, $start FROM $table " +
                        "WHERE ${qi(surahColumn)} = ? AND ? BETWEEN $start AND $end LIMIT 1"
                } else {
                    "SELECT $text, ${qi(surahColumn)}, $start FROM $table " +
                        "WHERE ${qi(surahColumn)} = ? AND $start = ? LIMIT 1"
                }
                for (sValue in surahCandidates) {
                    for (aValue in ayahCandidates) {
                        read(sql, arrayOf(sValue.toString(), aValue.toString()), surah, ayah, 1, -1)?.let {
                            return it.copy(surahNumber = surah, surahName = surahName(surah), ayahNumber = ayah)
                        }
                    }
                }
            }
        }

        schema.globalAyahColumn?.let { globalColumn ->
            val global = flattenAyahIndex(surah, ayah)
            val sql = "SELECT $text FROM $table WHERE ${qi(globalColumn)} = ? LIMIT 1"
            for (candidate in listOf(global, global - 1, global + 1).distinct().filter { it > 0 }) {
                read(sql, arrayOf(candidate.toString()), surah, ayah)?.let { return it }
            }
        }

        schema.compositeAyahColumn?.let { locationColumn ->
            val loc = qi(locationColumn)
            val compactExpr =
                "REPLACE(REPLACE(REPLACE(REPLACE($loc, ' ', ''), '/', ':'), '-', ':'), '|', ':')"
            val sql = "SELECT $text FROM $table WHERE $compactExpr = ? LIMIT 1"
            listOf("$surah:$ayah", "$surah/$ayah", "$surah-$ayah", "$surah|$ayah").firstNotNullOfOrNull { key ->
                read(sql, arrayOf(key))
            }?.let { return it }
        }

        // Last-resort compatibility for vendor-specific schemas: inspect likely
        // coordinate columns by their types/names and try their value pairs.
        genericCoordinateLookup(db, schema.table, schema.textColumn, surah, ayah, item)?.let { return it }

        return null
    }

    private fun genericCoordinateLookup(
        db: SQLiteDatabase,
        tableName: String,
        textColumn: String,
        surah: Int,
        ayah: Int,
        item: CatalogItem
    ): AyahTafsir? {
        val columns = mutableListOf<Pair<String, String?>>()
        db.rawQuery("PRAGMA table_info(${qi(tableName)})", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                val type = if (c.isNull(2)) null else c.getString(2)
                columns += name to type
            }
        }

        val numericColumns = columns.filter { (name, type) ->
            val n = normalize(name)
            val t = type.orEmpty().uppercase()
            t.contains("INT") ||
                n.contains("surah") || n.contains("sura") || n.contains("sora") ||
                n.contains("ayah") || n.contains("aya") || n.contains("verse")
        }.map { it.first }
            .distinct()
            .filterNot { it == textColumn }
            .take(10)

        val preferredSurah = numericColumns.filter {
            val n = normalize(it)
            n.contains("surah") || n.contains("sura") || n.contains("sora") || n.contains("chapter")
        }.ifEmpty { numericColumns }
        val preferredAyah = numericColumns.filter {
            val n = normalize(it)
            n.contains("ayah") || n.contains("aya") || n.contains("verse")
        }.ifEmpty { numericColumns }

        val table = qi(tableName)
        val text = qi(textColumn)

        fun find(sCol: String, aCol: String): AyahTafsir? {
            if (sCol == aCol) return null
            val sql = "SELECT $text, ${qi(sCol)}, ${qi(aCol)} FROM $table " +
                "WHERE ${qi(sCol)} = ? AND ${qi(aCol)} = ? LIMIT 1"
            val sValues = listOf(surah, surah - 1).filter { it > 0 }.distinct()
            val aValues = listOf(ayah, ayah - 1, ayah + 1).filter { it > 0 }.distinct()
            for (sValue in sValues) {
                for (aValue in aValues) {
                    db.rawQuery(sql, arrayOf(sValue.toString(), aValue.toString())).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val body = cursor.getString(0).orEmpty().trim()
                            if (body.isNotBlank()) {
                                return AyahTafsir(
                                    surah,
                                    surahName(surah),
                                    ayah,
                                    "tafsir",
                                    item.author,
                                    body,
                                    item.id
                                )
                            }
                        }
                    }
                }
            }
            return null
        }

        for (sCol in preferredSurah.take(6)) {
            for (aCol in preferredAyah.take(6)) {
                find(sCol, aCol)?.let { return it }
            }
        }

        val compositeColumns = columns.map { it.first }.filter {
            val n = normalize(it)
            n.contains("location") || n.contains("ayah") || n.contains("aya") ||
                n.contains("verse") || n.contains("sura") || n.contains("sora")
        }.filterNot { it == textColumn }.take(6)

        val candidates = listOf("$surah:$ayah", "$surah/$ayah", "$surah-$ayah", "$surah|$ayah")
        for (locationColumn in compositeColumns) {
            val loc = qi(locationColumn)
            val expr = "REPLACE(REPLACE(REPLACE(REPLACE($loc, ' ', ''), '/', ':'), '-', ':'), '|', ':')"
            val sql = "SELECT $text FROM $table WHERE $expr = ? LIMIT 1"
            for (key in candidates) {
                db.rawQuery(sql, arrayOf(key)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val body = cursor.getString(0).orEmpty().trim()
                        if (body.isNotBlank()) {
                            return AyahTafsir(surah, surahName(surah), ayah, "tafsir", item.author, body, item.id)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun flattenAyahIndex(surah: Int, ayah: Int): Int {
        var total = ayah
        for (i in 0 until (surah - 1).coerceAtLeast(0)) total += SURAH_AYAH_COUNTS[i]
        return total
    }

    private fun discoverSchema(db: SQLiteDatabase): Schema? {
        val tables = buildList {
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                null
            ).use { c ->
                while (c.moveToNext()) add(c.getString(0))
            }
        }

        data class Candidate(
            val schema: Schema,
            val score: Int
        )

        val candidates = ArrayList<Candidate>()
        for (table in tables) {
            val columns = mutableListOf<Pair<String, String?>>()
            db.rawQuery("PRAGMA table_info(${qi(table)})", null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1)
                    val type = if (c.isNull(2)) null else c.getString(2)
                    columns += name to type
                }
            }
            if (columns.isEmpty()) continue

            val normalized = columns.associate { normalize(it.first) to it.first }

            val text = firstColumn(
                normalized,
                "text", "tafsir_text", "text_tafsir", "content", "tafsir",
                "tafseer", "tafser", "explanation", "nass", "body", "description"
            ) ?: columns.firstOrNull { (name, type) ->
                val n = normalize(name)
                val t = type.orEmpty().uppercase()
                (n.contains("tafs") || n.contains("explan") || n.contains("content") || n.contains("text") || n.contains("body") || n.contains("nass")) &&
                    (t.isBlank() || t.contains("CHAR") || t.contains("TEXT") || t.contains("CLOB"))
            }?.first ?: continue

            val surah = firstColumn(
                normalized,
                "surah_id", "surah_number", "surah_no", "surah", "sourah",
                "sura_no", "sura", "sora_no", "sora", "sourah_no", "chapter_id"
            ) ?: columns.firstOrNull { (name, _) ->
                normalize(name).let { n ->
                    n == "surahnum" || n == "suranumber" || n == "sourahnum" ||
                        n == "sora" || n == "sorano" || n.contains("surah") || n.contains("sura") || n.contains("sora")
                }
            }?.first

            val ayah = firstColumn(
                normalized,
                "ayah_id", "ayah_number", "ayah_no", "ayah", "aya", "aya_no",
                "verse_id", "verse_number", "verse_no", "verse"
            ) ?: columns.firstOrNull { (name, _) ->
                normalize(name).let { n ->
                    n == "ayanumber" || n == "ayano" || n == "ayaid" ||
                        n.contains("ayah") || n.contains("aya") || n.contains("verse")
                }
            }?.first

            val start = firstColumn(
                normalized, "ayah_start", "start_ayah", "first_ayah",
                "ayah_from", "from_ayah", "start"
            )
            val end = firstColumn(
                normalized, "ayah_end", "end_ayah", "last_ayah",
                "ayah_to", "to_ayah", "end"
            )

            val global = if (surah == null && ayah == null) {
                firstColumn(
                    normalized, "global_ayah", "ayah_global", "global_id",
                    "verse_global", "verse_index", "global_index"
                ) ?: columns.firstOrNull { (name, _) ->
                    normalize(name).let { n ->
                        n == "id" || n == "pk" || n == "rowid"
                    }
                }?.first
            } else null

            val composite = if (surah == null && ayah == null && global == null) {
                columns.firstOrNull { (name, _) ->
                    normalize(name).let { n ->
                        n.contains("ayah") || n.contains("aya") || n.contains("verse") ||
                            n.contains("sura") || n.contains("sora") || n.contains("location")
                    }
                }?.first
            } else null

            if (surah == null && (global == null && composite == null)) continue
            if (ayah == null && start == null && global == null && composite == null) continue

            var score = 0
            val tableName = normalize(table)
            if (tableName == "tafsir" || tableName == "tafseer" || tableName.contains("tafsir")) score += 100
            if (tableName == "tafsir_ayahs") score += 25
            if (text == "text" || normalize(text) == "tafsir_text") score += 10
            if (surah != null) score += 30
            if (ayah != null) score += 30
            if (global != null) score += 10
            if (composite != null) score += 5

            candidates += Candidate(
                Schema(table, surah, ayah, start, end, text, global, composite),
                score
            )
        }
        return candidates.maxByOrNull { it.score }?.schema
    }

    private fun firstColumn(map: Map<String, String>, vararg names: String): String? =
        names.firstNotNullOfOrNull { map[normalize(it)] }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun qi(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

    private fun snippet(text: String, query: String): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        val needle = query.split(Regex("\\s+")).firstOrNull().orEmpty()
        val pos = compact.indexOf(needle, ignoreCase = true)
        val start = if (pos < 0) 0 else (pos - 70).coerceAtLeast(0)
        return compact.substring(start, minOf(compact.length, start + 180))
    }

    private fun surahName(number: Int): String = SURAH_NAMES.getOrElse(number - 1) { "السورة $number" }

    private fun isValidSqlite(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", true)
            }
        }
    }.getOrDefault(false)

    private fun atomicReplace(part: File, target: File) {
        target.parentFile?.mkdirs()
        val backup = File(target.parentFile, "${target.name}.bak")
        if (backup.exists()) backup.delete()
        var backedUp = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) error("تعذر حفظ النسخة القديمة من التفسير")
                backedUp = true
            }
            if (!part.renameTo(target)) error("تعذر تثبيت قاعدة التفسير")
            if (backup.exists()) backup.delete()
        } catch (t: Throwable) {
            target.delete()
            if (backedUp && backup.exists()) backup.renameTo(target)
            throw t
        } finally {
            part.delete()
        }
    }

    private fun safeFile(value: String): String {
        require(value.matches(Regex("Tafsir_[0-9]{3}\\.db"))) { "اسم ملف تفسير غير صالح" }
        return value
    }

    private val SURAH_NAMES = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس", "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
    )
}

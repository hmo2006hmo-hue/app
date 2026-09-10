package com.muddakir.quran

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only repository for the original Ma'raj Al-Mu'min SQLite book format.
 *
 * Each book contains:
 *   chapters(id, name, source_name)
 *   hadith(id, content, sayer, source, chapter_id)
 *
 * The database itself is always kept encrypted at rest. A short-lived plaintext
 * copy is materialized in the app cache only while SQLite is being opened.
 */
object BookRepository {
    data class Chapter(
        val id: Long,
        val title: String,
        val sourceName: String?,
        val sortOrder: Int
    )

    data class Item(
        val id: Long,
        val chapterId: Long?,
        val itemNumber: Int?,
        val title: String?,
        val content: String,
        val author: String?,
        val source: String?,
        val page: Int?,
        val sortOrder: Int
    )

    data class SearchResult(
        val item: Item,
        val snippet: String?
    )

    suspend fun getChapters(
        context: Context,
        fileName: String
    ): List<Chapter> = withContext(Dispatchers.IO) {
        openSession(context, fileName).use { it.getChapters() }
    }

    suspend fun getItems(
        context: Context,
        fileName: String,
        chapterId: Long? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<Item> = withContext(Dispatchers.IO) {
        openSession(context, fileName).use { it.getItems(chapterId, limit, offset) }
    }

    suspend fun getItem(
        context: Context,
        fileName: String,
        itemId: Long
    ): Item? = withContext(Dispatchers.IO) {
        openSession(context, fileName).use { it.getItem(itemId) }
    }

    suspend fun search(
        context: Context,
        fileName: String,
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        openSession(context, fileName).use { it.search(query, limit, offset) }
    }

    /**
     * Keeps one decrypted SQLite copy open for the whole reader session.
     * This avoids decrypting the complete book again for every chapter/search
     * operation, which was the main source of the long delays in the reader.
     */
    suspend fun openSession(context: Context, fileName: String): ReaderSession =
        withContext(Dispatchers.IO) {
            requireSafeFileName(fileName)
            val encrypted = BookStorage.encryptedFile(context, fileName)
            check(encrypted.isFile && BookCrypto.isEncrypted(encrypted)) {
                "الكتاب غير مثبت: $fileName"
            }

            val temp = File.createTempFile("quranstudy_reader_", ".db", context.cacheDir)
            try {
                BookCrypto.decryptToTemp(encrypted, temp)
                val db = SQLiteDatabase.openDatabase(
                    temp.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                // Keep the reader connection tuned for large read-only books.
                // These settings are per connection and do not modify the book.
                runCatching { db.execSQL("PRAGMA temp_store=MEMORY") }
                runCatching { db.execSQL("PRAGMA cache_size=-16384") }
                runCatching { db.execSQL("PRAGMA mmap_size=268435456") }
                ReaderSession(db, temp)
            } catch (t: Throwable) {
                temp.delete()
                throw IllegalStateException("تعذر فتح قاعدة الكتاب المحمية: $fileName", t)
            }
        }

    class ReaderSession internal constructor(
        private val db: SQLiteDatabase,
        private val tempFile: File
    ) : AutoCloseable {

        fun getChapters(): List<Chapter> =
            db.rawQuery(
                "SELECT id, name, source_name FROM chapters ORDER BY id",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Chapter(
                                id = cursor.getLong(0),
                                title = cursor.getString(1),
                                sourceName = cursor.getStringOrNull(2),
                                sortOrder = cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            )
                        )
                    }
                }
            }

        fun getItems(
            chapterId: Long? = null,
            limit: Int = 50,
            offset: Int = 0
        ): List<Item> {
            require(limit in 1..500) { "limit must be between 1 and 500" }
            require(offset >= 0) { "offset must be >= 0" }
            val sql = if (chapterId == null) {
                "SELECT id, chapter_id, content, sayer, source FROM hadith ORDER BY id LIMIT ? OFFSET ?"
            } else {
                "SELECT id, chapter_id, content, sayer, source FROM hadith WHERE chapter_id = ? ORDER BY id LIMIT ? OFFSET ?"
            }
            val args = if (chapterId == null) {
                arrayOf(limit.toString(), offset.toString())
            } else {
                arrayOf(chapterId.toString(), limit.toString(), offset.toString())
            }
            return db.rawQuery(sql, args).use(::readItems)
        }

        fun getItem(itemId: Long): Item? =
            db.rawQuery(
                "SELECT id, chapter_id, content, sayer, source FROM hadith WHERE id = ? LIMIT 1",
                arrayOf(itemId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) readItem(cursor) else null
            }

        fun search(query: String, limit: Int = 50, offset: Int = 0): List<SearchResult> {
            require(query.isNotBlank()) { "query must not be blank" }
            require(limit in 1..200) { "limit must be between 1 and 200" }
            require(offset >= 0) { "offset must be >= 0" }
            val tokens = query.trim().split(Regex("\\s+"))
                .map(String::trim).filter(String::isNotBlank).take(8)
            if (tokens.isEmpty()) return emptyList()

            val clauses = tokens.joinToString(" AND ") {
                "(h.content LIKE ? ESCAPE '\\' OR h.sayer LIKE ? ESCAPE '\\' OR h.source LIKE ? ESCAPE '\\' OR c.name LIKE ? ESCAPE '\\')"
            }
            val sql = """
                SELECT h.id, h.chapter_id, h.content, h.sayer, h.source
                FROM hadith h
                LEFT JOIN chapters c ON c.id = h.chapter_id
                WHERE $clauses
                ORDER BY h.id
                LIMIT ? OFFSET ?
            """.trimIndent()
            val args = ArrayList<String>(tokens.size * 4 + 2)
            tokens.forEach { token ->
                val pattern = "%${escapeLike(token)}%"
                repeat(4) { args += pattern }
            }
            args += limit.toString()
            args += offset.toString()
            return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val item = readItem(cursor)
                        add(SearchResult(item, item.content.take(180)))
                    }
                }
            }
        }

        /**
         * Best-effort lookup for tafsir books stored in the library catalog.
         * Most of these books use the generic chapters/hadith schema, so the
         * surah is located from the chapter title and the Quran verse text is
         * then used as a fast anchor inside that chapter.
         */
        fun findTafsirText(surahName: String, ayahText: String, ayahNumber: Int): String? {
            val chapters = getChapters()
            val normalizedSurah = normalizeArabic(surahName)
            val chapter = chapters.firstOrNull {
                val title = normalizeArabic(it.title)
                title == normalizedSurah ||
                    title.contains(normalizedSurah) ||
                    normalizedSurah.contains(title)
            }

            val tokens = normalizeArabic(ayahText)
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
                .distinct()
                .take(5)

            fun query(tokensToUse: List<String>, chapterId: Long?): String? {
                if (tokensToUse.isEmpty()) return null
                val clauses = tokensToUse.joinToString(" AND ") { "content LIKE ? ESCAPE '\\'" }
                val args = tokensToUse.map { "%${escapeLike(it)}%" }.toMutableList()
                val sql = if (chapterId != null) {
                    "SELECT content FROM hadith WHERE chapter_id = ? AND $clauses ORDER BY id LIMIT 1"
                        .also { args.add(0, chapterId.toString()) }
                } else {
                    "SELECT content FROM hadith WHERE $clauses ORDER BY id LIMIT 1"
                }
                return db.rawQuery(sql, args.toTypedArray()).use { c ->
                    if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotBlank() } else null
                }
            }

            // Prefer the exact chapter and progressively relax the text anchor
            // because editions differ in punctuation/diacritics and may split
            // a Quran verse from its tafsir in different ways.
            for (count in tokens.size downTo 1) {
                query(tokens.take(count), chapter?.id)?.let { return it }
            }
            for (count in tokens.size downTo 2) {
                query(tokens.take(count), null)?.let { return it }
            }

            // Some editions do not repeat the Quran text. In that case, a
            // chapter-level first item is a useful fallback instead of showing
            // an empty tafsir panel.
            chapter?.let {
                db.rawQuery(
                    "SELECT content FROM hadith WHERE chapter_id = ? ORDER BY id LIMIT 1",
                    arrayOf(it.id.toString())
                ).use { c ->
                    if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { text -> text.isNotBlank() }?.let { return it }
                }
            }
            return null
        }

        private fun normalizeArabic(value: String): String =
            value.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه')
                .replace(Regex("\\s+"), " ")
                .trim()

        override fun close() {
            runCatching { db.close() }
            runCatching { tempFile.delete() }
        }
    }

    suspend fun deleteBook(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        requireSafeFileName(fileName)
        val encrypted = BookStorage.encryptedFile(context, fileName)
        val legacy = File(context.filesDir, "library/books/$fileName")
        encrypted.delete() || legacy.delete()
    }

    private fun readItems(cursor: Cursor): List<Item> = buildList(cursor.count) {
        while (cursor.moveToNext()) add(readItem(cursor))
    }

    private fun readItem(cursor: Cursor): Item = Item(
        id = cursor.getLong(0),
        chapterId = cursor.getLongOrNull(1),
        itemNumber = null,
        title = null,
        content = cursor.getString(2),
        author = cursor.getStringOrNull(3),
        source = cursor.getStringOrNull(4),
        page = null,
        sortOrder = cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun requireSafeFileName(fileName: String) {
        require(fileName.matches(Regex("book_[A-Za-z0-9_-]+\\.db"))) {
            "Invalid book database filename: $fileName"
        }
    }
}

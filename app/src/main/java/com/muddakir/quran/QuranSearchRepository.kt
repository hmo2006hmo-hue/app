package com.muddakir.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only SQLite/FTS5 index for Quran text search.
 * Quran page JSON files remain the source for page rendering.
 */
object QuranSearchRepository {
    data class Result(
        val title: String,
        val subtitle: String,
        val surah: Int,
        val ayah: Int,
        val page: Int,
        val juz: Int,
        val text: String
    )

    private const val ASSET_PATH = "quran/quran_search.db"
    private const val LOCAL_DIR = "search"
    private const val LOCAL_FILE = "quran_search.db"

    suspend fun search(context: Context, query: String, limit: Int = 120): List<Result> =
        withContext(Dispatchers.IO) {
            val q = SearchRepository.normalize(query).replace(Regex("\\s+"), " ").trim()
            if (q.length < 2) return@withContext emptyList()
            require(limit in 1..500)
            ensureDatabase(context)
            openReadOnly(context).use { db ->
                val fts = runCatching {
                    db.rawQuery(
                        """
                        SELECT a.surah, a.surah_name, a.ayah, a.page, a.juz, a.text,
                               snippet(quran_fts, 0, '[', ']', '…', 18) AS snippet
                        FROM quran_fts
                        JOIN quran_ayahs a ON a.id = quran_fts.rowid
                        WHERE quran_fts MATCH ?
                        ORDER BY bm25(quran_fts), a.id
                        LIMIT ?
                        """.trimIndent(),
                        arrayOf(ftsQuery(q), limit.toString())
                    ).use { cursor -> readResults(cursor) }
                }.getOrDefault(emptyList())
                if (fts.isNotEmpty()) return@withContext fts

                val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (terms.isEmpty()) return@withContext emptyList()
                val where = terms.indices.joinToString(" AND ") { "normalized_text LIKE ?" }
                val args = terms.map { "%$it%" }.toTypedArray()
                db.rawQuery(
                    "SELECT surah, surah_name, ayah, page, juz, text FROM quran_ayahs WHERE $where ORDER BY id LIMIT ?",
                    args + limit.toString()
                ).use { cursor ->
                    buildList(cursor.count) {
                        while (cursor.moveToNext()) {
                            val surah = cursor.getInt(0)
                            val ayah = cursor.getInt(2)
                            val text = cursor.getString(5)
                            add(Result("${cursor.getString(1)} — الآية $ayah", text.take(180), surah, ayah, cursor.getInt(3), cursor.getInt(4), text))
                        }
                    }
                }
            }
        }

    private fun readResults(cursor: android.database.Cursor): List<Result> = buildList(cursor.count) {
        while (cursor.moveToNext()) {
            val surah = cursor.getInt(0)
            val ayah = cursor.getInt(2)
            val text = cursor.getString(5)
            val snippet = cursor.getString(6)?.takeIf { it.isNotBlank() } ?: text.take(180)
            add(Result("${cursor.getString(1)} — الآية $ayah", snippet, surah, ayah, cursor.getInt(3), cursor.getInt(4), text))
        }
    }

    private fun ensureDatabase(context: Context): File {
        val target = File(context.filesDir, "$LOCAL_DIR/$LOCAL_FILE")
        if (target.isFile && target.length() > 0L && isValid(target)) return target

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$LOCAL_FILE.part")
        if (temp.exists()) temp.delete()

        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(temp).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.fd.sync()
            }
        }
        if (!isValid(temp)) {
            temp.delete()
            error("Invalid Quran search database")
        }
        val backup = File(target.parentFile, "$LOCAL_FILE.bak")
        if (backup.exists() && !backup.delete()) {
            temp.delete()
            error("Unable to clear previous Quran search backup")
        }

        var backedUp = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    temp.delete()
                    error("Unable to preserve current Quran search database")
                }
                backedUp = true
            }
            if (!temp.renameTo(target)) {
                if (backedUp && backup.exists()) backup.renameTo(target)
                error("Unable to install Quran search database")
            }
        } catch (t: Throwable) {
            if (target.exists() && backup.exists()) target.delete()
            if (backedUp && backup.exists()) backup.renameTo(target)
            throw t
        } finally {
            temp.delete()
            if (backup.exists() && target.exists()) backup.delete()
        }
        return target
    }

    private fun openReadOnly(context: Context): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            File(context.filesDir, "$LOCAL_DIR/$LOCAL_FILE").path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

    private fun ftsQuery(query: String): String =
        query.split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" AND ") { token -> "\"${token.replace("\"", "\"\"")}\"" }

    private fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }
}

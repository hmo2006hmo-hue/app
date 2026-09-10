package com.muddakir.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only access to the new central library catalog.
 *
 * The catalog lives in assets/library.db and is copied to app-private storage
 * on first use. It contains metadata only; book/tafsir content remains in
 * independent databases.
 */
object LibraryCatalogRepository {

    data class Book(
        val id: Long,
        val externalId: String,
        val title: String,
        val author: String?,
        val description: String?,
        val dbFile: String,
        val format: String,
        val cover: String?,
        val sizeBytes: Long?,
        val chaptersCount: Int?,
        val itemsCount: Int?,
        val downloadUrl: String?,
        val sha256: String?,
        val version: Int?,
        val isDownloaded: Boolean,
        val sortOrder: Int?
    )

    private const val ASSET_PATH = "library.db"
    private const val LOCAL_DIR = "library"
    private const val LOCAL_FILE = "library.db"
    private const val CATALOG_VERSION = 2

    suspend fun ensureCatalog(context: Context): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "$LOCAL_DIR/$LOCAL_FILE")
        if (target.isFile && target.length() > 0L) {
            if (isCurrent(target)) return@withContext target
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$LOCAL_FILE.part")
        if (temp.exists()) temp.delete()

        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(temp).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.fd.sync()
            }
        }

        if (!isCurrent(temp)) {
            temp.delete()
            error("Invalid library catalog database")
        }

        val backup = File(target.parentFile, "$LOCAL_FILE.bak")
        if (backup.exists()) backup.delete()

        var movedToBackup = false
        if (target.exists()) {
            movedToBackup = target.renameTo(backup)
            if (!movedToBackup) {
                temp.delete()
                error("Unable to stage existing library catalog")
            }
        }

        if (temp.renameTo(target)) {
            backup.delete()
            target
        } else {
            if (target.exists()) target.delete()
            if (movedToBackup && backup.exists()) backup.renameTo(target)
            temp.delete()
            error("Unable to install library catalog")
        }
    }

    suspend fun getBooks(context: Context): List<Book> = withContext(Dispatchers.IO) {
        ensureCatalog(context)
        val books = openReadOnly(context).use { db ->
            db.rawQuery(
                """
                SELECT id, external_id, title, author, description, db_file, format, cover,
                       size_bytes, chapters_count, items_count, download_url, sha256,
                       version, is_downloaded, sort_order
                FROM books
                ORDER BY COALESCE(sort_order, 2147483647), id
                """.trimIndent(),
                null
            ).use { cursor ->
                buildList(cursor.count) {
                    while (cursor.moveToNext()) {
                        add(readBook(cursor))
                    }
                }
            }
        }
        LibraryBookManager.reconcile(context, books)
        books.map { book ->
            book.copy(isDownloaded = isBookInstalled(context, book))
        }
    }

    suspend fun getDownloadedBooks(context: Context): List<Book> =
        getBooks(context).filter { it.isDownloaded }

    suspend fun refreshBookInstallState(
        context: Context,
        book: Book
    ): Book = withContext(Dispatchers.IO) {
        book.copy(isDownloaded = isBookInstalled(context, book))
    }

    private fun isBookInstalled(context: Context, book: Book): Boolean =
        LibraryBookManager.isInstalled(context, book)

    suspend fun getBook(context: Context, id: Long): Book? = withContext(Dispatchers.IO) {
        ensureCatalog(context)
        openReadOnly(context).use { db ->
            db.rawQuery(
                """
                SELECT id, external_id, title, author, description, db_file, format, cover,
                       size_bytes, chapters_count, items_count, download_url, sha256,
                       version, is_downloaded, sort_order
                FROM books
                WHERE id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) readBook(cursor).let { book ->
                    book.copy(isDownloaded = isBookInstalled(context, book))
                } else null
            }
        }
    }

    suspend fun getBookByExternalId(context: Context, externalId: String): Book? = withContext(Dispatchers.IO) {
        require(externalId.isNotBlank()) { "externalId must not be blank" }
        ensureCatalog(context)
        openReadOnly(context).use { db ->
            db.rawQuery(
                """
                SELECT id, external_id, title, author, description, db_file, format, cover,
                       size_bytes, chapters_count, items_count, download_url, sha256,
                       version, is_downloaded, sort_order
                FROM books
                WHERE external_id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(externalId)
            ).use { cursor ->
                if (cursor.moveToFirst()) readBook(cursor).let { book ->
                    book.copy(isDownloaded = isBookInstalled(context, book))
                } else null
            }
        }
    }

    private fun openReadOnly(context: Context): SQLiteDatabase {
        val file = File(context.filesDir, "$LOCAL_DIR/$LOCAL_FILE")
        if (!file.isFile) error("Library catalog is not installed")
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            throw IllegalStateException("Unable to open library catalog", e)
        }
    }

    private fun isCurrent(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = db.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
                val count = db.rawQuery("SELECT COUNT(*) FROM books", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                val requiredColumns = setOf("id", "external_id", "title", "db_file", "format", "cover", "size_bytes", "chapters_count", "items_count", "download_url", "sha256", "version", "is_downloaded", "sort_order")
                val actualColumns = db.rawQuery("PRAGMA table_info(books)", null).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                }
                version == CATALOG_VERSION && count > 0 && requiredColumns.all { it in actualColumns } &&
                    db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                        cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                    }
            }
        } catch (_: SQLiteException) {
            false
        }
    }

    private fun readBook(cursor: android.database.Cursor): Book = Book(
        id = cursor.getLong(0),
        externalId = cursor.getString(1),
        title = cursor.getString(2),
        author = cursor.getStringOrNull(3),
        description = cursor.getStringOrNull(4),
        dbFile = cursor.getString(5),
        format = cursor.getString(6).lowercase(),
        cover = cursor.getStringOrNull(7),
        sizeBytes = if (cursor.isNull(8)) null else cursor.getLong(8),
        chaptersCount = if (cursor.isNull(9)) null else cursor.getInt(9),
        itemsCount = if (cursor.isNull(10)) null else cursor.getInt(10),
        downloadUrl = cursor.getStringOrNull(11),
        sha256 = cursor.getStringOrNull(12),
        version = if (cursor.isNull(13)) null else cursor.getInt(13),
        isDownloaded = cursor.getInt(14) != 0,
        sortOrder = if (cursor.isNull(15)) null else cursor.getInt(15)
    )

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}

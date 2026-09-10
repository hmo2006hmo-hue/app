package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NativeContentRepository {
    data class Occasion(val month: Int, val day: Int, val title: String, val subtitle: String, val icon: String)

    data class BookMeta(val id: String, val title: String, val author: String, val description: String, val file: String, val downloadUrl: String, val icon: String)

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    suspend fun occasions(context: Context): List<Occasion> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(readAsset(context, "occasions.json"))
            val arr = root.optJSONArray("events") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    add(Occasion(e.optInt("month"), e.optInt("day"), e.optString("title"), e.optString("subtitle"), e.optString("icon", "✨")))
                }
            }
        }.getOrElse { emptyList() }
    }

    /** Compatibility facade: book metadata now comes from the central SQLite catalog. */
    suspend fun books(context: Context): List<BookMeta> = withContext(Dispatchers.IO) {
        LibraryCatalogRepository.getBooks(context).map { book ->
            BookMeta(
                id = book.externalId,
                title = book.title,
                author = book.author.orEmpty(),
                description = book.description.orEmpty(),
                file = book.dbFile,
                downloadUrl = book.downloadUrl.orEmpty(),
                icon = book.cover?.takeIf { it.isNotBlank() } ?: "📚"
            )
        }
    }

    fun booksRoot(context: Context): File = File(context.filesDir, "library/books").apply { mkdirs() }

    fun bookFile(context: Context, book: BookMeta): File {
        require(book.id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid book id" }
        return File(booksRoot(context), book.id).resolve("book.json")
    }

    /** Delegates installation to the single format-aware library manager. */
    suspend fun downloadBook(context: Context, book: BookMeta, onProgress: (Int) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val catalogBook = LibraryCatalogRepository.getBookByExternalId(context, book.id)
                ?: error("الكتاب غير موجود في كتالوج المكتبة")
            onProgress(0)
            val result = LibraryBookManager.install(context, catalogBook)
            onProgress(100)
            result.file
        }
    }

    suspend fun installedBooks(context: Context, metas: List<BookMeta>): Set<String> = withContext(Dispatchers.IO) {
        metas.mapNotNull { meta ->
            runCatching {
                val catalogBook = LibraryCatalogRepository.getBookByExternalId(context, meta.id)
                    ?: return@mapNotNull null
                meta.id.takeIf { LibraryBookManager.isInstalled(context, catalogBook) }
            }.getOrNull()
        }.toSet()
    }

    fun todayKey(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

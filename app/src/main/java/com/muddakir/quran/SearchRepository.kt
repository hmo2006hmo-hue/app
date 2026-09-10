package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central search facade for Quran, tafsir, and library content.
 *
 * Search is SQLite/FTS5-backed wherever structured indexes exist. Library
 * metadata is searched from the central catalog, while installed books use
 * their own FTS5 database for full-text results.
 */
object SearchRepository {

    enum class Category(val key: String, val title: String) {
        ALL("all", "الكل"),
        QURAN("quran", "القرآن"),
        TAFSIR("tafsir", "التفسير"),
        BOOKS("books", "الكتب")
    }

    data class Result(
        val title: String,
        val subtitle: String,
        val category: Category,
        val surah: Int? = null,
        val ayah: Int? = null,
        val bookId: String? = null,
        val bookFormat: String? = null,
        val item: BookRepository.Item? = null,
        val snippet: String? = null
    )

    suspend fun search(
        context: Context,
        query: String,
        category: Category
    ): List<Result> = withContext(Dispatchers.IO) {
        val rawQuery = query.trim()
        val q = normalizeQuery(rawQuery)
        if (q.length < 2) return@withContext emptyList()

        val out = ArrayList<Result>(120)

        if (category == Category.ALL || category == Category.QURAN) {
            runCatchingCancellable { QuranSearchRepository.search(context, q, limit = 120) }.getOrDefault(emptyList()).forEach { result ->
                out += Result(
                    title = result.title,
                    subtitle = result.subtitle,
                    category = Category.QURAN,
                    surah = result.surah,
                    ayah = result.ayah,
                    snippet = result.subtitle
                )
            }
        }

        if (category == Category.ALL || category == Category.TAFSIR) {
            runCatchingCancellable { TafsirRepository.search(context, q, limit = 120) }.getOrDefault(emptyList()).forEach { result ->
                out += Result(
                    title = "تفسير ${result.surahName.ifBlank { "السورة ${result.surahNumber}" }} — الآية ${result.ayahNumber}",
                    subtitle = result.snippet.replace('\n', ' '),
                    category = Category.TAFSIR,
                    surah = result.surahNumber,
                    ayah = result.ayahNumber,
                    snippet = result.snippet
                )
            }
        }

        if (category == Category.ALL || category == Category.BOOKS) {
            // Keep the user's raw book query so vocalized Arabic can still match
            // the source text; metadata matching inside searchBooks normalizes it.
            runCatchingCancellable { searchBooks(context, rawQuery, out) }
        }

        out.take(120)
    }

    suspend fun searchBook(
        context: Context,
        fileName: String,
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<Result> = withContext(Dispatchers.IO) {
        val cleanQuery = normalizeQuery(query)
        require(cleanQuery.isNotBlank()) { "query must not be blank" }

        BookRepository.search(
            context = context,
            fileName = fileName,
            query = cleanQuery,
            limit = limit.coerceIn(1, 200),
            offset = offset.coerceAtLeast(0)
        ).map { result ->
            Result(
                title = result.item.title?.takeIf { it.isNotBlank() } ?: "نتيجة",
                subtitle = result.snippet?.takeIf { it.isNotBlank() } ?: result.item.content.take(180),
                category = Category.BOOKS,
                bookId = fileName.removeSuffix(".db"),
                bookFormat = "sqlite",
                item = result.item,
                snippet = result.snippet
            )
        }
    }

    private suspend fun searchBooks(
        context: Context,
        query: String,
        out: MutableList<Result>
    ) {
        val books = LibraryCatalogRepository.getBooks(context)
        for (book in books) {
            val metadata = listOf(book.title, book.author.orEmpty(), book.description.orEmpty())
                .joinToString(" ")
            if (containsNormalized(metadata, query)) {
                out += Result(
                    title = book.title,
                    subtitle = book.author.orEmpty(),
                    category = Category.BOOKS,
                    bookId = book.externalId,
                    bookFormat = book.format
                )
            }

            if (book.isDownloaded && book.format.equals("sqlite", ignoreCase = true)) {
                runCatchingCancellable {
                    searchBook(context, book.dbFile, query, limit = 20).forEach { result ->
                        out += result.copy(bookId = book.externalId, bookFormat = "sqlite")
                    }
                }
            } else if (book.isDownloaded && book.format.equals("json", ignoreCase = true)) {
                runCatchingCancellable {
                    LibraryRepository.loadPages(context, book.externalId)
                        .asSequence()
                        .filter { containsNormalized(it.title, query) || containsNormalized(it.text, query) || containsNormalized(it.footnote, query) }
                        .take(20)
                        .forEach { page ->
                            out += Result(
                                title = page.title.ifBlank { book.title },
                                subtitle = page.text.take(180),
                                category = Category.BOOKS,
                                bookId = book.externalId,
                                bookFormat = "json",
                                snippet = page.text.take(180)
                            )
                        }
                }
            }

            if (out.size >= 120) return
        }
    }

    fun normalize(value: String): String =
        value
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .trim()

    private fun normalizeQuery(query: String): String = normalize(query).replace(Regex("\\s+"), " ")

    private fun containsNormalized(text: String, normalizedQuery: String): Boolean =
        normalize(text).contains(normalizedQuery)
}

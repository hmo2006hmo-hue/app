package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Compatibility facade for the JSON-based library reader.
 *
 * Stage 25: the central SQLite catalog is now the only catalog source.
 * JSON book content remains stored separately and is read only after the
 * corresponding catalog entry confirms that the book is a JSON book.
 */
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val downloadUrl: String,
    val icon: String
)

data class LibraryPage(val title: String, val text: String, val footnote: String = "")

object LibraryRepository {
    suspend fun catalog(context: Context): List<LibraryBook> = withContext(Dispatchers.IO) {
        LibraryCatalogRepository.getBooks(context)
            .filter { it.format == "json" }
            .map { book ->
                LibraryBook(
                    id = book.externalId,
                    title = book.title,
                    author = book.author.orEmpty(),
                    description = book.description.orEmpty(),
                    downloadUrl = book.downloadUrl.orEmpty(),
                    icon = book.cover?.takeIf { it.isNotBlank() } ?: "📚"
                )
            }
    }

    fun bookDir(context: Context, id: String): File {
        requireSafeExternalId(id)
        return File(context.filesDir, "library/books/$id")
    }

    suspend fun loadPages(context: Context, id: String): List<LibraryPage> = withContext(Dispatchers.IO) {
        val book = LibraryCatalogRepository.getBookByExternalId(context, id)
            ?: return@withContext emptyList()
        if (book.format != "json") return@withContext emptyList()

        val file = LibraryBookManager.installedFile(context, book)
            ?: return@withContext emptyList()

        runCatching { parsePages(file.readText(Charsets.UTF_8)) }
            .getOrDefault(emptyList())
    }

    private fun parsePages(raw: String): List<LibraryPage> {
        val data = JSONObject(raw)
        val out = ArrayList<LibraryPage>()
        val pages = data.optJSONArray("pages")
        if (pages != null) {
            for (i in 0 until pages.length()) {
                val x = pages.optJSONObject(i) ?: continue
                val title = clean(x.optString("title").ifBlank {
                    x.optString("name").ifBlank { "صفحة ${i + 1}" }
                })
                val text = clean(x.optString("text").ifBlank {
                    x.optString("content").ifBlank {
                        x.optString("arabic_text").ifBlank { x.optString("body") }
                    }
                })
                val footnote = clean(x.optString("footnote"))
                if (title.isNotBlank() || text.isNotBlank()) out += LibraryPage(title, text, footnote)
            }
        }
        val sections = data.optJSONArray("sections")
        if (out.isEmpty() && sections != null) {
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val sectionTitle = clean(section.optString("title").ifBlank { section.optString("name") })
                val items = section.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    val x = items.optJSONObject(j) ?: continue
                    val title = clean(x.optString("title").ifBlank { sectionTitle })
                    val text = clean(x.optString("arabic_text").ifBlank {
                        x.optString("text").ifBlank { x.optString("content") }
                    })
                    if (title.isNotBlank() || text.isNotBlank()) out += LibraryPage(title, text)
                }
            }
        }
        val duas = data.optJSONArray("duas") ?: data.optJSONObject("book")?.optJSONArray("duas")
        if (out.isEmpty() && duas != null) {
            for (i in 0 until duas.length()) {
                val x = duas.optJSONObject(i) ?: continue
                val title = clean(x.optString("title").ifBlank {
                    "الدعاء ${x.optString("number").ifBlank { (i + 1).toString() }}"
                })
                val text = clean(x.optString("text").ifBlank { x.optString("content") })
                if (title.isNotBlank() || text.isNotBlank()) out += LibraryPage(title, text)
            }
        }
        return out
    }

    private fun clean(s: String): String = s.replace("\\n", "\n").trim()

    private fun requireSafeExternalId(value: String) {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid book id" }
    }
}

package com.muddakir.quran

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCatalogRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun catalog_is_valid_and_contains_clean_entries() = runBlocking {
        val file = LibraryCatalogRepository.ensureCatalog(context)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)

        val books = LibraryCatalogRepository.getBooks(context)
        assertTrue(books.isNotEmpty())
        assertTrue(books.all { it.format == "sqlite" })
        assertTrue(books.all { it.downloadUrl?.startsWith("https://github.com/hmo2006hmo-hue/books/releases/download/books-v1/") == true })
        assertTrue(books.all { it.dbFile.matches(Regex("book_[0-9]+\\.db")) })
        assertTrue(books.all { it.sha256?.matches(Regex("[0-9a-f]{64}")) == true })
        assertTrue(books.all { it.sizeBytes != null && it.sizeBytes > 0L })

        val first = LibraryCatalogRepository.getBookByExternalId(context, "book_1")
        assertNotNull(first)
        assertEquals("book_1.db", first?.dbFile)
        assertFalse(first?.isDownloaded == true)
    }
}

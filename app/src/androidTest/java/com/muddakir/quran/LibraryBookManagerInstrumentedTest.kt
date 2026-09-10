package com.muddakir.quran

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBookManagerInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun catalog_has_all_catalog_books() = runBlocking {
        val books = LibraryCatalogRepository.getBooks(context)
        assertTrue(books.isNotEmpty())
        assertEquals((1L..books.size.toLong()).toList(), books.map { it.id })
        assertTrue(books.all { it.format == "sqlite" })
        assertTrue(books.all { it.dbFile == "book_${it.id}.db" })
        assertTrue(books.all { !it.downloadUrl.isNullOrBlank() })
        assertTrue(books.all { !it.sha256.isNullOrBlank() })
    }

    @Test
    fun missing_install_is_not_reported_as_downloaded() = runBlocking {
        val book = LibraryCatalogRepository.getBook(context, 1L)
        requireNotNull(book)
        assertFalse(LibraryBookManager.isInstalled(context, book))
    }
}

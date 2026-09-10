package com.muddakir.quran

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun catalog_facade_mirrors_catalog() = runBlocking {
        val catalogBooks = LibraryCatalogRepository.getBooks(context)
        val facadeBooks = LibraryRepository.catalog(context)
        assertEquals(catalogBooks.count { it.format == "json" }, facadeBooks.size)
        assertEquals(catalogBooks.filter { it.format == "json" }.map { it.externalId }, facadeBooks.map { it.id })
    }

    @Test
    fun json_reader_refuses_unknown_book_id() = runBlocking {
        assertTrue(LibraryRepository.loadPages(context, "unknown_book").isEmpty())
    }
}

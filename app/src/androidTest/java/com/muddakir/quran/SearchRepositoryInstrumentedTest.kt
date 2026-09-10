package com.muddakir.quran

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appSearch_rejectsTooShortQueriesWithoutThrowing() = runBlocking {
        val results = SearchRepository.search(context, "ا", SearchRepository.Category.ALL)
        assertTrue(results.isEmpty())
    }

    @Test
    fun appSearch_lists_book_metadata_without_download() = runBlocking {
        val results = SearchRepository.search(context, "بحار", SearchRepository.Category.BOOKS)
        assertTrue(results.any { it.bookId == "book_1" })
    }
}

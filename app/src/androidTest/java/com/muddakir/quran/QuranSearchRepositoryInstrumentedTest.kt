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
class QuranSearchRepositoryInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun search_findsRealArabicAyah() = runBlocking {
        val page = QuranRepository.loadPage(context, 1)
        requireNotNull(page)
        val ayah = page.ayahs.first()
        val token = SearchRepository.normalize(ayah.text)
            .split(Regex("\\s+"))
            .first { it.length >= 2 }

        val results = QuranSearchRepository.search(context, token, limit = 20)

        assertTrue("SQLite/FTS5 Quran search returned no results", results.isNotEmpty())
        assertTrue(results.any { it.ayah == ayah.numberInSurah && it.surah == ayah.surah.number })
        assertEquals(1, results.first().page)
        assertFalse(results.first().text.isBlank())
    }

    @Test
    fun search_normalizesArabicDiacritics() = runBlocking {
        val results = QuranSearchRepository.search(context, "بِسْمِ", limit = 20)
        assertTrue(results.isNotEmpty())
    }
}

package com.muddakir.quran

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TafsirRepositoryInstrumentedTest {
    @Test
    fun catalog_contains_exactly_the_five_supported_tafsirs() {
        val files = TafsirRepository.catalog().map { it.fileName }
        assertEquals(
            listOf("Tafsir_001.db", "Tafsir_002.db", "Tafsir_004.db", "Tafsir_005.db", "Tafsir_006.db"),
            files
        )
        assertTrue(files.none { it == "Tafsir_003.db" })
        assertTrue(TafsirRepository.catalog().all { it.downloadUrl.startsWith("https://github.com/hmo2006hmo-hue/explanation/") })
        assertTrue(TafsirRepository.catalog().all { (it.estimatedSizeBytes ?: 0L) > 0L })
    }
}

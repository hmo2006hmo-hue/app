package com.muddakir.quran

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MushafActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PAGE = "mushaf_page"
        private const val EXTRA_TAFSIR_SURAH = "mushaf_tafsir_surah"
        private const val EXTRA_TAFSIR_AYAH = "mushaf_tafsir_ayah"
        private const val EXTRA_AUTO_PLAY = "mushaf_auto_play"
        private const val EXTRA_KHATMA_MODE = "mushaf_khatma_mode"

        @JvmStatic
        fun intent(context: Context, page: Int? = null, tafsirSurah: Int? = null, tafsirAyah: Int? = null, autoPlay: Boolean = false, khatmaMode: Boolean = false): Intent = Intent(context, MushafActivity::class.java).apply {
            if (page != null) putExtra(EXTRA_PAGE, page)
            if (tafsirSurah != null) putExtra(EXTRA_TAFSIR_SURAH, tafsirSurah)
            if (tafsirAyah != null) putExtra(EXTRA_TAFSIR_AYAH, tafsirAyah)
            if (autoPlay) putExtra(EXTRA_AUTO_PLAY, true)
            if (khatmaMode) putExtra(EXTRA_KHATMA_MODE, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") { window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }

        val page = getIntent().getIntExtra(EXTRA_PAGE, -1).takeIf { it > 0 }
        val tafsirSurah = getIntent().getIntExtra(EXTRA_TAFSIR_SURAH, -1).takeIf { it > 0 }
        val tafsirAyah = getIntent().getIntExtra(EXTRA_TAFSIR_AYAH, -1).takeIf { it > 0 }
        val autoPlay = getIntent().getBooleanExtra(EXTRA_AUTO_PLAY, false)
        val khatmaMode = getIntent().getBooleanExtra(EXTRA_KHATMA_MODE, false)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MushafScreen(onBack = { finish() }, initialPage = page, initialTafsirSurah = tafsirSurah, initialTafsirAyah = tafsirAyah, autoPlayOnOpen = autoPlay, khatmaMode = khatmaMode)
            }
        }
    }
}

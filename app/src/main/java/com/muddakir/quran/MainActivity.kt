package com.muddakir.quran

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    companion object {
        @JvmField val EXTRA_OPEN_PAGE = "open_page"
        @JvmField val EXTRA_OPEN_ADHKAR_TYPE = "open_adhkar_type"
        @JvmField val EXTRA_OPEN_TAFSIR = "open_tafsir"
        @JvmField val EXTRA_SURAH = "surah"
        @JvmField val EXTRA_AYAH = "ayah"
        @JvmField val BOOKMARK_PREFS = "quran_bookmark_prefs"
        @JvmField val KEY_BOOKMARK_TITLE = "last_title"
        @JvmField val KEY_BOOKMARK_AYAH = "last_ayah"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") { window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        ReminderBootstrap.ensureScheduled(this)
        routeIntent(intent)
    }

    private fun routeIntent(value: android.content.Intent?) {
        val screen = NativeNavigation.screenFromIntent(value)
        if (screen == NativeNavigation.SCREEN_MUSHAF) {
            val page = value?.getIntExtra("mushaf_page", -1)?.takeIf { it > 0 }
            val tafsir = value?.getBooleanExtra(EXTRA_OPEN_TAFSIR, false) == true
            val surah = value?.getIntExtra(EXTRA_SURAH, -1)?.takeIf { it > 0 }
            val ayah = value?.getIntExtra(EXTRA_AYAH, -1)?.takeIf { it > 0 }
            startActivity(MushafActivity.intent(this, page, if (tafsir) surah else null, if (tafsir) ayah else null, value?.getBooleanExtra("auto_play", false) == true))
            finish()
            return
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NativeAppRoot(screen)
            }
        }
    }

    override fun onNewIntent(newIntent: android.content.Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        routeIntent(newIntent)
    }
}

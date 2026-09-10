package com.muddakir.quran

import android.content.Context
import android.content.Intent

object NativeNavigation {
    const val SCREEN_HOME = "home"
    const val SCREEN_MUSHAF = "mushaf"
    const val SCREEN_ADHKAR = "adhkar"
    const val SCREEN_KHATMA = "khatma"
    const val SCREEN_SEARCH = "search"
    const val SCREEN_OCCASIONS = "occasions"
    const val SCREEN_NOTES = "notes"
    const val SCREEN_SETTINGS = "settings"
    const val SCREEN_DOWNLOADS = "downloads"
    const val SCREEN_LIBRARY = "library"
    const val SCREEN_TODAY = "today"
    const val SCREEN_REMINDERS = "reminders"
    const val SCREEN_AUDIO = "audio"

    fun open(context: Context, screen: String) {
        if (screen == SCREEN_MUSHAF) context.startActivity(MushafActivity.intent(context))
        else context.startActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PAGE, screen))
    }

    fun screenFromIntent(intent: Intent?): String {
        if (!intent?.getStringExtra(MainActivity.EXTRA_OPEN_ADHKAR_TYPE).isNullOrBlank()) return SCREEN_ADHKAR
        if (intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_TAFSIR, false) == true) return SCREEN_MUSHAF
        if (intent?.getBooleanExtra("auto_play", false) == true) return SCREEN_MUSHAF
        return when (intent?.getStringExtra(MainActivity.EXTRA_OPEN_PAGE)) {
            SCREEN_ADHKAR -> SCREEN_ADHKAR
            SCREEN_KHATMA -> SCREEN_KHATMA
            SCREEN_SEARCH -> SCREEN_SEARCH
            SCREEN_OCCASIONS -> SCREEN_OCCASIONS
            SCREEN_NOTES -> SCREEN_NOTES
            SCREEN_SETTINGS -> SCREEN_SETTINGS
            SCREEN_DOWNLOADS -> SCREEN_DOWNLOADS
            SCREEN_LIBRARY -> SCREEN_LIBRARY
            SCREEN_TODAY -> SCREEN_TODAY
            SCREEN_REMINDERS -> SCREEN_REMINDERS
            SCREEN_AUDIO -> SCREEN_AUDIO
            SCREEN_MUSHAF -> SCREEN_MUSHAF
            else -> SCREEN_HOME
        }
    }
}

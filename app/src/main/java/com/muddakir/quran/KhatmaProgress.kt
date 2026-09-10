package com.muddakir.quran

import android.content.Context

/** Tracks unique Quran ayahs that the user has actually reached while reading the Khatma. */
object KhatmaProgress {
    private const val PREFS = "khatma_native"
    private const val KEY_READ_AYAHS = "read_ayahs_v1"

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_READ_AYAHS).apply()
    }

    fun readKeys(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_READ_AYAHS, emptySet())
        ?.toSet() ?: emptySet()

    fun readCount(context: Context): Int = readKeys(context).size.coerceIn(0, 6236)

    fun markPageRead(context: Context, page: QcfPage) {
        val keys = readKeys(context).toMutableSet()
        page.lines.asSequence()
            .flatMap { it.words.asSequence() }
            .mapNotNull { it.verseKey }
            .filter { key ->
                val parts = key.split(":", limit = 2)
                val surah = parts.getOrNull(0)?.toIntOrNull()
                val ayah = parts.getOrNull(1)?.toIntOrNull()
                surah in 1..114 && ayah != null && ayah > 0
            }
            .forEach(keys::add)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_READ_AYAHS, keys).apply()
    }
}

package com.muddakir.quran

import android.content.Context

/** Small local preference store for library UX state. */
object LibraryPreferences {
    private const val FILE = "library_preferences"
    private const val FAVORITES = "favorite_book_ids"
    private const val LAST_PREFIX = "last_position_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favorites(context: Context): Set<Long> =
        prefs(context).getStringSet(FAVORITES, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun isFavorite(context: Context, bookId: Long): Boolean = bookId in favorites(context)

    fun setFavorite(context: Context, bookId: Long, value: Boolean) {
        val next = favorites(context).toMutableSet()
        if (value) next += bookId else next -= bookId
        prefs(context).edit().putStringSet(FAVORITES, next.map(Long::toString).toSet()).apply()
    }

    fun getLastPosition(context: Context, bookId: String): Long =
        prefs(context).getLong(LAST_PREFIX + bookId, 0L).coerceAtLeast(0L)

    fun setLastPosition(context: Context, bookId: String, position: Long) {
        prefs(context).edit().putLong(LAST_PREFIX + bookId, position.coerceAtLeast(0L)).apply()
    }
}

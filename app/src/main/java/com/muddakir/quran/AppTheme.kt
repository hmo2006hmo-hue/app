package com.muddakir.quran

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeName(val key: String, val title: String, val icon: String) {
    DARK("dark", "الأخضر", "🌿"),
    LIGHT("light", "الفاتح", "☀️"),
    PARCHMENT("parchment", "ورقي", "📜"),
    BLUE("blue", "أزرق", "🔵"),
    ROSE("rose", "وردي", "🌸"),
    NIGHT("night", "ليلي", "🌙"),
    BLACK("black", "أسود", "⬛")
}

data class AppPalette(
    val teal: Color,
    val teal2: Color,
    val gold: Color,
    val paper: Color,
    val paper2: Color,
    val ink: Color,
    val soft: Color,
    val line: Color
) {
    val readerBackground: Color get() = paper2
    val readerText: Color get() = ink
    val readerMuted: Color get() = soft
}

fun paletteFor(theme: AppThemeName): AppPalette = when (theme) {
    AppThemeName.DARK -> AppPalette(Color(0xFF123F35), Color(0xFF1D5B4D), Color(0xFFC9A227), Color(0xFFF6F0E4), Color(0xFFE7DFCF), Color(0xFF26342F), Color(0xFF68736D), Color(0xFFD9CDB7))
    AppThemeName.LIGHT -> AppPalette(Color(0xFF245B4D), Color(0xFF347663), Color(0xFFB99025), Color.White, Color(0xFFF2F5F3), Color(0xFF202825), Color(0xFF68736F), Color(0xFFD9E0DC))
    AppThemeName.PARCHMENT -> AppPalette(Color(0xFF57442A), Color(0xFF7A623D), Color(0xFFA47B18), Color(0xFFFBF4DF), Color(0xFFE8DCC2), Color(0xFF3E3428), Color(0xFF766A58), Color(0xFFD7C49D))
    AppThemeName.BLUE -> AppPalette(Color(0xFF1F466E), Color(0xFF326B9D), Color(0xFFD2A44A), Color(0xFFF7FBFF), Color(0xFFE6F0F8), Color(0xFF1F2B37), Color(0xFF667586), Color(0xFFCBD9E6))
    AppThemeName.ROSE -> AppPalette(Color(0xFF70405A), Color(0xFF9A5B78), Color(0xFFC89A45), Color(0xFFFFF9F7), Color(0xFFF6E8E3), Color(0xFF3B2A31), Color(0xFF7B6970), Color(0xFFE6CEC6))
    AppThemeName.NIGHT -> AppPalette(Color(0xFF14322C), Color(0xFF1B4038), Color(0xFFCAA64A), Color(0xFF1C1C1E), Color(0xFF0E0E10), Color(0xFFECECEC), Color(0xFFA3A3A6), Color(0xFF3A3A3C))
    AppThemeName.BLACK -> AppPalette(Color(0xFF161616), Color(0xFF303030), Color(0xFFD0A84C), Color(0xFF202020), Color(0xFF090909), Color(0xFFF3F3F3), Color(0xFFB9B9B9), Color(0xFF414141))
}

object AppThemeStore {
    private const val PREFS = "native_settings"
    private const val KEY = "app_theme"
    fun get(context: Context): AppThemeName {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AppThemeName.DARK.key)
        return AppThemeName.entries.firstOrNull { it.key == raw } ?: AppThemeName.DARK
    }
    fun save(context: Context, theme: AppThemeName) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.key).apply()
    }
}

val LocalAppPalette = staticCompositionLocalOf { paletteFor(AppThemeName.DARK) }

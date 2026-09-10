package com.muddakir.quran

import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Shia Hijri calendar is sighting-based (رؤية الهلال) as announced by
 * the office of the Grand Ayatollah al-Sistani, and can land a day off from
 * the tabular Umm al-Qura calculation Android's IslamicCalendar uses. For
 * "today" specifically, this repository fetches the authoritative date
 * published on sistani.org once per Gregorian day (cached in
 * SharedPreferences so the site is touched at most once daily), and falls
 * back to the tabular calculation whenever the fetch or parse fails for any
 * reason - the app must never break because a website changed its markup.
 */
object CalendarRepository {
    data class TodayDate(val weekday: String, val gregorian: String, val hijri: String, val hijriMonth: Int, val hijriDay: Int)

    private val IRAQ_TZ = TimeZone.getTimeZone("Asia/Baghdad")
    private val IRAQ_JAVA_TZ = java.util.TimeZone.getTimeZone("Asia/Baghdad")
    private val IRAQ_LOCALE = Locale("ar", "IQ")

    private const val PREFS = "sistani_hijri_cache"
    private const val KEY_DATE = "cache_gregorian_key"
    private const val KEY_DAY = "cache_hijri_day"
    private const val KEY_MONTH = "cache_hijri_month"
    private const val KEY_YEAR = "cache_hijri_year"
    private const val SISTANI_URL = "https://www.sistani.org/"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_READ_BYTES = 300_000

    private val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    suspend fun today(context: Context): TodayDate = withContext(Dispatchers.Default) {
        val algorithmic = forDate(Date())
        val sistani = withTimeoutOrNull(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS + 2_000L) {
            sistaniHijriToday(context)
        }
        if (sistani != null) {
            algorithmic.copy(
                hijri = "${sistani.first} ${hijriMonthName(sistani.second)} ${sistani.third} هـ",
                hijriMonth = sistani.second,
                hijriDay = sistani.first
            )
        } else {
            algorithmic
        }
    }

    fun todaySync(): TodayDate = forDate(Date())

    /** Returns (day, month, year) from sistani.org for today, using a
     * once-per-Gregorian-day cache. Returns null on any network, parsing,
     * or unexpected error so callers can fall back safely. */
    private suspend fun sistaniHijriToday(context: Context): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = IRAQ_JAVA_TZ }.format(Date())

        val cachedKey = prefs.getString(KEY_DATE, null)
        if (cachedKey == todayKey) {
            val d = prefs.getInt(KEY_DAY, -1)
            val m = prefs.getInt(KEY_MONTH, -1)
            val y = prefs.getInt(KEY_YEAR, -1)
            if (d > 0 && m in 1..12 && y > 0) return@withContext Triple(d, m, y)
        }

        val fetched = runCatching { fetchSistaniHtml() }.getOrNull() ?: return@withContext null
        val parsed = parseHijriFromHtml(fetched) ?: return@withContext null

        prefs.edit()
            .putString(KEY_DATE, todayKey)
            .putInt(KEY_DAY, parsed.first)
            .putInt(KEY_MONTH, parsed.second)
            .putInt(KEY_YEAR, parsed.third)
            .apply()
        parsed
    }

    private fun fetchSistaniHtml(): String {
        val connection = (URL(SISTANI_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mishkat-Native/1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return ""
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(MAX_READ_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                return String(buffer, 0, total, Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Looks for a "<day> - <hijri month name> - <year> هـ" pattern
     * anywhere in the page text, using Arabic-Indic or Western digits. This
     * intentionally does not depend on a specific HTML tag/id so a minor
     * markup change on the site does not silently break parsing - only a
     * change to the date phrase itself would. */
    private fun parseHijriFromHtml(html: String): Triple<Int, Int, Int>? {
        val monthNames = (1..12).map { hijriMonthName(it) }
        val monthPattern = monthNames.joinToString("|") { Regex.escape(it) }
        val digit = "[0-9٠-٩]"
        val regex = Regex("($digit{1,2})\\s*[-/]?\\s*($monthPattern)\\s*[-/]?\\s*($digit{4})\\s*ه")
        val match = regex.find(html) ?: return null
        val day = toWesternDigits(match.groupValues[1]).toIntOrNull() ?: return null
        val month = monthNames.indexOf(match.groupValues[2]) + 1
        val year = toWesternDigits(match.groupValues[3]).toIntOrNull() ?: return null
        if (day !in 1..30 || month !in 1..12) return null
        return Triple(day, month, year)
    }

    private fun toWesternDigits(s: String): String =
        s.map { c -> ARABIC_INDIC_DIGITS.indexOf(c).let { if (it >= 0) ('0' + it) else c } }.joinToString("")

    /** Converts an arbitrary Gregorian instant using the same Iraq/Um Al-Qura
     * rules as the main date display. This is used by the visual calendar. */
    fun forDate(date: Date): TodayDate {
        val now = date
        val weekday = SimpleDateFormat("EEEE", IRAQ_LOCALE).apply { timeZone = IRAQ_JAVA_TZ }.format(now)
        val gregorian = formatIraqiGregorian(now)

        val ic = IslamicCalendar(IRAQ_TZ, IRAQ_LOCALE).apply {
            setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
            time = now
        }
        val hijriDay = ic.get(IslamicCalendar.DAY_OF_MONTH)
        val hijriMonth = ic.get(IslamicCalendar.MONTH) + 1
        val hijriYear = ic.get(IslamicCalendar.YEAR)
        return TodayDate(
            weekday = weekday,
            gregorian = gregorian,
            hijri = "$hijriDay ${hijriMonthName(hijriMonth)} $hijriYear هـ",
            hijriMonth = hijriMonth,
            hijriDay = hijriDay
        )
    }

    private fun formatIraqiGregorian(now: Date): String {
        val day = SimpleDateFormat("d", IRAQ_LOCALE).apply { timeZone = IRAQ_JAVA_TZ }.format(now)
        val year = SimpleDateFormat("yyyy", Locale.US).apply { timeZone = IRAQ_JAVA_TZ }.format(now)
        val monthIndex = SimpleDateFormat("M", Locale.US).apply { timeZone = IRAQ_JAVA_TZ }.format(now).toInt()
        val month = arrayOf(
            "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
            "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
        )[monthIndex - 1]
        return "$day $month $year"
    }

    private fun hijriMonthName(m: Int): String = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    ).getOrElse(m - 1) { "" }

    suspend fun todayOccasions(context: Context): List<NativeContentRepository.Occasion> {
        val date = today(context)
        return NativeContentRepository.occasions(context).filter { it.month == date.hijriMonth && it.day == date.hijriDay }
    }
}

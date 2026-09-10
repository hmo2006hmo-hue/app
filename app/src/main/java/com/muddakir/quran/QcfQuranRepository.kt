package com.muddakir.quran

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * يطابق بنية assets/quran/qcf/page-{n}.json بالضبط (بيانات QCF4 - مصحف المدينة):
 * {
 *   "page": 1,
 *   "font": "QCF4_Hafs_01",
 *   "surahs": [ { "id":1, "name":"Al-Fatihah", "name_arabic":"الفاتحة", "verse_start":1, "verse_end":7 } ],
 *   "lines": [
 *     { "line": 1, "words": [ { "code":61696, "char":"\uf100", "font":"QCF4_QBSML", "text":"...", "type":"surah_header", "sura":1 } ] },
 *     { "line": 2, "words": [ { "code":..., "char":"...", "font":"QCF4_Hafs_01", "text":"بِسْمِ", "type":"word", "verse_key":"1:1", "position":1 }, ... ] }
 *   ]
 * }
 *
 * أنواع الكلمات (type): "surah_header" (شريط اسم السورة), "bismillah" (البسملة كسطر مستقل),
 * "word" (كلمة من آية), "end" (علامة نهاية الآية/رقمها), "quarter" (علامة ربع الحزب).
 */

data class QcfWord(
    val char: String,
    val font: String,
    val text: String,
    val type: String,
    val verseKey: String?,
    val position: Int?,
    val sura: Int?
)

data class QcfLine(
    val line: Int,
    val words: List<QcfWord>
)

data class QcfSurahOnPage(
    val id: Int,
    val nameArabic: String,
    val verseStart: Int,
    val verseEnd: Int
)

data class QcfPage(
    val page: Int,
    val defaultFont: String,
    val surahs: List<QcfSurahOnPage>,
    val lines: List<QcfLine>
)

/** موضع آية داخل المصحف: الصفحة والأسطر التي تظهر بها (من verses.json). */
data class VerseLocation(
    val page: Int,
    val firstLine: Int
)

object QcfQuranRepository {

    const val MIN_PAGE = 1
    const val MAX_PAGE = 604

    private val pageCache = LruCache<Int, QcfPage>(15)
    private var versesIndex: Map<String, VerseLocation>? = null

    suspend fun loadPage(context: Context, pageNumber: Int): QcfPage? {
        if (pageNumber < MIN_PAGE || pageNumber > MAX_PAGE) return null

        pageCache.get(pageNumber)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val json = context.assets
                    .open("quran/qcf/page-$pageNumber.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val page = parsePage(json)
                if (page != null) pageCache.put(pageNumber, page)
                page
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            }
        }
    }

    /** يرجع رقم الصفحة الذي تبدأ فيه آية معينة، عبر assets/quran/qcf/verses.json (يُحمَّل ويُخزَّن مرة واحدة). */
    suspend fun pageForVerse(context: Context, surah: Int, ayah: Int): Int? {
        val index = versesIndex ?: loadVersesIndex(context)
        return index["$surah:$ayah"]?.page
    }

    private suspend fun loadVersesIndex(context: Context): Map<String, VerseLocation> =
        withContext(Dispatchers.IO) {
            val result = try {
                val json = context.assets
                    .open("quran/qcf/verses.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val root = JSONObject(json)
                val map = HashMap<String, VerseLocation>(root.length())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val verseKey = keys.next()
                    val entry = root.getJSONObject(verseKey)
                    val page = entry.getInt("page")
                    val linesArr = entry.optJSONArray("lines")
                    val firstLine = if (linesArr != null && linesArr.length() > 0) {
                        linesArr.getJSONObject(0).getInt("line")
                    } else 1
                    map[verseKey] = VerseLocation(page, firstLine)
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
            versesIndex = result
            result
        }

    private fun parsePage(json: String): QcfPage? {
        return try {
            val root = JSONObject(json)
            val surahsArr = root.optJSONArray("surahs")
            val surahs = ArrayList<QcfSurahOnPage>()
            if (surahsArr != null) {
                for (i in 0 until surahsArr.length()) {
                    val s = surahsArr.getJSONObject(i)
                    surahs.add(
                        QcfSurahOnPage(
                            id = s.getInt("id"),
                            nameArabic = s.optString("name_arabic"),
                            verseStart = s.optInt("verse_start"),
                            verseEnd = s.optInt("verse_end")
                        )
                    )
                }
            }

            val linesArr = root.getJSONArray("lines")
            val lines = ArrayList<QcfLine>(linesArr.length())
            for (i in 0 until linesArr.length()) {
                val l = linesArr.getJSONObject(i)
                val wordsArr = l.getJSONArray("words")
                val words = ArrayList<QcfWord>(wordsArr.length())
                for (j in 0 until wordsArr.length()) {
                    val w = wordsArr.getJSONObject(j)
                    words.add(
                        QcfWord(
                            char = w.optString("char"),
                            font = w.optString("font"),
                            text = w.optString("text"),
                            type = w.optString("type"),
                            verseKey = if (w.has("verse_key") && !w.isNull("verse_key")) w.getString("verse_key") else null,
                            position = if (w.has("position") && !w.isNull("position")) w.getInt("position") else null,
                            sura = if (w.has("sura") && !w.isNull("sura")) w.getInt("sura") else null
                        )
                    )
                }
                lines.add(QcfLine(line = l.getInt("line"), words = words))
            }

            QcfPage(
                page = root.getInt("page"),
                defaultFont = root.optString("font"),
                surahs = surahs,
                lines = lines
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun clearCache() {
        pageCache.evictAll()
    }
}

/* ------------------------------------------------------------------------
 * عدد آيات كل سورة (١١٤ قيمة ثابتة) - تُستخدم لحساب رقم الآية العام (global)
 * من (رقم السورة، رقم الآية داخل السورة) للتوافق مع رابط الصوت والويدجتات
 * القديمة التي تعتمد على الرقم العام من ١ إلى ٦٢٣٦.
 * ------------------------------------------------------------------------ */
private val SURAH_AYAH_COUNTS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
    123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
    34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
    60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
    28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6
)

/** رقم الآية العام (١..٦٢٣٦) من رقم السورة ورقم الآية داخلها. */
fun globalAyahNumber(surah: Int, ayahInSurah: Int): Int {
    var total = 0
    for (i in 0 until (surah - 1)) total += SURAH_AYAH_COUNTS[i]
    return total + ayahInSurah
}

package com.muddakir.quran

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * يطابق بنية assets/quran/page-{n}.json بالضبط:
 * {
 *   "number": 1,
 *   "juz": 1,
 *   "ayahs": [
 *     {
 *       "number": 1,
 *       "text": "...",
 *       "surah": { "number": 1, "name": "الفاتحة" },
 *       "numberInSurah": 1,
 *       "juz": 1
 *     }
 *   ]
 * }
 */
data class Surah(
    val number: Int,
    val name: String
)

data class Ayah(
    val number: Int,
    val text: String,
    val surah: Surah,
    val numberInSurah: Int,
    val juz: Int
)

data class QuranPage(
    val number: Int,
    val juz: Int,
    val ayahs: List<Ayah>
)

object QuranRepository {

    // MIN/MAX لصفحات المصحف (604 صفحة)
    const val MIN_PAGE = 1
    const val MAX_PAGE = 604

    // كاش بسيط بالذاكرة حتى لا نعيد قراءة/تحليل نفس الصفحة من assets في كل مرة
    private val pageCache = LruCache<Int, QuranPage>(30)

    suspend fun loadPage(context: Context, pageNumber: Int): QuranPage? {
        if (pageNumber < MIN_PAGE || pageNumber > MAX_PAGE) {
            return null
        }

        pageCache.get(pageNumber)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val json = context.assets
                    .open("quran/page-$pageNumber.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val page = parsePage(json)
                if (page != null) {
                    pageCache.put(pageNumber, page)
                }
                page
            } catch (e: IOException) {
                // الملف غير موجود أو تعذّرت قراءته
                null
            } catch (e: JSONException) {
                // خطأ بتحليل الـ JSON
                null
            }
        }
    }

    private fun parsePage(json: String): QuranPage? {
        return try {
            val root = JSONObject(json)
            val ayahsArray = root.getJSONArray("ayahs")

            val ayahs = ArrayList<Ayah>(ayahsArray.length())
            for (i in 0 until ayahsArray.length()) {
                val a = ayahsArray.getJSONObject(i)
                val surahObj = a.getJSONObject("surah")

                ayahs.add(
                    Ayah(
                        number = a.getInt("number"),
                        text = a.getString("text"),
                        surah = Surah(
                            number = surahObj.getInt("number"),
                            name = surahObj.getString("name")
                        ),
                        numberInSurah = a.getInt("numberInSurah"),
                        juz = a.getInt("juz")
                    )
                )
            }

            QuranPage(
                number = root.getInt("number"),
                juz = root.getInt("juz"),
                ayahs = ayahs
            )
        } catch (e: JSONException) {
            null
        }
    }

    /** لمسح الكاش عند الحاجة (مثلاً ضغط ذاكرة) */
    fun clearCache() {
        pageCache.evictAll()
    }
}

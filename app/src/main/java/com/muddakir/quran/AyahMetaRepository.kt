package com.muddakir.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AyahMeta(val global: Int, val page: Int, val juz: Int, val hizb: Int)

object AyahMetaRepository {
    private var cache: Map<String, AyahMeta>? = null

    suspend fun get(context: Context, surah: Int, ayah: Int): AyahMeta? = (cache ?: load(context))["$surah:$ayah"]
    suspend fun all(context: Context): Map<String, AyahMeta> = cache ?: load(context)

    private suspend fun load(context: Context): Map<String, AyahMeta> = withContext(Dispatchers.IO) {
        val result = runCatching {
            val raw = context.assets.open("quran/ayah-meta.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val obj = root.optJSONObject("items") ?: return@runCatching emptyMap<String, AyahMeta>()
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val x = obj.optJSONObject(key) ?: continue
                    put(key, AyahMeta(
                        global = x.optInt("global", 0),
                        page = x.optInt("page", 1),
                        juz = x.optInt("juz", 1),
                        hizb = x.optInt("hizb", 0)
                    ))
                }
            }
        }.getOrElse { emptyMap() }
        cache = result
        result
    }
}

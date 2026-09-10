package com.muddakir.quran

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object NativeNotesStore {
    private const val PREFS = "native_notes_v2"
    private const val KEY = "notes"

    data class Note(val id: Long, val title: String, val body: String, val category: String, val createdAt: String)

    fun load(context: Context): List<Note> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Note(o.optLong("id"), o.optString("title"), o.optString("body"), o.optString("category"), o.optString("createdAt")))
            }
        }
    }.getOrElse { emptyList() }

    fun save(context: Context, note: Note) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == note.id }
        list.add(0, note)
        val arr = JSONArray()
        list.take(200).forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("body", it.body)
                put("category", it.category)
                put("createdAt", it.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun delete(context: Context, id: Long) {
        val arr = JSONArray()
        load(context).filterNot { it.id == id }.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("body", it.body); put("category", it.category); put("createdAt", it.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

package com.resqnet.app.data

import android.content.Context
import org.json.JSONArray

object ResponderCache {
    private const val PREF = "resqnet_responder_cache"
    private const val KEY_PHONES = "phones"
    private const val KEY_SAVED_AT = "saved_at"

    fun save(context: Context, phones: List<String>) {
        val merged = (load(context) + phones.map { it.trim() }).filter { it.isNotBlank() }.distinct().take(60)
        if (merged.isEmpty()) return
        val array = JSONArray()
        merged.forEach { array.put(it) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_PHONES, array.toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PHONES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

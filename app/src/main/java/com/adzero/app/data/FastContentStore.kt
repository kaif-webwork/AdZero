package com.adzero.app.data

import android.content.Context
import com.adzero.app.models.Video
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

object FastContentStore {
    private const val PREF_NAME = "adzero_fast_content_cache"
    private val gson = Gson()
    private val memoryStore = ConcurrentHashMap<String, List<Video>>()

    fun saveFeed(context: Context, key: String, videos: List<Video>) {
        if (videos.isEmpty()) return
        memoryStore[key] = videos
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(videos.take(30))
            prefs.edit().putString(key, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFeed(context: Context, key: String): List<Video> {
        // 1. Return from memory store (0ms)
        memoryStore[key]?.let { if (it.isNotEmpty()) return it }

        // 2. Return from SharedPreferences disk store (< 2ms)
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<Video>>() {}.type
                val list: List<Video> = gson.fromJson(json, type)
                if (list.isNotEmpty()) {
                    memoryStore[key] = list
                    return list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}

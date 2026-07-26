package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.adzero.app.models.Video
import org.json.JSONArray

/**
 * Manages user's real Watch History and Search History with persistent local storage.
 * NO demo or fake hardcoded data.
 */
object HistoryManager {
    private const val PREFS_NAME = "adzero_user_history"
    private const val KEY_SEARCH_HISTORY = "real_user_search_history"
    private const val MAX_HISTORY_ITEMS = 25

    val watchHistory: SnapshotStateList<Video> = mutableStateListOf()
    val searchHistory: SnapshotStateList<String> = mutableStateListOf()

    fun init(context: Context) {
        loadSearchHistory(context)
    }

    private fun loadSearchHistory(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return
            val jsonArray = JSONArray(jsonStr)
            searchHistory.clear()
            for (i in 0 until jsonArray.length()) {
                val query = jsonArray.optString(i)
                if (query.isNotBlank() && !searchHistory.contains(query)) {
                    searchHistory.add(query)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSearchHistory(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            searchHistory.take(MAX_HISTORY_ITEMS).forEach { jsonArray.put(it) }
            prefs.edit().putString(KEY_SEARCH_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addWatchHistory(video: Video) {
        watchHistory.removeAll { it.id == video.id }
        watchHistory.add(0, video)
    }

    fun addSearchQuery(context: Context, query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        searchHistory.removeAll { it.equals(trimmed, ignoreCase = true) }
        searchHistory.add(0, trimmed)
        if (searchHistory.size > MAX_HISTORY_ITEMS) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
        saveSearchHistory(context)
    }

    fun removeSearchQuery(context: Context, query: String) {
        searchHistory.remove(query)
        saveSearchHistory(context)
    }

    fun clearSearchHistory(context: Context) {
        searchHistory.clear()
        saveSearchHistory(context)
    }

    fun clearWatchHistory() {
        watchHistory.clear()
    }
}

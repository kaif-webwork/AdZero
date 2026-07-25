package com.adzero.app.data

import com.adzero.app.models.Video
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object HistoryManager {
    val watchHistory: SnapshotStateList<Video> = mutableStateListOf()
    val searchHistory: SnapshotStateList<String> = mutableStateListOf(
        "Jetpack Compose tutorial",
        "Android 15 Developer Features",
        "Kotlin Multiplatform 2.0"
    )

    fun addWatchHistory(video: Video) {
        watchHistory.removeAll { it.id == video.id }
        watchHistory.add(0, video)
    }

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        searchHistory.removeAll { it.equals(query, ignoreCase = true) }
        searchHistory.add(0, query)
    }

    fun removeSearchQuery(query: String) {
        searchHistory.remove(query)
    }

    fun clearWatchHistory() {
        watchHistory.clear()
    }
}

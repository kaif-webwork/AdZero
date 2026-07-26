package com.adzero.app.data

import androidx.compose.runtime.mutableStateListOf
import com.adzero.app.models.Video

data class UserPlaylist(
    val id: String,
    val title: String,
    val itemCount: Int,
    val thumbnailUrl: String,
    val isPrivate: Boolean = true
)

object PlaylistManager {
    val userPlaylists = mutableStateListOf<UserPlaylist>()
    val watchLaterList = mutableStateListOf<Video>()
    val likedVideosList = mutableStateListOf<Video>()

    fun addToWatchLater(video: Video) {
        if (!watchLaterList.any { it.id == video.id }) {
            watchLaterList.add(video)
        }
    }

    fun toggleLike(video: Video) {
        val existing = likedVideosList.indexOfFirst { it.id == video.id }
        if (existing >= 0) {
            likedVideosList.removeAt(existing)
        } else {
            likedVideosList.add(video)
        }
    }

    fun toggleWatchLater(video: Video) {
        if (watchLaterList.any { it.id == video.id }) {
            watchLaterList.removeAll { it.id == video.id }
        } else {
            watchLaterList.add(0, video)
        }
    }

    fun isWatchLater(videoId: String): Boolean {
        return watchLaterList.any { it.id == videoId }
    }
}

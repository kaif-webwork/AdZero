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

    init {
        // Seed user's connected YouTube playlists
        userPlaylists.addAll(
            listOf(
                UserPlaylist(
                    id = "pl_liked",
                    title = "Liked Videos",
                    itemCount = 142,
                    thumbnailUrl = "https://picsum.photos/seed/liked_playlist/400/225",
                    isPrivate = true
                ),
                UserPlaylist(
                    id = "pl_watch_later",
                    title = "Watch Later",
                    itemCount = 28,
                    thumbnailUrl = "https://picsum.photos/seed/watch_later/400/225",
                    isPrivate = true
                ),
                UserPlaylist(
                    id = "pl_lofi",
                    title = "My Favorite Lofi & Chill Mix",
                    itemCount = 54,
                    thumbnailUrl = "https://picsum.photos/seed/lofi_mix/400/225",
                    isPrivate = false
                ),
                UserPlaylist(
                    id = "pl_tech",
                    title = "Coding & AI Tutorials 2026",
                    itemCount = 36,
                    thumbnailUrl = "https://picsum.photos/seed/coding_ai/400/225",
                    isPrivate = false
                ),
                UserPlaylist(
                    id = "pl_gaming",
                    title = "Gaming Highlights & Clutches",
                    itemCount = 19,
                    thumbnailUrl = "https://picsum.photos/seed/gaming_clutches/400/225",
                    isPrivate = false
                )
            )
        )
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

package com.adzero.app.ui_state

import com.adzero.app.models.Video
import com.adzero.app.models.Comment
import com.adzero.app.models.Creator

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val videos: List<Video>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

data class PlayerUiState(
    val currentVideo: Video? = null,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val speed: String = "1.0x",
    val quality: String = "1080p",
    val isFullscreen: Boolean = false,
    val isPipActive: Boolean = false,
    val isMinimized: Boolean = false,
    val likesCount: String = "1.2K",
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val isSubscribed: Boolean = false,
    val comments: List<Comment> = emptyList()
)

data class SearchUiState(
    val query: String = "",
    val isSearchActive: Boolean = false,
    val searchHistory: List<String> = listOf("Jetpack Compose", "Clean Architecture", "Kotlin 2.0"),
    val results: List<Video> = emptyList()
)


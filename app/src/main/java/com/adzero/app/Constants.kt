package com.adzero.app

object Constants {
    val CATEGORIES = listOf(
        "All", "For You 👤", "Gaming", "Music", "Live", "Podcasts", 
        "Technology", "Education", "Movies", "News", "Sports"
    )

    val SEARCH_SUGGESTIONS = listOf(
        "Jetpack Compose tutorial",
        "Material 3 design guidelines",
        "Kotlin Multiplatform mobile",
        "Android 15 edge to edge tutorial",
        "Lo-fi beats for coding",
        "SpaceX Mars mission update",
        "How to build a custom ExoPlayer"
    )

    val TRENDING_SEARCHES = listOf(
        "Android 15 Developer Features",
        "AI Agent Coding Assistants",
        "Compose Multiplatform 1.6",
        "AMOLED Dark Mode Best Practices",
        "Clean Architecture in Android"
    )

    const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/kaif-webwork/AdZero/main/version.json"
    const val DEFAULT_APK_URL = "https://github.com/kaif-webwork/AdZero/releases/latest"
}

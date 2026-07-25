package com.adzero.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Shorts : Screen("shorts")
    object Create : Screen("create")
    object Subscriptions : Screen("subscriptions")
    object Profile : Screen("profile")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Channel : Screen("channel/{channelName}") {
        fun createRoute(channelName: String) = "channel/$channelName"
    }
    object Player : Screen("player/{videoId}") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
}

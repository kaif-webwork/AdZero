package com.adzero.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.adzero.app.models.Video

data class SubscribedChannel(
    val name: String,
    val avatarUrl: String,
    val subscriberCount: String = "1.2M subscribers"
)

object SubscriptionManager {
    // Subscribed channels from the user's real account (populated by RealAccountSyncManager)
    val subscribedChannels = mutableStateMapOf<String, SubscribedChannel>()

    // Real subscription feed videos from the user's account
    val subscriptionFeedVideos = mutableStateListOf<Video>()

    fun isSubscribed(channelName: String): Boolean {
        return subscribedChannels.containsKey(channelName)
    }

    fun toggleSubscription(channelName: String, avatarUrl: String = "", subsCount: String = "1.2M subscribers") {
        if (isSubscribed(channelName)) {
            subscribedChannels.remove(channelName)
        } else {
            val realAvatar = avatarUrl.ifBlank { "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png" }
            subscribedChannels[channelName] = SubscribedChannel(channelName, realAvatar, subsCount)
        }
    }
}

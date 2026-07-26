package com.adzero.app.data

import androidx.compose.runtime.mutableStateMapOf

data class SubscribedChannel(
    val name: String,
    val avatarUrl: String,
    val subscriberCount: String = "1.2M subscribers"
)

object SubscriptionManager {
    // Local user's subscribed channels
    val subscribedChannels = mutableStateMapOf<String, SubscribedChannel>()

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

package com.adzero.app.data

import androidx.compose.runtime.mutableStateMapOf

data class SubscribedChannel(
    val name: String,
    val avatarUrl: String,
    val subscriberCount: String = "1.2M subscribers"
)

object SubscriptionManager {
    // Persistent reactive state of subscribed channels
    val subscribedChannels = mutableStateMapOf<String, SubscribedChannel>()

    init {
        // Pre-seed some default subscriptions
        subscribedChannels["T-Series"] = SubscribedChannel("T-Series", "https://picsum.photos/seed/tseries/100/100", "265M subscribers")
        subscribedChannels["MrBeast"] = SubscribedChannel("MrBeast", "https://picsum.photos/seed/mrbeast/100/100", "300M subscribers")
        subscribedChannels["Marques Brownlee"] = SubscribedChannel("Marques Brownlee", "https://picsum.photos/seed/mkbhd/100/100", "18.5M subscribers")
        subscribedChannels["Rockstar Games"] = SubscribedChannel("Rockstar Games", "https://picsum.photos/seed/rockstar/100/100", "10.2M subscribers")
    }

    fun isSubscribed(channelName: String): Boolean {
        return subscribedChannels.containsKey(channelName)
    }

    fun toggleSubscription(channelName: String, avatarUrl: String = "", subsCount: String = "1.2M subscribers") {
        if (isSubscribed(channelName)) {
            subscribedChannels.remove(channelName)
        } else {
            subscribedChannels[channelName] = SubscribedChannel(channelName, avatarUrl.ifBlank { "https://picsum.photos/seed/${channelName.hashCode()}/100/100" }, subsCount)
        }
    }
}

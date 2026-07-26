package com.adzero.app.data

import android.content.Context
import com.adzero.app.App
import com.adzero.app.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object RealAccountSyncManager {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Validates a URL is HTTPS before we attach session cookies to a request. */
    private fun requireHttps(url: String): String {
        require(url.startsWith("https://")) {
            "Security: Refusing to send session cookies over non-HTTPS URL: $url"
        }
        return url
    }

    /**
     * Authenticated Live Account Data Fetcher using User's Session Cookies.
     * Fetches Real Subscriptions, Real History, Real Liked Videos & Real Playlists.
     */
    suspend fun syncAccountWithCookies(context: Context, cookies: String): Boolean = withContext(Dispatchers.IO) {
        if (cookies.isBlank()) return@withContext false

        try {
            // 1. Fetch Real Subscriptions Feed (https://www.youtube.com/feed/subscriptions)
            val subUrl = requireHttps("https://www.youtube.com/feed/subscriptions")
            val subRequest = Request.Builder()
                .url(subUrl)
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val subResponse = App.okHttpClient.newCall(subRequest).execute()
            val subHtml = subResponse.body?.string() ?: ""

            if (subHtml.contains("ytInitialData")) {
                val jsonStr = extractYtInitialData(subHtml)
                if (jsonStr.isNotBlank()) {
                    val channels = parseChannelsFromSubscriptionsJson(jsonStr)
                    if (channels.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            channels.forEach { (name, avatar, subs) ->
                                SubscriptionManager.subscribedChannels[name] = SubscribedChannel(
                                    name = name,
                                    avatarUrl = avatar,
                                    subscriberCount = subs
                                )
                            }
                        }
                    }
                }
            }

            // 2. Fetch Real Watch History (https://www.youtube.com/feed/history)
            val historyUrl = requireHttps("https://www.youtube.com/feed/history")
            val historyRequest = Request.Builder()
                .url(historyUrl)
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val historyResponse = App.okHttpClient.newCall(historyRequest).execute()
            val historyHtml = historyResponse.body?.string() ?: ""

            if (historyHtml.contains("ytInitialData")) {
                val jsonStr = extractYtInitialData(historyHtml)
                if (jsonStr.isNotBlank()) {
                    val historyVideos = parseVideosFromJson(jsonStr)
                    if (historyVideos.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            historyVideos.forEach { video ->
                                HistoryManager.addWatchHistory(video)
                            }
                        }
                    }
                }
            }

            // 3. Fetch Real Liked Videos Playlist (https://www.youtube.com/playlist?list=LL)
            val likedUrl = requireHttps("https://www.youtube.com/playlist?list=LL")
            val likedRequest = Request.Builder()
                .url(likedUrl)
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val likedResponse = App.okHttpClient.newCall(likedRequest).execute()
            val likedHtml = likedResponse.body?.string() ?: ""

            if (likedHtml.contains("ytInitialData")) {
                val jsonStr = extractYtInitialData(likedHtml)
                if (jsonStr.isNotBlank()) {
                    val likedVideos = parseVideosFromJson(jsonStr)
                    if (likedVideos.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            likedVideos.forEach { video ->
                                PlaylistManager.toggleLike(video)
                            }
                        }
                    }
                }
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun extractYtInitialData(html: String): String {
        return try {
            val marker = "var ytInitialData = "
            if (!html.contains(marker)) return ""
            val jsonStart = html.substringAfter(marker)
            val jsonEnd = jsonStart.substringBefore(";</script>")
            jsonEnd.trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseChannelsFromSubscriptionsJson(jsonStr: String): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        try {
            val json = JSONObject(jsonStr)
            val jsonText = json.toString()
            val channelRegex = """"title":\{"simpleText":"([^"]+)"\}.*?"url":"([^"]+)"""".toRegex()
            val matches = channelRegex.findAll(jsonText)

            for (m in matches.take(15)) {
                val name = m.groupValues[1]
                val avatar = "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png"
                if (name.isNotBlank() && name != "Home" && name != "Shorts" && name != "Subscriptions") {
                    result.add(Triple(name, avatar, "Subscribed"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseVideosFromJson(jsonStr: String): List<Video> {
        val result = mutableListOf<Video>()
        try {
            val videoIdRegex = """"videoId":"([a-zA-Z0-9_-]{11})"""".toRegex()
            val titleRegex = """"title":\{"runs":\[\{"text":"([^"]+)"\}""".toRegex()
            
            val videoIds = videoIdRegex.findAll(jsonStr).map { it.groupValues[1] }.distinct().take(12).toList()
            val titles = titleRegex.findAll(jsonStr).map { it.groupValues[1] }.toList()

            for (i in videoIds.indices) {
                val id = videoIds[i]
                val title = if (i < titles.size) titles[i] else "YouTube Video"
                result.add(
                    Video(
                        id = id,
                        title = title,
                        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        channelName = "YouTube Creator",
                        channelAvatarUrl = "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
                        views = "Real Account Video",
                        uploadDate = "",
                        duration = "3:45",
                        videoUrl = "https://www.youtube.com/watch?v=$id"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    suspend fun syncRealYouTubeAccount(context: Context, handleOrQuery: String): Boolean = withContext(Dispatchers.IO) {
        val savedCookies = UserAccountManager.userCookies
        if (!savedCookies.isNullOrBlank()) {
            return@withContext syncAccountWithCookies(context, savedCookies)
        }

        try {
            val service = ServiceList.YouTube
            val cleanQuery = handleOrQuery
                .substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .trim()

            val searchQueries = listOf(cleanQuery)
            var channelItem: ChannelInfoItem? = null

            for (q in searchQueries) {
                try {
                    val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(q, listOf("channels"), "")
                    val searchInfo = SearchInfo.getInfo(service, handler)
                    channelItem = searchInfo.relatedItems.filterIsInstance<ChannelInfoItem>().firstOrNull()
                    if (channelItem != null) break
                } catch (e: Exception) {
                    // Try next
                }
            }

            if (channelItem != null) {
                val channelInfo = ChannelInfo.getInfo(service, channelItem.url)
                val playlistsTab = channelInfo.tabs.find { it.id == "playlists" }
                val realPlaylists = if (playlistsTab != null) {
                    val playlistsInfo = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, playlistsTab)
                    playlistsInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().mapIndexed { idx, pl ->
                        UserPlaylist(
                            id = pl.url ?: "pl_$idx",
                            title = pl.name ?: "Saved Playlist",
                            itemCount = pl.streamCount.toInt(),
                            thumbnailUrl = pl.thumbnails.firstOrNull()?.url ?: channelInfo.avatars.firstOrNull()?.url ?: "",
                            isPrivate = false
                        )
                    }
                } else emptyList()

                if (realPlaylists.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        PlaylistManager.userPlaylists.clear()
                        PlaylistManager.userPlaylists.addAll(realPlaylists)
                    }
                }
            }

            return@withContext channelItem != null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}

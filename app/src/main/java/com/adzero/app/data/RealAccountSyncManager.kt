package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adzero.app.App
import com.adzero.app.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Syncs the user's real YouTube account data using the YouTube InnerTube API.
 *
 * InnerTube is YouTube's internal JSON API — far more reliable than HTML scraping
 * because it returns structured JSON rather than rendered page HTML that can vary
 * by bot-detection state, A/B tests, or compression.
 *
 * Endpoints used:
 *  - /youtubei/v1/guide              → Subscribed channels list (sidebar)
 *  - /youtubei/v1/browse FEsubscriptions → Subscription feed videos
 *  - /youtubei/v1/browse FEhistory   → Watch history
 *  - /youtubei/v1/browse VLLL        → Liked Videos playlist
 */
object RealAccountSyncManager {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val INNERTUBE_BASE = "https://www.youtube.com/youtubei/v1"
    private const val INNERTUBE_KEY  = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20240626.06.00"

    // ── Public sync state (Compose-observable) ─────────────────────────────
    var isSyncing by mutableStateOf(false)
        private set

    var lastSyncError by mutableStateOf<String?>(null)
        private set

    // ──────────────────────────────────────────────────────────────────────
    // Security helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun requireHttps(url: String): String {
        require(url.startsWith("https://")) {
            "Security: Refusing to send session cookies over non-HTTPS URL: $url"
        }
        return url
    }

    // ──────────────────────────────────────────────────────────────────────
    // InnerTube request builder
    // ──────────────────────────────────────────────────────────────────────

    private fun buildClientContext(): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("hl", "en")
                put("gl", "US")
                put("clientName", "WEB")
                put("clientVersion", CLIENT_VERSION)
            })
        })
    }

    private fun innerTubePost(endpoint: String, body: JSONObject, cookies: String): String {
        val url = requireHttps("$INNERTUBE_BASE/$endpoint?key=$INNERTUBE_KEY&prettyPrint=false")
        val requestBody = body.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Cookie", cookies)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("X-Goog-AuthUser", "0")
            .build()

        return App.okHttpClient.newCall(request).execute()
            .body?.string() ?: ""
    }

    // ──────────────────────────────────────────────────────────────────────
    // Main sync entry point
    // ──────────────────────────────────────────────────────────────────────

    suspend fun syncAccountWithCookies(context: Context, cookies: String): Boolean =
        withContext(Dispatchers.IO) {
            if (cookies.isBlank()) return@withContext false

            withContext(Dispatchers.Main) {
                isSyncing = true
                lastSyncError = null
            }

            var success = false
            try {
                // Run all 4 fetches — each is independent so a failure in one doesn't block others
                val channelResult = runCatching { fetchSubscribedChannels(cookies) }
                val feedResult    = runCatching { fetchSubscriptionFeed(cookies) }
                val historyResult = runCatching { fetchWatchHistory(cookies) }
                val likedResult   = runCatching { fetchLikedVideos(cookies) }

                // Log any per-fetch errors without failing the whole sync
                channelResult.exceptionOrNull()?.printStackTrace()
                feedResult.exceptionOrNull()?.printStackTrace()
                historyResult.exceptionOrNull()?.printStackTrace()
                likedResult.exceptionOrNull()?.printStackTrace()

                // Sync is considered successful if we got at least some data
                val gotChannels = SubscriptionManager.subscribedChannels.isNotEmpty()
                val gotFeed     = SubscriptionManager.subscriptionFeedVideos.isNotEmpty()
                val gotHistory  = HistoryManager.watchHistory.isNotEmpty()
                success = gotChannels || gotFeed || gotHistory

                withContext(Dispatchers.Main) {
                    if (!success) {
                        lastSyncError = "Could not load account data. Please check your connection and try again."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    lastSyncError = e.message ?: "Unknown sync error"
                }
            } finally {
                withContext(Dispatchers.Main) { isSyncing = false }
            }

            return@withContext success
        }

    // ──────────────────────────────────────────────────────────────────────
    // 1. Subscribed channels (via /guide)
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun fetchSubscribedChannels(cookies: String) {
        val body = buildClientContext()
        val response = innerTubePost("guide", body, cookies)
        if (response.isBlank()) return

        val channels = extractChannelsFromGuide(response)
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

    // ──────────────────────────────────────────────────────────────────────
    // 2. Subscription feed videos (browseId = FEsubscriptions)
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun fetchSubscriptionFeed(cookies: String) {
        val body = buildClientContext().apply { put("browseId", "FEsubscriptions") }
        val response = innerTubePost("browse", body, cookies)
        if (response.isBlank()) return

        val videos = extractVideosFromResponse(response)
        if (videos.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                SubscriptionManager.subscriptionFeedVideos.clear()
                SubscriptionManager.subscriptionFeedVideos.addAll(videos)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Watch history (browseId = FEhistory)
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun fetchWatchHistory(cookies: String) {
        val body = buildClientContext().apply { put("browseId", "FEhistory") }
        val response = innerTubePost("browse", body, cookies)
        if (response.isBlank()) return

        val videos = extractVideosFromResponse(response)
        if (videos.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                HistoryManager.watchHistory.clear()
                videos.forEach { HistoryManager.addWatchHistory(it) }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. Liked videos playlist (browseId = VLLL — YouTube's Liked Videos list)
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun fetchLikedVideos(cookies: String) {
        val body = buildClientContext().apply { put("browseId", "VLLL") }
        val response = innerTubePost("browse", body, cookies)
        if (response.isBlank()) return

        val videos = extractVideosFromResponse(response)
        if (videos.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                PlaylistManager.likedVideosList.clear()
                PlaylistManager.likedVideosList.addAll(videos)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Parsers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Extracts subscribed channels from a YouTube /guide InnerTube response.
     * The guide sidebar contains a `guideSubscriptionsSectionRenderer` with
     * `guideEntryRenderer` items for each subscribed channel.
     */
    private fun extractChannelsFromGuide(jsonStr: String): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        val SKIP = setOf(
            "Home", "Shorts", "Subscriptions", "You", "History", "Library",
            "Explore", "Trending", "Shopping", "Music", "Films", "Live",
            "Gaming", "News", "Sports", "Learning", "Fashion & Beauty", "Podcasts"
        )

        try {
            // Split on guideEntryRenderer to process each sidebar entry individually
            val chunks = jsonStr.split(""""guideEntryRenderer":""")
            for (i in 1 until chunks.size) {
                val chunk = chunks[i].take(800) // limit lookahead

                // Channel name — can be in simpleText or runs
                val name = """"(?:simpleText|text)":"([^"]{2,60})"""".toRegex()
                    .find(chunk)?.groupValues?.getOrNull(1)
                    ?.takeIf { it !in SKIP } ?: continue

                // Channel avatar — yt3.ggpht.com or googleusercontent.com
                val avatar = """"url":"(https://yt3\.[^"]+)"""".toRegex()
                    .find(chunk)?.groupValues?.getOrNull(1)
                    ?: """"url":"(https://[^"]*ggpht[^"]+)"""".toRegex()
                        .find(chunk)?.groupValues?.getOrNull(1)
                    ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png"

                result.add(Triple(name, avatar.cleanUrl(), "Subscribed"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result.distinctBy { it.first }.take(50)
    }

    /**
     * Extracts Video objects from an InnerTube browse response.
     *
     * Works for subscription feed, history, and playlist responses.
     * Splits on `"videoId":` to get per-video chunks, then extracts
     * all relevant fields from each chunk via targeted regex patterns.
     */
    private fun extractVideosFromResponse(jsonStr: String): List<Video> {
        val videos = mutableListOf<Video>()

        try {
            // Split the full JSON string on every occurrence of "videoId":"..."
            // Each split produces a chunk starting with the 11-char video ID
            val chunks = jsonStr.split(""""videoId":""")

            for (i in 1 until chunks.size) {
                // Take only enough of each chunk to find the relevant fields
                val chunk = chunks[i].take(2000)

                // ── Video ID ──────────────────────────────────────────────
                val id = """"([a-zA-Z0-9_-]{11})"""".toRegex()
                    .find(chunk)?.groupValues?.getOrNull(1) ?: continue
                if (id.length != 11) continue

                // ── Title (runs[] format or simpleText) ───────────────────
                val title =
                    """"title":\{"runs":\[\{"text":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: """"title":\{"simpleText":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: continue  // title is mandatory

                // ── Channel name ──────────────────────────────────────────
                val channel =
                    """"(?:ownerText|shortBylineText|longBylineText)":\{"runs":\[\{"text":"([^"]+)"""".toRegex()
                        .find(chunk)?.groupValues?.getOrNull(1) ?: "YouTube Creator"

                // ── View count ────────────────────────────────────────────
                val views =
                    """"viewCountText":\{"simpleText":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: """"viewCountText":\{"runs":\[\{"text":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: ""

                // ── Duration ──────────────────────────────────────────────
                val duration =
                    """"lengthText":\{[^}]*"simpleText":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: """"text":"(\d+:\d{2}(?::\d{2})?)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: ""

                // ── Upload date ───────────────────────────────────────────
                val uploadDate =
                    """"publishedTimeText":\{"simpleText":"([^"]+)"""".toRegex().find(chunk)?.groupValues?.getOrNull(1)
                    ?: ""

                // ── Channel avatar ────────────────────────────────────────
                val avatarRaw =
                    """"channelThumbnailWithLinkRenderer":\{"thumbnail":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex()
                        .find(chunk)?.groupValues?.getOrNull(1)
                    ?: """"authorThumbnail":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex()
                        .find(chunk)?.groupValues?.getOrNull(1)
                    ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png"

                videos.add(
                    Video(
                        id = id,
                        title = title,
                        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        channelName = channel,
                        channelAvatarUrl = avatarRaw.cleanUrl(),
                        views = views,
                        uploadDate = uploadDate,
                        duration = duration,
                        videoUrl = "https://www.youtube.com/watch?v=$id"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videos.distinctBy { it.id }.take(30)
    }

    /** Normalises protocol-relative URLs like //yt3.ggpht.com/... → https://yt3.ggpht.com/... */
    private fun String.cleanUrl(): String = when {
        startsWith("//") -> "https:$this"
        !startsWith("http") && isNotBlank() ->
            "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png"
        else -> this
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience re-sync trigger (called from ProfileScreen refresh button)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun syncRealYouTubeAccount(context: Context, handleOrQuery: String): Boolean {
        val savedCookies = UserAccountManager.userCookies
        return if (!savedCookies.isNullOrBlank()) {
            syncAccountWithCookies(context, savedCookies)
        } else false
    }
}

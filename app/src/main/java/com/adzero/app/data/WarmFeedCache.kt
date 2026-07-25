package com.adzero.app.data

import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

object WarmFeedCache {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val feedCache = ConcurrentHashMap<String, List<Video>>()
    private var isPrewarming = false

    fun prewarm(context: android.content.Context) {
        if (isPrewarming) return
        isPrewarming = true

        scope.launch {
            try {
                val service = ServiceList.YouTube

                val topics = listOf(
                    "All" to "Trending India 2026",
                    "Shorts" to "youtube shorts trending",
                    "Gaming" to "Gaming Highlights 2026",
                    "Music" to "New Music Videos 2026",
                    "Tech" to "Tech Reviews 2026"
                )

                // Fetch all feeds in parallel with async/awaitAll for 0ms load speed
                val deferreds = topics.map { (key, query) ->
                    async {
                        try {
                            val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(query, emptyList(), "")
                            val info = SearchInfo.getInfo(service, handler)
                            val items = info.relatedItems
                                ?.filterIsInstance<StreamInfoItem>()
                                ?.map { it.toVideo() }
                                ?.shuffled() ?: emptyList()

                            if (items.isNotEmpty()) {
                                feedCache[key] = items

                                // Speculative extraction of top 5 videos for 0ms instant playback
                                items.take(5).forEach { video ->
                                    ExtractionManager.startExtraction(video, isSpeculative = true)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                deferreds.awaitAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFeed(category: String): List<Video>? {
        val cached = feedCache[category]
        if (!cached.isNullOrEmpty()) {
            return cached
        }
        return feedCache["All"]
    }
}

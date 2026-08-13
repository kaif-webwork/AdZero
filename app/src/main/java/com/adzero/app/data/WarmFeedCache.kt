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
                    "Gaming" to "GTA 6 gameplay official",
                    "Music" to "New Music Video 2026",
                    "Live" to "Live stream 24/7",
                    "Podcasts" to "The Ranveer Show podcast",
                    "Technology" to "MKBHD Smartphone Review 2026",
                    "Education" to "Veritasium science experiment",
                    "Movies" to "Official Movie Trailer 2026",
                    "News" to "Aaj Tak Live News Today",
                    "Sports" to "India vs Australia Cricket Highlights"
                )

                // Fetch all category feeds in parallel for 0ms instant category switching
                val deferreds = topics.map { (key, query) ->
                    async {
                        try {
                            val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(query, emptyList(), "")
                            val info = SearchInfo.getInfo(service, handler)
                            val items = info.relatedItems
                                ?.filterIsInstance<StreamInfoItem>()
                                ?.map { it.toVideo() }
                                ?.distinctBy { it.id } ?: emptyList()

                            if (items.isNotEmpty()) {
                                feedCache[key] = items

                                // Speculative extraction of top 3 videos for 0ms instant playback
                                items.take(3).forEach { video ->
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
        return feedCache[category]
    }
}

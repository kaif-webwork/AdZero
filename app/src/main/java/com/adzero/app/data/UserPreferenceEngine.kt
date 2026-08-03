package com.adzero.app.data

import android.content.Context
import com.adzero.app.models.Video
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent local recommendation & preference engine.
 * Learns user interest topics, channel affinities, and search patterns
 * to personalize the "ALL" home feed without sending tracking data anywhere.
 */
object UserPreferenceEngine {

    private const val PREFS_NAME = "adzero_user_preference_profile"
    private const val KEY_TOPICS_JSON = "user_interest_topics_v1"
    private const val KEY_CHANNELS_JSON = "user_interest_channels_v1"

    private val topicScores = ConcurrentHashMap<String, Float>()
    private val channelScores = ConcurrentHashMap<String, Float>()

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "up", "about", "into", "over", "after",
        "video", "official", "full", "hd", "4k", "vs", "new", "2026", "2025",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "will", "would", "shall", "should", "may", "might"
    )

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadPreferences(context)
    }

    /**
     * Records a watched video event (+3.0 score weighting).
     */
    fun recordWatchEvent(context: Context, video: Video) {
        init(context)
        if (video.title.isNotBlank()) {
            extractKeywords(video.title).forEach { kw ->
                topicScores[kw] = (topicScores[kw] ?: 0f) + 3.0f
            }
        }
        if (video.channelName.isNotBlank() && video.channelName != "Unknown Channel") {
            val cleanChannel = video.channelName.trim().lowercase()
            channelScores[cleanChannel] = (channelScores[cleanChannel] ?: 0f) + 3.0f
        }
        savePreferences(context)
    }

    /**
     * Records a search query event (+2.5 score weighting).
     */
    fun recordSearchEvent(context: Context, query: String) {
        init(context)
        if (query.isNotBlank()) {
            extractKeywords(query).forEach { kw ->
                topicScores[kw] = (topicScores[kw] ?: 0f) + 2.5f
            }
        }
        savePreferences(context)
    }

    /**
     * Records a liked video event (+4.0 score weighting).
     */
    fun recordLikeEvent(context: Context, video: Video) {
        init(context)
        extractKeywords(video.title).forEach { kw ->
            topicScores[kw] = (topicScores[kw] ?: 0f) + 4.0f
        }
        if (video.channelName.isNotBlank()) {
            val cleanChannel = video.channelName.trim().lowercase()
            channelScores[cleanChannel] = (channelScores[cleanChannel] ?: 0f) + 4.0f
        }
        savePreferences(context)
    }

    /**
     * Returns top 2 personalized search queries for generating the "ALL" feed.
     */
    fun getPersonalizedQueries(): List<String> {
        val topTopics = topicScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(6)

        val topChannels = channelScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)

        val candidates = mutableListOf<String>()

        if (topTopics.isNotEmpty()) {
            val topCombo = topTopics.take(2).joinToString(" ")
            candidates.add(topCombo)
            if (topTopics.size >= 4) {
                candidates.add(topTopics.drop(2).take(2).joinToString(" "))
            }
        }

        if (topChannels.isNotEmpty()) {
            candidates.add(topChannels.first())
        }

        // Fallbacks if user profile is new
        if (candidates.isEmpty()) {
            candidates.add("Trending India 2026")
            candidates.add("Latest Music Tech Gaming 2026")
        }

        return candidates.distinct().take(3)
    }

    /**
     * Scores, ranks, and filters candidate videos for the "ALL" section
     * based on user preference vectors.
     */
    fun rankAndFilterFeed(videos: List<Video>): List<Video> {
        if (videos.isEmpty()) return emptyList()
        if (topicScores.isEmpty() && channelScores.isEmpty()) return videos.shuffled()

        val maxTopicScore = topicScores.values.maxOrNull() ?: 1f
        val maxChannelScore = channelScores.values.maxOrNull() ?: 1f

        val scoredList = videos.map { video ->
            var score = 0f

            // Keyword match score
            val keywords = extractKeywords(video.title)
            keywords.forEach { kw ->
                val kwScore = topicScores[kw] ?: 0f
                score += (kwScore / maxTopicScore) * 2.0f
            }

            // Channel match score
            val cleanChannel = video.channelName.trim().lowercase()
            val chScore = channelScores[cleanChannel] ?: 0f
            if (chScore > 0f) {
                score += (chScore / maxChannelScore) * 4.0f
            }

            // Add small random noise (0.0 to 0.5) so feed stays dynamic and non-repetitive
            val randomJitter = (0..50).random() / 100f
            video to (score + randomJitter)
        }

        // Sort videos by calculated relevance score
        return scoredList
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }
    }

    private fun loadPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            val topicsStr = prefs.getString(KEY_TOPICS_JSON, null)
            if (!topicsStr.isNullOrBlank()) {
                val json = JSONObject(topicsStr)
                json.keys().forEach { key ->
                    topicScores[key] = json.optDouble(key, 0.0).toFloat()
                }
            }

            val channelsStr = prefs.getString(KEY_CHANNELS_JSON, null)
            if (!channelsStr.isNullOrBlank()) {
                val json = JSONObject(channelsStr)
                json.keys().forEach { key ->
                    channelScores[key] = json.optDouble(key, 0.0).toFloat()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val topicsJson = JSONObject()
            topicScores.entries.sortedByDescending { it.value }.take(50).forEach { (k, v) ->
                topicsJson.put(k, v)
            }

            val channelsJson = JSONObject()
            channelScores.entries.sortedByDescending { it.value }.take(30).forEach { (k, v) ->
                channelsJson.put(k, v)
            }

            prefs.edit()
                .putString(KEY_TOPICS_JSON, topicsJson.toString())
                .putString(KEY_CHANNELS_JSON, channelsJson.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

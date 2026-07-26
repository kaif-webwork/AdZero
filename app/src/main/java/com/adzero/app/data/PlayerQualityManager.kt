package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adzero.app.models.VideoStream

/**
 * Manages user's global video quality preference across all videos.
 * When a user selects a video quality (e.g., 1080p, 720p, 480p, 360p, Auto),
 * that quality preference is remembered and automatically applied to all future videos!
 */
object PlayerQualityManager {
    private const val PREFS_NAME = "adzero_player_settings"
    private const val KEY_PREFERRED_QUALITY = "global_preferred_quality"

    var preferredQuality by mutableStateOf("Auto")
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferredQuality = prefs.getString(KEY_PREFERRED_QUALITY, "Auto") ?: "Auto"
    }

    fun setPreferredQuality(context: Context, quality: String) {
        val cleanQuality = if (quality.startsWith("Auto")) "Auto" else quality
        preferredQuality = cleanQuality
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFERRED_QUALITY, cleanQuality).apply()
    }

    /**
     * Finds the best matching stream based on user's preferred quality setting.
     * Matches exact quality resolution (e.g. 1080p, 720p, 480p, 360p).
     * If exact resolution is unavailable, falls back to the closest available quality.
     */
    fun findBestMatchingStream(streams: List<VideoStream>, targetQuality: String): VideoStream? {
        if (streams.isEmpty()) return null
        if (targetQuality.equals("Auto", ignoreCase = true)) return null

        val cleanTarget = targetQuality.replace("60", "").trim().lowercase()

        // 1. Look for exact quality match (e.g. "1080p60" or "1080p")
        val exactMatch = streams.firstOrNull { it.quality.equals(targetQuality, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 2. Look for base resolution match (e.g. "1080p" matching "1080p60")
        val baseMatch = streams.firstOrNull { it.quality.replace("60", "").trim().lowercase() == cleanTarget }
        if (baseMatch != null) return baseMatch

        // 3. Extract numeric height (e.g. 1080, 720, 480, 360) and find closest available resolution
        val targetHeight = extractHeight(targetQuality)
        if (targetHeight > 0) {
            return streams.minByOrNull { stream ->
                val streamHeight = extractHeight(stream.quality)
                if (streamHeight > 0) kotlin.math.abs(streamHeight - targetHeight) else Int.MAX_VALUE
            }
        }

        return streams.firstOrNull()
    }

    private fun extractHeight(qualityLabel: String): Int {
        val digits = qualityLabel.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}

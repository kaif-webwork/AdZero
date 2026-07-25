package com.adzero.app.data

import com.adzero.app.models.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

object ExtractionManager {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // Cache to store successful extractions: VideoId -> Pair<StreamInfo, CommentsInfo?>
    private val extractionCache = ConcurrentHashMap<String, Pair<StreamInfo, CommentsInfo?>>()
    
    private val _extractionState = MutableStateFlow<ExtractionResult?>(null)
    val extractionState = _extractionState.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    // Track which videos the user is actually waiting for
    private val requestedVideos = ConcurrentHashMap.newKeySet<String>()

    fun startExtraction(video: Video, isSpeculative: Boolean = false) {
        val normalizedId = normalizeId(video.id)
        
        if (!isSpeculative) {
            requestedVideos.add(normalizedId)
        }

        // Return if already cached
        val cached = extractionCache[normalizedId]
        if (cached != null) {
            if (!isSpeculative) {
                _extractionState.value = ExtractionResult.Success(normalizedId, cached.first, cached.second)
            }
            return
        }

        // If user explicitly clicked, update UI to loading state even if background job exists
        if (!isSpeculative) {
            _extractionState.value = ExtractionResult.Loading(video)
        }

        // Return if already being extracted
        if (activeJobs.containsKey(normalizedId)) {
            return
        }

        val job = scope.launch {
            try {
                val targetUrl = if (video.videoUrl.startsWith("http")) video.videoUrl 
                                else "https://www.youtube.com/watch?v=$normalizedId"

                // Fetch Stream and Comments in parallel
                val infoDeferred = async { StreamInfo.getInfo(targetUrl) }
                val commentsDeferred = async { 
                    try { CommentsInfo.getInfo(targetUrl) } catch (e: Exception) { null }
                }

                val info = infoDeferred.await()
                val comments = commentsDeferred.await()
                
                extractionCache[normalizedId] = Pair(info, comments)
                
                if (requestedVideos.contains(normalizedId)) {
                    _extractionState.value = ExtractionResult.Success(normalizedId, info, comments)
                }
            } catch (e: Exception) {
                if (requestedVideos.contains(normalizedId)) {
                    e.printStackTrace()
                    _extractionState.value = ExtractionResult.Error(normalizedId, e.message ?: "Unknown error")
                }
            } finally {
                activeJobs.remove(normalizedId)
            }
        }
        activeJobs[normalizedId] = job
    }

    fun getOptimalStreamForNetwork(context: android.content.Context, info: StreamInfo): String? {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)

        val downstreamKbps = capabilities?.linkDownstreamBandwidthKbps ?: 5000
        val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true

        val allStreams = mutableListOf<org.schabi.newpipe.extractor.stream.VideoStream>()
        info.videoStreams?.let { allStreams.addAll(it) }
        info.videoOnlyStreams?.let { allStreams.addAll(it) }

        if (allStreams.isEmpty()) {
            if (!info.hlsUrl.isNullOrBlank()) return info.hlsUrl
            if (!info.dashMpdUrl.isNullOrBlank()) return info.dashMpdUrl
            return null
        }

        val chosen = when {
            isWifi || downstreamKbps > 5000 -> {
                allStreams.firstOrNull { it.resolution?.contains("720") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("1080") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("480") == true }
                    ?: allStreams.firstOrNull()
            }
            downstreamKbps > 1500 -> {
                allStreams.firstOrNull { it.resolution?.contains("480") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("360") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("720") == true }
                    ?: allStreams.firstOrNull()
            }
            else -> {
                allStreams.firstOrNull { it.resolution?.contains("360") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("240") == true }
                    ?: allStreams.firstOrNull { it.resolution?.contains("480") == true }
                    ?: allStreams.firstOrNull()
            }
        }
        return chosen?.content ?: info.hlsUrl ?: info.dashMpdUrl
    }

    fun getCachedInfo(videoId: String): StreamInfo? {
        return extractionCache[normalizeId(videoId)]?.first
    }

    fun normalizeId(id: String): String {
        return when {
            id.contains("v=") -> id.substringAfter("v=").substringBefore("&").substringBefore("?")
            id.contains("/shorts/") -> id.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
            else -> id.substringAfterLast("/").substringBefore("?").substringBefore("&")
        }.ifBlank { id }
    }

    fun clear() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        requestedVideos.clear()
        _extractionState.value = null
    }

    sealed class ExtractionResult {
        data class Loading(val video: Video) : ExtractionResult()
        data class Success(val videoId: String, val info: StreamInfo, val comments: CommentsInfo? = null) : ExtractionResult()
        data class Error(val videoId: String, val message: String) : ExtractionResult()
    }
}

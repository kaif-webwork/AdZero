package com.adzero.app.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.text.Html
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adzero.app.*
import com.adzero.app.components.*
import com.adzero.app.data.*
import com.adzero.app.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

data class SubtitleTrack(
    val url: String,
    val languageCode: String,
    val languageName: String,
    val isAutoGenerated: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: Video,
    fraction: Float, // 1.0 = Expanded, 0.0 = Collapsed
    dragModifier: Modifier = Modifier,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ── Helper: Network-aware quality selection (Zero-Stutter Auto Quality) ───
    fun selectBestAutoQuality(streams: List<VideoStream>, bw: Int, wifi: Boolean): VideoStream? {
        if (streams.isEmpty()) return null

        fun findStream(quality: String, preferMp4: Boolean = true): VideoStream? {
            return if (preferMp4) {
                streams.firstOrNull { it.quality.contains(quality) && it.format.lowercase().contains("mp4") }
                    ?: streams.firstOrNull { it.quality.contains(quality) }
            } else {
                streams.firstOrNull { it.quality.contains(quality) }
            }
        }

        // 1. Prioritize Progressive streams if available (single stream, instant start, zero buffer)
        val progressive720 = streams.firstOrNull { !it.isVideoOnly && it.quality.contains("720") }
        if (progressive720 != null && (wifi || bw >= 2500)) return progressive720
        val progressive480 = streams.firstOrNull { !it.isVideoOnly && it.quality.contains("480") }
        if (progressive480 != null && bw in 1500..3499) return progressive480

        val effectiveBandwidth = if (wifi) 8000 else bw
        return when {
            effectiveBandwidth >= 8000 ->
                progressive720
                ?: findStream("720") // 720p MP4 is ultra-smooth and fast on mobile
                ?: findStream("1080")
                ?: findStream("480")
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 3500 ->
                progressive720
                ?: findStream("720")
                ?: findStream("480")
                ?: findStream("360")
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 1500 ->
                findStream("480")
                ?: findStream("360")
                ?: findStream("240")
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 500 ->
                findStream("360")
                ?: findStream("240")
                ?: findStream("144")
                ?: streams.firstOrNull { it.url.isNotBlank() }
            else ->
                findStream("360")
                ?: findStream("240")
                ?: findStream("144")
                ?: streams.firstOrNull()
        }
    }

    // ── Dynamic Video Data ────────────────────────────────────────────────
    var videoTitle by remember { mutableStateOf(video.title) }
    var channelName by remember { mutableStateOf(video.channelName) }
    var channelAvatar by remember { mutableStateOf(video.channelAvatarUrl) }
    var viewsDate by remember { mutableStateOf("${video.views} • ${video.uploadDate}") }
    var subscriberCount by remember { mutableStateOf(video.subscriberCount) }
    var likesCount by remember { mutableStateOf(video.likes) }
    var descriptionText by remember { mutableStateOf(video.description) }
    var relatedVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var commentsList by remember { mutableStateOf<List<com.adzero.app.models.Comment>>(emptyList()) }

    // ── Player & Stream State ─────────────────────────────────────────────
    var extractedVideoStreams by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var extractedAudioStreams by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var extractedSubtitles by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var selectedStream by remember { mutableStateOf<VideoStream?>(null) }
    var selectedAudioStream by remember { mutableStateOf<VideoStream?>(null) }
    var selectedSubtitleTrack by remember { mutableStateOf<SubtitleTrack?>(null) }
    var isCcEnabled by remember { mutableStateOf(false) }
    var showCcHud by remember { mutableStateOf<String?>(null) }
    var playerStatusText by remember { mutableStateOf("Extracting...") }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var doubleTapFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isLongPressing by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // true = user manually picked a quality; auto-select won't override it
    var userSelectedQuality by remember { mutableStateOf(false) }

    val displayQuality = remember(selectedStream, userSelectedQuality) {
        val q = selectedStream?.quality ?: "720p"
        if (!userSelectedQuality) {
            "Auto ($q)"
        } else {
            q
        }
    }

    // ── Gesture state: volume / brightness / scrub ────────────────────────
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volumeLevel by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var brightnessLevel by remember {
        val w = (context as? Activity)?.window
        mutableFloatStateOf(w?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    var showVolumeHud by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    // Pinch-to-fill: true = RESIZE_MODE_ZOOM (fill/crop), false = RESIZE_MODE_FIT (letterbox)
    var isFillMode by remember { mutableStateOf(false) }
    var showFillModeHud by remember { mutableStateOf(false) }

    val exoPlayer = remember { GlobalPlayerManager.getPlayer(context) }
    
    var isBuffering by remember { mutableStateOf(false) }

    val toggleCc: () -> Unit = remember(extractedSubtitles, isCcEnabled, selectedSubtitleTrack, exoPlayer) {
        {
            if (extractedSubtitles.isEmpty()) {
                showCcHud = "No Captions Available"
            } else if (isCcEnabled) {
                isCcEnabled = false
                selectedSubtitleTrack = null
                val params = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                exoPlayer.trackSelectionParameters = params
                showCcHud = "Captions OFF"
            } else {
                val track = selectedSubtitleTrack
                    ?: extractedSubtitles.firstOrNull { !it.isAutoGenerated && it.languageCode.startsWith("en") }
                    ?: extractedSubtitles.firstOrNull { !it.isAutoGenerated }
                    ?: extractedSubtitles.firstOrNull()

                if (track != null) {
                    isCcEnabled = true
                    selectedSubtitleTrack = track
                    val params = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(track.languageCode)
                        .build()
                    exoPlayer.trackSelectionParameters = params
                    showCcHud = "Captions ON (${track.languageName})"
                } else {
                    showCcHud = "No Captions Available"
                }
            }
        }
    }

    // ── FEATURE: Auto-hide controls when playing ─────────────────────────
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3500) // Hide after 3.5 seconds of inactivity
            isControlsVisible = false
        }
    }

    // ── FEATURE: Keep screen on during playback ──────────────────────────
    DisposableEffect(isPlaying) {
        val window = (context as? Activity)?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        totalDuration = exoPlayer.duration
                        isBuffering = false
                    }
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                // Only mark as paused if the player is truly paused (not just buffering a new video).
                // During setMediaSource/prepare(), ExoPlayer briefly reports isPlaying=false
                // even though playWhenReady=true. We ignore that transient state.
                if (playing) {
                    isPlaying = true
                } else if (exoPlayer.playbackState != Player.STATE_BUFFERING && exoPlayer.playbackState != Player.STATE_IDLE) {
                    isPlaying = false
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Invalidate cached stream for this video so next load will re-extract valid non-expired tokens
                com.adzero.app.data.ExtractionManager.invalidateCache(video.id)

                // If user specifically requested a quality, do NOT silently downgrade it!
                if (userSelectedQuality) {
                    // Just attempt to retry the same stream since it's the requested quality
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }
                
                // Automatic 0-pause recovery: fallback to progressive stream if YouTube CDN drops chunk
                val fallback = extractedVideoStreams.firstOrNull { !it.isVideoOnly && it.url.isNotEmpty() }
                    ?: extractedVideoStreams.firstOrNull { it.url != selectedStream?.url }
                if (fallback != null && fallback != selectedStream) {
                    selectedStream = fallback
                } else {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Real-time playback position observer ──
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(500)
            val pos = exoPlayer.currentPosition
            if (pos >= 0L && pos != playbackPosition) {
                playbackPosition = pos
            }
        }
    }

    suspend fun processStreamInfo(info: StreamInfo, comments: org.schabi.newpipe.extractor.comments.CommentsInfo?) {
        val isRealLiveContent = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                                info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                                video.isLive

        val hlsStream = if (isRealLiveContent) info.hlsUrl?.let { VideoStream(it, "LIVE", isHls = true) } else null
        val progressive = info.videoStreams?.map { VideoStream(it.content, it.resolution, format = it.format?.name?.lowercase() ?: "mp4", isVideoOnly = false) } ?: emptyList()
        val vOnly = info.videoOnlyStreams?.map { VideoStream(it.content, it.resolution, format = it.format?.name?.lowercase() ?: "mp4", isVideoOnly = true) } ?: emptyList()
        val audio = info.audioStreams?.mapIndexed { index, audioStream ->
            // Determine if this is the original/default track using NewPipe's audioTrackType
            // audioTrackType: null or "ORIGINAL" = original, "DUBBED" = dubbed, "DESCRIPTIVE" = audio description
            val trackType = try {
                audioStream.audioTrackType?.name ?: ""
            } catch (e: Exception) { "" }
            val isOriginal = trackType.isBlank() || trackType.equals("ORIGINAL", ignoreCase = true)
            val isDubbed = trackType.equals("DUBBED", ignoreCase = true) || trackType.equals("DRC", ignoreCase = true)
            val isDescriptive = trackType.equals("DESCRIPTIVE", ignoreCase = true)

            // Build a clean display name
            val baseName = when {
                !audioStream.audioTrackName.isNullOrBlank() -> audioStream.audioTrackName!!
                audioStream.audioLocale != null -> audioStream.audioLocale!!.displayName
                index == 0 -> "Original"
                else -> "Track ${index + 1}"
            }
            val suffix = when {
                isDubbed     -> " (Dubbed)"
                isDescriptive -> " (Audio Description)"
                else         -> ""
            }
            val bitrateStr = if (audioStream.averageBitrate > 0) " · ${audioStream.averageBitrate}kbps" else ""
            val fullDisplayName = "$baseName$suffix$bitrateStr"

            VideoStream(
                url = audioStream.content,
                quality = "$baseName$suffix",  // quality used internally for matching
                format = audioStream.format?.name?.lowercase() ?: "m4a", // Store format to match with video later (webm vs m4a)
                isVideoOnly = false,
                displayName = fullDisplayName,
                isOriginalTrack = isOriginal
            )
        } ?: emptyList()

        // ── Network-aware quality tier detection ─────────────────────────────
        // Reads actual downstream bandwidth and maps it to the best playable quality.
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val bandwidthKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val isWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            || caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true

        // Quality tier thresholds (kbps):
        //  < 500  → 144p or lowest available (2G / very poor connection)
        //  < 1500 → 360p  (3G / weak 4G)
        //  < 4000 → 480p  (average 4G)
        //  < 8000 → 720p  (good 4G)
        //  ≥ 8000 → 1080p (WiFi / fast 4G)

        val allVideoStreams = progressive + vOnly

        val parsedSubtitles = try {
            info.subtitles?.map { sub ->
                val langCode = try { sub.languageTag ?: "en" } catch (e: Exception) { "en" }
                val langName = try {
                    val name = sub.displayLanguageName
                    if (!name.isNullOrBlank()) name else langCode
                } catch (e: Exception) { langCode }
                val isAuto = try { sub.isAutoGenerated } catch (e: Exception) { false }
                val url = try { sub.content } catch (e: Exception) { "" }

                SubtitleTrack(
                    url = url,
                    languageCode = langCode,
                    languageName = langName,
                    isAutoGenerated = isAuto
                )
            }?.filter { it.url.isNotBlank() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        withContext(Dispatchers.Main) {
            extractedVideoStreams = (if (hlsStream != null) listOf(hlsStream) else emptyList()) + progressive + vOnly
            extractedAudioStreams = audio
            extractedSubtitles = parsedSubtitles

            // Default to English or first non-auto subtitle track if available
            val defaultSub = parsedSubtitles.firstOrNull { !it.isAutoGenerated && it.languageCode.startsWith("en") }
                ?: parsedSubtitles.firstOrNull { !it.isAutoGenerated }
                ?: parsedSubtitles.firstOrNull()
            selectedSubtitleTrack = defaultSub

            // Apply user's saved global quality preference across all videos
            val globalPref = com.adzero.app.data.PlayerQualityManager.preferredQuality
            if (globalPref != "Auto") {
                val matchedStream = com.adzero.app.data.PlayerQualityManager.findBestMatchingStream(extractedVideoStreams, globalPref)
                if (matchedStream != null) {
                    selectedStream = matchedStream
                    userSelectedQuality = true
                    playerStatusText = "${matchedStream.quality}"
                }
            }

            // Fall back to Auto Quality if no saved preference or stream unavailable
            if (selectedStream == null || !userSelectedQuality) {
                val autoSelected = if (isRealLiveContent) {
                    hlsStream ?: allVideoStreams.firstOrNull()
                } else {
                    val candidateStreams = if (allVideoStreams.any { it.url.isNotBlank() }) allVideoStreams else progressive
                    selectBestAutoQuality(candidateStreams, bandwidthKbps, isWifi)
                }
                if (!userSelectedQuality) {
                    selectedStream = autoSelected
                    playerStatusText = selectedStream?.let { s ->
                        val bw = if (bandwidthKbps > 0) " (${bandwidthKbps}kbps)" else ""
                        "Auto • ${s.quality}$bw"
                    } ?: "No streams found"
                }
            }
            // Always update audio track default (doesn't override user audio selection)
            if (selectedAudioStream == null) {
                selectedAudioStream = audio.firstOrNull { it.isOriginalTrack } ?: audio.firstOrNull()
            }
        }
        
        val related = info.relatedItems?.filterIsInstance<StreamInfoItem>()?.map { it.toVideo() } ?: emptyList()
        val realComments = comments?.relatedItems?.map { it.toComment() } ?: emptyList()

        withContext(Dispatchers.Main) {
            videoTitle = info.name ?: video.title
            channelName = info.uploaderName ?: video.channelName
            channelAvatar = info.uploaderAvatars.firstOrNull()?.url ?: video.channelAvatarUrl
            val viewStr = if (info.viewCount > 0) formatNumberCount(info.viewCount) + " views" else video.views
            
            val displayDate = info.textualUploadDate?.let { date ->
                if (date.contains("T") && date.contains("-")) {
                    date.substringBefore("T")
                } else date
            } ?: ""
            
            viewsDate = if (displayDate.isEmpty()) viewStr else "$viewStr • $displayDate"
            subscriberCount = if (info.uploaderSubscriberCount > 0) formatNumberCount(info.uploaderSubscriberCount) + " subscribers" else video.subscriberCount
            likesCount = if (info.likeCount > 0) formatNumberCount(info.likeCount) else video.likes
            descriptionText = info.description?.content ?: ""
            relatedVideos = related
            com.adzero.app.data.GlobalPlayerManager.setQueue(related)
            commentsList = realComments
            playerStatusText = if (extractedVideoStreams.isEmpty()) "No playable streams" else "Playing ad-free 🛡️"
        }
    }

    var activeAudioUrl by remember { mutableStateOf<String?>(null) }
    var lastLoadedStreamUrl by remember { mutableStateOf<String?>(null) }
    var lastPlayedVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(video.id) {
        // 1. INSTANTLY stop and clear previous video playback & audio
        try {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Reset player state for new video
        playbackPosition = 0L
        totalDuration = 0L
        selectedStream = null
        selectedAudioStream = null
        extractedVideoStreams = emptyList()
        extractedAudioStreams = emptyList()
        lastLoadedStreamUrl = null
        activeAudioUrl = null
        userSelectedQuality = (com.adzero.app.data.PlayerQualityManager.preferredQuality != "Auto")

        val normalizedId = ExtractionManager.normalizeId(video.id)
        val cached = ExtractionManager.extractionState.value
        var hasProcessedCached = false
        if (cached is ExtractionManager.ExtractionResult.Success && cached.videoId == normalizedId) {
            hasProcessedCached = true
            processStreamInfo(cached.info, cached.comments)
        } else {
            ExtractionManager.startExtraction(video)
        }

        ExtractionManager.extractionState.collectLatest { result ->
            when (result) {
                is ExtractionManager.ExtractionResult.Success -> {
                    if (result.videoId == normalizedId) {
                        if (!hasProcessedCached) {
                            processStreamInfo(result.info, result.comments)
                        }
                        hasProcessedCached = false
                    }
                }
                is ExtractionManager.ExtractionResult.Error -> if (result.videoId == normalizedId) playerStatusText = "Error: ${result.message.take(40)}"
                is ExtractionManager.ExtractionResult.Loading -> if (ExtractionManager.normalizeId(result.video.id) == normalizedId) playerStatusText = "Extracting..."
                null -> {}
            }
        }
    }


    LaunchedEffect(selectedStream, selectedAudioStream, playbackSpeed, video.id) {
        selectedStream?.let { stream ->
            val currentAudioStream = selectedAudioStream // Capture into local val for smart casting
            val currentSpeed = exoPlayer.playbackParameters.speed
            val currentAudioUrl = currentAudioStream?.url

            // Guard: skip reload if nothing changed
            val isAlreadyLoaded = lastLoadedStreamUrl == stream.url
                && currentSpeed == playbackSpeed
                && activeAudioUrl == currentAudioUrl
                && exoPlayer.playbackState != Player.STATE_IDLE
                && exoPlayer.playbackState != Player.STATE_ENDED
            if (isAlreadyLoaded) return@LaunchedEffect
            
            // Start from 0 for a new video; preserve position when changing quality/audio for same video.
            val currentPos = if (lastPlayedVideoId == video.id && lastPlayedVideoId != null) {
                exoPlayer.currentPosition.coerceAtLeast(0L)
            } else {
                0L
            }
            
            lastLoadedStreamUrl = stream.url
            activeAudioUrl = currentAudioUrl
            lastPlayedVideoId = video.id

            val httpDsFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)

            val upstreamDsFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDsFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(upstreamDsFactory)

            val subtitleConfigs = extractedSubtitles.map { sub ->
                val mimeType = if (sub.url.contains("ttml") || sub.url.contains("xml")) {
                    androidx.media3.common.MimeTypes.APPLICATION_TTML
                } else {
                    androidx.media3.common.MimeTypes.TEXT_VTT
                }

                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(mimeType)
                    .setLanguage(sub.languageCode)
                    .setLabel(sub.languageName + if (sub.isAutoGenerated) " (auto-generated)" else "")
                    .build()
            }

            val mediaMetadata = GlobalPlayerManager.buildMetadata(video)
            val videoMediaItemBuilder = MediaItem.Builder()
                .setMediaId(video.id)
                .setUri(stream.url)
                .setMediaMetadata(mediaMetadata)
            if (subtitleConfigs.isNotEmpty()) {
                videoMediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
            val videoMediaItem = videoMediaItemBuilder.build()

            val videoSource = if (stream.isHls) {
                HlsMediaSource.Factory(upstreamDsFactory).createMediaSource(videoMediaItem)
            } else {
                mediaSourceFactory.createMediaSource(videoMediaItem)
            }
            
            val fallbackAudio = currentAudioStream ?: extractedAudioStreams.firstOrNull { it.isOriginalTrack } ?: extractedAudioStreams.firstOrNull()
            
            // Container matching prevents timescale timestamp drift in MergingMediaSource
            val chosenAudio = if (stream.isVideoOnly) {
                val fmt = stream.format.lowercase()
                val isVideoWebM = fmt.contains("webm") || fmt.contains("vp")
                val isVideoMp4 = fmt.contains("mp4") || fmt.contains("mpeg") || fmt.contains("avc") || fmt.contains("av01")
                
                val matchingContainerAudio = extractedAudioStreams.firstOrNull { audio ->
                    val afmt = audio.format.lowercase()
                    val isAudioWebM = afmt.contains("webm") || afmt.contains("opus") || afmt.contains("webma")
                    val isAudioMp4 = afmt.contains("m4a") || afmt.contains("mp4") || afmt.contains("aac")
                    
                    (currentAudioStream == null || audio.quality == currentAudioStream.quality) &&
                    ((isVideoWebM && isAudioWebM) || (isVideoMp4 && isAudioMp4))
                } ?: extractedAudioStreams.firstOrNull { audio ->
                    val afmt = audio.format.lowercase()
                    val isAudioWebM = afmt.contains("webm") || afmt.contains("opus") || afmt.contains("webma")
                    val isAudioMp4 = afmt.contains("m4a") || afmt.contains("mp4") || afmt.contains("aac")
                    (isVideoWebM && isAudioWebM) || (isVideoMp4 && isAudioMp4)
                }
                matchingContainerAudio ?: fallbackAudio
            } else {
                fallbackAudio
            }

            val finalSource = if (!stream.isHls && stream.isVideoOnly && chosenAudio != null
                && chosenAudio.url.isNotBlank()) {
                val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(chosenAudio.url))
                MergingMediaSource(true, true, videoSource, audioSource)
            } else {
                videoSource
            }
            
            // Correct ExoPlayer API order: set source → prepare → play
            exoPlayer.playWhenReady = true
            exoPlayer.setMediaSource(finalSource, currentPos)
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.prepare()
            exoPlayer.play()
            GlobalPlayerManager.onVideoStarted(context, video)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exoPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isCollapsed = fraction < 0.5f

    // ── System Immersive Mode in Landscape (100% Edge-to-Edge Fullscreen) ──
    DisposableEffect(isLandscape) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            val window = activity.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val disposeActivity = context as? android.app.Activity
            if (disposeActivity != null) {
                val window = disposeActivity.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isCollapsed) Color.Transparent else Color.Black.copy(alpha = if (isLandscape) 1f else fraction))
    ) {
        if (isCollapsed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 86.dp)
                    .height(64.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF212121))
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {},
                            onDrag = { change, dragAmount ->
                                // Swipe UP -> Expand to full player
                                if (dragAmount.y < -15f) {
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExpand()
                                }
                                // Swipe DOWN -> Close player
                                else if (dragAmount.y > 15f) {
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        exoPlayer.stop()
                                        exoPlayer.clearMediaItems()
                                    } catch (e: Exception) { e.printStackTrace() }
                                    onClose()
                                }
                                // Horizontal Swipe (Left or Right) -> Dismiss player
                                else if (kotlin.math.abs(dragAmount.x) > 25f) {
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        exoPlayer.stop()
                                        exoPlayer.clearMediaItems()
                                    } catch (e: Exception) { e.printStackTrace() }
                                    onClose()
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpand() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: 16:9 Video surface or Thumbnail
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        if (selectedStream != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    view.player = exoPlayer
                                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            )
                        } else {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Center: Video Title & Channel Name
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = video.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = channelName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    // Right: Play/Pause and Close buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    exoPlayer.stop()
                                    exoPlayer.clearMediaItems()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onClose()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Bottom: Red Progress Bar Line across the miniplayer
                val progress = if (totalDuration > 0) (playbackPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Color(0xFFFF2661))
                    )
                }
            }
        } else {
            Column(
                modifier = if (!isLandscape && fraction > 0.5f) Modifier.statusBarsPadding().fillMaxSize() else Modifier.fillMaxSize()
            ) {
                // Use LocalConfiguration for correct screen width (excludes navigation bar, in dp)
                val configuration = LocalConfiguration.current
                val expandedVideoHeight = configuration.screenWidthDp.dp * (9f / 16f)

                // Main Video Container Box (Exact 16:9 Aspect Ratio)
                Box(
                    modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier
                        .fillMaxWidth()
                        .height(expandedVideoHeight)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedStream != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.player = exoPlayer
                                view.resizeMode = if (isFillMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                view.subtitleView?.apply {
                                    setStyle(
                                        androidx.media3.ui.CaptionStyleCompat(
                                            android.graphics.Color.WHITE,
                                            android.graphics.Color.TRANSPARENT,
                                            android.graphics.Color.TRANSPARENT,
                                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                            android.graphics.Color.BLACK,
                                            null
                                        )
                                    )
                                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                                }
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
                        }
                    }

                        // ── YouTube Gesture Layer ─────────────────────────────────────────────
                        // Unified high-performance gesture processor: tap, double-tap seek, long-press 2x,
                        // vertical swipe (minimize in portrait, landscape toggle, brightness/volume),
                        // horizontal scrub, pinch-to-fill
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isLandscape, totalDuration, playbackSpeed) {
                                    val swipeDownThreshold = 35.dp.toPx()
                                    val swipeUpThreshold = 42.dp.toPx()
                                    val touchSlop = 12.dp.toPx()

                                    var lastTapTime = 0L
                                    var lastTapX = 0f
                                    var pendingSingleTapJob: kotlinx.coroutines.Job? = null

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downX = down.position.x
                                        val screenW = size.width.toFloat()

                                        var isDragging = false
                                        var dragDirection: String? = null // "horizontal" | "vertical"
                                        var isLongPressActive = false
                                        var gestureFinished = false

                                        // Launch long press detector (400ms hold -> 2x speed)
                                        val longPressJob = scope.launch {
                                            delay(400)
                                            if (!isDragging && !gestureFinished) {
                                                isLongPressActive = true
                                                isLongPressing = true
                                                exoPlayer.setPlaybackSpeed(2.0f)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }

                                        var cumDx = 0f
                                        var cumDy = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()

                                            // Multi-touch for Pinch-to-Zoom (Landscape mode)
                                            if (isLandscape && event.changes.size >= 2) {
                                                longPressJob.cancel()
                                                val p0 = event.changes[0]
                                                val p1 = event.changes[1]
                                                val prevDist = (p0.previousPosition - p1.previousPosition).getDistance()
                                                val currDist = (p0.position - p1.position).getDistance()
                                                if (prevDist > 0f) {
                                                    val zoomRatio = currDist / prevDist
                                                    if (zoomRatio > 1.04f && !isFillMode) {
                                                        p0.consume()
                                                        p1.consume()
                                                        isFillMode = true
                                                        showFillModeHud = true
                                                    } else if (zoomRatio < 0.96f && isFillMode) {
                                                        p0.consume()
                                                        p1.consume()
                                                        isFillMode = false
                                                        showFillModeHud = true
                                                    }
                                                }
                                            }

                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) {
                                                // Finger lifted
                                                break
                                            }

                                            val deltaX = change.position.x - change.previousPosition.x
                                            val deltaY = change.position.y - change.previousPosition.y
                                            cumDx += deltaX
                                            cumDy += deltaY

                                            val absTotalX = kotlin.math.abs(cumDx)
                                            val absTotalY = kotlin.math.abs(cumDy)

                                            // Determine if gesture has crossed touch slop
                                            if (!isDragging && (absTotalX > touchSlop || absTotalY > touchSlop)) {
                                                isDragging = true
                                                longPressJob.cancel()
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = null
                                                dragDirection = if (absTotalY > absTotalX) "vertical" else "horizontal"
                                            }

                                            if (isDragging) {
                                                longPressJob.cancel()
                                                when (dragDirection) {
                                                    "vertical" -> {
                                                        change.consume()
                                                        if (!isLandscape) {
                                                            // PORTRAIT MODE:
                                                            // 1. Swipe Down -> Minimize to Miniplayer
                                                            if (cumDy > swipeDownThreshold) {
                                                                gestureFinished = true
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                onMinimize()
                                                                break
                                                            }
                                                            // 2. Swipe Up -> Fullscreen Landscape
                                                            else if (cumDy < -swipeUpThreshold) {
                                                                gestureFinished = true
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                                break
                                                            }
                                                        } else {
                                                            // LANDSCAPE MODE:
                                                            // 1. Swipe Down -> Exit to Portrait
                                                            if (cumDy > swipeDownThreshold * 1.25f) {
                                                                gestureFinished = true
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                                break
                                                            }
                                                            // 2. Left side -> Brightness
                                                            else if (downX < screenW * 0.45f) {
                                                                val delta = -deltaY * 0.005f
                                                                val newBright = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                                                brightnessLevel = newBright
                                                                val activity = context as? Activity
                                                                activity?.window?.let { w ->
                                                                    val lp = w.attributes
                                                                    lp.screenBrightness = newBright
                                                                    w.attributes = lp
                                                                }
                                                                showBrightnessHud = true
                                                                showVolumeHud = false
                                                            }
                                                            // 3. Right side -> Volume
                                                            else if (downX > screenW * 0.55f) {
                                                                val delta = -deltaY * 0.005f
                                                                val newVol = (volumeLevel + delta * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                                                volumeLevel = newVol
                                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                                                showVolumeHud = true
                                                                showBrightnessHud = false
                                                            }
                                                        }
                                                    }
                                                    "horizontal" -> {
                                                        if (!isLandscape && totalDuration > 0) {
                                                            change.consume()
                                                            val scrubDelta = (deltaX / screenW) * totalDuration
                                                            scrubPosition = (scrubPosition + scrubDelta).toLong().coerceIn(0L, totalDuration)
                                                            isScrubbing = true
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // --- GESTURE ENDED (Finger Lifted) ---
                                        longPressJob.cancel()

                                        // If 2x speed was active, restore playback speed
                                        if (isLongPressActive) {
                                            isLongPressing = false
                                            exoPlayer.setPlaybackSpeed(playbackSpeed)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }

                                        // If scrubbing was active, seek to scrubbed position
                                        if (isScrubbing && !isLandscape) {
                                            exoPlayer.seekTo(scrubPosition)
                                            isScrubbing = false
                                        }

                                        // If it was a quick touch and NOT dragging / long pressing / finished:
                                        if (!isDragging && !isLongPressActive && !gestureFinished) {
                                            val tapTime = System.currentTimeMillis()
                                            val isDoubleTap = (tapTime - lastTapTime < 320) && (kotlin.math.abs(downX - lastTapX) < 140f)

                                            if (isDoubleTap) {
                                                // Cancel pending single tap
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = null
                                                lastTapTime = 0L

                                                // Double tap seek (-10s / +10s)
                                                val isRight = downX > (screenW / 2)
                                                if (isRight) {
                                                    exoPlayer.seekTo(exoPlayer.currentPosition + 10_000)
                                                    doubleTapFeedback = Pair(true, "+10s")
                                                } else {
                                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                                                    doubleTapFeedback = Pair(false, "-10s")
                                                }
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else {
                                                lastTapTime = tapTime
                                                lastTapX = downX

                                                // Wait 300ms for a possible second tap before toggling controls overlay
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = scope.launch {
                                                    delay(300)
                                                    isControlsVisible = !isControlsVisible
                                                }
                                            }
                                        }
                                    }
                                }
                        )

                        // Fill Mode HUD
                        if (showFillModeHud) {
                            LaunchedEffect(isFillMode) {
                                delay(1200)
                                showFillModeHud = false
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 56.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.70f))
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFillMode) Icons.Default.ZoomOutMap else Icons.Default.FitScreen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isFillMode) "Fill" else "Fit",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Controls Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            PlayerControlsOverlay(
                                isPlaying = isPlaying,
                                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                onForward = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) },
                                onNext = { com.adzero.app.data.GlobalPlayerManager.playNext() },
                                onPrevious = { com.adzero.app.data.GlobalPlayerManager.playPrevious() },
                                onBack = {
                                    if (isLandscape) {
                                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        onMinimize()
                                    }
                                },
                                onSettings = { showSettingsSheet = true },
                                onAudioClick = { showAudioDialog = true },
                                onCcClick = { toggleCc() },
                                isCcEnabled = isCcEnabled,
                                currentPosition = playbackPosition,
                                totalDuration = totalDuration,
                                onSeek = { exoPlayer.seekTo(it) },
                                currentQuality = displayQuality,
                                currentSpeed = playbackSpeed,
                                onSpeedClick = { showSpeedDialog = true },
                                videoTitle = videoTitle,
                                video = video,
                                selectedStream = selectedStream
                            )
                        }

                        // ── Double-tap Seek Ripple Feedback ──────────────────────────────────
                        val feedback = doubleTapFeedback
                        if (feedback != null) {
                            LaunchedEffect(feedback) { delay(700); doubleTapFeedback = null }
                            val rippleAlpha by animateFloatAsState(
                                targetValue = 0.18f,
                                animationSpec = tween(300), label = "ripple"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.42f)
                                    .align(if (feedback.first) Alignment.CenterEnd else Alignment.CenterStart)
                                    .background(
                                        Color.White.copy(alpha = rippleAlpha),
                                        if (feedback.first) RoundedCornerShape(topStart = 120.dp, bottomStart = 120.dp)
                                        else RoundedCornerShape(topEnd = 120.dp, bottomEnd = 120.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (feedback.first) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(feedback.second, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ── Scrubbing position indicator ─────────────────────────────────────
                        if (isScrubbing && totalDuration > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = formatTime(scrubPosition),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // ── Volume HUD pill ──────────────────────────────────────────────────
                        if (showVolumeHud) {
                            LaunchedEffect(volumeLevel) { delay(1200); showVolumeHud = false }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 20.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = when {
                                            volumeLevel <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                                            volumeLevel < maxVolume * 0.4f -> Icons.AutoMirrored.Filled.VolumeDown
                                            else -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${((volumeLevel / maxVolume) * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // ── Brightness HUD pill ──────────────────────────────────────────────
                        if (showBrightnessHud) {
                            LaunchedEffect(brightnessLevel) { delay(1200); showBrightnessHud = false }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 20.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = when {
                                            brightnessLevel < 0.3f -> Icons.Default.BrightnessLow
                                            brightnessLevel < 0.7f -> Icons.Default.BrightnessMedium
                                            else -> Icons.Default.BrightnessHigh
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${(brightnessLevel * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // ── 2× Speed indicator ───────────────────────────────────────────────
                        if (isLongPressing) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 14.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("2× Speed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ── Persistent Red YouTubeTimeBar (ALWAYS IN FRONT AT THE BOTTOM) ──
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = if (isLandscape) 2.dp else 0.dp)
                        ) {
                            YouTubeTimeBar(
                                currentPosition = playbackPosition,
                                totalDuration = totalDuration,
                                onSeek = { exoPlayer.seekTo(it) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                // ── YouTube 2026 Expanded UI — Portrait Only ─────────────────
                if (!isLandscape) {
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    var isSubscribed by remember { mutableStateOf(false) }
                    var isMoreRelatedLoading by remember { mutableStateOf(false) }
                    val relatedListState = rememberLazyListState()

                    fun loadMoreRelatedVideos() {
                        if (isMoreRelatedLoading) return
                        isMoreRelatedLoading = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val service = ServiceList.YouTube
                                val topic = if (channelName.isNotBlank()) "$channelName videos" else "$videoTitle related"
                                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic, emptyList(), "")
                                val res = try { SearchInfo.getInfo(service, queryHandler).relatedItems } catch(e: Exception) { emptyList() }
                                val existingIds = relatedVideos.map { it.id }.toSet()
                                val newItems = res.filterIsInstance<StreamInfoItem>().map { it.toVideo() }.filter { it.id !in existingIds }

                                withContext(Dispatchers.Main) {
                                    relatedVideos = (relatedVideos + newItems).distinctBy { it.id }
                                    com.adzero.app.data.GlobalPlayerManager.setQueue(relatedVideos)
                                    isMoreRelatedLoading = false
                                }
                            } catch(e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) { isMoreRelatedLoading = false }
                            }
                        }
                    }

                    val shouldLoadMoreRelated = remember {
                        derivedStateOf {
                            val totalItems = relatedListState.layoutInfo.totalItemsCount
                            totalItems > 0 && (relatedListState.firstVisibleItemIndex + 4) >= totalItems
                        }
                    }

                    LaunchedEffect(shouldLoadMoreRelated.value) {
                        if (shouldLoadMoreRelated.value && !isMoreRelatedLoading && relatedVideos.isNotEmpty()) {
                            loadMoreRelatedVideos()
                        }
                    }

                    LazyColumn(
                        state = relatedListState,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(fraction),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // ── Title + Views row (YouTube 2026 format) ─────────
                        item(key = "player_title_views", contentType = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = videoTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = 21.sp,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "@${channelName.replace(" ", "")}  $likesCount likes  $viewsDate",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isDescriptionExpanded) " Show less" else " ...more",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                if (isDescriptionExpanded && descriptionText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HtmlText(
                                        html = descriptionText,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // ── Channel Avatar + Subscribe + Action Pills Row (YouTube 2026) ─────
                        item(key = "player_channel_row", contentType = "channel_row") {
                            UnifiedChannelAndActionRow(
                                avatar = channelAvatar,
                                name = channelName,
                                isSubscribed = isSubscribed,
                                onSubscribeToggle = { isSubscribed = !isSubscribed },
                                onChannelClick = onChannelClick,
                                likes = likesCount,
                                onDownloadClick = { showDownloadSheet = true }
                            )
                        }

                        // ── Comments Card (Exact YouTube 2026 Rounded Card) ──
                        item(key = "player_comments_card", contentType = "comments_card") {
                            CommentsCard(
                                commentCount = commentsList.size,
                                onClick = { showCommentsSheet = true }
                            )
                        }

                        // ── Related Videos (no extra header) ──────────────────
                        items(
                            items = relatedVideos,
                            key = { related -> "rel_${related.id}" },
                            contentType = { _ -> "related_video" }
                        ) { related ->
                            VideoCard(
                                video = related,
                                onClick = {
                                    com.adzero.app.data.ExtractionManager.startExtraction(related)
                                    onVideoClick(related)
                                },
                                onChannelClick = onChannelClick
                            )
                        }

                        if (isMoreRelatedLoading) {
                            item(key = "player_related_loader", contentType = "loader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.adzero.app.components.YouTubeLoading()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDownloadSheet) {
            com.adzero.app.components.DownloadBottomSheet(
                videoTitle = videoTitle,
                videoStreams = extractedVideoStreams,
                audioStreams = extractedAudioStreams,
                onDismiss = { showDownloadSheet = false }
            )
        }

        if (showQualityDialog) {
            val currentAutoQuality = extractedVideoStreams.firstOrNull()?.quality ?: "720p"
            val autoLabel = "Auto ($currentAutoQuality)"
            val availableQualities = extractedVideoStreams.map { it.quality }.distinct()
            val qualityOptions = listOf(autoLabel) + availableQualities

            SelectionDialog(
                title = "Select Quality",
                options = qualityOptions,
                onSelect = { selectedOption ->
                    if (selectedOption.startsWith("Auto")) {
                        com.adzero.app.data.PlayerQualityManager.setPreferredQuality(context, "Auto")
                        userSelectedQuality = false // Reset to auto mode
                        val progressive = extractedVideoStreams.filter { !it.isVideoOnly }
                        val vOnly = extractedVideoStreams.filter { it.isVideoOnly }
                        val candidateStreams = if (progressive.any { it.url.isNotBlank() }) progressive else vOnly
                        
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                        val bw = caps?.linkDownstreamBandwidthKbps ?: 0
                        val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true ||
                                   caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
                        
                        selectedStream = selectBestAutoQuality(candidateStreams, bw, wifi)
                    } else {
                        com.adzero.app.data.PlayerQualityManager.setPreferredQuality(context, selectedOption)
                        userSelectedQuality = true // Lock to manual selection
                        selectedStream = extractedVideoStreams.find { it.quality == selectedOption }
                    }
                    showQualityDialog = false
                },
                onDismiss = { showQualityDialog = false }
            )
        }

        if (showAudioDialog) {
            AudioTrackDialog(
                tracks = extractedAudioStreams,
                selectedTrack = selectedAudioStream,
                onSelect = { track ->
                    selectedAudioStream = track
                    showAudioDialog = false
                },
                onDismiss = { showAudioDialog = false }
            )
        }
        if (showSettingsSheet) {
            VideoSettingsModalSheet(
                currentQuality = displayQuality,
                currentSpeed = playbackSpeed,
                currentAudioTrack = selectedAudioStream?.quality ?: "Original",
                currentSubtitleTrack = if (isCcEnabled && selectedSubtitleTrack != null) selectedSubtitleTrack!!.languageName else "Off",
                onOpenQuality = { showQualityDialog = true },
                onOpenSpeed = { showSpeedDialog = true },
                onOpenAudio = { showAudioDialog = true },
                onOpenSubtitles = { showSubtitlesDialog = true },
                onDismiss = { showSettingsSheet = false }
            )
        }
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = playbackSpeed,
                onSelectSpeed = { speed ->
                    playbackSpeed = speed
                    exoPlayer.setPlaybackSpeed(speed)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
        if (showSubtitlesDialog) {
            SubtitlesDialog(
                subtitles = extractedSubtitles,
                selectedSubtitle = selectedSubtitleTrack,
                isCcEnabled = isCcEnabled,
                onSelect = { track ->
                    if (track == null) {
                        isCcEnabled = false
                        selectedSubtitleTrack = null
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        exoPlayer.trackSelectionParameters = params
                        showCcHud = "Captions OFF"
                    } else {
                        isCcEnabled = true
                        selectedSubtitleTrack = track
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setPreferredTextLanguage(track.languageCode)
                            .build()
                        exoPlayer.trackSelectionParameters = params
                        showCcHud = "Captions ON (${track.languageName})"
                    }
                    showSubtitlesDialog = false
                },
                onDismiss = { showSubtitlesDialog = false }
            )
        }
        if (showCommentsSheet) CommentBottomSheet(comments = commentsList, onClose = { showCommentsSheet = false }, sheetState = sheetState)
    }
}

@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit = {},
    onForward: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAudioClick: () -> Unit = {},
    onCcClick: () -> Unit = {},
    isCcEnabled: Boolean = false,
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    currentQuality: String,
    currentSpeed: Float = 1.0f,
    onSpeedClick: () -> Unit,
    videoTitle: String = "",
    video: Video,
    selectedStream: VideoStream?
) {
    val context = LocalContext.current
    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        // Top Bar ─ anchored to top of overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 24.dp else 12.dp, vertical = if (isLandscape) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back / Minimize Button + Video Title in Landscape
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isLandscape) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isLandscape) "Back to Portrait" else "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (isLandscape && videoTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f)
                    )
                }
            }

            // Right: Action Controls (Speed + Captions + Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onSpeedClick, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SlowMotionVideo, "Playback Speed", tint = Color.White, modifier = Modifier.size(22.dp))
                        if (currentSpeed != 1.0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF2661))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${currentSpeed}x",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onCcClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isCcEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                        contentDescription = "Captions",
                        tint = if (isCcEnabled) Color(0xFFFF2661) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Center Media Controls ─ anchored to center of overlay
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 90.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }


        // Bottom Controls ─ anchored precisely directly above the persistent seekbar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = if (isLandscape) 18.dp else 12.dp)
        ) {
            // Time text + Fullscreen icon row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 28.dp else 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val isRealLive = (selectedStream?.isHls == true) || video.isLive
                val cleanQuality = if (currentQuality == "LIVE") "" else currentQuality

                if (isRealLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Red)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val timeFormatted = formatTime(currentPosition) + " / " + formatTime(totalDuration)
                    val timeWithQuality = if (cleanQuality.isNotBlank()) "$timeFormatted • $cleanQuality" else timeFormatted
                    Text(
                        text = timeWithQuality,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                val activity = context as? Activity
                IconButton(
                    onClick = {
                        if (isLandscape) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isLandscape) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = if (isLandscape) "Exit Fullscreen" else "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeTimeBar(
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val thumbRadiusAnimated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 8f else 4f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh),
        label = "thumb_radius"
    )

    val trackHeightAnimated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 5f else 3f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh),
        label = "track_height"
    )

    val progress = if (totalDuration > 0) {
        (if (isDragging) dragPosition else currentPosition.toFloat()) / totalDuration.toFloat()
    } else 0f

    val coerceProgress = progress.coerceIn(0f, 1f)
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .pointerInput(totalDuration) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    val targetPos = (newProgress * totalDuration).toLong()
                    dragPosition = targetPos.toFloat()
                    onSeek(targetPos)
                }
            }
            .pointerInput(totalDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val startPos = (offset.x / size.width).coerceIn(0f, 1f) * totalDuration
                        dragPosition = startPos
                        lastSeekTime = System.currentTimeMillis()
                        onSeek(startPos.toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newPos = (change.position.x / size.width).coerceIn(0f, 1f) * totalDuration
                        dragPosition = newPos

                        val now = System.currentTimeMillis()
                        if (now - lastSeekTime > 60L) {
                            lastSeekTime = now
                            onSeek(newPos.toLong())
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragPosition.toLong())
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Time Scrub Tooltip Bubble while dragging
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formatTime(dragPosition.toLong()),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val width = size.width
            val canvasHeight = size.height
            val currentTrackHeight = trackHeightAnimated.dp.toPx()
            val activeWidth = width * coerceProgress
            val centerY = canvasHeight - (currentTrackHeight / 2f)

            // 1. Inactive Track (Semi-transparent white)
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, canvasHeight - currentTrackHeight),
                size = androidx.compose.ui.geometry.Size(width, currentTrackHeight)
            )

            // 2. Active Track (Vibrant YouTube Red)
            drawRect(
                color = Color(0xFFFF2661),
                topLeft = androidx.compose.ui.geometry.Offset(0f, canvasHeight - currentTrackHeight),
                size = androidx.compose.ui.geometry.Size(activeWidth, currentTrackHeight)
            )

            // 3. Smooth Red Scrubber Thumb Circle
            drawCircle(
                color = Color(0xFFFF2661),
                radius = thumbRadiusAnimated.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(activeWidth, centerY)
            )
        }
    }
}

@Composable
fun UnifiedChannelAndActionRow(
    avatar: String,
    name: String,
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit,
    onChannelClick: (String) -> Unit,
    likes: String,
    onDownloadClick: () -> Unit = {}
) {
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Channel Avatar + Red LIVE Ring
        Box(
            modifier = Modifier.clickable { onChannelClick(name) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
                    .padding(2.dp)
            ) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Subscribe Pill Button
        Button(
            onClick = onSubscribeToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSubscribed) surfaceColor else Color.White,
                contentColor = if (isSubscribed) onSurfaceColor else Color.Black
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = if (isSubscribed) "Subscribed" else "Subscribe",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Combined Like / Dislike pill ──────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(20.dp))
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable { isLiked = !isLiked; if (isLiked) isDisliked = false }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Like",
                    modifier = Modifier.size(16.dp),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else onSurfaceColor
                )
                if (likes.isNotBlank()) {
                    Text(
                        text = likes,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceColor
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height(16.dp).width(1.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
            Box(
                modifier = Modifier
                    .clickable { isDisliked = !isDisliked; if (isDisliked) isLiked = false }
                    .padding(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    modifier = Modifier.size(16.dp),
                    tint = if (isDisliked) MaterialTheme.colorScheme.error else onSurfaceColor
                )
            }
        }

        // Download pill
        ActionPill(
            icon = Icons.Outlined.FileDownload,
            label = "Download",
            surfaceColor = surfaceColor,
            contentColor = onSurfaceColor,
            onClick = onDownloadClick
        )
        // Share pill
        ActionPill(icon = Icons.Outlined.Share, label = "Share", surfaceColor = surfaceColor, contentColor = onSurfaceColor)
        // Remix/AI Sparkles pill
        ActionPill(icon = Icons.Outlined.AutoAwesome, label = "Remix", surfaceColor = surfaceColor, contentColor = onSurfaceColor)
        // 3-Dots pill
        ActionPill(icon = Icons.Default.MoreHoriz, label = "", surfaceColor = surfaceColor, contentColor = onSurfaceColor)
    }
}

@Composable
fun ActionPill(
    icon: ImageVector,
    label: String,
    surfaceColor: Color,
    contentColor: Color,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = contentColor
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun CommentsCard(commentCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            // Top Row: "Comments" + count + 3 dots icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comments",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$commentCount",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Bottom Row: Avatar + Comment Input Capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Comment...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    color: Color = Color.Unspecified
) {
    val textColor = if (color == Color.Unspecified) {
        val argb = MaterialTheme.colorScheme.onSurface.toArgb()
        android.graphics.Color.argb(
            (android.graphics.Color.alpha(argb)),
            (android.graphics.Color.red(argb)),
            (android.graphics.Color.green(argb)),
            (android.graphics.Color.blue(argb))
        )
    } else {
        color.toArgb()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                this.maxLines = maxLines
                this.ellipsize = android.text.TextUtils.TruncateAt.END
                this.textSize = fontSize.value
                this.setTextColor(textColor)
            }
        },
        update = { tv ->
            tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        }
    )
}

@Composable
fun AudioTrackDialog(
    tracks: List<com.adzero.app.models.VideoStream>,
    selectedTrack: com.adzero.app.models.VideoStream?,
    onSelect: (com.adzero.app.models.VideoStream) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = Color(0xFF6C63FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Audio Language",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (tracks.isEmpty()) {
                    // No audio tracks available
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No separate audio tracks\navailable for this video",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        tracks.forEachIndexed { index, track ->
                            val isSelected = selectedTrack?.url == track.url
                            val trackLabel = track.displayName.ifBlank { track.quality.ifBlank { "Track ${index + 1}" } }
                            val isOriginal = track.isOriginalTrack

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .clickable { onSelect(track) }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track type icon
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isOriginal) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            else Color(0xFF2196F3).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isOriginal) Icons.Default.RecordVoiceOver else Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = if (isOriginal) Color(0xFF4CAF50) else Color(0xFF2196F3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Track name + badge
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackLabel,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Original / Dubbed badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isOriginal) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                else Color(0xFF2196F3).copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isOriginal) "Original" else "Dubbed",
                                            color = if (isOriginal) Color(0xFF4CAF50) else Color(0xFF2196F3),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Checkmark for selected
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF6C63FF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (index < tracks.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFF6C63FF), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SelectionDialog(title: String, options: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2661).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = Color(0xFFFF2661),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        val isAuto = option.startsWith("Auto", ignoreCase = true)
                        val isHighRes = option.contains("1080") || option.contains("2160") || option.contains("4k", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                                .clickable { onSelect(option) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            val badgeLabel = when {
                                isAuto -> "RECOMMENDED"
                                isHighRes -> "HD / 4K"
                                else -> "SD"
                            }
                            val badgeColor = when {
                                isAuto -> Color(0xFFFF2661)
                                isHighRes -> Color(0xFF6C63FF)
                                else -> Color.White.copy(alpha = 0.6f)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFFFF2661), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsModalSheet(
    currentQuality: String,
    currentSpeed: Float,
    currentAudioTrack: String,
    currentSubtitleTrack: String = "Off",
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Video Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Quality Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onDismiss(); onOpenQuality() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.HighQuality, null, tint = Color(0xFFFF2661), modifier = Modifier.size(22.dp))
                    Text("Quality", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(currentQuality, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Playback Speed Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onDismiss(); onOpenSpeed() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.SlowMotionVideo, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(22.dp))
                    Text("Playback Speed", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (currentSpeed == 1.0f) "Normal (1.0x)" else "${currentSpeed}x",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Captions (CC) Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onDismiss(); onOpenSubtitles() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ClosedCaption, null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                    Text("Captions (CC)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(currentSubtitleTrack, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Audio Track Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onDismiss(); onOpenAudio() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Translate, null, tint = Color(0xFF00E676), modifier = Modifier.size(22.dp))
                    Text("Audio Track", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(currentAudioTrack, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SubtitlesDialog(
    subtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    isCcEnabled: Boolean,
    onSelect: (SubtitleTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Subtitles / Captions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "Off" option
                    val isOffSelected = !isCcEnabled || selectedSubtitle == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isOffSelected) Color(0xFFFF9800).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                            .border(BorderStroke(1.dp, if (isOffSelected) Color(0xFFFF9800) else Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                            .clickable { onSelect(null) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Off",
                            fontSize = 14.sp,
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isOffSelected) Color(0xFFFF9800) else Color.White
                        )
                        if (isOffSelected) {
                            Icon(Icons.Default.CheckCircle, "Selected", tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                        }
                    }

                    subtitles.forEach { track ->
                        val isSelected = isCcEnabled && selectedSubtitle?.url == track.url
                        val displayName = track.languageName + if (track.isAutoGenerated) " (auto-generated)" else ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0xFFFF9800).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                .border(BorderStroke(1.dp, if (isSelected) Color(0xFFFF9800) else Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                                .clickable { onSelect(track) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color(0xFFFF9800) else Color.White
                            )

                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, "Selected", tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(
        0.25f to "0.25x (Very Slow)",
        0.5f to "0.5x (Slow)",
        0.75f to "0.75x (Slightly Slow)",
        1.0f to "Normal (1.0x)",
        1.25f to "1.25x (Slightly Fast)",
        1.5f to "1.5x (Fast)",
        1.75f to "1.75x (Faster)",
        2.0f to "2.0x (Double Speed)",
        2.5f to "2.5x (Ultra Fast)"
    )

    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6C63FF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SlowMotionVideo,
                            contentDescription = null,
                            tint = Color(0xFF6C63FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Playback Speed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speeds.forEach { (speedValue, speedLabel) ->
                        val isSelected = Math.abs(currentSpeed - speedValue) < 0.05f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectSpeed(speedValue) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = speedLabel,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color(0xFF6C63FF) else Color.White
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF6C63FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFF6C63FF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

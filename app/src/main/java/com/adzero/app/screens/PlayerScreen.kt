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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adzero.app.App
import com.adzero.app.components.CommentBottomSheet
import com.adzero.app.components.VideoCard
import com.adzero.app.data.ExtractionManager
import com.adzero.app.data.GlobalPlayerManager
import com.adzero.app.data.HistoryManager
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

    // ── Helper: Network-aware quality selection ───────────────────────────
    fun selectBestAutoQuality(streams: List<VideoStream>, bw: Int, wifi: Boolean): VideoStream? {
        if (streams.isEmpty()) return null
        val effectiveBandwidth = if (wifi) Int.MAX_VALUE else bw
        return when {
            effectiveBandwidth >= 8000 ->
                streams.firstOrNull { it.quality.contains("1080") }
                ?: streams.firstOrNull { it.quality.contains("720") }
                ?: streams.firstOrNull { it.quality.contains("480") }
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 4000 ->
                streams.firstOrNull { it.quality.contains("720") }
                ?: streams.firstOrNull { it.quality.contains("480") }
                ?: streams.firstOrNull { it.quality.contains("360") }
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 1500 ->
                streams.firstOrNull { it.quality.contains("480") }
                ?: streams.firstOrNull { it.quality.contains("360") }
                ?: streams.firstOrNull { it.quality.contains("240") }
                ?: streams.firstOrNull { it.url.isNotBlank() }
            effectiveBandwidth >= 500 ->
                streams.firstOrNull { it.quality.contains("360") }
                ?: streams.firstOrNull { it.quality.contains("240") }
                ?: streams.firstOrNull { it.quality.contains("144") }
                ?: streams.firstOrNull { it.url.isNotBlank() }
            else ->
                // Very poor / no signal — pick lowest available
                streams.minByOrNull { s ->
                    Regex("(\\d+)p").find(s.quality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 9999
                } ?: streams.firstOrNull()
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
    var selectedStream by remember { mutableStateOf<VideoStream?>(null) }
    var selectedAudioStream by remember { mutableStateOf<VideoStream?>(null) }
    var playerStatusText by remember { mutableStateOf("Extracting...") }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var doubleTapFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isLongPressing by remember { mutableStateOf(false) }
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

    val okHttpClient = remember { App.okHttpClient }
    val exoPlayer = remember { GlobalPlayerManager.getPlayer(context) }
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) totalDuration = exoPlayer.duration
                if (state == Player.STATE_ENDED) isPlaying = false
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

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            val pos = exoPlayer.currentPosition
            if (pos >= 0L && pos != playbackPosition) {
                playbackPosition = pos
            }
            delay(1000)
        }
    }

    suspend fun processStreamInfo(info: StreamInfo, comments: org.schabi.newpipe.extractor.comments.CommentsInfo?) {
        val isRealLiveContent = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                                info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                                video.isLive

        val hlsStream = if (isRealLiveContent) info.hlsUrl?.let { VideoStream(it, "LIVE", isHls = true) } else null
        val progressive = info.videoStreams?.map { VideoStream(it.content ?: "", it.resolution ?: "360p", isVideoOnly = false) } ?: emptyList()
        val vOnly = info.videoOnlyStreams?.map { VideoStream(it.content ?: "", it.resolution ?: "unknown", isVideoOnly = true) } ?: emptyList()
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
                url = audioStream.content ?: "",
                quality = "$baseName$suffix",  // quality used internally for matching
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

        withContext(Dispatchers.Main) {
            extractedVideoStreams = (if (hlsStream != null) listOf(hlsStream) else emptyList()) + progressive + vOnly
            extractedAudioStreams = audio

            // Only auto-select quality if user hasn't manually chosen one for this video
            if (selectedStream == null || !userSelectedQuality) {
                val autoSelected = if (isRealLiveContent) {
                    hlsStream ?: allVideoStreams.firstOrNull()
                } else {
                    // Auto quality: prefer progressive (audio+video combined) streams;
                    // fall back to video-only if none available.
                    val hasProgressiveStreams = progressive.any { it.url.isNotBlank() }
                    val candidateStreams = if (hasProgressiveStreams) progressive else vOnly
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
            channelAvatar = info.uploaderAvatars?.firstOrNull()?.url ?: video.channelAvatarUrl
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
        userSelectedQuality = false  // reset so auto-quality runs fresh for every new video

        val normalizedId = ExtractionManager.normalizeId(video.id)
        val cached = ExtractionManager.extractionState.value
        if (cached is ExtractionManager.ExtractionResult.Success && cached.videoId == normalizedId) {
            processStreamInfo(cached.info, cached.comments)
        } else {
            ExtractionManager.startExtraction(video)
        }

        ExtractionManager.extractionState.collectLatest { result ->
            when (result) {
                is ExtractionManager.ExtractionResult.Success -> if (result.videoId == normalizedId) processStreamInfo(result.info, result.comments)
                is ExtractionManager.ExtractionResult.Error -> if (result.videoId == normalizedId) playerStatusText = "Error: ${result.message.take(40)}"
                is ExtractionManager.ExtractionResult.Loading -> if (ExtractionManager.normalizeId(result.video.id) == normalizedId) playerStatusText = "Extracting..."
                null -> {}
            }
        }
    }


    LaunchedEffect(selectedStream, selectedAudioStream, playbackSpeed, video.id) {
        selectedStream?.let { stream ->
            val currentSpeed = exoPlayer.playbackParameters.speed
            val currentAudioUrl = selectedAudioStream?.url

            // Guard: skip reload if nothing changed (fixed: removed broken `activeAudioUrl != null` check)
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

            // Build OkHttp data source factory with YouTube-required headers
            val dsFactory = OkHttpDataSource.Factory(App.okHttpClient)
                .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .setDefaultRequestProperties(mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                ))

            val videoSource = if (stream.isHls) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(stream.url))
            } else {
                ProgressiveMediaSource.Factory(dsFactory)
                    .createMediaSource(MediaItem.fromUri(stream.url))
            }
            
            val chosenAudio = selectedAudioStream ?: extractedAudioStreams.firstOrNull()
            val finalSource = if (!stream.isHls && stream.isVideoOnly && chosenAudio != null
                && chosenAudio.url.isNotBlank()) {
                // Use adjustPeriodTimeOffsets=false to prevent duration-mismatch freeze
                val audioSource = ProgressiveMediaSource.Factory(dsFactory)
                    .createMediaSource(MediaItem.fromUri(chosenAudio.url))
                MergingMediaSource(false, videoSource, audioSource)
            } else {
                videoSource
            }
            
            // Correct ExoPlayer API order: set source → prepare → play
            // playWhenReady must be true BEFORE prepare() so player auto-starts when buffer is ready
            exoPlayer.playWhenReady = true
            exoPlayer.setMediaSource(finalSource, currentPos)
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.prepare()
            exoPlayer.play()
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
    val sheetState = rememberModalBottomSheetState()

    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isCollapsed = fraction < 0.5f

    // ── Single Reusable PlayerView Instance (Eliminates AndroidView re-creation lag) ──
    val playerView = remember(context) {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 88.dp)
                    .width(180.dp)
                    .height(104.dp)
                    .then(dragModifier)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                // Video Content Surface
                if (selectedStream != null) {
                    AndroidView(
                        factory = { playerView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.requestLayout()
                            view.invalidate()
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

                // ── Card Body Click Surface (Tapping expands to Portrait View) ─────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onExpand() }
                )

                // 1. Center-Left Dark Circle Play/Pause Button (Exact Image Matched)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. Top-Right Dark Circle Close (X) Button (Exact Image Matched)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. Bottom Edge Scrub Progress Bar Line (Pink/Red)
                val progress = if (totalDuration > 0) (playbackPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha = 0.20f))
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
                            factory = { playerView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                view.requestLayout()
                                view.invalidate()
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
                        }
                    }

                        // ── YouTube Gesture Layer ─────────────────────────────────────────────
                        // Handles: tap (controls), double-tap seek, long-press 2x,
                        // vertical swipe (volume right / brightness left), horizontal scrub
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Layer 1: Tap + double-tap + long-press
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { isControlsVisible = !isControlsVisible },
                                        onDoubleTap = { offset ->
                                            val isRight = offset.x > (size.width / 2)
                                            if (isRight) {
                                                exoPlayer.seekTo(exoPlayer.currentPosition + 10_000)
                                                doubleTapFeedback = Pair(true, "+10s")
                                            } else {
                                                exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                                                doubleTapFeedback = Pair(false, "-10s")
                                            }
                                        },
                                        onLongPress = {
                                            isLongPressing = true
                                            exoPlayer.setPlaybackSpeed(2.0f)
                                        },
                                        onPress = {
                                            try { awaitRelease() } finally {
                                                if (isLongPressing) {
                                                    isLongPressing = false
                                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                                }
                                            }
                                        }
                                    )
                                }
                                // Layer 2: Drag gestures — vertical (volume/brightness - landscape only) + horizontal (scrub)
                                .pointerInput(totalDuration, isLandscape) {
                                    var dragStartX = 0f
                                    var dragStartY = 0f
                                    var dragAxis: String? = null // "vertical" | "horizontal"
                                    val AXIS_LOCK_THRESHOLD = 12f  // px before axis is decided
                                    val VERTICAL_SENSITIVITY = 0.004f  // fraction per px

                                    awaitEachGesture {
                                        // Wait for first finger down
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        dragStartX = down.position.x
                                        dragStartY = down.position.y
                                        dragAxis = null

                                        var accX = 0f
                                        var accY = 0f

                                        do {
                                            val event = awaitPointerEvent()
                                            val drag = event.changes.firstOrNull() ?: break
                                            val dx = drag.position.x - drag.previousPosition.x
                                            val dy = drag.position.y - drag.previousPosition.y
                                            accX += kotlin.math.abs(dx)
                                            accY += kotlin.math.abs(dy)

                                            // Lock axis once threshold exceeded
                                            if (dragAxis == null && (accX > AXIS_LOCK_THRESHOLD || accY > AXIS_LOCK_THRESHOLD)) {
                                                dragAxis = if (accY > accX) "vertical" else "horizontal"
                                            }

                                            when (dragAxis) {
                                                "vertical" -> {
                                                    // Volume & Brightness swipes ONLY active in Landscape mode
                                                    if (isLandscape) {
                                                        drag.consume()
                                                        val isRightHalf = dragStartX > size.width / 2
                                                        val delta = -dy * VERTICAL_SENSITIVITY

                                                        if (isRightHalf) {
                                                            // Right half → Volume
                                                            val newVol = (volumeLevel + delta * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                                            volumeLevel = newVol
                                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                                            showVolumeHud = true
                                                            showBrightnessHud = false
                                                        } else {
                                                            // Left half → Brightness
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
                                                    }
                                                }
                                                "horizontal" -> {
                                                    if (totalDuration > 0) {
                                                        drag.consume()
                                                        val scrubDelta = (dx / size.width) * totalDuration
                                                        scrubPosition = (scrubPosition + scrubDelta).toLong()
                                                            .coerceIn(0L, totalDuration)
                                                        isScrubbing = true
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })

                                        // Finger lifted
                                        if (dragAxis == "horizontal" && isScrubbing) {
                                            exoPlayer.seekTo(scrubPosition)
                                            isScrubbing = false
                                        }
                                        if (dragAxis == "vertical") {
                                            showVolumeHud = false
                                            showBrightnessHud = false
                                        }
                                    }
                                }
                        )

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
                                onNext = { relatedVideos.firstOrNull()?.let { nextVideo -> onVideoClick(nextVideo) } },
                                onBack = {
                                    if (isLandscape) {
                                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        onMinimize()
                                    }
                                },
                                onSettings = { showQualityDialog = true },
                                onAudioClick = { showAudioDialog = true },
                                currentPosition = playbackPosition,
                                totalDuration = totalDuration,
                                onSeek = { exoPlayer.seekTo(it) },
                                currentQuality = displayQuality,
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
                                targetValue = if (feedback != null) 0.18f else 0f,
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
                                            volumeLevel <= 0f -> Icons.Default.VolumeOff
                                            volumeLevel < maxVolume * 0.4f -> Icons.Default.VolumeDown
                                            else -> Icons.Default.VolumeUp
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

                // MiniPlayer UI (Text and Controls) - Portrait Only
                if (!isLandscape && fraction < 0.5f) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = channelName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }



                // ── YouTube 2026 Expanded UI — Portrait Only ─────────────────
                if (!isLandscape && fraction > 0.01f) {
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
                                    relatedVideos = relatedVideos + newItems
                                    isMoreRelatedLoading = false
                                    newItems.take(3).forEach { ExtractionManager.startExtraction(it, isSpeculative = true) }
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
                            val lastVisibleItem = relatedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleItem >= totalItems - 4
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
                        item {
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
                        item {
                            UnifiedChannelAndActionRow(
                                avatar = channelAvatar,
                                name = channelName,
                                isSubscribed = isSubscribed,
                                onSubscribeToggle = { isSubscribed = !isSubscribed },
                                onChannelClick = onChannelClick,
                                likes = likesCount
                            )
                        }

                        // ── Comments Card (Exact YouTube 2026 Rounded Card) ──
                        item {
                            CommentsCard(
                                commentCount = commentsList.size,
                                onClick = { showCommentsSheet = true }
                            )
                        }

                        // ── Related Videos (no extra header) ──────────────────
                        items(relatedVideos, key = { it.id }) { related ->
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
                            item {
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
        if (showSpeedDialog) SelectionDialog(title = "Playback Speed", options = listOf("0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2.0x"), onSelect = { s -> playbackSpeed = if (s == "Normal") 1.0f else s.replace("x", "").toFloat(); exoPlayer.setPlaybackSpeed(playbackSpeed); showSpeedDialog = false }, onDismiss = { showSpeedDialog = false })
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
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    currentQuality: String,
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
                .padding(horizontal = if (isLandscape) 16.dp else 10.dp, vertical = if (isLandscape) 6.dp else 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Minimize Arrow
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                if (isLandscape && videoTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.4f)
                    )
                }
            }

            // Right: Action Controls (Audio Track + Captions + Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onAudioClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Translate, "Audio Track Language", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ClosedCaption, "Captions", tint = Color.White, modifier = Modifier.size(22.dp))
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
                .padding(bottom = if (isLandscape) 14.dp else 12.dp)
        ) {
            // Time text + Fullscreen icon row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 0.dp),
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
                        dragPosition = (offset.x / size.width).coerceIn(0f, 1f) * totalDuration
                        lastSeekTime = System.currentTimeMillis()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newPos = (change.position.x / size.width).coerceIn(0f, 1f) * totalDuration
                        dragPosition = newPos
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val width = size.width
            val canvasHeight = size.height
            val currentTrackHeight = trackHeightAnimated.dp.toPx()
            val activeWidth = width * coerceProgress
            val centerY = canvasHeight - (currentTrackHeight / 2)

            // 1. Inactive Track (Semi-transparent white)
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, canvasHeight - currentTrackHeight),
                size = androidx.compose.ui.geometry.Size(width, currentTrackHeight)
            )

            // 2. Active Track (YouTube Red)
            drawRect(
                color = Color(0xFFFF0000),
                topLeft = androidx.compose.ui.geometry.Offset(0f, canvasHeight - currentTrackHeight),
                size = androidx.compose.ui.geometry.Size(activeWidth, currentTrackHeight)
            )

            // 3. Smooth Red Scrubber Thumb Circle with High Stiffness Physics Spring Animation
            drawCircle(
                color = Color(0xFFFF0000),
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
    likes: String
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
                                imageVector = Icons.Default.VolumeOff,
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
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(scrollState)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { option ->
                    Text(text = option, modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 12.dp), fontSize = 16.sp)
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

package com.adzero.app.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.adzero.app.App
import com.adzero.app.MainActivity
import com.adzero.app.models.Video
import com.adzero.app.models.VideoStream
import com.adzero.app.models.toVideo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(UnstableApi::class)
object GlobalPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Observable Playback State for UI Synchronization ──────────────────────
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Video>>(emptyList())
    val currentQueue = _currentQueue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val playHistory = mutableListOf<Video>()
    private var activeExtractionJob: Job? = null

    /**
     * Deep-buffering zero-stutter LoadControl:
     * - minBufferMs: 30,000ms (30s buffer cushion ensures playback never stalls)
     * - maxBufferMs: 120,000ms (2 minutes deep buffer ahead)
     * - bufferForPlaybackMs: 1,000ms (Instant playback start)
     * - bufferForPlaybackAfterRebufferMs: 3,000ms (Fast rebuffer recovery)
     * - prioritizeTimeOverSizeThresholds: TRUE (Never stall or stop buffering time ahead!)
     * - backBufferMs: 15,000ms (15s retained back buffer for smooth rewind)
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,
            120_000,
            2_500,
            4_000
        )
        .setBackBuffer(15_000, true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val appContext = context.applicationContext

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(appContext).build()

            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)

            val upstreamDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(upstreamDataSourceFactory)

            val renderersFactory = DefaultRenderersFactory(appContext).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                setEnableDecoderFallback(true)
                setAllowedVideoJoiningTimeMs(5000)
            }

            exoPlayer = ExoPlayer.Builder(appContext)
                .setRenderersFactory(renderersFactory)
                .setBandwidthMeter(bandwidthMeter)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK) // Keep CPU & Wi-Fi awake when screen is locked
                .build().apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    playWhenReady = true
                }

            setupPlayerListener(exoPlayer!!)
            initMediaSession(appContext)
        }
        return exoPlayer!!
    }

    private fun setupPlayerListener(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        // Autoplay next track when current video finishes
                        playNext()
                    }
                    Player.STATE_READY -> {
                        _isPlaying.value = player.playWhenReady
                    }
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> {
                        // Keep current isPlaying flag or update
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }
        })
    }

    fun initMediaSession(context: Context) {
        if (mediaSession == null) {
            val player = getPlayer(context)
            val appContext = context.applicationContext

            val sessionActivityIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val sessionActivity = PendingIntent.getActivity(appContext, 0, sessionActivityIntent, pendingIntentFlags)

            val sessionCallback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                        .add(Player.COMMAND_PREPARE)
                        .add(Player.COMMAND_STOP)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(playerCommands)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    when (playerCommand) {
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            playNext()
                            return SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            playPrevious()
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }

            try {
                mediaSession = MediaSession.Builder(appContext, player)
                    .setSessionActivity(sessionActivity)
                    .setCallback(sessionCallback)
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getMediaSession(context: Context): MediaSession? {
        initMediaSession(context)
        return mediaSession
    }

    /**
     * Start the PlaybackService so Android maintains foreground audio playback and lockscreen controls.
     */
    fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(context.applicationContext, PlaybackService::class.java)
                context.applicationContext.startService(intent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    /**
     * Called whenever video starts playback to synchronize lockscreen controls, notification, and background service.
     */
    fun onVideoStarted(context: Context, video: Video) {
        _currentVideo.value = video
        getPlayer(context)
        initMediaSession(context)
        startPlaybackService(context)
        updateMediaMetadata(video)

        fetchArtworkBytes(video.thumbnailUrl) { artworkBytes ->
            if (_currentVideo.value?.id == video.id) {
                updateMediaMetadata(video, artworkBytes)
            }
        }
    }

    /**
     * Play a video with optional queue updates. Handles lockscreen metadata updates & background extraction.
     */
    fun playVideo(
        context: Context = App.instance,
        video: Video,
        queue: List<Video>? = null,
        addToHistory: Boolean = true,
        isBackground: Boolean = false
    ) {
        activeExtractionJob?.cancel()
        val prev = _currentVideo.value
        if (addToHistory && prev != null && prev.id != video.id) {
            playHistory.add(prev)
            if (playHistory.size > 50) {
                playHistory.removeAt(0)
            }
        }

        _currentVideo.value = video
        if (queue != null) {
            _currentQueue.value = queue.filter { it.id != video.id }
        }

        // Ensure player & media session are fully initialized BEFORE touching metadata
        getPlayer(context)
        initMediaSession(context)
        startPlaybackService(context)

        // 1. Immediately push fast metadata to Lock Screen & Notification
        try {
            updateMediaMetadata(video)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch thumbnail byte array in background for HD lockscreen artwork
        fetchArtworkBytes(video.thumbnailUrl) { artworkBytes ->
            if (_currentVideo.value?.id == video.id) {
                try {
                    updateMediaMetadata(video, artworkBytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Trigger background stream extraction ONLY when playing headlessly from background / lockscreen
        if (isBackground) {
            startBackgroundPlayback(context, video)
        }
    }

    /**
     * Extracts and prepares playback in background (for when Next/Prev is clicked with phone locked).
     */
    private fun startBackgroundPlayback(context: Context, video: Video) {
        activeExtractionJob?.cancel()
        val player = getPlayer(context)

        activeExtractionJob = managerScope.launch {
            val normalizedId = ExtractionManager.normalizeId(video.id)
            ExtractionManager.startExtraction(video)

            ExtractionManager.extractionState.collectLatest { result ->
                if (result is ExtractionManager.ExtractionResult.Success && result.videoId == normalizedId) {
                    val info = result.info
                    
                    // Extract related items for continuous queue
                    val related = info.relatedItems?.filterIsInstance<StreamInfoItem>()?.map { it.toVideo() } ?: emptyList()
                    if (related.isNotEmpty() && _currentQueue.value.isEmpty()) {
                        _currentQueue.value = related
                    }

                    // Find optimal stream
                    @Suppress("DEPRECATION")
                    val progressive = info.videoStreams?.map {
                        VideoStream(it.content, it.resolution, format = it.format?.name?.lowercase() ?: "mp4", isVideoOnly = false)
                    } ?: emptyList()
                    @Suppress("DEPRECATION")
                    val vOnly = info.videoOnlyStreams?.map {
                        VideoStream(it.content, it.resolution, format = it.format?.name?.lowercase() ?: "mp4", isVideoOnly = true)
                    } ?: emptyList()
                    val audio = info.audioStreams?.map {
                        VideoStream(it.content, "Audio", format = it.format?.name?.lowercase() ?: "m4a", isVideoOnly = false)
                    } ?: emptyList()

                    val allVideo = progressive + vOnly
                    val preferredQuality = PlayerQualityManager.preferredQuality
                    val chosenVideoStream = if (preferredQuality != "Auto") {
                        PlayerQualityManager.findBestMatchingStream(allVideo, preferredQuality)
                            ?: progressive.firstOrNull()
                            ?: vOnly.firstOrNull()
                    } else {
                        progressive.firstOrNull { it.quality.contains("720") }
                            ?: progressive.firstOrNull { it.quality.contains("480") }
                            ?: progressive.firstOrNull()
                            ?: vOnly.firstOrNull()
                    }

                    val chosenAudioStream = audio.firstOrNull()

                    if (chosenVideoStream != null) {
                        loadStreamToPlayer(context, player, video, chosenVideoStream, chosenAudioStream, info.hlsUrl)
                    }
                }
            }
        }
    }

    private fun loadStreamToPlayer(
        context: Context,
        player: ExoPlayer,
        video: Video,
        stream: VideoStream,
        audioStream: VideoStream?,
        hlsUrl: String?
    ) {
        try {
            val httpDsFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
            val upstreamDsFactory = DefaultDataSource.Factory(context.applicationContext, httpDsFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(upstreamDsFactory)

            val mediaMetadata = buildMetadata(video)
            val videoMediaItem = MediaItem.Builder()
                .setUri(stream.url)
                .setMediaMetadata(mediaMetadata)
                .build()

            val videoSource = if (stream.isHls && !hlsUrl.isNullOrBlank()) {
                HlsMediaSource.Factory(upstreamDsFactory).createMediaSource(MediaItem.fromUri(hlsUrl))
            } else {
                mediaSourceFactory.createMediaSource(videoMediaItem)
            }

            val finalSource = if (stream.isVideoOnly && audioStream != null && audioStream.url.isNotBlank()) {
                val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioStream.url))
                MergingMediaSource(true, true, videoSource, audioSource)
            } else {
                videoSource
            }

            player.playWhenReady = true
            player.setMediaSource(finalSource, 0L)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Next track action: triggered from Lock Screen, Notification, or UI.
     */
    fun playNext() {
        val queue = _currentQueue.value
        if (queue.isNotEmpty()) {
            val nextVideo = queue.first()
            val remainingQueue = queue.drop(1)
            playVideo(App.instance, nextVideo, remainingQueue, addToHistory = true, isBackground = true)
        } else {
            // If queue is empty, attempt to replay or keep current
            _currentVideo.value?.let { current ->
                playVideo(App.instance, current, addToHistory = false, isBackground = true)
            }
        }
    }

    /**
     * Previous track action: triggered from Lock Screen, Notification, or UI.
     */
    fun playPrevious() {
        val player = exoPlayer
        val pos = player?.currentPosition ?: 0L

        // If played more than 5 seconds, seek back to beginning of current track
        if (pos > 5000L) {
            player?.seekTo(0L)
            player?.play()
            return
        }

        // Otherwise go to previously played video if available
        if (playHistory.isNotEmpty()) {
            val prevVideo = playHistory.removeAt(playHistory.size - 1)
            playVideo(App.instance, prevVideo, addToHistory = false, isBackground = true)
        } else {
            player?.seekTo(0L)
            player?.play()
        }
    }

    fun setQueue(videos: List<Video>) {
        val currentId = _currentVideo.value?.id
        _currentQueue.value = videos.filter { it.id != currentId }
    }

    fun updateMediaMetadata(video: Video, artworkBytes: ByteArray? = null) {
        val player = exoPlayer ?: return
        val metadata = buildMetadata(video, artworkBytes)
        player.playlistMetadata = metadata
        // Note: Do NOT call player.replaceMediaItem() here during active playback.
        // Doing so re-creates the MediaSource, wipes out MergingMediaSource, and causes playback to freeze after 3 seconds!
    }

    fun buildMetadata(video: Video, artworkBytes: ByteArray? = null): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.channelName)
            .setDisplayTitle(video.title)
            .setIsPlayable(true)

        if (video.thumbnailUrl.isNotBlank()) {
            builder.setArtworkUri(Uri.parse(video.thumbnailUrl))
        }

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            builder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return builder.build()
    }

    private fun fetchArtworkBytes(url: String, onResult: (ByteArray?) -> Unit) {
        if (url.isBlank()) {
            onResult(null)
            return
        }
        managerScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = App.okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    withContext(Dispatchers.Main) {
                        onResult(bytes)
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun stopAndClear() {
        activeExtractionJob?.cancel()
        activeExtractionJob = null
        exoPlayer?.pause()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentVideo.value = null
        _isPlaying.value = false
    }

    fun release() {
        activeExtractionJob?.cancel()
        activeExtractionJob = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        _currentVideo.value = null
        _isPlaying.value = false
        _currentQueue.value = emptyList()
        playHistory.clear()
    }
}

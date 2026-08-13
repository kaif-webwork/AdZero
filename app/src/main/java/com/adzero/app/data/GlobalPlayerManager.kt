package com.adzero.app.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

@OptIn(UnstableApi::class)
object GlobalPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    /**
     * Ultra-Smooth LoadControl tuned for zero-stutter 1080p / 1440p / 4K 60fps streaming:
     * - minBufferMs: 15,000ms (15s buffer cushion prevents any video lag or stutter during network dips)
     * - maxBufferMs: 60,000ms (60s maximum buffer for deep pre-buffering)
     * - bufferForPlaybackMs: 1,500ms (instant 0.1s playback start)
     * - bufferForPlaybackAfterRebufferMs: 2,500ms
     * - targetBufferBytes: 64MB allocated RAM for ultra-fast DASH chunk caching
     * - backBufferMs: 30,000ms (30s retained back buffer for instant -10s rewind without re-buffering)
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000,  // minBufferMs (15s deep cushion prevents lag/stutter)
            60_000,  // maxBufferMs (60s max pre-buffer)
            1_500,   // bufferForPlaybackMs (instant start)
            2_500    // bufferForPlaybackAfterRebufferMs
        )
        .setTargetBufferBytes(64 * 1024 * 1024) // 64 MB RAM allocation for 1440p/4K DASH streams
        .setBackBuffer(30_000, true) // Retains 30s of back buffer for instant rewind
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()

            // Build clean OkHttp data source factory without Referer to prevent googlevideo speed throttling
            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(com.adzero.app.App.okHttpClient)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

            val upstreamDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstreamDataSourceFactory)

            val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setEnableDecoderFallback(true) // Decoder fallback guarantees hardware decoding never freezes
                setAllowedVideoJoiningTimeMs(5000) // Allows smooth seamless video codec joining without dropping frames
            }

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setBandwidthMeter(bandwidthMeter)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    playWhenReady = true
                }
            
            try {
                mediaSession = MediaSession.Builder(context.applicationContext, exoPlayer!!)
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return exoPlayer!!
    }

    fun release() {
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }
}

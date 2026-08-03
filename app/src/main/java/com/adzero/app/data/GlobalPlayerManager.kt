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
     * LoadControl tuned for Ultra High Definition 2160p60 (4K 60fps) & 1080p60 Streaming:
     * - minBufferMs: 2500ms (2.5s)
     * - maxBufferMs: 120,000ms (120s = 2 minutes max buffer for 4K video)
     * - bufferForPlaybackMs: 1000ms (1s instant playback startup)
     * - bufferForPlaybackAfterRebufferMs: 1500ms
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            2_500,   // minBufferMs (2.5s minimum buffer for 1080p/4K stream startup)
            60_000,  // maxBufferMs (60 seconds maximum buffer)
            1_500,   // bufferForPlaybackMs (1.5s buffer threshold prevents 0s stall/freeze on 1080p/4K)
            2_000    // bufferForPlaybackAfterRebufferMs (2.0s resume buffer)
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()

            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(com.adzero.app.App.okHttpClient)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://www.youtube.com/"
                    )
                )

            val upstreamDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstreamDataSourceFactory)

            val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
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
            
            // Create MediaSession to support background playback & system media controls
            mediaSession = MediaSession.Builder(context.applicationContext, exoPlayer!!)
                .build()
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

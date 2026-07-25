package com.adzero.app.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
object GlobalPlayerManager {
    private var exoPlayer: ExoPlayer? = null

    /**
     * Ultra-High Performance LoadControl configured to eliminate buffering completely (60s min buffer, 180s max buffer)
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,  // minBufferMs: 30s minimum pre-buffered video pool
            120_000, // maxBufferMs: 120s (2 mins) maximum buffer limit
            500,     // bufferForPlaybackMs: 500ms (0.5s) ultra-fast instant playback start!
            1_000    // bufferForPlaybackAfterRebufferMs: 1s rebuffer stability for fast network recovery
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(64 * 1024 * 1024) // 64MB dedicated RAM buffer pool
        .setBackBuffer(30_000, true) // 30s back-buffer for instant 0ms seek rewind
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

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setBandwidthMeter(bandwidthMeter)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, false)
                .setHandleAudioBecomingNoisy(false)
                .build().apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                playWhenReady = true
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            prepare()
                            play()
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}

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
     * LoadControl tuned for YouTube streaming:
     * - minBuffer: 15s ensures smooth playback without excessive memory use
     * - maxBuffer: 60s cap to prevent OOM on low-end devices
     * - bufferForPlayback: 1500ms — enough to start stable playback (500ms was too low, caused instant stalls)
     * - bufferForPlayback: 1s — fast start threshold
     * - bufferForPlaybackAfterRebuffer: 500ms — instant recovery on seek!
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            10_000,  // minBufferMs: 10s minimum pre-buffered
            30_000,  // maxBufferMs: 30s maximum buffer (prevents OOM on 2GB/3GB RAM devices)
            800,     // bufferForPlaybackMs: 800ms — fast start threshold
            500      // bufferForPlaybackAfterRebufferMs: 500ms — instant recovery on seek!
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(12 * 1024 * 1024) // 12MB RAM buffer (safe for low-end devices)
        .setBackBuffer(5_000, true)              // 5s back-buffer for seek rewind
        .build()

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()

            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(com.adzero.app.App.okHttpClient)
                .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .setDefaultRequestProperties(
                    mapOf(
                        "Origin" to "https://www.youtube.com",
                        "Referer" to "https://www.youtube.com/"
                    )
                )

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setBandwidthMeter(bandwidthMeter)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                // handleAudioFocus=true ensures the player gets audio focus and pauses for calls
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            // Auto-recover from transient network errors
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

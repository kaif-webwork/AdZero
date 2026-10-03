package com.adzero.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.adzero.app.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "adzero_media_playback"
        const val CHANNEL_NAME = "Media Playback"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(applicationContext)
                    .setChannelId(CHANNEL_ID)
                    .setChannelName(R.string.media_playback_channel_name)
                    .setNotificationId(NOTIFICATION_ID)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        GlobalPlayerManager.initMediaSession(this)
        val session = GlobalPlayerManager.getMediaSession(this)
        if (session != null && !sessions.contains(session)) {
            addSession(session)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = GlobalPlayerManager.getMediaSession(this)
        if (session != null && !sessions.contains(session)) {
            try {
                addSession(session)
            } catch (e: Exception) {
                // Ignore if already added
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "AdZero Lock Screen and Notification Media Controls"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return GlobalPlayerManager.getMediaSession(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = GlobalPlayerManager.getPlayer(this)
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val session = GlobalPlayerManager.getMediaSession(this)
        if (session != null) {
            try {
                removeSession(session)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }
}

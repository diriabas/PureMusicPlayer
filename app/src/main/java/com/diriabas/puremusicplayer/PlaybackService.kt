
package com.diriabas.puremusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player

        val activityIntent = Intent(
            this,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val defaultProvider =
            DefaultMediaNotificationProvider(this)

        setMediaNotificationProvider(
            object : MediaNotification.Provider {

                override fun getNotificationChannelInfo():
                    MediaNotification.Provider.NotificationChannelInfo {
                    return defaultProvider.getNotificationChannelInfo()
                }

                override fun createNotification(
                    mediaSession: MediaSession,
                    mediaButtonPreferences: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    onNotificationChangedCallback:
                        MediaNotification.Provider.Callback
                ): MediaNotification {

                    val original = defaultProvider.createNotification(
                        mediaSession,
                        mediaButtonPreferences,
                        actionFactory,
                        onNotificationChangedCallback
                    )

                    val hiddenNotification =
                        Notification.Builder(
                            this@PlaybackService,
                            original.notification
                        )
                            .setVisibility(Notification.VISIBILITY_SECRET)
                            .build()

                    return MediaNotification(
                        original.notificationId,
                        hiddenNotification
                    )
                }
            }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        exoPlayer?.release()
        exoPlayer = null

        super.onDestroy()
    }
}

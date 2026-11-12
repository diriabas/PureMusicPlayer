package com.diriabas.puremusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_UPDATE_EQUALIZER =
            "com.diriabas.puremusicplayer.UPDATE_EQUALIZER"
        const val EXTRA_EQ_ENABLED = "eq_enabled"
        const val EXTRA_EQ_LEVELS = "eq_levels"
        const val EXTRA_BASS_STRENGTH = "bass_strength"
        const val EXTRA_VIRTUALIZER_STRENGTH = "virtualizer_strength"
        const val ACTION_START_SLEEP_TIMER = "com.diriabas.puremusicplayer.START_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.diriabas.puremusicplayer.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
    }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val softwareEqualizer = SoftwareEqualizerAudioProcessor()
    private val sleepTimerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerOriginalVolume = 1f

    override fun onCreate() {
        super.onCreate()

        val preferences = getSharedPreferences("pure_music_player", MODE_PRIVATE)
        val initialLevels = preferences.getString("eq_levels", null)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 10 }
            ?.toIntArray() ?: IntArray(10)
        softwareEqualizer.updateSettings(
            preferences.getBoolean("eq_enabled", true),
            initialLevels,
            preferences.getInt("eq_bass", 0),
            preferences.getInt("eq_virtualizer", 0)
        )

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(softwareEqualizer))
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, renderersFactory).build()
        // Let ExoPlayer own Android audio focus: pause on competing audio/calls
        // and resume only when focus is regained and playback was active before.
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        exoPlayer = player

        val activityIntent = Intent(this, MainActivity::class.java)

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

                    val modifiedNotification =
                        Notification.Builder.recoverBuilder(
                            this@PlaybackService,
                            original.notification
                        )
                            .setVisibility(Notification.VISIBILITY_SECRET)
                            .build()

                    return MediaNotification(
                        original.notificationId,
                        modifiedNotification
                    )
                }

                override fun handleCustomCommand(
                    session: MediaSession,
                    action: String,
                    extras: Bundle
                ): Boolean {
                    return defaultProvider.handleCustomCommand(
                        session,
                        action,
                        extras
                    )
                }
            }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_TIMER_MINUTES, 0)
                startSleepTimer(minutes)
            }
            ACTION_CANCEL_SLEEP_TIMER -> cancelSleepTimer()
            ACTION_UPDATE_EQUALIZER -> {
            val enabled = intent.getBooleanExtra(EXTRA_EQ_ENABLED, true)
            val levels = intent.getIntArrayExtra(EXTRA_EQ_LEVELS)
                ?: IntArray(10)
            val bass = intent.getIntExtra(EXTRA_BASS_STRENGTH, 0)
            val virtual = intent.getIntExtra(EXTRA_VIRTUALIZER_STRENGTH, 0)

            applyAudioSettings(enabled, levels, bass, virtual)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }


    private fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        val player = exoPlayer ?: return
        sleepTimerOriginalVolume = player.volume
        val totalMs = minutes * 60_000L
        val fadeMs = 30_000L.coerceAtMost(totalMs)
        val stopAt = android.os.SystemClock.elapsedRealtime() + totalMs
        val tick = object : Runnable {
            override fun run() {
                val remaining = (stopAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    player.pause()
                    player.volume = sleepTimerOriginalVolume
                    sleepTimerRunnable = null
                    return
                }
                if (remaining <= fadeMs) {
                    player.volume = sleepTimerOriginalVolume * (remaining.toFloat() / fadeMs.toFloat())
                }
                sleepTimerHandler.postDelayed(this, 1_000L)
            }
        }
        sleepTimerRunnable = tick
        sleepTimerHandler.post(tick)
    }

    private fun cancelSleepTimer() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        exoPlayer?.volume = sleepTimerOriginalVolume
    }

    private fun applyAudioSettings(
        enabled: Boolean,
        levels: IntArray,
        bass: Int,
        virtual: Int
    ) {
        softwareEqualizer.updateSettings(enabled, levels, bass, virtual)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        cancelSleepTimer()
        sleepTimerHandler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        mediaSession = null

        exoPlayer?.release()
        exoPlayer = null

        super.onDestroy()
    }
}

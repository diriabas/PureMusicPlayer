package com.diriabas.puremusicplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_UPDATE_EQUALIZER =
            "com.diriabas.puremusicplayer.UPDATE_EQUALIZER"
        const val EXTRA_EQ_ENABLED = "eq_enabled"
        const val EXTRA_EQ_LEVELS = "eq_levels"
        const val EXTRA_BASS_STRENGTH = "bass_strength"
        const val EXTRA_VIRTUALIZER_STRENGTH = "virtualizer_strength"

        private val EQ_FREQUENCIES_HZ = intArrayOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )
    }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initializeAudioEffects(audioSessionId)
            }
        })
        initializeAudioEffects(player.audioSessionId)

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
        if (intent?.action == ACTION_UPDATE_EQUALIZER) {
            val enabled = intent.getBooleanExtra(EXTRA_EQ_ENABLED, true)
            val levels = intent.getIntArrayExtra(EXTRA_EQ_LEVELS)
                ?: IntArray(10)
            val bass = intent.getIntExtra(EXTRA_BASS_STRENGTH, 0)
            val virtual = intent.getIntExtra(EXTRA_VIRTUALIZER_STRENGTH, 0)

            applyAudioSettings(enabled, levels, bass, virtual)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return

        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId)
        } catch (_: Exception) {
            equalizer = null
        }

        try {
            bassBoost?.release()
            bassBoost = BassBoost(0, sessionId)
        } catch (_: Exception) {
            bassBoost = null
        }

        try {
            virtualizer?.release()
            virtualizer = Virtualizer(0, sessionId)
        } catch (_: Exception) {
            virtualizer = null
        }

        val preferences = getSharedPreferences("pure_music_player", MODE_PRIVATE)
        val enabled = preferences.getBoolean("eq_enabled", true)
        val levels = preferences.getString("eq_levels", null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 10 }
            ?.toIntArray() ?: IntArray(10)
        applyAudioSettings(
            enabled,
            levels,
            preferences.getInt("eq_bass", 0),
            preferences.getInt("eq_virtualizer", 0)
        )
    }

    private fun applyAudioSettings(
        enabled: Boolean,
        levels: IntArray,
        bass: Int,
        virtual: Int
    ) {
        try {
            val effect = equalizer
            if (effect != null) {
                val range = effect.bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val bandCount = effect.numberOfBands.toInt()

                for (band in 0 until bandCount) {
                    val centerHz = effect.getCenterFreq(band.toShort()).toInt() / 1000
                    val nearestIndex = EQ_FREQUENCIES_HZ.indices.minByOrNull {
                        abs(EQ_FREQUENCIES_HZ[it] - centerHz)
                    } ?: 0
                    val requestedMb = levels.getOrElse(nearestIndex) { 0 } * 100
                    val clampedMb = requestedMb.coerceIn(minLevel, maxLevel)
                    effect.setBandLevel(band.toShort(), clampedMb.toShort())
                }
                effect.setEnabled(enabled)
            }
        } catch (_: Exception) {
            // Some device audio-effect implementations reject individual bands.
        }

        try {
            bassBoost?.let {
                it.setStrength((bass.coerceIn(0, 100) * 10).toShort())
                it.setEnabled(enabled && bass > 0)
            }
        } catch (_: Exception) {
        }

        try {
            virtualizer?.let {
                it.setStrength((virtual.coerceIn(0, 100) * 10).toShort())
                it.setEnabled(enabled && virtual > 0)
            }
        } catch (_: Exception) {
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        virtualizer = null

        mediaSession?.release()
        mediaSession = null

        exoPlayer?.release()
        exoPlayer = null

        super.onDestroy()
    }
}

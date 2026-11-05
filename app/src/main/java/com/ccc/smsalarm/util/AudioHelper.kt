package com.ccc.smsalarm.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioHelper"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var previousAlarmVolume = -1

    /** @return true if playback was accepted (it actually starts via the prepared listener). */
    fun startAlarm(volume: Int = -1): Boolean {
        stopAlarm() // also restores any previously overridden stream volume

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = if (volume < 0) maxVolume else volume.coerceIn(0, maxVolume)

        val alarmUri: Uri = Settings.System.DEFAULT_ALARM_ALERT_URI ?: run {
            Log.e(TAG, "Device has no default alarm sound")
            return false
        }

        return try {
            // The system-wide alarm volume is only raised while the alarm runs;
            // stopAlarm() puts the user's original value back. Inside the try:
            // some ROMs throw from setStreamVolume, which must not take down
            // vibration and flashlight running after this.
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { player -> player.start() }
                setOnErrorListener { player, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    runCatching { player.release() }
                    mediaPlayer = null
                    true
                }
                setDataSource(context, alarmUri)
                prepareAsync()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
            mediaPlayer = null
            restoreAlarmVolume()
            false
        }
    }

    fun stopAlarm() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        restoreAlarmVolume()
    }

    private fun restoreAlarmVolume() {
        if (previousAlarmVolume < 0) return
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                previousAlarmVolume,
                0
            )
        }
        previousAlarmVolume = -1
    }
}

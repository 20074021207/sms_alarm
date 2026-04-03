package com.ccc.smsalarm.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    fun startAlarm(volume: Int = -1) {
        stopAlarm()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = if (volume < 0) maxVolume else volume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

        val alarmUri: Uri = Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: Uri.parse("content://settings/system/alarm_alert")

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setDataSource(context, alarmUri)
            prepare()
            start()
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
    }
}

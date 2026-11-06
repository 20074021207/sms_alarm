package com.ccc.smsalarm.util

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.ccc.smsalarm.R
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
    @Volatile private var currentUri: Uri? = null

    /** The built-in alarm sound packaged with the APK. */
    private fun builtinAlarmUri(): Uri = Uri.parse(
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alarm_default}"
    )

    /**
     * Plays [ringtoneUri] when set, falling back to the built-in raw ringtone —
     * a stale or unreadable picker URI must never leave the alarm silent.
     * @return true if playback was accepted (it actually starts via the prepared listener).
     */
    fun startAlarm(volume: Int = -1, ringtoneUri: String? = null): Boolean {
        stopAlarm() // also restores any previously overridden stream volume

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = if (volume < 0) maxVolume else volume.coerceIn(0, maxVolume)

        val candidates = mutableListOf<Uri>()
        if (!ringtoneUri.isNullOrBlank()) {
            runCatching { Uri.parse(ringtoneUri) }.getOrNull()?.let { candidates.add(it) }
        }
        candidates.add(builtinAlarmUri())

        for (uri in candidates) {
            try {
                // The system-wide alarm volume is only raised while the alarm runs;
                // stopAlarm() puts the user's original value back. Inside the try:
                // some ROMs throw from setStreamVolume, which must not take down
                // vibration and flashlight running after this.
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

                currentUri = uri
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
                        restoreAlarmVolume()
                        // Sync fallback above can't cover decode failures that
                        // surface after prepareAsync — retry once with the
                        // built-in sound (guard prevents infinite recursion).
                        if (uri != builtinAlarmUri()) {
                            Log.w(TAG, "Custom ringtone failed async — falling back to built-in")
                            startAlarm(volume, null)
                        }
                        true
                    }
                    setDataSource(context, uri)
                    prepareAsync()
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start alarm sound from $uri", e)
                mediaPlayer = null
                restoreAlarmVolume()
            }
        }
        return false
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
        currentUri = null
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

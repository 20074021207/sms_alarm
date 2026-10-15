package com.ccc.smsalarm.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ccc.smsalarm.MainActivity
import com.ccc.smsalarm.R
import com.ccc.smsalarm.data.repository.SettingsRepository
import com.ccc.smsalarm.ui.screens.AlarmActivity
import com.ccc.smsalarm.util.AudioHelper
import com.ccc.smsalarm.util.FlashlightHelper
import com.ccc.smsalarm.util.VibrationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_alarm_channel"
        const val NOTIFICATION_ID = 1
        const val FALLBACK_NOTIFICATION_ID = 3
        const val ACTION_STOP = "com.ccc.smsalarm.ACTION_STOP"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        private const val ALARM_WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * Last-resort alarm surface when the foreground service cannot start
         * (e.g. Android 12+ background FGS restrictions after MonitorService died).
         */
        fun postFallbackNotification(context: Context, sender: String, body: String) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
                val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                    putExtra(EXTRA_SENDER, sender)
                    putExtra(EXTRA_BODY, body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(context.getString(R.string.alarm_triggered))
                    .setContentText(context.getString(R.string.keyword_detected))
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(FALLBACK_NOTIFICATION_ID, notification)
            } catch (_: Exception) {
                // Nothing more we can do from this context.
            }
        }
    }

    @Inject lateinit var audioHelper: AudioHelper
    @Inject lateinit var vibrationHelper: VibrationHelper
    @Inject lateinit var flashlightHelper: FlashlightHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var effectsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: ""
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""

        // Must reach startForeground immediately; everything slow goes to the coroutine below.
        val notification = buildNotification(sender, body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        AlarmState.trigger(sender, body)

        // A second matching SMS re-enters here on the same instance: restart effects cleanly.
        effectsJob?.cancel()
        effectsJob = null
        stopEffects()
        effectsJob = serviceScope.launch {
            // Settings come from DataStore on disk — never block the main thread for them.
            val settings = settingsRepository.settings.first()
            // Audio failure (no default alarm ringtone, decoder error…) must not
            // take down vibration and flashlight with it.
            audioHelper.startAlarm(settings.alarmVolume)
            if (settings.enableVibration) vibrationHelper.startVibration()
            if (settings.enableFlashlight) flashlightHelper.startFlashlight(serviceScope)
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(sender: String, body: String): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_BODY, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content intent to open MainActivity with alarm dialog
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 1, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.alarm_triggered))
            .setContentText(getString(R.string.keyword_detected))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_alarm), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "SMS alarm notifications"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        // Re-entrant alarm must not leak the previous lock.
        releaseWakeLock()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmsAlarm::AlarmWakeLock"
        ).apply { acquire(ALARM_WAKELOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopEffects() {
        audioHelper.stopAlarm()
        vibrationHelper.stopVibration()
        flashlightHelper.stopFlashlight()
    }

    private fun stopAlarm() {
        effectsJob?.cancel()
        effectsJob = null
        stopEffects()
        releaseWakeLock()
        AlarmState.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarm()
        // Only cancelled on destroy: a cancelled scope would silently kill the
        // flashlight of the next alarm arriving on this same instance.
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

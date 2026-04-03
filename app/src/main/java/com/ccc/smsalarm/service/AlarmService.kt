package com.ccc.smsalarm.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
        const val ACTION_STOP = "com.ccc.smsalarm.ACTION_STOP"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
    }

    @Inject lateinit var audioHelper: AudioHelper
    @Inject lateinit var vibrationHelper: VibrationHelper
    @Inject lateinit var flashlightHelper: FlashlightHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentVolume = -1
    private var enableVibration = true
    private var enableFlashlight = true

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

        // Load settings synchronously
        runBlocking {
            val settings = settingsRepository.settings.first()
            currentVolume = settings.alarmVolume
            enableVibration = settings.enableVibration
            enableFlashlight = settings.enableFlashlight
        }

        // Acquire WakeLock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SmsAlarm::AlarmWakeLock"
        ).apply { acquire(10 * 60 * 1000L) }

        // Start foreground with notification
        val notification = buildNotification(sender, body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start alarm effects
        audioHelper.startAlarm(currentVolume)
        if (enableVibration) vibrationHelper.startVibration()
        if (enableFlashlight) flashlightHelper.startFlashlight(serviceScope)

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.alarm_triggered))
            .setContentText(getString(R.string.keyword_detected))
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

    private fun stopAlarm() {
        audioHelper.stopAlarm()
        vibrationHelper.stopVibration()
        flashlightHelper.stopFlashlight()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

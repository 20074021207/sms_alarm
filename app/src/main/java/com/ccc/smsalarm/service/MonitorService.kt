package com.ccc.smsalarm.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ccc.smsalarm.MainActivity
import com.ccc.smsalarm.R
import com.ccc.smsalarm.receiver.RestartReceiver

class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        const val CHANNEL_ID = "sms_monitor_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.ccc.smsalarm.ACTION_STOP_MONITOR"
        private const val RESTART_REQUEST_CODE = 1001
        private const val WATCHDOG_REQUEST_CODE = 1002
        private const val RESTART_DELAY_MS = 1000L
        private const val WATCHDOG_INTERVAL_MS = 5 * 60_000L // 5 minutes

        /** Schedule a one-shot restart via AlarmManager */
        fun scheduleRestart(context: Context, delayMs: Long = RESTART_DELAY_MS) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, RestartReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, RESTART_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs,
                    pendingIntent
                )
                Log.d(TAG, "Restart scheduled in ${delayMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule restart", e)
            }
        }

        /** Schedule periodic watchdog to check service liveness */
        fun scheduleWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, RestartReceiver::class.java).apply {
                    action = "com.ccc.smsalarm.ACTION_WATCHDOG"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, WATCHDOG_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog scheduled in ${WATCHDOG_INTERVAL_MS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule watchdog", e)
            }
        }

        fun cancelWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RestartReceiver::class.java).apply {
                action = "com.ccc.smsalarm.ACTION_WATCHDOG"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, WATCHDOG_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    private var explicitlyStopped = false
    private var screenReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenReceiver()
        Log.d(TAG, "MonitorService created in process: ${getProcessName()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            explicitlyStopped = true
            cancelWatchdog(this)
            stopSelf()
            return START_NOT_STICKY
        }

        explicitlyStopped = false

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Schedule periodic watchdog
        scheduleWatchdog(this)

        return START_STICKY
    }

    /**
     * Called when the user swipes the app from recents.
     * If running in a separate process, this process survives.
     * Schedule a restart as safety net.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — scheduling restart")
        scheduleRestart(this, RESTART_DELAY_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy: explicitlyStopped=$explicitlyStopped")
        if (!explicitlyStopped) {
            scheduleRestart(this, RESTART_DELAY_MS)
        }
        unregisterScreenReceiver()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun getProcessName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // Fallback for older APIs
            val pid = android.os.Process.myPid()
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.find { it.pid == pid }?.processName ?: "unknown"
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.monitor_running))
            .setContentText(getString(R.string.monitor_running_desc))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTicker(getString(R.string.monitor_running))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_monitor),
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "短信监控服务状态"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF — starting OnePixelActivity")
                        OnePixelActivity.start(ctx)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Delay finish to avoid immediate process priority drop
                        Log.d(TAG, "Screen ON — scheduling delayed finish of OnePixelActivity")
                        handler.postDelayed({
                            OnePixelActivity.finishSelf()
                            // Re-ensure foreground notification after activity closes
                            refreshNotification()
                        }, 3000)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // User just unlocked — safety net: refresh notification and schedule restart
                        Log.d(TAG, "USER_PRESENT — refreshing service")
                        refreshNotification()
                        scheduleRestart(ctx, 5000)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        Log.d(TAG, "ScreenReceiver registered")
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
    }

    /** Refresh the foreground notification to keep the process alive */
    private fun refreshNotification() {
        try {
            val notification = buildNotification()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh notification", e)
        }
    }
}

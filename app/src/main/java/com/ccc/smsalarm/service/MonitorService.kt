package com.ccc.smsalarm.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.ccc.smsalarm.MainActivity
import com.ccc.smsalarm.R
import com.ccc.smsalarm.data.repository.dataStore
import com.ccc.smsalarm.receiver.RestartReceiver
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        const val CHANNEL_ID = "sms_monitor_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.ccc.smsalarm.ACTION_STOP_MONITOR"
        private const val RESTART_REQUEST_CODE = 1001
        private const val WATCHDOG_REQUEST_CODE = 1002
        private const val ALARM_CLOCK_SHOW_REQUEST_CODE = 1003
        private const val START_SERVICE_REQUEST_CODE = 1004
        private const val RESTART_DELAY_MS = 1000L
        private const val WATCHDOG_INTERVAL_MS = 5 * 60_000L // 5 minutes

        /**
         * Start the monitor from any process state. On Android 12+ a plain
         * startForegroundService() from the background throws
         * ForegroundServiceStartNotAllowedException; starting through a
         * PendingIntent lets the system perform the start for us.
         */
        fun startMonitorService(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct start rejected, retrying via PendingIntent", e)
                try {
                    PendingIntent.getForegroundService(
                        context, START_SERVICE_REQUEST_CODE, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ).send()
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to start MonitorService", e2)
                }
            }
        }

        fun scheduleRestart(context: Context, delayMs: Long = RESTART_DELAY_MS) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, RestartReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, RESTART_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // setAlarmClock fires exactly on time, survives Doze, needs no
                // permission, and while it fires the app sits on the system's
                // temporary allowlist — which is what permits the background
                // foreground-service start in RestartReceiver. Inexact
                // setAndAllowWhileIdle alarms are deferred by Doze and grant
                // no such allowance.
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        System.currentTimeMillis() + delayMs,
                        alarmClockShowIntent(context)
                    ),
                    pendingIntent
                )
                Log.d(TAG, "Restart scheduled in ${delayMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule restart", e)
            }
        }

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
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                        alarmClockShowIntent(context)
                    ),
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

        private fun alarmClockShowIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                // Request code must differ from the notification's content intent (0)
                // so the two PendingIntents don't overwrite each other.
                context, ALARM_CLOCK_SHOW_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private var explicitlyStopped = false
    private var userPresentReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerUserPresentReceiver()
        Log.d(TAG, "MonitorService created in process: ${getProcessName()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        val stopRequested = intent?.action == ACTION_STOP
        val enabled = !stopRequested && isMonitoringEnabled()
        explicitlyStopped = stopRequested || !enabled

        // Enter foreground FIRST. Callers start this service with
        // startForegroundService(); skipping startForeground() — even when we
        // intend to stop immediately — crashes the process with
        // ForegroundServiceDidNotStartInTimeException.
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (stopRequested) {
            Log.d(TAG, "Stop requested — stopping service")
            cancelWatchdog(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // If monitoring is disabled (e.g. system restarted a START_STICKY service), stop immediately
        if (!enabled) {
            Log.d(TAG, "Monitoring disabled — stopping service")
            cancelWatchdog(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleWatchdog(this)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — scheduling restart")
        if (isMonitoringEnabled()) {
            scheduleRestart(this, RESTART_DELAY_MS)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy: explicitlyStopped=$explicitlyStopped")
        if (!explicitlyStopped && isMonitoringEnabled()) {
            scheduleRestart(this, RESTART_DELAY_MS)
        }
        unregisterUserPresentReceiver()
        super.onDestroy()
    }

    private fun isMonitoringEnabled(): Boolean {
        return runCatching {
            runBlocking {
                applicationContext.dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { it[booleanPreferencesKey("monitoring_enabled")] ?: true }
                    .first()
            }
        }.getOrDefault(true)
    }

    @Suppress("DEPRECATION")
    private fun getProcessName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
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

    /**
     * Listen for USER_PRESENT (user unlocked screen) as a safety net
     * to refresh the foreground notification.
     */
    private fun registerUserPresentReceiver() {
        if (userPresentReceiver != null) return
        userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    Log.d(TAG, "USER_PRESENT — refreshing notification")
                    try {
                        val notification = buildNotification()
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to refresh notification", e)
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(userPresentReceiver, filter)
        Log.d(TAG, "UserPresentReceiver registered")
    }

    private fun unregisterUserPresentReceiver() {
        userPresentReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            userPresentReceiver = null
        }
    }
}

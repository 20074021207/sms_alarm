package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ccc.smsalarm.di.AppEntryPoint
import com.ccc.smsalarm.service.MonitorService
import dagger.hilt.android.EntryPointAccessors

class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppEntryPoint::class.java
        )
        if (!entryPoint.settingsRepository().isMonitoringEnabledSync()) {
            Log.d(TAG, "Monitoring disabled — skipping restart")
            return
        }

        try {
            val serviceIntent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // e.g. Android 12+ background FGS restriction when nothing keeps us exempt.
            // Reschedule so the keep-alive chain never dies instead of silently ending.
            Log.e(TAG, "Failed to start MonitorService — rescheduling watchdog", e)
            MonitorService.scheduleWatchdog(context)
        }
    }
}

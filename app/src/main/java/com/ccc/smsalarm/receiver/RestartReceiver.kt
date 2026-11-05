package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        // Reschedule the watchdog first so the keep-alive chain continues even
        // if the service start below fails; then start via the unified entry
        // (direct start, PendingIntent fallback when Android 12+ rejects it).
        MonitorService.scheduleWatchdog(context)
        MonitorService.startMonitorService(context)
    }
}

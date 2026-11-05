package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ccc.smsalarm.di.AppEntryPoint
import com.ccc.smsalarm.service.MonitorService
import dagger.hilt.android.EntryPointAccessors

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")
        val action = intent.action ?: return

        // Restart MonitorService on boot / app update.
        // USER_PRESENT and POWER_* are deliberately NOT handled: implicit
        // broadcast restrictions killed them for manifest receivers on API 26+.
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Check the master switch first: starting the service while
            // monitoring is disabled would just flash a notification and stop.
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppEntryPoint::class.java
            )
            if (!entryPoint.settingsRepository().isMonitoringEnabledSync()) {
                Log.d(TAG, "Monitoring disabled — skipping start")
                return
            }

            MonitorService.startMonitorService(context)
        }
    }
}

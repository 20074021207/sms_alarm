package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ccc.smsalarm.service.MonitorService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")
        val action = intent.action ?: return

        // Restart MonitorService on boot and various system events
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            try {
                val serviceIntent = Intent(context, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MonitorService", e)
            }
        }
    }
}

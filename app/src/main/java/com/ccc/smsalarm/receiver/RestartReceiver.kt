package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ccc.smsalarm.service.MonitorService

class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")
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

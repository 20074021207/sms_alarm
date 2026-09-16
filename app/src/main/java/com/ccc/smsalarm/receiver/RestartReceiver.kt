package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import com.ccc.smsalarm.data.repository.dataStore
import com.ccc.smsalarm.service.MonitorService
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")

        val enabled = runCatching {
            runBlocking {
                context.dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { it[booleanPreferencesKey("monitoring_enabled")] ?: true }
                    .first()
            }
        }.getOrDefault(true)

        if (!enabled) {
            Log.d(TAG, "Monitoring disabled — skipping restart")
            return
        }

        // Reschedule the watchdog first so the keep-alive chain continues even
        // if the service start below fails.
        MonitorService.scheduleWatchdog(context)
        MonitorService.startMonitorService(context)
    }
}

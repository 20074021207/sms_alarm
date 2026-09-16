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
            // Check the master switch first: starting the service while
            // monitoring is disabled would just flash a notification and stop.
            val enabled = runCatching {
                runBlocking {
                    context.dataStore.data
                        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                        .map { it[booleanPreferencesKey("monitoring_enabled")] ?: true }
                        .first()
                }
            }.getOrDefault(true)

            if (!enabled) {
                Log.d(TAG, "Monitoring disabled — skipping start")
                return
            }

            MonitorService.startMonitorService(context)
        }
    }
}

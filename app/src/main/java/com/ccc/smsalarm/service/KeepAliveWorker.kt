package com.ccc.smsalarm.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ccc.smsalarm.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "KeepAliveWorker executing")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AppEntryPoint::class.java
        )
        if (!entryPoint.settingsRepository().isMonitoringEnabledSync()) {
            Log.d(TAG, "Monitoring disabled — skipping service start")
            return Result.success()
        }

        try {
            val intent = Intent(applicationContext, MonitorService::class.java)
            applicationContext.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MonitorService", e)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "keep_alive_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Periodic work scheduled (15 min interval)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic work cancelled")
        }
    }
}

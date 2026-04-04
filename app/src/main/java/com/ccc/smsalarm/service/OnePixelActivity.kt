package com.ccc.smsalarm.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

class OnePixelActivity : Activity() {

    companion object {
        private const val TAG = "OnePixelActivity"
        private var instance: OnePixelActivity? = null

        fun start(context: Context) {
            if (instance != null) return
            try {
                val intent = Intent(context, OnePixelActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OnePixelActivity", e)
            }
        }

        fun finishSelf() {
            instance?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        Log.d(TAG, "OnePixelActivity created")

        // Make window 1x1 pixel, positioned at top-left
        window.setGravity(Gravity.START or Gravity.TOP)
        val params = window.attributes
        params.width = 1
        params.height = 1
        params.x = 0
        params.y = 0
        window.attributes = params

        // Ensure MonitorService is running
        try {
            val intent = Intent(this, MonitorService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MonitorService", e)
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}

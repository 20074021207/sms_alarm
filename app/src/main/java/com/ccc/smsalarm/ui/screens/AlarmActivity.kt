package com.ccc.smsalarm.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccc.smsalarm.service.AlarmService
import com.ccc.smsalarm.service.AlarmState
import com.ccc.smsalarm.ui.theme.SmsAlarmTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sender = intent.getStringExtra(AlarmService.EXTRA_SENDER) ?: ""
        val body = intent.getStringExtra(AlarmService.EXTRA_BODY) ?: ""

        // Keep process-wide alarm state consistent no matter which surface
        // launched us (service notification or fallback notification).
        AlarmState.trigger(sender, body)

        setContent {
            SmsAlarmTheme {
                // Closing the alarm from another surface (main dialog, notification
                // action) must dismiss this screen too instead of leaving a stale one.
                val alarmInfo by AlarmState.state.collectAsState()
                LaunchedEffect(alarmInfo.active) {
                    if (!alarmInfo.active) finish()
                }

                AlarmScreen(
                    sender = alarmInfo.sender,
                    body = alarmInfo.body,
                    onStop = {
                        val stopIntent = Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_STOP
                        }
                        startService(stopIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(sender: String, body: String, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "短信告警",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "发送者: $sender",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "停止告警",
                    fontSize = 24.sp
                )
            }
        }
    }
}

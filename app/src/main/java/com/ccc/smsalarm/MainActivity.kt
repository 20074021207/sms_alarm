package com.ccc.smsalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ccc.smsalarm.service.AlarmService
import com.ccc.smsalarm.service.AlarmState
import com.ccc.smsalarm.service.KeepAliveWorker
import com.ccc.smsalarm.service.MonitorService
import com.ccc.smsalarm.ui.screens.MainScreen
import com.ccc.smsalarm.ui.screens.SetupGuideScreen
import com.ccc.smsalarm.ui.theme.SmsAlarmTheme
import com.ccc.smsalarm.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "sms_alarm_prefs"
        private const val KEY_SETUP_DONE = "setup_done"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start persistent monitoring service
        val monitorIntent = Intent(this, MonitorService::class.java)
        startForegroundService(monitorIntent)

        // Schedule WorkManager periodic keep-alive check
        KeepAliveWorker.schedule(this)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val setupDone = prefs.getBoolean(KEY_SETUP_DONE, false)

        setContent {
            SmsAlarmTheme {
                if (!setupDone) {
                    SetupGuideScreen {
                        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
                        // Recreate activity to load main content
                        recreate()
                    }
                } else {
                    MainContent()
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val smsList by viewModel.smsList.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val alarmInfo by AlarmState.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreen(
            smsList = smsList,
            rules = rules,
            settings = settings,
            onAddRule = { keywords, mode -> viewModel.addRule(keywords, mode) },
            onDeleteRule = { viewModel.deleteRule(it) },
            onToggleRule = { viewModel.toggleRule(it) },
            onVolumeChange = { viewModel.updateVolume(it) },
            onVibrationChange = { viewModel.updateVibration(it) },
            onFlashlightChange = { viewModel.updateFlashlight(it) }
        )

        // Stop alarm dialog overlay
        if (alarmInfo.active) {
            AlarmStopDialog(
                sender = alarmInfo.sender,
                body = alarmInfo.body
            )
        }
    }
}

@Composable
private fun AlarmStopDialog(sender: String, body: String) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* don't dismiss by tapping outside */ },
        confirmButton = {
            Button(
                onClick = {
                    val stopIntent = Intent(context, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "停止告警",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "短信告警",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sender.isNotEmpty()) {
                    Text("发送者: $sender", style = MaterialTheme.typography.bodyLarge)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

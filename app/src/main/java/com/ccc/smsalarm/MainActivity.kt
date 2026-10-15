package com.ccc.smsalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.ccc.smsalarm.data.repository.SettingsRepository
import com.ccc.smsalarm.service.AlarmService
import com.ccc.smsalarm.service.AlarmState
import com.ccc.smsalarm.service.KeepAliveWorker
import com.ccc.smsalarm.service.MonitorService
import com.ccc.smsalarm.ui.screens.MainScreen
import com.ccc.smsalarm.ui.screens.SetupGuideScreen
import com.ccc.smsalarm.ui.theme.SmsAlarmTheme
import com.ccc.smsalarm.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    companion object {
        private const val PREFS_NAME = "sms_alarm_prefs"
        private const val KEY_SETUP_DONE = "setup_done"
    }

    private fun isSetupDone(): Boolean =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false)

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start monitoring only if enabled; DataStore is disk IO, keep it off the main thread
        lifecycleScope.launch(Dispatchers.Default) {
            if (settingsRepository.isMonitoringEnabledSync()) {
                startForegroundService(Intent(this@MainActivity, MonitorService::class.java))
                KeepAliveWorker.schedule(this@MainActivity)
            }
        }

        setContent {
            SmsAlarmTheme {
                if (!isSetupDone()) {
                    SetupGuideScreen {
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_SETUP_DONE, true).apply()
                        // Recreate activity to load main content
                        recreate()
                    }
                } else {
                    MainContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // SMS permission revoked from system settings after setup → run the guide again
        if (isSetupDone() && !hasSmsPermission()) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SETUP_DONE, false).apply()
            recreate()
        }
    }
}

@Composable
private fun MainContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
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
            onEditRule = { rule, keywords, mode -> viewModel.updateRule(rule, keywords, mode) },
            onDeleteRule = { viewModel.deleteRule(it) },
            onToggleRule = { viewModel.toggleRule(it) },
            onMonitoringChange = { enabled ->
                viewModel.updateMonitoring(enabled)
                if (enabled) {
                    val intent = Intent(context, MonitorService::class.java)
                    context.startForegroundService(intent)
                    KeepAliveWorker.schedule(context)
                } else {
                    val intent = Intent(context, MonitorService::class.java).apply {
                        action = MonitorService.ACTION_STOP
                    }
                    context.startService(intent)
                    KeepAliveWorker.cancel(context)
                }
            },
            onVolumeChange = { viewModel.updateVolume(it) },
            onVibrationChange = { viewModel.updateVibration(it) },
            onFlashlightChange = { viewModel.updateFlashlight(it) },
            onDeleteSms = { viewModel.deleteSms(it) },
            onDeleteAllSms = { viewModel.deleteAllSms() }
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

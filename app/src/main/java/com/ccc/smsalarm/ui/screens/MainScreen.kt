package com.ccc.smsalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import com.ccc.smsalarm.data.model.AppSettings
import com.ccc.smsalarm.data.model.MatchMode

import com.ccc.smsalarm.service.AlarmService
import com.ccc.smsalarm.service.AlarmState
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.NotificationsActive

@Composable
fun MainScreen(
    smsList: List<MatchedSmsEntity>,
    rules: List<AlarmRuleEntity>,
    settings: AppSettings,
    onAddRule: (keywords: List<String>, matchMode: MatchMode) -> Unit,
    onDeleteRule: (AlarmRuleEntity) -> Unit,
    onToggleRule: (AlarmRuleEntity) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onFlashlightChange: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val alarmInfo by AlarmState.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("告警记录") },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("配置") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("保活") },
                        icon = { Icon(Icons.Default.Security, contentDescription = null) }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> SmsListTab(smsList)
                1 -> ConfigTab(
                    rules = rules,
                    settings = settings,
                    onAddRule = onAddRule,
                    onDeleteRule = onDeleteRule,
                    onToggleRule = onToggleRule,
                    onVolumeChange = onVolumeChange,
                    onVibrationChange = onVibrationChange,
                    onFlashlightChange = onFlashlightChange
                )
                2 -> KeepAliveGuideTab()
            }
        }

        // Alarm stop dialog overlay
        if (alarmInfo.active) {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { },
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
                        if (alarmInfo.sender.isNotEmpty()) {
                            Text("发送者: ${alarmInfo.sender}")
                        }
                        Text(alarmInfo.body)
                    }
                }
            )
        }
    }
}

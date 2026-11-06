package com.ccc.smsalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import com.ccc.smsalarm.data.model.AppSettings
import com.ccc.smsalarm.data.model.MatchMode

@Composable
fun MainScreen(
    smsList: List<MatchedSmsEntity>,
    rules: List<AlarmRuleEntity>,
    settings: AppSettings,
    onAddRule: (keywords: List<String>, matchMode: MatchMode) -> Unit,
    onEditRule: (rule: AlarmRuleEntity, keywords: List<String>, matchMode: MatchMode) -> Unit,
    onDeleteRule: (AlarmRuleEntity) -> Unit,
    onToggleRule: (AlarmRuleEntity) -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onFlashlightChange: (Boolean) -> Unit,
    onRingtoneChange: (String) -> Unit,
    alarmTesting: Boolean,
    onTestAlarmToggle: () -> Unit,
    onDeleteSms: (Long) -> Unit,
    onDeleteSmsSelected: (List<Long>) -> Unit,
    onDeleteAllSms: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> SmsListTab(
                    smsList = smsList,
                    onDeleteSms = onDeleteSms,
                    onDeleteSmsSelected = onDeleteSmsSelected,
                    onDeleteAllSms = onDeleteAllSms
                )
                1 -> ConfigTab(
                    rules = rules,
                    settings = settings,
                    onAddRule = onAddRule,
                    onEditRule = onEditRule,
                    onDeleteRule = onDeleteRule,
                    onToggleRule = onToggleRule,
                    onMonitoringChange = onMonitoringChange,
                    onVolumeChange = onVolumeChange,
                    onVibrationChange = onVibrationChange,
                    onFlashlightChange = onFlashlightChange,
                    onRingtoneChange = onRingtoneChange,
                    alarmTesting = alarmTesting,
                    onTestAlarmToggle = onTestAlarmToggle
                )
                2 -> KeepAliveGuideTab()
            }
        }
    }
}

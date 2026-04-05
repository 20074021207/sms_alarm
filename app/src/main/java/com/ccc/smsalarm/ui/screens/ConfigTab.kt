package com.ccc.smsalarm.ui.screens

import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ccc.smsalarm.data.db.entity.AlarmRuleEntity
import com.ccc.smsalarm.data.model.AppSettings
import com.ccc.smsalarm.data.model.MatchMode

@Composable
fun ConfigTab(
    rules: List<AlarmRuleEntity>,
    settings: AppSettings,
    onAddRule: (keywords: List<String>, matchMode: MatchMode) -> Unit,
    onEditRule: (rule: AlarmRuleEntity, keywords: List<String>, matchMode: MatchMode) -> Unit,
    onDeleteRule: (AlarmRuleEntity) -> Unit,
    onToggleRule: (AlarmRuleEntity) -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onFlashlightChange: (Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AlarmRuleEntity?>(null) }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { keywords, mode ->
                onAddRule(keywords, mode)
                showAddDialog = false
            }
        )
    }

    editingRule?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onConfirm = { keywords, mode ->
                onEditRule(rule, keywords, mode)
                editingRule = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master switch at the top
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.monitoringEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (settings.monitoringEnabled) "短信监控已开启" else "短信监控已关闭",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = settings.monitoringEnabled,
                        onCheckedChange = onMonitoringChange
                    )
                }
            }
        }

        // Rules section (only shown when monitoring is enabled)
        if (settings.monitoringEnabled) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("告警规则", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加规则")
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                RuleItem(
                    rule = rule,
                    onToggle = { onToggleRule(rule) },
                    onDelete = { onDeleteRule(rule) },
                    onClick = { editingRule = rule }
                )
            }
        }

        // Settings section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("告警设置", style = MaterialTheme.typography.titleMedium)
        }

        item {
            val context = LocalContext.current
            val audioManager = context.getSystemService(AudioManager::class.java)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = if (settings.alarmVolume < 0) maxVolume else settings.alarmVolume

            Column {
                Text("音量", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..maxVolume.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("震动", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.enableVibration,
                    onCheckedChange = onVibrationChange
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("闪光灯", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.enableFlashlight,
                    onCheckedChange = onFlashlightChange
                )
            }
        }
    }
}

@Composable
private fun RuleItem(
    rule: AlarmRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rule.keywords.forEach { kw ->
                        AssistChip(
                            onClick = {},
                            label = { Text(kw) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (rule.matchMode == MatchMode.ANY) "任一匹配" else "全部匹配",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (keywords: List<String>, matchMode: MatchMode) -> Unit
) {
    var keywordText by remember { mutableStateOf("") }
    var matchMode by remember { mutableStateOf(MatchMode.ANY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("关键词") },
                    placeholder = { Text("多个关键词用逗号分隔") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = matchMode == MatchMode.ANY,
                            onClick = { matchMode = MatchMode.ANY }
                        )
                        Text("任一匹配")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = matchMode == MatchMode.ALL,
                            onClick = { matchMode = MatchMode.ALL }
                        )
                        Text("全部匹配")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keywords = keywordText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (keywords.isNotEmpty()) {
                        onConfirm(keywords, matchMode)
                    }
                },
                enabled = keywordText.trim().isNotEmpty()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun EditRuleDialog(
    rule: AlarmRuleEntity,
    onDismiss: () -> Unit,
    onConfirm: (keywords: List<String>, matchMode: MatchMode) -> Unit
) {
    var keywordText by remember { mutableStateOf(rule.keywords.joinToString(", ")) }
    var matchMode by remember { mutableStateOf(rule.matchMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("关键词") },
                    placeholder = { Text("多个关键词用逗号分隔") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = matchMode == MatchMode.ANY,
                            onClick = { matchMode = MatchMode.ANY }
                        )
                        Text("任一匹配")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = matchMode == MatchMode.ALL,
                            onClick = { matchMode = MatchMode.ALL }
                        )
                        Text("全部匹配")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keywords = keywordText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (keywords.isNotEmpty()) {
                        onConfirm(keywords, matchMode)
                    }
                },
                enabled = keywordText.trim().isNotEmpty()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

package com.ccc.smsalarm.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ccc.smsalarm.data.db.entity.MatchedSmsEntity
import com.ccc.smsalarm.ui.theme.HighlightYellow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SmsListTab(
    smsList: List<MatchedSmsEntity>,
    onDeleteSms: (Long) -> Unit,
    onDeleteSmsSelected: (List<Long>) -> Unit,
    onDeleteAllSms: () -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清除全部") },
            text = { Text("确定要删除所有告警记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllSms()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除告警记录") },
            text = { Text("确定删除选中的 ${selectedIds.size} 条告警记录吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSmsSelected(selectedIds.toList())
                        showDeleteConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                selectedCount = selectedIds.size,
                totalCount = smsList.size,
                allSelected = smsList.isNotEmpty() && selectedIds.size == smsList.size,
                onSelectAll = {
                    selectedIds =
                        if (selectedIds.size == smsList.size) emptySet()
                        else smsList.map { it.id }.toSet()
                },
                onDelete = { showDeleteConfirm = true },
                deleteEnabled = selectedIds.isNotEmpty(),
                onExit = {
                    selectionMode = false
                    selectedIds = emptySet()
                }
            )
        } else if (smsList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showClearAllDialog = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清除全部")
                }
            }
        }

        if (smsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无告警短信",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (!selectionMode) {
                Text(
                    text = "左滑可删除单条，长按可多选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(smsList, key = { it.id }) { sms ->
                    val selected = sms.id in selectedIds
                    val smsItem: @Composable () -> Unit = {
                        SmsItem(
                            sms = sms,
                            selectionMode = selectionMode,
                            selected = selected,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds =
                                        if (selected) selectedIds - sms.id
                                        else selectedIds + sms.id
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(sms.id)
                                }
                            }
                        )
                    }

                    // Swipe-to-delete only outside selection mode: inside it a
                    // swipe would fight the checkboxes and delete mid-selection.
                    if (selectionMode) {
                        smsItem()
                    } else {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    onDeleteSms(sms.id)
                                    true
                                } else {
                                    false
                                }
                            },
                            positionalThreshold = { totalDistance -> totalDistance * 0.4f }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                        else -> Color.Transparent
                                    },
                                    label = "bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 2.dp)
                                        .let { m ->
                                            if (color != Color.Transparent) m.background(color, MaterialTheme.shapes.medium)
                                            else m
                                        },
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        "删除",
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(end = 20.dp)
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            smsItem()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    onExit: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = "取消选择")
            }
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll, enabled = totalCount > 0) {
                Text(if (allSelected) "取消全选" else "全选")
            }
            IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除选中项",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmsItem(
    sms: MatchedSmsEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sms.sender,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = formatTime(sms.receivedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                val annotatedBody = buildAnnotatedString {
                    var remaining = sms.body
                    val keywords = sms.matchedKeywords.sortedByDescending { it.length }

                    while (remaining.isNotEmpty()) {
                        var earliestMatch: Pair<Int, String>? = null
                        for (kw in keywords) {
                            val idx = remaining.indexOf(kw, ignoreCase = true)
                            if (idx >= 0) {
                                if (earliestMatch == null || idx < earliestMatch.first) {
                                    earliestMatch = idx to kw
                                }
                            }
                        }
                        if (earliestMatch == null) {
                            append(remaining)
                            break
                        }
                        val (idx, kw) = earliestMatch
                        append(remaining.substring(0, idx))
                        withStyle(SpanStyle(background = HighlightYellow)) {
                            append(remaining.substring(idx, idx + kw.length))
                        }
                        remaining = remaining.substring(idx + kw.length)
                    }
                }
                Text(text = annotatedBody, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

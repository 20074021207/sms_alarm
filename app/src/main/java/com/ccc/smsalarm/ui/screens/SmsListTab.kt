package com.ccc.smsalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    smsList: List<MatchedSmsEntity>
) {
    if (smsList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无告警短信",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(smsList, key = { it.id }) { sms ->
                SmsItem(sms)
            }
        }
    }
}

@Composable
private fun SmsItem(sms: MatchedSmsEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

package com.ccc.smsalarm.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ccc.smsalarm.ui.theme.SmsAlarmTheme

@Composable
fun SetupGuideScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun checkSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    fun checkNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    var smsGranted by remember { mutableStateOf(checkSmsGranted()) }
    var notificationGranted by remember { mutableStateOf(checkNotificationGranted()) }
    var dndGranted by remember { mutableStateOf(notificationManager.isNotificationPolicyAccessGranted) }
    var batteryOptimized by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }
    var fsiGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent()
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        smsGranted = checkSmsGranted()
        notificationGranted = checkNotificationGranted()
    }

    // Refresh all states when screen resumes focus (user returns from settings)
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Periodically recheck on composition
            Triple(checkSmsGranted(), checkNotificationGranted(), notificationManager.isNotificationPolicyAccessGranted)
        }.collect { (sms, notif, dnd) ->
            smsGranted = sms
            notificationGranted = notif
            dndGranted = dnd
        }
    }

    SmsAlarmTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "权限设置",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "为了确保告警功能正常工作，请授予以下权限：",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // SMS & Notification permissions
                PermissionItem(
                    title = "短信和通知权限",
                    description = "接收短信并发送告警通知",
                    granted = smsGranted && notificationGranted
                ) {
                    val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }

                // DND access
                PermissionItem(
                    title = "勿扰模式访问",
                    description = "在勿扰模式下也能播放告警音",
                    granted = dndGranted
                ) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }

                // Battery optimization
                PermissionItem(
                    title = "电池优化白名单",
                    description = "防止系统杀死后台告警服务",
                    granted = batteryOptimized
                ) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }

                // Full screen intent (Android 14+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fsiGranted) {
                    PermissionItem(
                        title = "全屏通知权限",
                        description = "在锁屏状态下显示告警界面",
                        granted = fsiGranted
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // Recheck all permissions
                        smsGranted = checkSmsGranted()
                        notificationGranted = checkNotificationGranted()
                        dndGranted = notificationManager.isNotificationPolicyAccessGranted
                        batteryOptimized = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                            .isIgnoringBatteryOptimizations(context.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            fsiGranted = notificationManager.canUseFullScreenIntent()
                        }

                        if (smsGranted && notificationGranted && dndGranted && batteryOptimized && fsiGranted) {
                            onAllGranted()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("已授权，继续")
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已授权",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequest) {
                    Text("授权")
                }
            }
        }
    }
}

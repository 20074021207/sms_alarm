package com.ccc.smsalarm.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ccc.smsalarm.service.KeepAliveWorker

@Composable
fun KeepAliveGuideTab() {
    val context = LocalContext.current

    // Battery optimization status
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryOptimized by remember {
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Special-access grants happen in system Settings — recheck when coming back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "保活设置",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "以下设置可确保短信监控在后台持续运行。第 1 步是决定性步骤：实测华为设备上，「启动管理」未放行的应用在滑掉最近任务后会被系统强停（连前台服务都会被杀，锁卡片也无效）；改为手动管理三开后即可存活。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 1. Auto-start / launch management (decisive step, verified on EMUI)
        AutoStartCard()

        // 2. Battery optimization
        PermissionGuideCard(
            title = "电池优化白名单",
            description = "防止系统在低电量时杀死后台服务",
            isGranted = !batteryOptimized,
            onFix = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to app settings
                    context.startActivity(openAppSettings(context))
                }
            }
        )

        // 3. Background unrestricted
        BackgroundCard(context)

        // 4. Schedule WorkManager
        Button(
            onClick = {
                KeepAliveWorker.schedule(context)
                Toast.makeText(context, "定时保活任务已启动", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("启动定时保活任务")
        }
    }
}

@Composable
private fun AutoStartCard() {
    val oemSteps = remember { getAutoStartSteps() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("自启动权限（必做）", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = oemSteps,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionGuideCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onFix: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
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
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已开启",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onFix) {
                    Text("开启")
                }
            }
        }
    }
}

@Composable
private fun BackgroundCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("后台活动无限制", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "在应用设置中，将后台活动设为「不受限制」或「无限制」。部分系统需要同时在电池设置中关闭「智能限制」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { context.startActivity(openAppSettings(context)) }
            ) {
                Text("去设置")
            }
        }
    }
}

private fun openAppSettings(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

private fun getAutoStartSteps(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "小米/Redmi 手机：\n设置 → 应用设置 → 授权管理 → 自启动管理 → 找到「短信告警」并开启"

        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "华为/荣耀 手机（实测必做）：\n设置 → 电池 → 启动管理 → 找到「短信告警」→ 关闭「自动管理」→ 开启自启动、关联启动、后台活动三个开关\n未完成此步时，滑掉最近任务会连前台服务一起被系统强停，锁卡片也无法阻止"

        manufacturer.contains("oppo") || manufacturer.contains("realme") ->
            "OPPO/真我 手机：\n设置 → 电池 → 应用速冻 → 关闭「短信告警」的速冻\n设置 → 权限与隐私 → 自启动管理 → 开启"

        manufacturer.contains("vivo") ->
            "Vivo 手机：\n设置 → 更多设置 → 权限管理 → 自启动 → 找到「短信告警」并开启\ni管家 → 应用管理 → 权限管理 → 自启动"

        manufacturer.contains("samsung") ->
            "三星 手机：\n设置 → 设备维护 → 电池 → 未监视的应用 → 添加「短信告警」\n设置 → 应用程序 → 短信告警 → 电池 → 不受限制"

        manufacturer.contains("meizu") ->
            "魅族 手机：\n设置 → 应用管理 → 权限管理 → 自启动 → 开启「短信告警」\n设置 → 电池优化 → 关闭「短信告警」的优化"

        else ->
            "通用步骤：\n设置 → 应用 → 找到「短信告警」→ 开启自启动\n设置 → 电池 → 找到本应用 → 选择「不受限制」"
    }
}

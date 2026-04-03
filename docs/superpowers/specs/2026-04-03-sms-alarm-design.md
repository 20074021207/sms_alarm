# SMS Alarm App - Design Spec

## Context

开发一款Android短信告警App。当收到的短信包含特定关键词时，立即以最大音量播放告警声音（绕过静音/勿扰模式），同步进行震动和闪光灯闪烁。告警持续直到用户手动关闭。适用于需要紧急响应短信通知的场景（如服务器告警、紧急通知等）。

## 技术决策

- **技术栈**: Kotlin + Jetpack Compose + Material 3
- **架构**: MVVM + ViewModel + StateFlow
- **DI**: Hilt
- **存储**: Room（规则和短信记录）+ DataStore（应用设置）
- **最低版本**: minSdk 26 (Android 8.0)
- **包名**: com.ccc.smsalarm
- **架构方案**: 单Activity主界面 + 独立AlarmActivity + Foreground Service

## 整体架构与数据流

```
短信到达 → SmsReceiver(BroadcastReceiver)
  → 解析短信内容 → 从Room读取规则 → 关键词匹配
  → 匹配成功:
      1. 存入MatchedSms表
      2. 启动AlarmService(Foreground Service)
         → AudioHelper: MediaPlayer + STREAM_ALARM最大音量循环播放
         → VibrationHelper: VibrationEffect波形循环震动
         → FlashlightHelper: Camera2 torch 500ms闪烁循环
         → WakeLock: PARTIAL_WAKE_LOCK保持CPU
         → 发送Full-Screen Intent通知
      3. 显示AlarmActivity(锁屏上方)
         → 显示匹配短信内容 + 大号"停止告警"按钮
         → 用户点击停止 → 停止所有效果 → 关闭
  → 匹配失败: 忽略
```

## 项目结构

```
com.ccc.smsalarm/
  data/
    db/
      AppDatabase.kt
      dao/
        AlarmRuleDao.kt
        MatchedSmsDao.kt
      entity/
        AlarmRuleEntity.kt
        MatchedSmsEntity.kt
    repository/
      AlarmRuleRepository.kt
      MatchedSmsRepository.kt
    model/
      AlarmRule.kt
      MatchedSms.kt
      AppSettings.kt
      MatchMode.kt    // enum: ANY, ALL
  receiver/
    SmsReceiver.kt
  service/
    AlarmService.kt
  ui/
    screens/
      MainScreen.kt          // 两个Tab容器
      SmsListTab.kt          // 已匹配短信列表
      ConfigTab.kt           // 关键词规则 + 音量/震动/闪光设置
      AlarmActivity.kt       // 全屏告警(独立Activity)
      SetupGuideScreen.kt    // 首次启动权限引导
    viewmodel/
      SmsListViewModel.kt
      ConfigViewModel.kt
    theme/
      Theme.kt
      Color.kt
  util/
    AudioHelper.kt
    FlashlightHelper.kt
    VibrationHelper.kt
  MainActivity.kt
  SmsAlarmApp.kt             // Application类(Hilt入口)
```

## 数据模型

### Room数据库 - AlarmRuleEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, autoGenerate) | 规则ID |
| keywords | List\<String\> | 关键词列表 |
| matchMode | String | "ANY" 或 "ALL" |
| enabled | Boolean | 是否启用 |
| createdAt | Long | 创建时间戳 |

### Room数据库 - MatchedSmsEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, autoGenerate) | 记录ID |
| sender | String | 发送者号码 |
| body | String | 短信内容 |
| matchedKeywords | List\<String\> | 匹配到的关键词 |
| matchedRuleId | Long | 匹配的规则ID |
| receivedAt | Long | 接收时间戳 |

### DataStore - AppSettings

| 设置项 | 类型 | 默认值 |
|--------|------|--------|
| alarmVolume | Int | 最大值(getStreamMaxVolume) |
| enableVibration | Boolean | true |
| enableFlashlight | Boolean | true |

## 权限

### AndroidManifest.xml声明的权限

```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### 运行时/特殊权限（需引导用户授予）

1. **RECEIVE_SMS** — 标准运行时权限弹窗
2. **POST_NOTIFICATIONS** — 标准运行时权限弹窗 (Android 13+)
3. **ACCESS_NOTIFICATION_POLICY** — 跳转 `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`
4. **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** — 跳转电池优化白名单设置
5. **USE_FULL_SCREEN_INTENT** — Android 14+需验证 `notificationManager.canUseFullScreenIntent()`，否则跳转设置

### 首次启动权限引导

应用首次打开时展示SetupGuideScreen，逐步引导用户授予所有权限。全部授予后才能进入主界面。

## 核心组件设计

### SmsReceiver (BroadcastReceiver)

- 监听 `android.provider.Telephony.SMS_RECEIVED`
- `android:exported="true"` (Android 12+必须)
- onReceive中：用 `Telephony.Sms.Intents.getMessagesFromIntent()` 解析短信
- 从Room读取所有 enabled 规则，逐条匹配
- ANY模式：短信包含任一关键词即匹配
- ALL模式：短信必须包含所有关键词才匹配
- 匹配成功 → 存储到MatchedSms表 → `startForegroundService(AlarmService)`
- 注意：必须在10秒内完成，Room操作需用 `runBlocking`（因为BroadcastReceiver生命周期限制）

### AlarmService (Foreground Service)

- `foregroundServiceType="specialUse"`
- onCreate中：
  1. 构建通知渠道和通知（CATEGORY_ALARM, PRIORITY_MAX）
  2. 设置Full-Screen Intent指向AlarmActivity
  3. 调用 `startForeground()`
- onStartCommand中：
  1. 获取WakeLock（PARTIAL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP）
  2. AudioHelper：设置STREAM_ALARM为用户配置音量，MediaPlayer looping=true开始播放
  3. VibrationHelper：启动波形震动循环（500ms震/500ms间隔）
  4. FlashlightHelper：启动闪光灯闪烁循环（500ms开/500ms关）
- 停止Action处理：
  1. 停止MediaPlayer并release
  2. vibrator.cancel()
  3. 关闭闪光灯
  4. 释放WakeLock
  5. stopSelf()

### AlarmActivity (全屏告警界面)

- 独立Activity，Compose渲染
- `setShowWhenLocked(true)` + `setTurnScreenOn(true)` 覆盖锁屏
- Window flags: `FLAG_KEEP_SCREEN_ON` + `FLAG_DISMISS_KEYGUARD`
- UI：极简，只显示：
  - 匹配的短信内容
  - 大号"停止告警"按钮
- 点击停止 → 发送Intent给AlarmService的停止Action → `finish()`

### AudioHelper

- 使用 `AudioManager.STREAM_ALARM` 音频流
- 设置音量：`audioManager.setStreamVolume(STREAM_ALARM, configVolume, 0)`
- MediaPlayer配置：
  - `AudioAttributes.Builder().setUsage(USAGE_ALARM).setContentType(CONTENT_TYPE_SONIFICATION)`
  - `isLooping = true`
  - 使用系统默认告警音 `Settings.System.DEFAULT_ALARM_ALERT_URI`

### VibrationHelper

- 使用 `VibrationEffect.createWaveform(timings, amplitudes, repeatIndex)`
- 模式：`longArrayOf(0, 500, 500)` + `intArrayOf(0, 255, 0)`，repeat从index 0循环
- 停止时调用 `vibrator.cancel()`

### FlashlightHelper

- 使用 Camera2 API `CameraManager.setTorchMode()`
- 在协程中循环：`delay(500)` 切换开关
- 检查 `FLASH_INFO_AVAILABLE` 确认设备有闪光灯
- 处理 `CameraAccessException`

## 主界面UI设计

### Tab 1 - 短信列表 (SmsListTab)

- LazyColumn，按receivedAt倒序排列
- 每项卡片显示：
  - 发送者号码
  - 短信内容（匹配的关键词用高亮色标注）
  - 接收时间（相对时间如"3分钟前"）
- 空状态：居中文字"暂无告警短信"

### Tab 2 - 配置规则 (ConfigTab)

**规则管理区域：**
- 规则列表，每条规则显示：
  - 关键词tags（FlowRow布局）
  - 匹配模式标签（"任一匹配"/"全部匹配"）
  - 启用开关（Switch）
  - 删除按钮
- 底部"添加规则"按钮 → 弹出Dialog：
  - 关键词输入框（提示"多个关键词用逗号分隔"）
  - 匹配模式选择：RadioButton（任一匹配/全部匹配）
  - 确认按钮

**告警设置区域：**
- 音量Slider（0到getStreamMaxVolume(STREAM_ALARM)，默认最大）
- 震动开关 Switch
- 闪光灯开关 Switch

## 测试验证

1. **基本功能测试**：
   - 配置关键词规则，发送包含关键词的短信，验证告警触发
   - 发送不含关键词的短信，验证不触发
   - 多规则场景：任一规则匹配即触发
2. **静音/勿扰测试**：
   - 手机设为静音模式，发送匹配短信，验证告警音正常播放
   - 开启勿扰模式并授予DND权限，验证告警音正常播放
3. **锁屏测试**：
   - 锁屏状态下发送匹配短信，验证屏幕点亮并显示AlarmActivity
4. **停止测试**：
   - 告警播放中点击停止按钮，验证声音/震动/闪光全部停止
5. **持久性测试**：
   - 告警持续5分钟以上不自动停止，直到用户手动关闭
6. **多关键词匹配测试**：
   - ANY模式：短信包含任一关键词即触发
   - ALL模式：短信包含所有关键词才触发

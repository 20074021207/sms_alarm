# SMS Alarm - 短信告警

一款 Android 短信告警应用，当收到匹配关键词规则的短信时，自动触发音频、震动和闪光灯告警。

## 功能特性

- **关键词规则匹配** - 支持多关键词 AND/OR 匹配模式，长短信（多分片）合并后整体匹配；输入支持中英文逗号/分号/顿号分隔
- **多种告警方式** - 音频播放（闹钟流、勿扰下可响）、震动（USAGE_ALARM）、闪光灯闪烁
- **锁屏告警** - 匹配短信触发全屏告警界面 + 全屏通知
- **保活机制** - 前台服务 + 精确闹钟看门狗 + WorkManager 周期兜底 + 开机自启
- **失败降级** - 前台服务启动受限（Android 12+）时退化为全屏告警通知，绝不崩溃
- **首次引导** - 首次运行引导授予所需权限；权限被撤销后再次进入会重新引导

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Hilt 依赖注入
- Room 数据库 / DataStore 偏好设置
- Coroutines / Flow
- Target SDK 35, Min SDK 26

## 构建与运行

```bash
# Debug 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# Release 构建（需要在仓库根目录放置未入库的 keystore.properties）：
#   storeFile=../sms_alarm_release.jks
#   storePassword=... / keyAlias=... / keyPassword=...
./gradlew assembleRelease
```

单元测试覆盖关键词解析、Room 类型转换和规则匹配逻辑（`./gradlew testDebugUnitTest`）。

## 后台保活说明（重要）

厂商系统对后台进程的处理差异极大。实测（华为 P30 Pro / EMUI / Android 10）：

- **「启动管理」未放行的应用，滑掉最近任务会被系统强停**——前台服务一并被杀、看门狗闹钟与定时任务全部被系统取消、连短信广播都不再投递，任何代码都无法自救；**锁定最近任务卡片也不能避免**。
- 在 设置 → 电池 → 启动管理 中改为**手动管理并开启全部三个开关**后，滑掉任务进程存活。
- 普通杀进程（非强停）由 START_STICKY 秒级自动恢复。

应用内「保活」Tab 按厂商给出了对应设置指引。

## 项目结构

```
app/src/main/java/com/ccc/smsalarm/
├── data/
│   ├── db/          # Room 实体、DAO、类型转换器
│   ├── repository/  # 业务逻辑 + 数据访问
│   └── model/       # 设置与匹配模式模型
├── di/              # Hilt 模块 + 后台组件共享 EntryPoint
├── receiver/        # SmsReceiver / BootReceiver / RestartReceiver
├── service/         # AlarmService（告警）、MonitorService（监控）、KeepAliveWorker、AlarmState
├── ui/
│   ├── screens/     # Compose 页面（记录/配置/保活 + 全屏告警 + 首次引导）
│   ├── viewmodel/
│   └── theme/
└── util/            # 音频、震动、闪光灯工具
```

## 工作流程

1. 短信到达 → `SmsReceiver` 合并全部分片后提取发送者和内容
2. `AlarmRuleRepository.matchSms()` 匹配已启用的关键词规则
3. 匹配成功 → 保存到数据库 → 启动 `AlarmService` 前台服务（失败则降级为全屏通知）
4. `AlarmService` 驱动音频、震动、闪光灯同时告警，直到用户停止

## 许可证

MIT License

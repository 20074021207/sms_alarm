# SMS Alarm - 短信告警

一款 Android 短信告警应用，当收到匹配关键词规则的短信时，自动触发音频、震动和闪光灯告警。

## 功能特性

- **关键词规则匹配** - 支持多关键词 AND/OR 匹配模式
- **多种告警方式** - 音频播放、手机震动、闪光灯闪烁（可同时开启）
- **锁屏告警** - 来匹配短信时弹出全屏告警界面
- **保活机制** - 支持前台服务保活，防止系统杀后台
- **首次引导** - 首次运行引导用户授予所需权限

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Hilt 依赖注入
- Room 数据库
- DataStore 偏好设置
- Coroutines / Flow
- Target SDK 35, Min SDK 26

## 构建与运行

```bash
# Debug 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# Release 构建
./gradlew assembleRelease

# 运行测试
./gradlew test
```

## 项目结构

```
app/src/main/java/com/ccc/smsalarm/
├── data/
│   ├── db/          # Room 实体、DAO、类型转换器
│   ├── repository/  # 业务逻辑 + 数据访问
│   └── model/       # 领域模型
├── receiver/        # SMS 广播接收器
├── service/         # 告警前台服务
├── ui/
│   ├── screens/     # Compose 页面
│   └── viewmodel/   # ViewModel
├── util/            # 音频、震动、闪光灯工具类
└── di/              # Hilt 模块
```

## 工作流程

1. 短信到达 → `SmsReceiver` 提取发送者和内容
2. `AlarmRuleRepository.matchSms()` 匹配已启用的关键词规则
3. 匹配成功 → 保存到数据库 → 启动 `AlarmService` 前台服务
4. `AlarmService` 驱动音频、震动、闪光灯同时告警

## 许可证

MIT License

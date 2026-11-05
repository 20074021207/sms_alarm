# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS Alarm is an Android app that triggers audio/vibration/flashlight alarms when incoming SMS messages match user-defined keyword rules. The UI is entirely in Chinese.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, Coroutines/Flow. Target SDK 35, min SDK 26.

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Release build — requires keystore.properties at the repo root (not committed):
#   storeFile=../sms_alarm_release.jks / storePassword=... / keyAlias=... / keyPassword=...
./gradlew assembleRelease
```

No lint configuration is present. The project uses KSP (not kapt).

```bash
# Unit tests (keyword parsing, Room converters, rule matching)
./gradlew testDebugUnitTest
```

## Architecture

**MVVM with Repository pattern, single process:**

- `MainActivity` → `MainViewModel` → Repositories → Room/DataStore
- `MainScreen` hosts three tabs: `SmsListTab` (告警记录), `ConfigTab` (配置), `KeepAliveGuideTab` (保活)
- `AlarmActivity` is a separate lock-screen full-screen alarm, launched via full-screen notification intent
- `AlarmState` (singleton object with `StateFlow`) shares alarm state between Activity and Service; the stop dialog lives in `MainActivity` only (do not re-add it to `MainScreen`)

**Key data flow:**
1. SMS arrives → `SmsReceiver` concatenates all PDU fragments, then matches once against enabled rules
2. `AlarmRuleRepository.matchSms()` checks keywords (ANY/ALL match modes)
3. On match: saves to `MatchedSmsEntity` table → starts `AlarmService` (foreground). If the FGS start fails (Android 12+ background restriction) the receiver falls back to `AlarmService.postFallbackNotification()`
4. `AlarmService` drives audio (`AudioHelper`), vibration (`VibrationHelper`), flashlight (`FlashlightHelper`); settings are loaded in a coroutine, never via runBlocking
5. Keep-alive: `MonitorService` (START_STICKY FGS, `stopWithTask=false`, unified start via `MonitorService.startMonitorService` with PendingIntent fallback for Android 12+) + setAlarmClock watchdog (`RestartReceiver` — fires exactly, survives Doze, and its temporary allowlist is what allows the background FGS start) + `KeepAliveWorker` (15-min WorkManager) + `BootReceiver` (BOOT_COMPLETED / QUICKBOOT / MY_PACKAGE_REPLACED only — USER_PRESENT and POWER_* are dead on API 26+)

**Package structure under `com.ccc.smsalarm`:**
- `data/db/` — Room entities, DAOs, type converters, AppDatabase
- `data/repository/` — Business logic + data access; `SettingsRepository` is the ONLY place that reads `monitoring_enabled` (background components use `AppEntryPoint`)
- `data/model/` — AppSettings + MatchMode
- `di/AppEntryPoint` — shared Hilt entry point for receivers/workers
- `receiver/` — SmsReceiver, BootReceiver, RestartReceiver
- `service/` — AlarmService, MonitorService, KeepAliveWorker, AlarmState
- `ui/` — screens + viewmodel + theme
- `util/` — AudioHelper, VibrationHelper, FlashlightHelper, Keywords (separator-aware keyword parsing)

## Key Design Decisions & Constraints

- **Single process on purpose.** MonitorService previously ran in `:monitor`; that made DataStore a multi-process hazard (default DataStore has no cross-process coordination, the monitor process could read stale settings). Removed after on-device verification.
- **AudioHelper saves and restores the system alarm stream volume**; playback uses `prepareAsync` and must never throw out of `startAlarm` (vibration/flashlight continue when audio fails).
- **VibrationHelper uses USAGE_ALARM attributes** so the pattern survives DND.
- **AlarmService keeps one service-level scope**, cancelled only in `onDestroy`; per-alarm work goes into `effectsJob` so a re-entrant alarm on the same instance still gets a flashlight.
- **Keep-alive reality (verified 2026-10 on Huawei P30 Pro / EMUI, Android 10):** swiping the task away force-stops apps whose Huawei 启动管理 is not set to 手动管理 (three toggles on) — the card lock does NOT protect, and after a force-stop nothing in-app can recover (alarms, jobs and broadcasts are all cancelled). The KeepAliveGuideTab therefore puts 启动管理 first as the decisive step. Ordinary (non-force-stop) kills are covered by START_STICKY (~1.4s revival).
- **Permissions kept minimal:** RECEIVE_SMS only (no READ_SMS — the app never queries the SMS provider), no CAMERA (setTorchMode doesn't need it), no SYSTEM_ALERT_WINDOW (no overlay code). The keep-alive watchdog uses `setAlarmClock`, which needs no exact-alarm permission.

## Design Spec

A detailed Chinese-language design document exists at `docs/superpowers/specs/2026-04-03-sms-alarm-design.md`.

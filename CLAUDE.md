# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SMS Alarm is an Android app that triggers audio/vibration/flashlight alarms when incoming SMS messages match user-defined keyword rules. The UI is entirely in Chinese.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, Coroutines/Flow. Target SDK 35, min SDK 26.

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest
```

No lint configuration is present. The project uses KSP (not kapt) for annotation processing.

## Architecture

**MVVM with Repository pattern:**

- `MainActivity` → `MainViewModel` → Repositories → Room/DataStore
- Single navigation structure: `MainScreen` with two tabs (`ConfigTab`, `SmsListTab`)
- `AlarmActivity` is a separate lock-screen overlay, launched via full-screen notification intent

**Key data flow:**
1. SMS arrives → `SmsReceiver` (BroadcastReceiver) extracts sender/body
2. `AlarmRuleRepository.matchSms()` checks keywords against enabled rules (ANY/ALL match modes)
3. On match: saves to `MatchedSmsEntity` table → starts `AlarmService` (foreground service)
4. `AlarmService` drives audio (`AudioHelper`), vibration (`VibrationHelper`), flashlight (`FlashlightHelper`) simultaneously
5. `AlarmState` (singleton object with `StateFlow`) shares alarm state across Activity and Service

**Package structure under `com.ccc.smsalarm`:**
- `data/db/` — Room entities, DAOs, type converters, AppDatabase
- `data/repository/` — Business logic + data access (AlarmRuleRepository includes matching logic)
- `data/model/` — Domain models (AlarmRule, MatchedSms, MatchMode enum, AppSettings)
- `receiver/` — SmsReceiver
- `service/` — AlarmService (foreground) + AlarmState
- `ui/viewmodel/` — MainViewModel
- `ui/screens/` — Compose screens
- `util/` — AudioHelper, VibrationHelper, FlashlightHelper
- `di/` — Hilt AppModule

## Key Design Decisions

- **SmsReceiver uses manual Hilt injection** (`@Inject lateinit var`) since BroadcastReceivers can't use `@AndroidEntryPoint`. Entry point is obtained via `EntryPointAccessors`.
- **TypeConverters** in `Converters.kt` handle `List<String>` (as JSON) and `MatchMode` enum for Room.
- **AlarmService** manages its own `CoroutineScope` with `SupervisorJob`, cleaned up in `onDestroy()`.
- **Permissions are handled via SetupGuideScreen** — a first-run flow that requests SMS, notification, DND access, battery optimization whitelist, and full-screen intent (Android 14+).
- **Flashlight** uses Camera2 API (`CameraManager.setTorchMode`) with a coroutine-driven 500ms blink cycle.
- **WakeLock** in AlarmService uses `PARTIAL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP` with 10-minute timeout.

## Design Spec

A detailed Chinese-language design document exists at `docs/superpowers/specs/2026-04-03-sms-alarm-design.md` covering architecture, database schema, permission requirements, and testing scenarios.

# NovaTune Engine

A sandbox-compliant Android 10–15 gaming HUD / telemetry utility built with Jetpack Compose.

## What is real

- Reads `ActivityManager.MemoryInfo` for actual available/total RAM, low-memory state and threshold.
- Samples NovaTune's real Java/native heap and live thread count.
- Uses a user-granted `TYPE_APPLICATION_OVERLAY` sidebar.
- Runs the overlay as a user-visible foreground service using `specialUse` on Android 14+.
- Requests the highest compatible display mode for **NovaTune's own Activity window**.
- Uses a short-lived `THREAD_PRIORITY_FOREGROUND` worker for NovaTune's own manual reclaim task.
- Manual reclaim calls `System.gc()` and `Runtime.runFinalization()` for **NovaTune only**.
- Monitors network transport/capability state with `ConnectivityManager.NetworkCallback`.
- Includes low-delay TOS helpers for **NovaTune-owned sockets** only.

## What it intentionally does not claim

A normal third-party Android app cannot, without privileged access, force another game's CPU scheduler,
GC, sockets, display mode, or LMK score. NovaTune therefore never displays fake "RAM freed from game"
or fake "CPU boosted" values.

The app does not request `INTERNET`, Root, Shizuku, ADB, system signature, or any API key.

## Build

The included GitHub Actions workflow builds a debug APK with JDK 17, Gradle 9.6, AGP 9.4.0,
compileSdk 37, targetSdk 35 and minSdk 29.

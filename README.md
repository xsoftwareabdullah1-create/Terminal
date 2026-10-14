# AstraTerm

A lightweight Android terminal prototype using Java UI, a C++ JNI PTY bridge, and Android's /system/bin/sh.

## Status
This is an initial implementation scaffold intended to build through GitHub Actions. It launches the Android system shell in a pseudo-terminal; it is not a full Linux distribution or Termux replacement. The command set depends on Android version. No root access or package manager is provided.

## Build APK
1. Open the Actions tab and select Android APK.
2. Run the workflow (or push a commit to main).
3. Download the AstraTerm-debug-apk artifact from a successful run.

## Local build
Install JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0 / NDK 27.2.12479018, then run ./gradlew assembleDebug. This repository will include a Gradle wrapper in a later setup step if available.

## Architecture
- Java Activity handles lifecycle, input, PTY I/O, and session cleanup.
- C++17 JNI opens a PTY with POSIX PTY APIs, starts /system/bin/sh, and exposes the master descriptor.
- A custom Canvas view displays monospace output and a visible cursor.
- XML is used for the initial screen layout.

## Limitations
- The renderer is a lightweight terminal display, not a complete VT100/xterm emulator. ANSI handling, full-screen apps, advanced cursor addressing, and terminal resize support are limited.
- Commands are Android system utilities; availability varies by device.
- No bundled BusyBox, apt ecosystem, multiple concurrent sessions, or full scrollback selection is included yet.
- APK size and device-level runtime behavior must be measured after a successful workflow/device test; no size or install test is claimed in advance.

## Security
Runs without root. Files are confined by Android app sandbox except where Android system commands permit access. No broad storage permissions are requested.
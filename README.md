# AstraTerm

**A lightweight Android terminal experiment inspired by Termux.** AstraTerm combines a native Java interface with a small C++/JNI pseudo-terminal bridge. It launches Android's own `/system/bin/sh` without root.

> **Project status:** early prototype. It is not yet a drop-in Termux replacement and does not include a Linux distribution or Termux's package manager.

## Features

- Dark, landscape-friendly terminal-style screen with monospace output and a visible cursor
- Java Android UI with a native C++17 PTY bridge
- Interactive Android system shell
- On-screen CTRL, ESC, TAB, and arrow-key shortcuts
- Private app home directory
- GitHub Actions build that uploads a debug APK artifact
- No root permission requested

## Screens and architecture

- **UI:** Java, Android Views, XML layout, and a custom Canvas terminal view
- **Native layer:** C++17 and JNI
- **Shell:** Android `/system/bin/sh`
- **Build:** Android Gradle Plugin, Gradle, Android SDK, NDK, and CMake

## Download the APK

1. Open the repository's [Actions page](https://github.com/xsoftwareabdullah1-create/Terminal/actions).
2. Open the latest **AstraTerm Android CI** run.
3. Wait for every step to show a green check mark.
4. Under **Artifacts**, download **AstraTerm-debug-apk**.
5. Extract the ZIP and install `app-debug.apk` on a compatible Android device.

If the run is red, open the failed step and read the first actual `FAILURE`, `error:`, or `What went wrong` message. A failed workflow does not produce a verified APK artifact.

## Build in Termux

A local Android build requires a compatible JDK, Gradle, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, and CMake 3.22.1. Android SDK setup on a phone can be storage-intensive; GitHub Actions is the recommended build route for this repository.

This repository currently uses the Gradle installation supplied by CI and **does not include the Gradle Wrapper**. Therefore, do not run `./gradlew` unless wrapper files are added later. With Gradle 8.9 and the Android SDK configured, build from the repository root using:

```sh
gradle --no-daemon clean assembleDebug
```

The output should be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Requirements and compatibility

- Android 10 (API 29) or newer
- ARM64 (`arm64-v8a`) build only
- Android system shell; available commands vary by device

## Current limitations

AstraTerm is a prototype, not a complete VT100/xterm emulator. Full ANSI/CSI parsing, terminal resizing, alternate screen buffers, mouse support, full-screen TUI compatibility, robust scrollback selection, multiple sessions, and a bundled package ecosystem are not implemented. Android's shell environment is not a general-purpose Linux distribution, and commands such as `apt` are not included.

The APK must pass CI and be installed/tested on a real device before release readiness or final APK size can be claimed.

## Permissions and security

AstraTerm does not request root access or broad shared-storage permissions. The shell runs under the app's Android UID and is subject to Android's application sandbox. Do not assume this provides the same packages or filesystem access as Termux.

## Contributing / reporting a build failure

Please include:
- the failed workflow run link,
- the exact failing step,
- the first relevant error message and surrounding log lines,
- the Android version and device architecture for runtime issues.

See [GitHub Actions](https://github.com/xsoftwareabdullah1-create/Terminal/actions) for build status.

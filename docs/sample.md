---
title: Run the sample
description: Try Scout on desktop, Android or iOS and find the code behind each feature.
---

# Meet Scout

Scout is a small offline chat about getting outside. It demonstrates the same public components you can use in your app: text, image attachments, a custom trail card and two themes.

```{figure} assets/bot-controls.gif
:width: 320px
:alt: Opening the chat overflow menu, pausing the bot and changing the theme

Pause the bot or switch themes from the overflow menu.
```

## Platforms and requirements

| Target | Requirements |
| --- | --- |
| Desktop JVM | JDK 21; the shared Gradle build also requires Android SDK 37. Runtime artifacts are selected for macOS arm64/x64, Linux arm64/x64 or Windows x64. |
| Android | Android SDK 37, JDK 21, and an emulator or device on API 24 or later. |
| iOS | macOS, Xcode and XcodeGen. The sample declares iOS 15.0 and builds arm64 device/simulator frameworks. |

The project’s pinned Kotlin version is 2.4.0 and Compose Multiplatform is 1.11.1. Set your Android SDK path using `ANDROID_HOME` or the untracked `local.properties` file.

Android and arm64 iOS simulator journeys have been exercised. Minimum OS versions, every desktop architecture and physical iOS devices have not all been verified. The iOS software keyboard and real platform media adapters need validation in your host app.

## Desktop

From the repository root:

```sh
./gradlew :sample:run
```

## Android

Open the repository in Android Studio, choose the `androidApp` configuration and run it on a device or emulator. To build the debug APK from a terminal:

```sh
./gradlew :androidApp:assembleDebug
```

The APK is written to `androidApp/build/outputs/apk/debug/`.

## iOS

Generate the Xcode project, then open it:

```sh
cd iosApp
xcodegen generate
open ChatSample.xcodeproj
```

Choose the `ChatSample` scheme and an arm64 simulator. The Xcode build phase builds the shared Compose framework through Gradle. Physical-device signing uses your own development team.

## What to try

Send a text message and wait for Scout’s reply. Tap the paperclip to attach the bundled mountain illustration, then send it. Scout also shares at most three unsolicited messages, spaced 15–30 seconds apart, including an image and trail idea, then idles. User interaction can start another burst.

Open the overflow menu for **Forest theme** or **Pause bot**. Pausing cancels queued arrivals and replies; you can still send your own messages. **Resume bot** starts one fresh schedule. Switching themes preserves the draft and reading position.

The bot is a local simulation, not an AI integration. Messages stay in memory, and reopening the sample starts a new session. The illustration is bundled with the project under Apache 2.0; no network or platform photo permission is needed.

## Find the implementation

| File | What to learn |
| --- | --- |
| [SampleApp.kt](../sample/src/commonMain/kotlin/dev/chat/sample/SampleApp.kt) | Theme, scaffold, media slots, attachment chooser and custom trail card. |
| [BotHost.kt](../sample/src/commonMain/kotlin/dev/chat/sample/BotHost.kt) | Host-owned state, local replies and bounded scheduling. |
| [FakeMediaHost.kt](../sample/src/commonMain/kotlin/dev/chat/sample/FakeMediaHost.kt) | Simulated selection, image state and a local viewer. |
| [SampleTheme.kt](../sample/src/commonMain/kotlin/dev/chat/sample/SampleTheme.kt) | Default and Forest palettes. |

Ready to build your own? Start with [your first conversation](getting-started.md).

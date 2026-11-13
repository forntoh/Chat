# Compose chat — text conversation candidate

A host-owned text conversation for Android, iOS and desktop JVM. This slice includes a shared UI and an in-memory sample; transport, authentication and persistence belong to the consumer.

Use JDK 21 (`JAVA_HOME`) to launch Gradle and provide the compilation toolchain, Android SDK (`ANDROID_HOME`), and a full Xcode installation (`DEVELOPER_DIR` if it is not selected globally). The committed `gradle/gradle-daemon-jvm.properties` selects Java 25 for the Gradle daemon independently of `JAVA_HOME`. Gradle uses a matching installed/cached JDK or provisions it through the download URLs in that file; first-time provisioning needs network access. Kotlin/JVM compilation still uses the JDK 21 toolchain configured by the convention plugin.

```sh
./gradlew :chat:material3:jvmTest :sample:jvmTest :androidApp:assembleDebug
./gradlew :sample:run
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n dev.chat.sample.android/.MainActivity
cd iosApp
xcodegen generate
xcodebuild -project ChatSample.xcodeproj -scheme ChatSample -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

The iOS project is reproducibly generated from `iosApp/project.yml` with XcodeGen. Set a simulator destination when building for a particular runtime. Physical-device signing is host setup.

See [contract and architecture](docs/implementation.md) for public state/events and dependency decisions. Native evidence and limitations are recorded in the handoff document; target declarations alone are not verified platform support. This candidate has only local verification coordinates; public distribution is not configured.

See [earlier history](docs/history.md) for host paging, reader anchoring, retry and the custom/Material3 sample controls.

Verify selectable artifacts locally (no network publication):

```sh
./gradlew :chat:core:publishAllPublicationsToStagingRepository :chat:ui:publishAllPublicationsToStagingRepository :chat:material3:publishAllPublicationsToStagingRepository
./gradlew -p consumers -PchatRepository="$PWD/build/staging" verifySelection jvmJar compileAndroidMain compileKotlinIosArm64 compileKotlinIosSimulatorArm64 :custom-ui:jvmTest
```

Library packages use the `dev.forntoh.chat` root (`core`, `ui`, `material3`, `actions`, `media`).
Sample and consumer harness packages remain separate.

Choose `dev.forntoh.chat.candidate:chat-core:0.1.0-local` for contracts, `chat-ui` for custom Compose content, or `chat-material3` for default presentation. The separate consumer build shows each integration. All base artifacts use the same configured platform/toolchain floors.

See [customization](docs/customization.md) for host strings, ordered semantics, mixed renderers
and the default chat theme and customizable scaffold.

See [message actions](docs/actions.md) for independently selectable `chat-actions`, quote/edit/delete
state, explicit host clipboard integration and a Material3-free outside consumer.

`ConversationTheme` and `ConversationSlots` live in `dev.forntoh.chat.ui`. For optional Material3 presentation,
wrap the same `dev.forntoh.chat.ui.Conversation` in `dev.forntoh.chat.material3.ChatTheme { ... }`.
Use `ChatScaffold(title = { Text("Design team") }) { Conversation(state, ::handleEvent) }`
inside that theme for the full customizable screen shell.
The Material3 module supplies slot renderers and has no separate Conversation component.

See [host-supplied images](docs/media.md) for independently selectable `chat-media`, guarded
image drafts, host rendering/upload/viewer integration and the bundled **Image sample**.
Stage `:chat:media:publishAllPublicationsToStagingRepository` alongside core/ui, then verify
`:media-only:verifySelection :media-only:jvmTest` in the separate consumer build.

See [support and versioning](docs/support.md), [changelog](CHANGELOG.md),
[contributing](CONTRIBUTING.md), [Apache-2.0 license](LICENSE) for original library code and
[dependency inventory](docs/dependencies.md). Public distribution remains unconfigured.

For combined actions and media graph evidence, stage core/ui/actions/media and run:

```sh
./gradlew -p consumers -PchatRepository="$PWD/build/staging" :combined:verifySelection
```

The independent consumer checks JVM, Android and both arm64 iOS artifact graphs without running a
module/runtime matrix. It selects both optional features without selecting Material3.

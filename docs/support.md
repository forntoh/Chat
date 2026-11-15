# Platform support

Chat declares Android, desktop JVM, arm64 iOS device and arm64 iOS simulator targets. Configured floors describe the build; they are not tested minimum-runtime guarantees.

| Platform | Configured floor | Local verification scope | Limits |
| --- | --- | --- | --- |
| Android | API 24; compile/target API 37 in the sample | API 37 emulator builds and bounded sample journeys | API 24 runtime and physical devices unverified; AGP 9.1.0 reports its tested compile SDK ceiling as 36.1 |
| iOS | iOS 15; arm64 device/simulator | iOS 27 arm64 simulator build/launch with bundled images | iOS 15 runtime, physical devices/signing and Intel simulator unverified |
| Desktop JVM | JVM 21 | macOS arm64 builds and public UI/host checks | Windows/Linux and other desktop architectures unverified |

The pinned stack uses Kotlin/compiler 2.4.0, Compose 1.11.1, Material3 1.11.0-alpha07, AGP 9.1.0 and Gradle 9.4.1. Compilation uses JDK 21 and the producer daemon selects JDK 25. Material3 is a prerelease dependency. Compatibility with other toolchains has not been established.

The sample's bundled media and simulated service do not verify real permissions, picker, network, upload storage or persistence. iOS software-keyboard overlap remains unverified. Broad screen-reader, RTL/locale, orientation, font-scale and device campaigns are outside the current verification scope.

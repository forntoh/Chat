# Candidate support and versioning

The current version is `0.1.0-local`, available only from a locally staged Maven repository.
Public coordinates, distribution ownership and a public release channel are unverified.

| Platform | Configured target / floor | Observed evidence | Limits |
| --- | --- | --- | --- |
| Android | API 24 minimum; compile SDK 37 | Earlier Android 17 emulator launch/text-send smoke; final baseline builds and independent consumer compilation | Minimum API runtime and physical devices unverified; AGP 9.1.0 warns SDK 37 exceeds its tested 36.1 range |
| iOS | iOS 15; arm64 device and arm64 simulator | Earlier iOS 27 arm64 simulator launch/text-send smoke; baseline consumer compilation for both arm64 targets | iOS 15 runtime, physical device/signing and Intel simulator unverified |
| Desktop JVM | JVM 21 | Earlier macOS arm64 launch/text-send smoke; baseline JVM builds/public-contract tests | Windows/Linux and other desktop architectures unverified |

Configured floors are not tested minimum-runtime guarantees. Native smoke results came from the
first text-conversation slice, not a fresh run on every candidate. Exact source/artifact identities
and command results belong in the release evidence. Broad screen-reader, RTL/locale, orientation,
font scaling, dark-mode, keyboard and device/configuration campaigns remain deferred.

| Artifact | Adds | Required Chat modules |
| --- | --- | --- |
| `chat-core` | Host-owned state and intents; no Compose | none |
| `chat-ui` | Shared conversation behavior and custom slots | core |
| `chat-material3` | Optional default slots, theme and scaffold | ui, core |
| `chat-actions` | Optional reply/edit/delete and host clipboard actions | ui, core |
| `chat-media` | Optional host-supplied image drafts/rendering | ui, core |

All five artifacts declare the same Android, JVM and arm64 iOS targets. Actions and media can be
selected together without Material3; neither brings the other by default. Samples, transport,
authentication, persistence, upload/viewer services and platform authorization belong to the host.
See [actions](actions.md), [media](media.md) and [customization](customization.md).

The pinned stack is Kotlin/compiler 2.4.0, Compose 1.11.1, Material3 1.11.0-alpha07,
AGP 9.1.0 and Gradle 9.4.1. Compilation uses JDK 21; the producer daemon selects JDK 25.
Material3 is a prerelease dependency. No compatibility with other toolchain versions is claimed.

## Versioning and deprecation policy

Use one aligned version for all Chat modules. Local candidate coordinates can be replaced during
preparation and are not immutable public releases. Record artifact checksums when sharing evidence.
The first public version and coordinates require a separate release decision.

For future public releases, use semantic versioning: patches for compatible fixes, minor versions
for compatible additions, and major versions for breaking public contracts. During `0.x`, minor
versions may change public contracts; document migrations in the changelog. Patch releases should
remain compatible. Runtime behavior and host-owned state/event contracts are part of that API.

Prefer Kotlin `@Deprecated` with a replacement and migration note before removing a public API.
Keep deprecated APIs through at least the next minor release where feasible; announce removal in
the changelog, reserving incompatible removal for a major release after 1.0 (a minor release in
0.x). Security or correctness exceptions must explicitly explain the break and migration. This is
a policy for releases going forward, not a claim of an existing release history or support SLA.

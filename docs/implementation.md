# Text conversation slice

The approved test seam is a consumer mounting `Conversation` with host snapshots and captured public events. Desktop Compose UI tests exercise sends, host decisions, delivery/retry and conversation changes. Native sample journeys separately verify keyboard-driven viewport adjustment and navigation. No backend or persistence is introduced.

Modules: `:chat:core` contains framework-free public models and intents; `:chat:ui` owns shared timeline/submission behavior and typed message/composer slots using Compose foundation; `:chat:material3` supplies optional default presentation; `sample` owns the in-memory fake delivery service and retained per-conversation state; `androidApp` and `iosApp` are thin launchers. Desktop launches `sample`. Services never flow into `chat`.

Initial pinned build: Kotlin/compiler 2.4.0, Compose 1.11.1, AGP 9.1.0, Gradle 9.4.1, JDK 21. Android min API 24, compile/target 37; iOS deployment target 15; desktop JVM 21. Runtime coverage and exact candidate will be recorded separately.

The native Apple targets are arm64 device and arm64 simulator; Intel iOS simulators are not configured. Desktop candidate validation targets macOS arm64 only; Windows/Linux compatibility requires future native verification. Android 24 and iOS 15 are configured floors, not tested minimum-runtime claims. Build tooling currently emits an AGP validation warning for SDK 37; that warning is not suppressed.

Sources: [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin). These are pinned engineering choices, not claims of testing every supported OS.

## Public state and events

`dev.chat.material3.Conversation(state, onEvent)` accepts an immutable `ConversationState`. The host supplies conversation identity, current participant identity, ordered messages and a draft. Each message carries stable ID, participant, epoch timestamp, host-formatted display timestamp, unmodified text and delivery state. No server schema exists: these public Kotlin types are the integration boundary and the UI reads them directly.

`DraftChanged` requests a text update. `SendDraft` carries conversation ID, draft revision and exact text. A local synchronous latch blocks a second activation before the host can publish pending state; `Draft.submitting` also blocks externally pending submissions. The host must increment the draft revision when accepting (clear explicitly) or refusing (retain explicitly). Until that resolution the composer is locked. The host must reject stale conversation/revision events. Recomposition and mounting do not emit events. Draft revisions must never be reused within the retained conversation state.

`RetryMessage` carries conversation ID and existing message ID. The host changes that row's delivery state and deduplicates backend requests. The UI does not manufacture sent state, retry automatically, sort messages, or clear drafts. Host snapshots have unique message IDs; later snapshots replace the content for those IDs. Equal timestamps follow host list order.

The sample retains separate snapshots above its navigation switch for the lifetime of the mounted app. It uses deterministic manual completion controls (success/failure), so pending state and retry can be inspected without timing races. This fake service performs no network access. Production hosts own authorization, transport, deduplication and persistence; the sample is not a production service adapter. No process-death guarantee is made.

Material3 is pinned to the `1.11.0-alpha07` component paired with Compose 1.11.1 in its [release table](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html); that prerelease dependency is a candidate risk. No publishing coordinates, release task or credentials are configured in this slice.

## Selectable architecture amendment

The included `build-logic` applies `chat.kmp` (Android KMP, arm64 iOS device/simulator, JVM, JDK 21 and test foundations), `chat.compose` (Compose compiler/plugin), and `chat.staging` (local candidate metadata). Plugin versions come from the shared `gradle/libs.versions.toml`; namespaces and dependencies stay in module builds. No root subproject mutation is used. PataPlace core/model and shared/ui were inspected as a separation reference; no application/service dependencies were copied.

Dependency direction: material3 → ui → core. Public model types live in `dev.chat.core`; custom presentation uses `dev.chat.ui.Conversation` with `messageContent` and `composerContent`. The composer slot receives the exact host draft, pending/canSend state and guarded change/send callbacks. The message slot receives the identified message, current-participant flag and nullable retry callback. Material3 exposes the same slots with default content. These typed slots allow host-owned optional content without new message identity, state ownership, reflection or mandatory feature dependencies. Actions/media implementations remain in their later slices.

Each library produces distinct Gradle module metadata/POMs for root, Android, JVM and both iOS targets. `chat.staging` writes only to `build/staging`; `dev.chat.candidate:*:0.1.0-local` are local verification coordinates, not approved public coordinates. No remote publication repository is configured. Samples/launchers apply no publication convention.

`consumers/` is a separate Gradle build with no producer project dependency, included build or convention plugins. Its core-only, custom-ui and default-material3 projects consume only staged artifact coordinates. Consumer plugin versions are explicitly pinned as an independent adoption example. The custom consumer uses foundation controls and runs the accepted public send/refusal/delivery/retry checks without Material3. `verifySelection` validates resolved runtime graphs; compile checks cover Android, iOS arm64/simulator arm64 and JVM. The source tree/build-logic is needed to *produce* artifacts, never to consume them.

## Current verification scope

Landscape/orientation, text-resizing/font-scaling and dark-mode tests are owner-deferred, with no coverage claim. Functional send/refusal/delivery/retry, keyboard opening/closing, draft retention, RTL, screen-reader semantics and module/platform evidence remain required. Public source KDoc describes the implemented ownership and callback contracts; staged source-artifact inspection belongs to release preparation.

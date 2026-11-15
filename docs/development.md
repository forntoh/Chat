# Development

Keep changes focused and preserve the optional-module boundaries. Core has no UI or services; UI owns shared behavior; Material3, actions and media remain optional. Hosts enforce access control and own data, transport and persistence. Never commit credentials or real user content.

## Quality checks

Run the same checks as CI from the repository root:

```sh
./gradlew detekt ktlintCheck
npm ci --ignore-scripts
npm run docs:build
```

Use `./gradlew ktlintFormat` to apply formatting, inspect the diff, then check again. CI never commits formatting. Reports are under `build/reports/quality`; MyST 1.13.0 writes `_build/html` without deploying it. Use Node.js 22 or newer. The CLI and its dependency closure are pinned by `package-lock.json`; `myst.yml` pins the prebuilt book theme to an immutable archive. The build checks links and fails on unresolved references using MyST’s `--check-links --strict` flags.

The `chat.quality` convention pins Detekt 1.23.8 and ktlint 1.8.0 in isolated JDK 21 processes. Detekt uses syntax analysis without type resolution, so compilation and behavior tests remain necessary. There is no baseline or ignored failure setting.

Checks cover all maintained library, sample, Android, JVM and iOS Kotlin sources/tests, consumers, build logic and Gradle Kotlin scripts. They exclude generated/local `**/build/**`, `**/.gradle/**`, `**/.kotlin/**`, `**/.git/**` and `evidence/**`, `**/node_modules/**` and `**/_build/**`. No platform or source set is omitted.

`.editorconfig` owns formatting and line length. Compose functions retain their naming and declarative-tree exceptions; complex guards are still checked. Appearance properties, named arguments, fixture values, SDK settings and test expectations retain literal numbers. Algorithm thresholds are named. Local annotations explain compatibility operations, the slot factory, manual contract fixture operations, one continuous pixel/anchor helper and Swift entry-point spelling. The configuration is in `config/quality/detekt.yml`.

## Behavioral checks

Agree on the public seam before writing a test: supply snapshots to Conversation and observe emitted events, or resolve staged artifacts in an independent consumer. Start with a failing focused check, implement the behavior, and run affected checks. Reuse unchanged evidence instead of repeating a platform matrix.

```sh
./gradlew :sample:jvmTest
./gradlew :androidApp:assembleDebug
```

Run relevant `:chat:MODULE:jvmTest` tasks when shared behavior changes. See [sample setup](sample.md) for iOS. The isolated consumer build validates local artifact graphs. Run the relevant `:chat:MODULE:publishAllPublicationsToStagingRepository` tasks locally, then run `./gradlew -p consumers -PchatRepository=/absolute/path/to/staging :combined:verifySelection`. This checks artifact graphs, not native runtime behavior.

## Review

Describe the final behavior and relevant validation briefly. Visible changes need current screenshots at 320px width and recordings for interaction or motion. Keep originals and candidate/platform/viewport provenance; report genuine failures, skips and limits. Independent code review and owner sign-off are required. Production migrations, database pushes, main merges and releases remain human-gated.

Contributions use [Apache-2.0](license.md) unless explicitly agreed otherwise. Preserve [third-party terms](third-party-licenses.md) and fixture provenance.

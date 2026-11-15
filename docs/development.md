# Development

Keep changes focused and preserve the optional-module boundaries. Core has no UI or services; UI owns shared behavior; Material3, actions and media remain optional. Hosts enforce access control and own data, transport and persistence. Never commit credentials or real user content.

## Quality checks

Run the same checks as CI from the repository root:

```sh
./gradlew detekt ktlintCheck
python3.12 -m venv .venv-docs
.venv-docs/bin/pip install -r requirements-docs.txt
.venv-docs/bin/mkdocs build --strict
```

Use `./gradlew ktlintFormat` to apply formatting, inspect the diff, then check again. CI never commits formatting. Reports are under `build/reports/quality`; MkDocs writes `site` without deploying it. Python dependencies, including transitive dependencies, are pinned in `requirements-docs.txt`.

The `chat.quality` convention pins Detekt 1.23.8 and ktlint 1.8.0 in isolated JDK 21 processes. Detekt uses syntax analysis without type resolution, so compilation and behavior tests remain necessary. There is no baseline or ignored failure setting.

Checks cover all maintained library, sample, Android, JVM and iOS Kotlin sources/tests, consumers, build logic and Gradle Kotlin scripts. They exclude generated/local `**/build/**`, `**/.gradle/**`, `**/.kotlin/**`, `**/.git/**` and `evidence/**`. No platform or source set is omitted.

`.editorconfig` owns formatting and line length. Compose functions retain their naming and declarative-tree exceptions; complex guards are still checked. Appearance properties, named arguments, fixture values, SDK settings and test expectations retain literal numbers. Algorithm thresholds are named. Local annotations explain compatibility operations, the slot factory, diagnostic host function counts, one continuous pixel/anchor helper and Swift entry-point spelling. The configuration is in `config/quality/detekt.yml`.

## Behavioral checks

Agree on the public seam before writing a test: supply snapshots to Conversation and observe emitted events, or resolve staged artifacts in an independent consumer. Start with a failing focused check, implement the behavior, and run affected checks. Reuse unchanged evidence instead of repeating a platform matrix.

```sh
./gradlew :sample:jvmTest
./gradlew :androidApp:assembleDebug
```

Run relevant `:chat:MODULE:jvmTest` tasks when shared behavior changes. See [sample setup](sample.md) for iOS and developer fixtures. For independent consumer graphs, stage the required modules as described in [getting started](getting-started.md), then run `./gradlew -p consumers -PchatRepository=/absolute/path/to/staging :combined:verifySelection`. This checks artifact graphs, not native runtime behavior.

## Review

Describe the final behavior and relevant validation briefly. Visible changes need current screenshots at 320px width and recordings for interaction or motion. Keep originals and candidate/platform/viewport provenance; report genuine failures, skips and limits. Independent code review and owner sign-off are required. Production migrations, database pushes, main merges and releases remain human-gated.

Contributions use [Apache-2.0](license.md) unless explicitly agreed otherwise. Preserve [third-party terms](third-party-licenses.md) and fixture provenance.

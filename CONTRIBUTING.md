# Contributing

Use the [sample](docs/sample.md) to explore behavior and the [integration guide](docs/integration.md) to understand state ownership before changing a public contract.

## Project structure

| Directory | Responsibility |
| --- | --- |
| `chat/core` | Plain Kotlin state and events. |
| `chat/ui` | Conversation behavior and foundation slots. |
| `chat/material3` | Material 3 presentation. |
| `chat/media`, `chat/actions` | Optional feature adapters and presentation. |
| `sample`, `androidApp`, `iosApp` | Shared demo and platform entry points. |
| `build-logic` | Shared build and quality conventions. |
| `docs` | User guides and real sample media. |

Keep service, storage and platform resource operations in the host. Optional modules must remain optional. Put dependency and plugin versions in `gradle/libs.versions.toml`, reusing its aliases in Gradle scripts.

## Check Kotlin

With the [sample toolchain](docs/sample.md#platforms-and-requirements) configured:

```sh
./gradlew detekt ktlintCheck
```

Run `./gradlew ktlintFormat` to format changes, then rerun the checks. Both tools share the repository’s EditorConfig and quality configuration. Maintained Kotlin and Gradle Kotlin scripts are checked; generated and build output are excluded. Detekt’s rule exceptions are in `config/quality/detekt.yml`.

For a shared UI change, compile the desktop sample and Android app:

```sh
./gradlew :sample:compileKotlinJvm :androidApp:assembleDebug
```

Verify relevant interactions through the public conversation seam. Document actual results and limits; do not treat a prior run as validation of new code.

## Preview the documentation

Use Node.js 22 or later. The MyST CLI and theme are pinned, and npm dependencies are locked:

```sh
npm ci --ignore-scripts
npm run docs:serve
```

Open the local address printed by MyST. Before submitting changes, build the static site and check references:

```sh
npm run docs:build
```

The output is in `_build/html/`. The Quality workflow runs the same strict documentation build and Kotlin checks with read-only repository permissions.

Write for someone integrating the library: show the result, explain the next step and use the actual public API. Keep the sidebar short. Use real implementation screenshots and recordings for visible changes, with readable alt text and appropriately sized images. Keep build receipts and delivery evidence out of the user guides.

## Propose a change

Keep the change focused and explain the behavior it improves. Include relevant validation and current screenshots for UI changes. Do not commit local SDK paths, credentials or generated build output. Publication, deployment and releases are separate maintainer actions.

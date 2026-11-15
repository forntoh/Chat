# Dependencies and licenses

Chat's production modules keep a small set of direct dependencies. Actions and media remain independently selectable, and neither requires Material3.

| Module | Direct production dependencies |
| --- | --- |
| Core | Kotlin stdlib 2.4.0 |
| UI | Core; Compose runtime/foundation 1.11.1 |
| Actions | UI and core |
| Media | UI and core |
| Material3 | UI/core; Compose Material3 1.11.0-alpha07; Compose Icons Tabler Outline 2.2.1 |

These are direct dependencies, not an exhaustive inventory of every platform-specific transitive artifact. The build selects JVM, Android and arm64 iOS variants. Inspect the actual resolved graph and bundled notices when preparing a distribution.

Original library code and the sample's mountain illustration use the repository's [Apache-2.0 license](license.md). Kotlin, AndroidX and Compose dependencies declare Apache-2.0 in their upstream metadata. Preserve their applicable notices when redistributing artifacts.

The optional Material3 module uses Compose Icons Tabler Outline and its Tabler dependency, whose published metadata declares MIT. The pinned upstream source includes the wrapper and icon notices, retained here:

- [Compose Icons MIT text](third-party/compose-icons-MIT.txt), from the [versioned upstream license](https://github.com/composablehorizons/compose-icons/blob/81bcbb50a91ce57888c781e19e81207643df39fa/LICENSE).
- [Tabler MIT text](third-party/tabler-MIT.txt), from the [versioned module notice](https://github.com/composablehorizons/compose-icons/blob/81bcbb50a91ce57888c781e19e81207643df39fa/icons-tabler-outline-cmp/LICENSE.md).

These terms apply to the dependencies rather than replacing the license of original Chat code. Host fonts, images, native payloads and platform SDKs require their own distribution assessment.

Build tools are separate from the library's production graph: Gradle 9.4.1, Kotlin/compiler 2.4.0, Compose plugin 1.11.1 and AGP 9.1.0. Tests select Kotlin test and Compose UI test/JUnit4. Quality tooling runs Detekt 1.23.8 and ktlint 1.8.0 in isolated processes. Documentation uses MyST 1.13.0, pinned with `package-lock.json`.

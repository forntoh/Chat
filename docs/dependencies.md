# Candidate dependency inventory

This inventory covers production library dependencies resolved from the existing
`dev.forntoh.chat.candidate:*:0.1.0-local` stage for JVM runtime, Android compile and arm64 iOS
device/simulator compile graphs. The combined actions+media graph plus default Material3 graph
cover all five Chat modules. It lists root metadata modules and selected platform modules separately;
these are coordinates, not 210 separately bundled libraries. No dependency was added or upgraded.

## Direct production dependencies

| Chat module | Direct dependencies |
| --- | --- |
| core | Kotlin stdlib 2.4.0 (plugin-added) |
| ui | core; Compose runtime/foundation 1.11.1; Kotlin stdlib |
| actions | ui; Kotlin stdlib |
| media | ui; Kotlin stdlib |
| material3 | ui; Compose Material3 1.11.0-alpha07; `com.composables:icons-tabler-outline-cmp:2.2.1`; Kotlin stdlib |

See [the full resolved inventory](dependency-licenses.tsv): 210 external root/platform coordinates,
with exact selected version, license name/URL declared in that version's cached POM, and POM SHA-256.
Every listed coordinate has a POM license declaration. A declaration is evidence of upstream metadata,
not a certification of every bundled source or native component. The staged Chat artifacts keep their
existing dependency metadata; this pass does not change POM license fields or public coordinates.

To reproduce the resolved graph inventory without running tests:

```sh
./gradlew --offline -p consumers -PchatRepository=/absolute/path/to/staging \
  -I "$PWD/consumers/inventory.init.gradle" -PinventoryOutput=/absolute/path/to/dependency-graphs.json dependencyInventory
```

The resulting JSON records every selected coordinate and actual artifact path by graph. Cached POMs
live under `$GRADLE_USER_HOME/caches/modules-2/files-2.1/<group>/<artifact>/<version>/*/*.pom`
(default Gradle user home: `~/.gradle`). POM digests in the TSV bind the declarations to the evidence.
The release evidence bundle retains those exact POM bytes, resolved artifact identities, graph log,
and embedded-license entry paths/digests. Offline resolution requires the dependencies already cached.

## Substantiated third-party material

AndroidX, Compose, Kotlin, kotlinx, Skiko, JBR API and JSpecify coordinates in this inventory declare
Apache-2.0 in their POMs. Preserve upstream notices/licenses when redistributing their artifacts.
The resolved JAR/AAR/KLIB inspection found embedded license texts and no separately named NOTICE file;
this is an archive-entry inspection, not a source-level native-library audit. Original Chat library code
uses the root [Apache-2.0 text](../LICENSE), copied from the
[Apache Software Foundation](https://www.apache.org/licenses/LICENSE-2.0.txt).

The optional Material3 dependency selects Compose Icons Tabler Outline 2.2.1, which transitively selects
Compose Icons Tabler 2.2.1. Their published POMs declare MIT. The upstream `2.2.1` tag resolves to commit
`81bcbb50a91ce57888c781e19e81207643df39fa`; the corresponding source includes both the wrapper license
and Tabler's copyright/permission notice. Exact copies are retained here:

- [Compose Icons MIT text](third-party/compose-icons-MIT.txt), from
  [versioned upstream LICENSE](https://github.com/composablehorizons/compose-icons/blob/81bcbb50a91ce57888c781e19e81207643df39fa/LICENSE).
- [Tabler MIT text](third-party/tabler-MIT.txt), from
  [versioned module LICENSE.md](https://github.com/composablehorizons/compose-icons/blob/81bcbb50a91ce57888c781e19e81207643df39fa/icons-tabler-outline-cmp/LICENSE.md).

These texts apply to the optional dependencies, not to original Chat code. Keep them with distributions
that include that material. The owner-removed Compose Material Icons notice is not restored: this
candidate uses Tabler, and no Material Icons dependency appears in the resolved production graphs.
Material3 and its ripple implementation are different components and remain listed in the inventory.

## Inventory boundaries

Build-only tools are Gradle 9.4.1, Kotlin/compiler/Compose compiler 2.4.0, Compose Gradle plugin 1.11.1,
AGP 9.1.0, JDK 21 compilation and JDK 25 producer daemon. Test-only dependencies include Kotlin test
2.4.0 and Compose UI test/JUnit4 1.11.1. Samples additionally select desktop runtime/native Skiko support.
These build/test/sample tool closures are not library production dependency graphs and are not included
in the 210-coordinate TSV. The source settings/catalog and consumer builds pin their direct versions.

POM declarations alone do not establish all obligations for a future host distribution (especially
native Skia/Skiko payloads, JDKs, host-provided fonts/images and platform SDKs). Reinventory the actual
release packaging and preserve its own notices before public distribution. No public publication,
license clearance for host content, or exhaustive embedded/native license audit is claimed here.

## Local documentation-bearing sources

To build source archives containing current KDoc plus the candidate guides/licenses without
compiling binaries or publishing anything:

```sh
./gradlew --offline -I docs/source-artifacts.init.gradle releaseSourceDocs
```

Archives are written under `build/release-source-docs/<module>/<task>/`. The init script adds
`META-INF/chat/` documentation to source archive tasks only. It does not replace the existing Maven
stage, rewrite its metadata/checksums or assert a new binary candidate. Keep these separately identified
archives alongside the unchanged staged binaries until an authorized release packaging step.

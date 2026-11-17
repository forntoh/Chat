# Prepare an unsigned 0.0.1 candidate locally

This procedure creates local artifacts for review. It does not sign, tag, deploy,
run hosted workflows, access credentials, or publish remotely. The selected
coordinates are `dev.forntoh.chat:chat-{core,ui,material3,actions,media}:0.0.1`,
with the existing Android, JVM, iOS arm64 and iOS simulator arm64 publications.
The shared version and consumer aliases remain in `gradle/libs.versions.toml`.

## Inputs and prerequisites

Use a clean checkout of the exact reviewed full commit SHA, Python 3.9+ and the
existing Gradle wrapper. Set `JAVA_HOME` to an installed JDK if the shell cannot
find Java; the repository requests Gradle JDK 25 and Kotlin toolchain 21. Android
SDK 37 and a macOS host with the existing Kotlin/Native and Xcode prerequisites
are needed for all configured targets. Existing dependency caches may be reused;
normal Gradle dependency downloads are possible. No publishing credentials are
required. Coordinate an exclusive source/build writer interval first.

From the repository root, choose a new absolute directory outside the checkout
(or under its ignored `build/` directory):

```sh
python3 scripts/package-local.py \
  --commit FULL_REVIEWED_COMMIT_SHA \
  --output /absolute/new/pat273-candidate
```

The output directory must not exist. The driver records commit/tree and exact
Gradle command in `candidate.json`, saves `gradle.log`, publishes all five modules
to that directory's `repository/`, and compiles the existing sample for JVM,
Android, iOS arm64 and iOS simulator arm64. Publishing also builds current library
artifacts for those targets. It preserves historical `build/staging` contents.
The convention rejects an already existing coordinate/version directory even
when its publication task is invoked directly.

## Review evidence

Success means `candidate.json` says `LOCAL_VERIFIED`. Missing artifacts,
compilation failure, changed source, wrong coordinates or dependency closures
leave the candidate incomplete and exit unsuccessfully. `inventory.json` lists
all 25 POMs/Gradle metadata publications, variants, dependencies, sources and
SHA-256 checksums of every repository file. `SHA256SUMS` can be checked from the
candidate directory with `shasum -a 256 -c SHA256SUMS`. Re-run read-only validation:

```sh
python3 scripts/verify_packaging.py /absolute/new/pat273-candidate/repository
```

The validator checks metadata-linked artifact sizes/hashes, local variant
redirects and coordinated internal dependencies. Five selection closures must be:

| Selection | Required chat modules |
| --- | --- |
| Core only | core |
| Custom UI | core, ui |
| Default Material3 | core, ui, material3 |
| Actions only | core, ui, actions |
| Media only | core, ui, media |

Core/UI/actions/media selections must not pull Material3 or Tabler icons through
any declared POM/Gradle dependency. These are staged metadata assertions and
current project compilation. Fresh external-consumer resolution/compilation is
**UNVERIFIED**, per Jonas's approved seam decision; it remains a publication
readiness gap. No removed consumer app or test suite is recreated.

Apache-2.0 POM metadata and source/binary JAR/AAR attribution are configured by the
publishing convention. Source JARs retain Kotlin source and its KDoc. Root
`LICENSE`/`NOTICE` also accompany the candidate for native outputs. Rendered
javadoc/KDoc artifacts are absent; the eventual repository's documentation,
identity/developer, signing and metadata requirements must be confirmed before
publication. This slice does not select or configure a remote publishing service.

## Failure and immutable versions

Keep failed candidates and their logs as evidence. Never repair an incomplete
candidate in place or treat a partial inventory as success. Fix the cause, commit
the change and rerun with its exact commit into a fresh directory. An existing
output path fails before Gradle runs. Local candidate success never establishes
public availability or repository acceptance.

Before any later public publication, the authorized operator must independently
verify ownership/access to the `dev.forntoh.chat` namespace, the chosen Maven
repository, least-privilege upload credentials, signing identity and required
public key, repository-specific POM/documentation requirements, and the exact
approved commit/checksums. Git remote configuration alone proves none of these;
namespace/signing access has not been verified here. Do not put secrets in
commands, tickets, documents or logs. Credential installation, signing, tags,
push/PR, release/publication, merge and deployment require their separate gates.

If a later authorized remote upload fails partway, inspect the provider's actual
state before retrying. A provider-owned unpromoted staging transaction may be
resumed or dropped only through its supported recovery flow and separate
authority. Never overwrite an immutable released version. If any version has
become public, reconcile the published checksums and use a new coordinated
version for changed bytes; a missing module is still a failed release. No remote
retry/recovery command is executed by this local workflow.

Oliver reviews the exact completed candidate first; Zainab assesses release
preparation afterward, and final owner acceptance remains required. Existing
platform/device deferrals and Pages protection/deployment prerequisites remain
unverified. This nonvisual slice adds no UI evidence or platform campaign.

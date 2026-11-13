# Contributing

Discuss a focused issue before changing public state/event contracts or dependency boundaries.
Keep core free of UI/services; ui owns shared behavior; Material3, actions and media stay optional.
The host owns authorization, data, transport and persistence. Never commit credentials or real user data.

Use the pinned toolchains and platform setup in [README](README.md) and [support](docs/support.md).
Use separate Git worktrees for concurrent independent changes. Keep changes small and include meaningful
public KDoc, migration notes and a changelog entry when public behavior changes.

Follow test-driven development at the agreed public seam: host snapshots into `Conversation`, captured
public events out, or published artifacts into an independent consumer. Add a failing focused check,
then the smallest implementation. Reuse matching prior evidence and run affected checks; avoid an
exhaustive module/runtime matrix. The README lists build commands. For the combined dependency seam:

```sh
./gradlew -p consumers -PchatRepository=/absolute/path/to/staging :combined:verifySelection
```

This resolves JVM runtime, Android compile and both iOS compile artifact graphs; it is not a native
runtime test. Stage artifacts locally first as described in README. Do not substitute producer project
or included-build dependencies for consumer evidence.

Describe the problem, resulting behavior, exact candidate/toolchain and commands/results in a concise
PR. For visible changes attach current implementation screenshots at 320px width, plus a recording
when needed to show interaction/navigation. Preserve evidence of genuine failures/skips. Obtain
independent code review and owner sign-off. Production migrations/database pushes, main merges and
releases require explicit human approval; no push, tag, signing or publication is implied by a local build.

Original library code is under [Apache-2.0](LICENSE). Intentionally submitted contributions are under
the same terms unless explicitly stated otherwise, consistent with section 5. Preserve applicable
third-party terms and document provenance; do not infer dependency licenses from this project's license.
See [dependency inventory](docs/dependencies.md).

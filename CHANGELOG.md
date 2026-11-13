# Changelog

## Unreleased — initial local candidate

No public release has been published. `com.forntoh.chat.candidate:*:0.1.0-local` are local verification coordinates.

- Library namespace/root package is `com.forntoh.chat`; update prior `dev.chat.*` library imports
  and local staging group to `com.forntoh.chat.candidate`. Sample/consumer packages are unchanged.
- Host-owned conversation state/events and shared Compose UI for Android, arm64 iOS and desktop JVM.
- Stable message identity, guarded send, host delivery/retry and draft acceptance/retention.
- Host-driven earlier history, reader anchoring and loading/error/retry/exhaustion states.
- Custom renderers, strings, theme and scaffold; optional Material3 presentation.
- Independently selectable message actions and host-supplied image integration.
- Public KDoc, independent staged consumers and focused public-contract checks.
- Candidate support/versioning guidance, contribution guide, Apache-2.0 text for original library
  code and evidence-based dependency inventory.

Transport, authentication, persistence, upload and viewer services remain host responsibilities.
See [support and versioning](docs/support.md) for configured floors and verification limits,
and [customization](docs/customization.md) for prerelease API migration guidance.

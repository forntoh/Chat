# Changelog

## Unreleased — initial local candidate

No public release has been published. `dev.forntoh.chat.candidate:*:0.1.0-local` are local verification coordinates.

- Follow system light/dark appearance by default, retaining explicit host color-scheme overrides. The custom sample palette has both appearances.
- Keep the input and keyboard focused through Send, dismiss focus on timeline taps, and focus the input on explicit Reply. Pending edits remain guarded; custom fields can observe ComposerContent.focusRequest.
- Cap the entire return-to-latest animation at 360ms, using virtualized positions for long histories so unseen rows do not block each frame.
- Expand the sample simulator with the remaining conversation scenarios, startup reply examples, and a controllable image-state gallery.
- iOS floating composer bottom and side gutters match at 26pt to follow the screen corner curve; the explicit keyboard gap remains.
  Reply context omits the redundant label and centers dismiss beside the preview, aligned with Send.
- The default navigation header is transparent with timeline content scrolling behind it.
  Shared viewport padding keeps initial content, reply centering and bottom safe areas clear;
  system bottom spacing replaces the extra decorative composer margin when the keyboard is
  closed, while an explicit 8dp gap remains above the visible keyboard.
- The composer floats over the timeline with scrollable bottom clearance. Material3 keeps Send
  inside its borderless rounded field and centers placeholder/text vertically. Optional actions use
  an icon-only reply/edit dismiss control beside the preview. Header/leading slots accept host content. History
  scrolls behind the surrounding space; multiline growth preserves latest/history reader intent.
- Return-to-latest uses eased scrolling with a fixed duration cap. Sending from history
  returns to latest; keyboard/composer viewport changes retain bottom-follow, including oversized final messages.
- Replies can render an inset, slot-customizable sender/excerpt preview inside the message bubble.
  Tapping a loaded quote smoothly centers its stable target and briefly highlights it.
  Navigation follows the stable target across history prepends and cancels if it disappears.
  Custom row slots receive `MessagePresentation` for their own animation; unavailable targets remain inert.
- History feedback scrolls with the timeline. Arrivals follow readers already at the bottom;
  readers in history retain their anchor. Latest floats at the lower right as a small chevron,
  animates in/out and smoothly returns to the newest message without taking timeline height.
- Optional actions add long-press bubble focus with a up to 5% sender-edge anchored lift (capped to keep wide custom content within safe bounds), 40% black scrim, 8dp action gaps, bounded vertical
  menu and independent consumer content above/below. Tap leaves actions closed; dismissal and
  stale-target guards preserve host state. The live bubble layer moves continuously to and from
  the action layout, with no duplicate left in the timeline. Native travel starts after popup
  placement; bubbles move only when action space is needed, and menu shadows have scroll-viewport
  clearance. The iOS scrim covers the full window; action controls respect safe areas. Motion can be disabled.
- Material3 bubbles keep time/status beside the final text line when measured space permits,
  wrapping only to avoid overlap. Bubbles stay content-sized with bounded gutters. The optional
  `MessageMetadata` helper places sender headings outside and time inside the bubble through
  the existing metadata slot; plain and empty overrides remain supported.
- Material3 exposes a bubble-only modifier for host touch feedback and actions, clipped to
  grouped corners. The sample excludes sender headings, avatars, gutters and Retry from message
  action activation; its custom surface retains equivalent handling.
- Library namespace/root package is `dev.forntoh.chat`; update prior `dev.chat.*` library imports
  and local staging group to `dev.forntoh.chat.candidate`. Sample/consumer packages are unchanged.
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

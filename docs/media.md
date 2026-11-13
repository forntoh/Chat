# Host-supplied images

Select `dev.chat.candidate:chat-media:0.1.0-local`. Media depends on core/ui and Compose
foundation; it adds no actions, Material3, picker, permissions, loader, network or upload SDK.
Core descriptors have no Compose or platform handles. Existing text-only source callers keep
empty attachment defaults. `consumers/media-only` is an independent staged-artifact integration.

```kotlin
val media = rememberMedia(hostConversation, hostMedia, ::handleMediaRequest, ::handleEvent)
val base = LocalConversationSlots.current
Conversation(hostConversation, media.onConversationEvent,
    slots = base.copy(
        message = { row, outgoing, retry ->
            Column {
                base.message(row, outgoing, retry)
                MediaAttachments(media, row, renderer = { target -> HostImage(target) })
            }
        },
        composer = { content ->
            Column {
                MediaDraft(media, content, renderer = { target -> HostImage(target) })
                base.composer(media.composer(content))
            }
        },
    ))
```

`HostImage` is a consumer renderer, not a library loader. It draws only content the host has
selected/authorized. Report loading, missing content or render failure as `ImageState` against
the exact `MediaTarget`; do not throw from composition. No renderer or unsupported content
produces visible and accessible fallback. Mount attachment controls outside cleared/merged
text-row semantics. Ready message images and the View control emit a host viewer request.

`Attachment` supplies a stable host ID, MIME type, opaque content reference and optional localized
description. IDs are unique within their draft or message and scoped by conversation. Preserve
them on upload retry. A changed resource must get a changed descriptor. Null/blank references
show unavailable content; valid images permit image-only send. Text plus images uses the same
`SendDraft` with exact text, revision and a copied attachment list. Host snapshots/lists must be
immutable. The optional actions adapter preserves this attachment snapshot in quoted-send requests. Core/ui do not resolve tokens or infer authorization.

The shared Conversation owns one synchronous draft latch for send **and** attachment mutations.
Always forward its `ComposerContent` and `onDraftAction` through custom adapters. Do not create
another Conversation or a parallel send control with its own state. Calling Select/Remove latches
the draft before the host callback: all default/custom sends and attachment mutation callbacks
are suppressed until the host publishes a fresh draft revision. `Draft.submitting` also locks
controls across remounts. Advance `Draft.revision` after every accepted/refused selection or
removal, including cancellation/denial with unchanged content, and advance `MediaState.revision`
after resolving each media intent. Host selection sets `MediaState.selecting` while in flight;
this prevents sends even across unmounts. Cancellation/denial retains text and existing images;
removal changes only the selected content. Resolve local handler exceptions explicitly through
new revisions. No operation auto-sends/retries on recomposition.

`MediaTarget` carries the conversation, exact attachment descriptor and full owning draft or
message snapshot. `MediaRequest` adds the media revision. Select has no attachment; Remove has
a draft attachment; Retry/View refer to message attachments. Requests revalidate current
snapshots; mutations/retries latch per media revision while viewer activation can repeat. Retry
is offered only for Failed upload state. Upload/loading presentation is independent of message
delivery and must never manufacture Sent. Host acceptance clears a draft only through a new host
snapshot; upload failure can follow acceptance and must retain the same logical row/attachment.

The host must validate authorization and these identities again both on request and on completion.
It owns service deduplication and invalidating async generations across navigation, draft revision
replacement, removal and message replacement. Increment retained media revisions on navigation,
including returning to the same conversation; discard obsolete image-state keys. Equality checks
cannot distinguish different resources if a host reuses an identical descriptor/session. Selection,
upload and render results must never be applied by list position or attachment ID alone.

`MediaTheme` supplies `MediaSlots`; explicit per-surface slots take precedence. Override typed
control/feedback composables and the description fallback to localize or style content. Selection
errors are already localized host strings. Default image descriptions use host text, falling back
to “Image attachment”. Custom controls must honor enabled, provide focus/button semantics and
invoke the guarded callback only on user activation. Theme/renderer changes do not reset draft
or submission state. No grouped-label model is added.

The shared sample's **Image sample** opens a foundation-only media journey on all three configured
targets. Its original bundled vector is a compiled resource; manual controls simulate selection,
cancellation, denial, host acceptance/refusal, 25% upload, failure and stable-identity retry, plus a
host viewer. Switching media rooms retains drafts and invalidates pending fake services; interrupted
uploads become retryable failures. There is no real OS permission flow, upload, remote downloading,
persistence or production authorization. Native target compilation is separate from runtime proof.

Focused tests mount the public Conversation seam for image-only/text-image pending submission,
selection/removal/cancel/deny, stale targets/results, progress/failure/retry, viewer and fallback.
Existing shared UI/sample regressions remain. Native builds and the outside consumer check changed
resources and artifact boundaries. Screen-reader campaigns, RTL/locale, orientation, font scaling,
dark mode, exhaustive keyboard/device/configuration and every-module-combination runtime suites
remain owner-deferred; they are not claimed as covered.

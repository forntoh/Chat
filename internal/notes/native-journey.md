# Reproduce the native text journey

Run the same sample on each target. The service is in memory and delivery completes only when a host control is activated.

1. Enter only spaces/newlines: Send stays disabled. Replace with multiline text and an emoji: Send becomes enabled.
2. Activate Send once. Send and composer become disabled; Accept and Keep draft appear. Repeated activation must not add another message.
3. Choose Keep draft. Exact text remains, Send becomes enabled, and no timeline row is added.
4. Send again and choose Accept. Composer clears; one outgoing row appears as Sending.
5. Choose Fail delivery. That row becomes Failed with Retry. Activate Retry: the row becomes Sending. Choose Deliver: that row becomes Sent. The timeline contains one copy of the message.
6. Enter a new draft, leave the conversation, then open it. Text remains. The composer remains reachable and the retained host draft survives navigation.
7. Open the native keyboard, enter several lines, and confirm Send and native navigation remain reachable. Verify long messages scroll in the timeline without displacing the composer.

Current early-stage validation is limited to affected builds, one basic launch/text-send smoke journey on Android, iOS and desktop, and a small shared public-contract suite for send suppression, failure/retry identity and host-owned draft state. Reuse valid candidate evidence; do not rerun the complete matrix without a relevant change or failure. The steps above describe product behavior, not a mandatory exhaustive campaign.

Automated entry points include `:chat:ui:jvmTest`, `:chat:material3:jvmTest`, `:sample:jvmTest`, `:androidApp:connectedDebugAndroidTest`, and the generated `ChatSampleUITests` Xcode target. Record exact candidate, actual evidence and limitations in the handoff.

Owner-deferred: dedicated TalkBack/VoiceOver and other screen-reader campaigns, RTL/locale matrices, landscape/orientation, text-resizing/font-scaling, dark mode, exhaustive keyboard/navigation and broad device/configuration coverage. These do not gate this slice. Existing semantics and localization support remain available. No deferred coverage is claimed.

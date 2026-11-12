# Reproduce the native text journey

Run the same sample on each target. The service is in memory and delivery completes only when a host control is activated.

1. Enter only spaces/newlines: Send stays disabled. Replace with multiline text and an emoji: Send becomes enabled.
2. Activate Send once. Send and composer become disabled; Accept and Keep draft appear. Repeated activation must not add another message.
3. Choose Keep draft. Exact text remains, Send becomes enabled, and no timeline row is added.
4. Send again and choose Accept. Composer clears; one outgoing row appears as Sending.
5. Choose Fail delivery. That row becomes Failed with Retry. Activate Retry: the row becomes Sending. Choose Deliver: that row becomes Sent. The timeline contains one copy of the message.
6. Enter a new draft, leave the conversation, then open it. Text remains. The composer remains reachable and the retained host draft survives navigation.
7. Open the native keyboard, enter several lines, and confirm Send and native navigation remain reachable. Verify long messages scroll in the timeline without displacing the composer.

Automated entry points: `:chat:material3:jvmTest`, `:sample:jvmTest`, `:androidApp:connectedDebugAndroidTest`, and the generated `ChatSampleUITests` Xcode target. Automated semantics tests do not substitute for screen-reader or physical-device validation. Record actual successes, failures and skips in the candidate handoff.

Owner-deferred at this stage: landscape/orientation, text-resizing/font-scaling and dark-mode tests. Do not execute these or report them as passed. Keyboard-driven viewport adjustment remains in scope.

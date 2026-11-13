# Default Material3 presentation

Reference inspected at android/compose-samples commit
[`25631e8`](https://github.com/android/compose-samples/tree/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetchat):
[Conversation.kt](https://github.com/android/compose-samples/blob/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/Conversation.kt),
[UserInput.kt](https://github.com/android/compose-samples/blob/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/UserInput.kt),
[Typography.kt](https://github.com/android/compose-samples/blob/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetchat/app/src/main/java/com/example/compose/jetchat/theme/Typography.kt),
[Themes.kt](https://github.com/android/compose-samples/blob/25631e8f544a9a1e3f01e296eaf1b4c28428196c/Jetchat/app/src/main/java/com/example/compose/jetchat/theme/Themes.kt).

| Reference treatment | Default artifact/result | Intentional difference |
| --- | --- | --- |
| Contiguous author groups, 42dp avatars, compact sender/time | Shared row context, avatar gutter and host avatar slot with initials; metadata once per group; 4/8dp gaps | Initials instead of upstream portraits; no profile navigation or implicit fetch |
| Owner-supplied Jetchat screenshot: shared left avatar gutter, muted incoming and high-contrast outgoing groups | Default artifact aligns both directions to the same left gutter; incoming surfaceContainerHighest/onSurface and outgoing primary/onPrimary; grouped 4/20dp top-start corners | This supplied visual target takes precedence over the newer pinned source’s alternating layout and lighter outgoing role; host MaterialTheme still owns the palette |
| Body 16/24sp; distinct author/time; centered channel title | Default bodyLarge, semibold 14sp author within compact metadata; sample header 20/24sp in CenterAlignedTopAppBar | System fonts instead of upstream Karla/Montserrat assets; combined sender/time label instead of two font families |
| Integrated 32dp rounded input card, focused/idle hierarchy | Rounded shared composer, focus outline, exact multiline input, icon Send; media host adds guarded image action inside that field | No recording, glow, emoji, video or unsupported controls |
| Subordinate day/header and jump control | Scaffold owns sample top bar and safe insets; content consumes its padding; quiet history feedback and tonal return-to-latest control | No date dividers: current host contract has formatted time, not explicit dates; no invented calendar data |
| Contextual supported interactions | Sample message context menus, quote/edit/error and explicit delete confirmation; bundled image chooser, preview, upload feedback and viewer | Only implemented actions/media; fake completion and failure scenarios are in toolbar overflow |

`chat-material3` provides presentation around the shared `chat-ui` Conversation. It has no
mandatory actions/media dependency. A consumer can wrap `Conversation` in `JetchatTheme` without
writing its own row or composer. The independent `consumers/default-material3` build consumes
local staged artifacts and captures that exact integration. Source compatibility is retained
for existing shared renderer calls; the unshipped candidate makes no binary compatibility promise.

The owner subsequently supplied [this Jetchat screenshot](https://github.com/android/compose-samples/raw/main/Jetchat/screenshots/screenshots.png)
as the visual reference. Its central conversation panel informs the current left gutter, contrast
and centered app bar. Drawer/profile screens and unsupported composer actions remain outside this slice.
Both sample screens use Material3 Scaffold; the app bar owns its top safe inset, while content
applies and consumes Scaffold padding before the shared Conversation handles IME padding. Media
navigation is inside the same app-bar hierarchy rather than a second outer header.

The sample opens on a believable conversation and completes in-memory fake sends automatically.
Tap or long-press a message to open supported actions. Toolbar overflow contains demo fixtures,
theme switches and Manual service for acceptance/refusal/failure scenarios. Bundled-image selection
is a sample chooser, not an OS picker or real permission/upload integration. No persistence,
authentication, real transport or production access control is claimed.

Implementation captures come from the desktop Compose shared renderer. They demonstrate changed
surfaces at 360dp and a representative 320dp narrow layout, including ordinary typing/sending and
relevant action/media states; they are not native device captures. Android assembly and iOS
compilation are separate build evidence. Previously deferred platform/configuration campaigns
remain deferred. No screenshot regression framework was added.

The visual reference is the Android Open Source Project's Apache-2.0-licensed Jetchat sample.
This implementation uses original Compose Multiplatform code; no upstream source files, portraits,
fonts, branding or other assets were copied. It follows the supported visual treatments rather
than claiming parity with the complete Jetchat application.

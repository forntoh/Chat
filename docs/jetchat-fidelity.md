# Reference chat design

The owner's five-second attachment on PAT-254 replaces the previous Jetchat target.
The default artifact, sample and independent staged-artifact consumer share the same palette,
renderers and optional scaffold. Passing behavior tests alone does not establish visual fidelity.

| Supplied animation | Default artifact | Intentional difference |
| --- | --- | --- |
| White screen, blue outgoing and gray incoming pills | ChatTheme owns portable blue/white/gray Material3 roles; outgoing right, incoming left; constrained rounded pills | Host colors and typography remain customizable |
| Small incoming avatar at end of contiguous group | Existing row context selects group end; incoming gutter and host avatar slot with initials fallback | No copied portrait or implicit image fetch; no outgoing avatar |
| Small times inside bubbles | Host metadata subslot flows after body and wraps below long text; full ordered sender/text/time/delivery semantics per message | No invented date separators or read receipts; pending/failure words and Retry remain explicit |
| Left identity header and back action | Optional ChatScaffold supplies identity layout, safe inset consumption and customizable chrome | Hosts own title/avatar/navigation; no profile screen, inert buttons or copied device status bar |
| Rounded gray field and separate blue circular action | Guarded exact multiline field and enabled/disabled circular Tabler Send | Send replaces unsupported microphone; only the media host adds a supported attachment action |
| Keyboard and message sequence | Single shared Conversation keeps draft, send and scroll behavior; focused shared-renderer typing/send sequence evidence | Desktop Compose captures show shared presentation, not native keyboard/device runtime proof |

Both sample screens use ChatScaffold. The sample opens on a believable multi-participant
conversation, completes fake sends automatically and offers supported quote/edit/delete actions
contextually. Fixture/theme/failure controls stay in toolbar overflow. The image sample preserves
host-owned direction-aligned attachments, bundled image selection, draft/upload errors and viewer.
There is no server, persistence, OS picker or real upload service.

Default icons come from the owner-requested
[Compose Icons Tabler outline library](https://github.com/composablehorizons/compose-icons),
`com.composables:icons-tabler-outline-cmp:2.2.1` (MIT), referenced in the version catalog.
ChatIcons only delegates ImageVector properties; no hand-copied SVG paths remain.
The dependency is confined to chat-material3; core and foundation-only integrations remain independent.

See [customization](customization.md) for the minimal scaffold integration and overrides.
Evidence uses actual desktop Compose output at ordinary and narrow sizes, downscaled to 320px.
Android assembly and iOS compilation are separate checks. The previously deferred native and
configuration campaigns remain deferred; no new screenshot regression framework is introduced.

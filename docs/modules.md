# Select modules

Start with the smallest layer that fits your host. Optional modules remain independent, including when selected together.

| Module | What it provides | Chat dependencies |
| --- | --- | --- |
| `chat-core` | Messages, participants, drafts, attachments and events; no Compose | None |
| `chat-ui` | Shared timeline, guarded composer, history and foundation slots | Core |
| `chat-material3` | Default renderers, palette, icons and scaffold | UI and core |
| `chat-actions` | Reply, edit, delete and explicit host clipboard actions | UI and core |
| `chat-media` | Guarded image drafts, attachment presentation and host viewer intents | UI and core |

For a custom foundation UI, select `chat-ui` and provide slots through `ConversationTheme`. For the reference presentation, select `chat-material3` and use `ChatTheme`. Both render the same `Conversation`; neither owns another draft or service connection.

Images and actions do not pull in each other or Material3. Inside the source checkout, a host can select both:

```kotlin
commonMain.dependencies {
    implementation(project(":chat:actions"))
    implementation(project(":chat:media"))
}
```

For staged artifact dependencies, use the matching `chat-*` artifact names under `dev.forntoh.chat.candidate` at `0.1.0-local`, as described in [getting started](getting-started.md). All five declare Android, JVM and arm64 iOS targets.

Transport, authentication, persistence, upload storage, permissions and resource loading belong to the host. Module selection alone does not provide those services. See the [dependency and license inventory](third-party-licenses.md) for the production graph.

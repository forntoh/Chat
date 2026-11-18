---
title: A conversation worth opening
description: Build a conversation that feels at home in your app, with Compose Multiplatform.
site:
  hide_outline: true
---

::::{grid} 1 1 2 2
:class: chat-hero

:::{grid-item}
**COMPOSE MULTIPLATFORM · ANDROID / iOS / DESKTOP**

Build chat into your app with a ready-made timeline, composer and Material 3 theme. Bring your own messages, service and visual identity.

Start with a complete screen. Add images, swap the palette or turn a message into a product card — all in the same conversation.

**[Build your first conversation →](getting-started.md)**

[Try the offline sample](sample.md)
:::

:::{grid-item}
```{figure} assets/custom-card.png
:width: 280px
:alt: The Scout chat showing text, an image and a custom lakeside trail recommendation

Real UI from the Scout sample.
```
:::
::::

## Start with a screen. Make it your own.

Chat handles grouping, delivery feedback and scrolling while your application stays in charge of data. Use the Material 3 components or bring your own design system through rendering slots.

::::{grid} 1 1 2 2

:::{card} Your first conversation
:link: getting-started.md

Add Chat and build a working screen with typing and sending.
:::

:::{card} Connect your app
:link: integration.md

Choose modules and connect your state holder, API and persistence.
:::

:::{card} Make it yours
:link: customization.md

Set your palette, render custom message bodies and adapt the composer.
:::

:::{card} Add features
:link: features.md

Integrate images, message actions, earlier history and motion preferences.
:::
::::

## See it in motion

```{figure} assets/bot-chat.gif
:width: 280px
:alt: Scout sending and receiving text, images and a custom trail recommendation

The offline sample, using the public components. [Run it yourself](sample.md).
```

## Built for shared Compose apps

The library targets Android, arm64 iOS and desktop JVM. It exposes Kotlin snapshots and callbacks, so your existing state holder, networking client and persistence layer can remain in charge. Web targets are not included.

The [sample guide](sample.md#platforms-and-requirements) lists platform requirements. Contributor setup and quality commands live in [Contributing](../CONTRIBUTING.md).

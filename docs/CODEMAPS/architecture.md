# ReplayGIF Architecture

<!-- Generated: 2026-03-09 | Files scanned: 127 | Token estimate: ~600 -->

## Overview

Paper plugin (api-version 1.18) that captures world snapshots around players, triggers on events (death, API, webhook), and renders animated GIFs to filesystem or webhooks.

## System Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TRIGGER SOURCES                              │
├─────────────┬─────────────┬──────────────┬──────────────────────────┤
│ DeathListener│DynamicListen│ApiTrigger   │ WebhookInboundServer     │
│ (player_death)│(triggers.yml)│(Event/API) │ POST /trigger            │
└──────┬──────┴──────┬──────┴──────┬───────┴────────────┬─────────────┘
       │             │             │                    │
       └─────────────┴─────────────┴────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ TriggerHandler   │  ← validates buffer, enqueues async job
                    └────────┬─────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│SnapshotBuffer│     │IsometricRender│     │OutputProfileReg  │
│ .slice()     │ ──► │ WorldSnapshot │ ──► │ profile → targets│
└──────────────┘     │ → BufferedImg │     └────────┬─────────┘
                     └──────┬───────┘              │
                            │                       ▼
                            ▼              ┌───────────────┐
                     ┌──────────────┐       │ OutputTarget  │
                     │ GifEncoder  │       │ Filesystem    │
                     │ images→GIF  │       │ Discord/Webhook│
                     └──────────────┘       └───────────────┘
```

## Data Flow

1. **Capture**: SnapshotScheduler (main thread) → WorldSnapshot per tick → SnapshotBuffer per player
2. **Trigger**: Event/API/Webhook → TriggerRuleRegistry → TriggerHandler.handle(TriggerContext)
3. **Render**: buffers.slice() → frames → IsometricRenderer → BufferedImage[] → GifEncoder → byte[]
4. **Output**: OutputProfileRegistry.getTargets(profile) → OutputTarget.send()

## Service Boundaries

| Layer | Components |
|-------|------------|
| Plugin bootstrap | ReplayGifPlugin.onEnable() — init order: Config → BlockRegistry → BlockColorMap → Textures → Renderer → TriggerHandler |
| Config | ConfigManager (config.yml, renderer.yml, outputs.yml, triggers.yml) |
| Capture | SnapshotScheduler, SnapshotBuffer, BlockBreakTracker, CombatEventTracker, ActionBarTracker |
| Trigger | DeathListener, DynamicListenerRegistry, ApiTriggerListener, WebhookInboundServer, ReplayGifAPIImpl |
| Render | IsometricRenderer, GifEncoder |
| Output | OutputProfileRegistry, FilesystemOutput, DiscordWebhookOutput, GenericWebhookOutput |

## Key Files

- `ReplayGifPlugin.java` — bootstrap, player join/quit, reload (346 lines)
- `TriggerHandler.java` — single entry for all triggers, async render pool
- `SnapshotScheduler.java` — periodic capture, main-thread
- `ConfigManager.java` — config loading

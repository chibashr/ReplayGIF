# ReplayGIF Backend / Pipeline

<!-- Generated: 2026-03-09 | Files scanned: 127 | Token estimate: ~650 -->

## Routes (HTTP Webhook)

| Method | Path | Handler | Flow |
|--------|------|---------|------|
| POST | `/trigger` | WebhookInboundServer | JSON body → TriggerRuleRegistry.matchInbound → TriggerHandler.handle → 202 + job_id |

Headers: `X-ReplayGif-Secret`, `Content-Type: application/json`

## Trigger Pipeline

```
TriggerContext (subject UUID, label, pre/post sec, profiles, metadata)
    → TriggerHandler.handle()
    → buffers.get(uuid) → SnapshotBuffer.slice(triggerTime, pre, post)
    → IsometricRenderer.render(frames)
    → GifEncoder.encode(images)
    → OutputProfileRegistry.getTargets(profile) → OutputTarget.send(bytes, context)
```

## Key Mappings

| Component | Responsibility |
|-----------|-----------------|
| TriggerRuleRegistry | Load triggers.yml; match internal event, inbound event_key, api defaults |
| TriggerHandler | Validate buffer, enqueue RenderJob on async pool, run render→encode→dispatch |
| OutputProfileRegistry | Load outputs.yml; map profile name → List\<OutputTarget\> |
| ConfigManager | Load config.yml, renderer.yml; provide typed accessors |

## Service → Config

| Service | Config Source |
|---------|---------------|
| SnapshotBuffer capacity | config.yml buffer_seconds × fps |
| SnapshotScheduler rate | config.yml fps |
| WebhookInboundServer | config.yml webhook_server (enabled, port, secret) |
| Render pool size | config.yml async_threads |
| Internal/inbound rules | triggers.yml internal, inbound |
| Output targets | outputs.yml profiles |
| IsometricRenderer | renderer.yml (tile_width, volume_size, cut_offset, etc.) |

## Key Files

- `TriggerHandler.java` — handle(), RenderJob execution
- `WebhookInboundServer.java` — HTTP server, /trigger handler
- `ReplayGifAPIImpl.java` — ServicesManager provider, trigger()
- `OutputProfileRegistry.java` — profile → targets
- `ConfigManager.java` — config access

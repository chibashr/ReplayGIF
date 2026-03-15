# ReplayGIF Runbook

## Deployment

1. Build: `./gradlew jar`
2. Copy `build/libs/ReplayGif-<version>.jar` to server `plugins/` folder
3. Start or reload server; config files created in `plugins/ReplayGif/` on first run
4. Edit configs as needed; use `/replaygif reload` or restart to apply

## Health Checks

| Check | Command / Indicator |
|-------|---------------------|
| Plugin loaded | Console: `ReplayGif enabling...` → `SnapshotScheduler started.` |
| Buffers active | `/replaygif status` — shows players with buffers |
| Webhook listening | Console: `WebhookInboundServer listening on port <port>` (when enabled) |

## Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| Trigger ignored, no buffer | Player has no snapshot buffer | Ensure player online; wait for scheduler to populate; check buffer_seconds × fps |
| Output profile not found | Rule references non-existent profile | Add profile in `outputs.yml` or fix profile name in `triggers.yml` |
| Webhook 401 | Wrong or missing `X-ReplayGif-Secret` | Match header value to `webhook_server.secret` in `config.yml` |
| API/event trigger does nothing | API triggers disabled | Set `allow_api_triggers: true` in `config.yml` |
| Slice returned no frames | Time window has no frames | Increase buffer_seconds or fps; ensure player online long enough |
| Entities gray/colored boxes | Missing textures | Configure `resource_pack_path` or `client_jar_path`; run `extractAllTextures` for bundled |

## Rollback

1. Stop server
2. Remove or replace `ReplayGif-<version>.jar` in `plugins/`
3. Restart server
4. Configs in `plugins/ReplayGif/` are preserved; remove folder for clean uninstall

## Alerts / Escalation

- **Secret warning:** If `webhook_server.secret` is still `changeme` with webhook enabled, change it in production.
- **Memory:** Large `buffer_seconds` × `fps` × players increases RAM; tune `async_threads` for CPU load.

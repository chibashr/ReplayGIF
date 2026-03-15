# ReplayGIF Data / Config

<!-- Generated: 2026-03-09 | Files scanned: 127 | Token estimate: ~500 -->

## Config Files (plugins/ReplayGif/)

| File | Purpose |
|------|---------|
| config.yml | buffer_seconds, fps, spectator_capture_seconds, async_threads, allow_api_triggers, webhook_server |
| renderer.yml | tile_width/height, volume_size, cut_offset, texture paths, skin settings, gif_background, hud |
| outputs.yml | profiles: name → list of targets (filesystem, discord_webhook, generic_webhook) |
| triggers.yml | internal rules, inbound rules, api defaults |
| block_colors.json | Auto-generated from block_colors_defaults.json; user-editable block colors |
| lang/en_US.yml | Localisation |

## Data Relationships

```
triggers.yml internal.player_death
    → output_profiles: ["default"]
    → pre_seconds, post_seconds

outputs.yml profiles.default
    → targets: [{ type: filesystem, path_template: "gifs/{world}/{player}_{timestamp}.gif" }]

config.yml
    → buffer_seconds × fps = SnapshotBuffer capacity per player
```

## Runtime State (in-memory)

| Structure | Location | Lifecycle |
|-----------|-----------|-----------|
| Map\<UUID, SnapshotBuffer\> | ReplayGifPlugin.buffers | Created on PlayerJoin, removed on PlayerQuit |
| RenderJob | TriggerHandler.activeJobs | Per async job; cleared on completion |
| SkinCache | SkinCache | TTL-based; shutdown on disable |
| texture_cache/ | plugin data folder | Mojang/McAsset downloads |

## No Database

Plugin uses YAML configs and in-memory buffers. No SQL/NoSQL.

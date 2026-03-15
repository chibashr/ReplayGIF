# Project Spec: Minecraft Replay Plugin

_Last updated: 2026-03-14_
_Version: 5.0_

## Overview
- **Type**: Paper Minecraft plugin + managed sidecar renderer (separate JVM process)
- **Problem statement**: Automatically capture and render isometric GIF replays of notable in-game events on a Minecraft server.
- **Primary user**: Small private server admins running servers for friends
- **Core action**: On a configured trigger event, the plugin captures a rolling world-state snapshot and hands it to a sidecar renderer, which produces an isometric GIF using real Minecraft assets.

---

## Scope

### In scope (v1)
- Rolling world-state capture buffer: 5 seconds before event, 2 seconds after (configurable)
- Trigger system: any Paper event by class name, dynamically registered from config; plugin ships a built-in player-extraction map for all known player-associated Paper events; unknown events rejected at startup with a clear error
- Player death is the required default trigger, pre-configured out of the box
- Global config defaults with per-trigger overrides for: capture window, capture radius, output destinations, cooldowns, camera behavior
- Per-player and global cooldowns, both configurable
- Isometric GIF rendering using real Minecraft textures; assets extracted from the local Minecraft client jar (Paper caches this at `cache/mojang_<version>.jar` in the server root); assets unpacked to `plugins/ReplayPlugin/assets/` on first sidecar run; mcasset.cloud used as fallback only if local jar is not found
- Block model support scope (v1): full-cube blocks + common non-cube shapes (slabs, stairs, fences, walls, doors, trapdoors, glass panes, iron bars); biome-tinted blocks (grass, leaves, water) supported via colormap lookup from client jar; animated textures (fire, lava, water, portals) rendered as static using first animation frame; unsupported block types render as a solid cube tinted with the block's average texture color
- GIF color quantization: per-frame local palette (256 colors per frame, independently optimized per frame); no dithering
- Configurable capture sample rate (default: 2 ticks / 10fps); capture rate and GIF output fps are independently configurable
- Camera-frustum occlusion cutout: per-frame, blocks occluding the player from the isometric camera are hidden
- Camera locked on triggering player by default; Y position smoothly interpolated between ticks (dampened)
- Vanilla-accurate lighting: top faces brightest, side faces darker, bottom faces darkest; no dynamic lighting simulation
- Configurable GIF frame rate (default: 10 fps)
- Configurable capture radius (default: 5-chunk radius = 11×11 chunk area centered on player)
- Configurable GIF resolution in pixels-per-block (default: 32px)
- Render queue: sequential FIFO; one job rendered at a time; configurable max queue size (default: `max_queue_size: 10`); jobs exceeding the limit are dropped and logged
- Configurable output destinations per trigger: disk, Discord webhook, in-game delivery (sends a clickable link in chat to the player who triggered the event, if they are online)
- GIF output filename format: `<player>_<event>_<timestamp>.gif` (e.g. `steve_PlayerDeathEvent_20260314-153042.gif`)
- GIF retention: no automatic deletion; admin manages disk manually
- GIFs and job queue stored under `plugins/ReplayPlugin/replays/`
- Silent render completion — no in-game notification when a GIF finishes
- Error surfacing: server console logs + in-game notifications to players with admin permission node
- Sidecar: Java, bundled inside the plugin `.jar`, extracted to disk on first run, launched as a separate JVM process
- Plugin manages full sidecar lifecycle: start on enable, monitor, restart on crash, shut down gracefully on server stop
- On server shutdown: wait for current render job to finish, then kill sidecar
- If sidecar fails to start: disable all triggers until sidecar is confirmed running; log error
- Permission model: LuckPerms-compatible permission nodes per command
- In-game commands (all permission-gated):
  - `/replay reload` — reload config.yml
  - `/replay trigger <player> <event>` — force-trigger a render manually
  - `/replay queue` — view render queue status: currently-rendering job (player name, event type, elapsed time) + count of pending jobs
  - `/replay queue clear` — clear the render queue
  - `/replay trigger enable <event>` — enable a specific trigger
  - `/replay trigger disable <event>` — disable a specific trigger

### Out of scope (v1)
- Nothing confirmed out of scope

---

## Constraints
- Single `.jar` drop-in install; no manual setup beyond dropping the file in `/plugins`
- Server must not stutter during renders — all rendering offloaded to sidecar JVM process
- Paper 1.21.x (latest release)
- Sidecar runtime: Java (no additional runtime dependency beyond the server's JVM)
- Asset source: local Minecraft client jar (always present on a Paper server); mcasset.cloud is a secondary fallback only
- Sidecar JVM max heap is configurable via `sidecar_max_heap_mb` in config.yml (default: 512)

---

## Architecture

### Components
1. **Plugin (Paper JVM)**: event listener registration, world-state capture buffer, job serialization, sidecar lifecycle management, command handling, config management
2. **Sidecar (separate JVM process)**: job queue watcher, isometric renderer, GIF encoder, asset fetcher/cache, output dispatcher

### IPC: File-based queue
- Plugin writes JSON job files to `plugins/ReplayPlugin/replays/queue/`
- Sidecar watches the directory, picks up jobs, writes completed GIFs to `plugins/ReplayPlugin/replays/output/`
- Job files include: serialized world-state snapshot, trigger metadata (event type, player UUID, timestamp), per-trigger render config

### Sidecar lifecycle
- Bundled as a separate executable `.jar` inside the plugin `.jar`
- Extracted to `plugins/ReplayPlugin/sidecar/` on first run
- Plugin launches it as a subprocess on enable: `java -Xmx<sidecar_max_heap_mb>m -jar sidecar.jar <data-dir>`
- Plugin monitors process health; restarts on crash
- On server shutdown: plugin sends a shutdown signal (sentinel file in queue dir), waits for current job to finish, then kills the process if still running

---

## Data Model

- **Capture buffer**: per-player rolling ring buffer of world-state snapshots at the configured capture sample rate (default: every 2 ticks); each snapshot contains block states and light levels within the configured radius (from `ChunkSnapshot`, captured on main thread), plus a separately-captured entity state record (see below)
- **Entity state** (captured per snapshot, main thread): player position (x/y/z), yaw, pitch, pose (STANDING, SNEAKING, SWIMMING, SLEEPING, FALL_FLYING), equipment slots (main hand, off hand, head, chest, legs, feet item IDs), skin texture URL
- **Render job** (JSON file): `{ player_uuid, event_type, timestamp, pre_frames[], post_frames[], render_config }`
- **Render config** (per job): fps, pixels_per_block, capture_radius, camera_mode, cutout_enabled, output_destinations
- **Asset cache**: extracted from local Minecraft client jar at `plugins/ReplayPlugin/assets/`; organized as `assets/minecraft/textures/block/<n>.png`, `assets/minecraft/blockstates/<n>.json`, `assets/minecraft/models/block/<n>.json`, `assets/minecraft/textures/colormap/`; a `cache-version.txt` file records the Minecraft version the assets were extracted from; on every sidecar startup, if `cache-version.txt` is missing or version does not match the current server version, the asset cache is fully re-extracted
- **GIF output**: written to `plugins/ReplayPlugin/replays/output/`; retained per configured retention policy

---

## Trigger System

- Config references Paper event class names (e.g. `PlayerDeathEvent`, `BlockBreakEvent`)
- On startup, plugin iterates configured triggers, resolves each class via the built-in extraction map, registers a dynamic listener
- Unknown event classes (not in extraction map) are rejected at startup with a descriptive error in console
- High-frequency events (e.g. move events) are not blocked by default but cooldown config is the admin's responsibility
- Each trigger config block:
```yaml
triggers:
  player_death:
    event: PlayerDeathEvent
    enabled: true
    pre_seconds: 5
    post_seconds: 2
    radius_chunks: 5        # radius; total capture area = (radius*2+1)^2 chunks = 11x11
    capture_rate_ticks: 2   # capture one snapshot every N ticks (default: 2 = 10fps)
    fps: 10
    pixels_per_block: 32
    cooldown:
      per_player_seconds: 0
      global_seconds: 0
    destinations:
      - type: disk
      - type: discord_webhook
        url: "https://discord.com/api/webhooks/..."
```

Global config also includes:
```yaml
sidecar_max_heap_mb: 512   # max JVM heap for the sidecar process
max_queue_size: 10         # max queued render jobs; new jobs dropped when exceeded
```

---

## Rendering Pipeline

1. Sidecar picks up job file from queue directory (`.json` files only)
2. On first run (or if asset cache is missing/stale): extract textures, blockstate JSON, block model JSON, and colormap files from the local Minecraft client jar (`cache/mojang_<version>.jar`) into `plugins/ReplayPlugin/assets/`; fall back to mcasset.cloud per-asset if jar is not found
3. Deserializes world-state frames
4. For each frame:
   a. Resolve block model for each block: parse blockstate JSON to select variant, load block model JSON to get element geometry and texture references, fetch texture PNG from asset cache
   b. Unsupported block types (not in Tier 1/2 model set): render as solid cube using average color of the block's top texture
   c. Biome-tinted blocks (grass, leaves, water): apply tint using colormap lookup from the captured biome data
   d. Animated textures (fire, lava, water, portals): use first frame of the `.mcmeta` animation; render as static
   e. Project all block elements isometrically (45° horizontal, ~26.57° vertical / dimetric projection)
   f. Per-frame frustum occlusion: cast rays from the isometric camera direction through each voxel toward the player's position; mark any block whose voxel volume intersects a ray to the player as occluded and skip rendering it
   g. Apply vanilla directional lighting (face-based shading multipliers: top = 1.0, north/south = 0.8, east/west = 0.6, bottom = 0.5)
   h. Composite player entity sprite (constructed from skin texture, pose, equipment) on top of the block layer at the player's projected position
   i. Apply smooth Y camera interpolation between capture frames (dampened lerp)
5. Apply per-frame local palette color quantization (256 colors, no dithering) to each composited frame
6. Encode frames to GIF89a at configured fps
7. Write GIF to disk at `plugins/ReplayPlugin/replays/output/<player>_<event>_<timestamp>.gif`
8. Dispatch GIF to configured output destinations
9. Delete job file from queue directory

---

## Failure Modes

| Scenario | Behavior |
|----------|----------|
| Chunk not in capture buffer at event time | Retry once after 1 tick; if still unavailable, drop job and log |
| Two players trigger simultaneously | Both jobs enqueue; rendered sequentially in FIFO order |
| Render queue at max capacity (`max_queue_size`) | New job dropped; event logged to console and in-game admins |
| Player disconnects during post-event window | Stop capture immediately; submit job with truncated clip |
| Sidecar crashes mid-render | Log error, notify in-game admins, re-queue current job at front of queue, restart sidecar; if job fails again on retry, drop it and log |
| Sidecar fails to start on plugin enable | Disable all triggers, log error; re-enable when sidecar confirmed running |
| Local Minecraft client jar not found at startup | Log warning; sidecar falls back to mcasset.cloud per-asset fetching |
| mcasset.cloud unreachable and asset not in local cache | Render fails; log error with missing asset identifier |
| Discord webhook delivery fails | Retry once after 5 seconds; if retry fails, log error with job ID and drop |
| Unknown event class in config | Reject at startup, log descriptive error, skip that trigger |

---

## Permission Nodes

| Node | Description |
|------|-------------|
| `replay.admin` | Grants access to all commands and in-game error notifications |
| `replay.command.reload` | `/replay reload` |
| `replay.command.trigger` | `/replay trigger` |
| `replay.command.queue` | `/replay queue` |
| `replay.command.queue.clear` | `/replay queue clear` |
| `replay.command.trigger.toggle` | `/replay trigger enable/disable` |

---

## Success Criteria
- A player dies; a GIF is produced showing up to 5s before and 2s after the death
- GIF is isometric, uses real Minecraft textures, player is always visible via per-frame frustum cutout
- Server shows no measurable TPS drop during or after a render
- Single `.jar` drop-in install; no manual configuration of sidecar required
- Unknown event class names in config are caught at startup, not at runtime
- Sidecar crash does not crash or stutter the Paper server

---

## Open Questions
- None — all questions resolved

---

## Change Log
| Version | Change | Reason |
|---------|--------|--------|
| 1.0 | Initial spec | First pass after core problem + scope interview |
| 2.0 | Rendering, failure modes, asset sourcing, operational config | Remaining interview topics |
| 3.0 | Full spec complete | IPC, sidecar runtime, trigger system, commands, permissions, config structure, architecture |
| 4.0 | Pre-build spec review resolutions | Asset source changed to local client jar (mcasset.cloud demoted to fallback); block model scope defined (Tier 2: cubes + common non-cube shapes); GIF quantization specified (per-frame local palette, no dithering); capture rate made configurable (default 2 ticks); entity state fields fully specified; occlusion algorithm described; Discord webhook retry policy defined (once after 5s); sidecar heap made configurable; radius_chunks clarified as radius not diameter |
| 5.0 | Completeness pass | GIF filename format defined; in-game delivery defined (clickable chat link to triggering player); asset cache version tracking added (re-extract on version mismatch); render queue max size added (configurable, default 10, drop-and-log on overflow); retention policy defined (no automatic deletion, admin manages disk manually); /replay queue output defined (current job + pending count); sidecar crash recovery updated (re-queue at front, retry once before dropping); queue overflow added to failure modes |
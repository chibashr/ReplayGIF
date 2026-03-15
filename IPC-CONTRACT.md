# Render Job JSON Schema
# IPC contract between the plugin (writer) and sidecar (reader).
# File path: plugins/ReplayPlugin/replays/queue/<uuid>.json
# Both sides must conform to this schema exactly.

## Top-level fields

| Field         | Type   | Required | Description |
|---------------|--------|----------|-------------|
| player_uuid   | string | yes      | UUID of the triggering player, standard hyphenated format |
| event_type    | string | yes      | Paper event class simple name, e.g. "PlayerDeathEvent" |
| timestamp     | string | yes      | Capture time, format: yyyyMMdd-HHmmss |
| render_config | object | yes      | Per-job render settings (see below) |
| pre_frames    | array  | yes      | Frames captured before the trigger event; may be empty |
| post_frames   | array  | yes      | Frames captured after the trigger event; may be empty |

## render_config object

| Field                | Type    | Required | Description |
|----------------------|---------|----------|-------------|
| fps                  | integer | yes      | GIF output frame rate |
| pixels_per_block     | integer | yes      | GIF resolution: pixels per Minecraft block |
| capture_radius_chunks| integer | yes      | Capture radius in chunks (radius, not diameter) |
| camera_mode          | string  | yes      | "follow_player" is the only supported value in v1 |
| cutout_enabled       | boolean | yes      | Whether per-frame frustum occlusion cutout is applied |
| output_destinations  | array   | yes      | List of destination objects (see below) |

## output_destinations entry

Disk:
```json
{ "type": "disk" }
```

Discord webhook:
```json
{ "type": "discord_webhook", "url": "https://discord.com/api/webhooks/..." }
```

In-game chat link:
```json
{ "type": "in_game" }
```

## Frame object (pre_frames and post_frames entries)

| Field           | Type    | Required | Description |
|-----------------|---------|----------|-------------|
| frame_index     | integer | yes      | 0-based index within the full clip |
| captured_at_tick| integer | yes      | Server tick at which the snapshot was taken |
| entity_state    | object  | yes      | Player state at this tick (see below) |
| chunks          | array   | yes      | List of chunk snapshots within capture radius |

## entity_state object

| Field           | Type   | Required | Description |
|-----------------|--------|----------|-------------|
| x               | number | yes      | World X coordinate |
| y               | number | yes      | World Y coordinate |
| z               | number | yes      | World Z coordinate |
| yaw             | number | yes      | Head yaw in degrees |
| pitch           | number | yes      | Head pitch in degrees |
| pose            | string | yes      | One of: STANDING, SNEAKING, SWIMMING, SLEEPING, FALL_FLYING |
| equipment       | object | yes      | Equipment slot item IDs (see below) |
| skin_texture_url| string | yes      | Full URL to the player's skin texture |

## equipment object

| Field     | Type   | Required | Description |
|-----------|--------|----------|-------------|
| main_hand | string | yes      | Minecraft item ID, e.g. "minecraft:diamond_sword"; "minecraft:air" if empty |
| off_hand  | string | yes      | Minecraft item ID; "minecraft:air" if empty |
| head      | string | yes      | Minecraft item ID; "minecraft:air" if empty |
| chest     | string | yes      | Minecraft item ID; "minecraft:air" if empty |
| legs      | string | yes      | Minecraft item ID; "minecraft:air" if empty |
| feet      | string | yes      | Minecraft item ID; "minecraft:air" if empty |

## chunk object (within chunks array)

| Field   | Type   | Required | Description |
|---------|--------|----------|-------------|
| chunk_x | integer| yes      | Chunk X coordinate |
| chunk_z | integer| yes      | Chunk Z coordinate |
| biome   | string | yes      | Minecraft biome namespaced ID, e.g. "minecraft:plains" |
| blocks  | array  | yes      | List of block entries within this chunk (see below) |

## block object (within chunk.blocks)

| Field                  | Type    | Required | Description |
|------------------------|---------|----------|-------------|
| x                      | integer | yes      | World X coordinate |
| y                      | integer | yes      | World Y coordinate |
| z                      | integer | yes      | World Z coordinate |
| block_id               | string  | yes      | Minecraft block namespaced ID, e.g. "minecraft:stone" |
| block_state_properties | object  | yes      | Map of blockstate property key→value strings; empty object {} if none |
| sky_light              | integer | yes      | Sky light level 0–15 |
| block_light            | integer | yes      | Block light level 0–15 |

## Output filename

Completed GIFs are written to:
  plugins/ReplayPlugin/replays/output/<player_name>_<event_type>_<timestamp>.gif

Example: steve_PlayerDeathEvent_20260314-153042.gif

## Sentinel shutdown file

To signal the sidecar to shut down after completing the current job, the plugin
writes a file named:
  plugins/ReplayPlugin/replays/queue/SHUTDOWN

The sidecar must check for this file after completing each job and exit cleanly
when found. The sentinel file is not a render job and must never be parsed as one.

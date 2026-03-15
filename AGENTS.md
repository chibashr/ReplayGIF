# AGENTS.md — Replay Plugin Build Agents

## Build Assumptions
- Player extraction map: hardcoded Map<String, Function<Event, Player>> for all known Paper player-associated events.
- ChunkSnapshot captured synchronously on the main thread. Entity state captured alongside.
- Sidecar queue watcher polls every 500ms. FIFO order determined by file creation timestamp (Files.getAttribute "creationTime").
- Player sprite: planar quad mapped from skin texture regions (head, torso, arms, legs). No skeletal animation.
- Equipment overlay: scaled top-texture of the item composited at hand/head position. No 3D item model.
- GIF89a: implemented from scratch using LZW with per-frame local color tables. No third-party GIF library.
- Discord webhook: sends GIF as multipart/form-data file attachment.
- In-game delivery: ClickEvent.Action.OPEN_URL pointing to file URI. Documented limitation noted in README.
- camera_mode "follow_player" is the only supported value; field serialized for forward compatibility.

## Agent: PLUGIN-CONFIG
Scope: plugin/src/main/java/com/replayplugin/config/
Responsibilities: Parse config.yml and config.test.yml into typed POJOs. Validate trigger event class names against PlayerExtractionMap. Provide default fallback values for all optional fields.
Dependencies: none
Must not touch: trigger registration, capture, sidecar lifecycle
Output contract:
  - PluginConfig.load(JavaPlugin plugin) → PluginConfig
  - PluginConfig.getTriggers() → Map<String, TriggerConfig>
  - PluginConfig.getSidecarMaxHeapMb() → int
  - PluginConfig.getMaxQueueSize() → int
  - TriggerConfig fields: event, enabled, preSeconds, postSeconds, radiusChunks, captureRateTicks, fps, pixelsPerBlock, cooldownPerPlayer, cooldownGlobal, destinations

## Agent: PLUGIN-TRIGGER
Scope: plugin/src/main/java/com/replayplugin/trigger/
Responsibilities: Dynamically register and unregister Paper event listeners for each configured trigger. Enforce per-player and global cooldowns. Invoke capture pipeline on event fire.
Dependencies: PLUGIN-CONFIG
Must not touch: config parsing, capture implementation, sidecar
Output contract:
  - TriggerRegistry.register(TriggerConfig, CaptureBuffer, RenderQueue)
  - TriggerRegistry.unregister(String eventClassName)
  - TriggerRegistry.isRegistered(String eventClassName) → boolean
  - PlayerExtractionMap.getPlayer(Event) → Optional<Player>

## Agent: PLUGIN-CAPTURE
Scope: plugin/src/main/java/com/replayplugin/capture/
Responsibilities: Maintain per-player rolling ring buffer of WorldSnapshots. Capture ChunkSnapshot and EntityState on the main thread at the configured tick rate. On trigger, flush pre-frames, schedule post-frame capture, then submit RenderJob.
Dependencies: PLUGIN-TRIGGER (interfaces only)
Must not touch: trigger registration, sidecar, command handling
Output contract:
  - CaptureBuffer.onTick(Player)
  - CaptureBuffer.onTrigger(Player, TriggerConfig) → RenderJob
  - WorldSnapshot containing: List<ChunkData>, EntityState, capturedAtTick
  - EntityState fields: x, y, z, yaw, pitch, pose, equipment (all 6 slots), skinTextureUrl
  - ChunkData fields: chunkX, chunkZ, biome, List<BlockEntry{x,y,z,blockId,blockStateProperties,skyLight,blockLight}>

## Agent: PLUGIN-JOB
Scope: plugin/src/main/java/com/replayplugin/job/
Responsibilities: Serialize RenderJob to JSON per IPC-CONTRACT.md. Write job files to queue directory. Enforce max_queue_size. Manage FIFO queue state for /replay queue command output.
Dependencies: PLUGIN-CAPTURE (RenderJob type)
Must not touch: sidecar lifecycle, commands
Output contract:
  - JobSerializer.serialize(RenderJob) → String (JSON per IPC-CONTRACT.md)
  - RenderQueue.enqueue(RenderJob) → boolean (false if queue full)
  - RenderQueue.getStatus() → QueueStatus{currentJob, pendingCount}
  - RenderQueue.clear()
  - GIF filename: <playerName>_<eventType>_<timestamp>.gif (yyyyMMdd-HHmmss)

## Agent: PLUGIN-SIDECAR
Scope: plugin/src/main/java/com/replayplugin/sidecar/
Responsibilities: Extract sidecar.jar from plugin resources on first run. Launch as subprocess with configured heap. Monitor process health; restart on crash. Send shutdown sentinel on server stop. Disable all triggers if sidecar fails to start.
Dependencies: PLUGIN-CONFIG (heap config), PLUGIN-JOB (queue dir path)
Must not touch: rendering, capture, commands
Output contract:
  - SidecarManager.start() → void (extracts JAR if needed, launches process)
  - SidecarManager.isRunning() → boolean
  - SidecarManager.shutdown() → void (writes SHUTDOWN sentinel, waits, force-kills)
  - SidecarManager.onCrash(Runnable) — registers crash callback (notifies admins, re-enables triggers)

## Agent: PLUGIN-COMMAND
Scope: plugin/src/main/java/com/replayplugin/command/, com/replayplugin/util/
Responsibilities: Implement /replay command tree. Check LuckPerms permission nodes. Invoke config reload, trigger toggle, queue inspection, force-trigger, queue clear. Broadcast admin notifications.
Dependencies: PLUGIN-CONFIG, PLUGIN-TRIGGER, PLUGIN-JOB, PLUGIN-SIDECAR
Must not touch: capture, rendering
Output contract: all subcommands per plugin.yml; permission nodes per project-spec.md

## Agent: SIDECAR-ASSET
Scope: sidecar/src/main/java/com/replayplugin/sidecar/asset/
Responsibilities: On startup, check cache-version.txt. If missing or stale, extract textures/blockstates/models/colormaps from local Minecraft client jar. Fall back to mcasset.cloud per-asset if jar not found. Log warnings on fallback failures.
Dependencies: none
Must not touch: rendering, GIF encoding
Output contract:
  - AssetManager.initialize(Path dataDir, String mcVersion) → void
  - AssetManager.getTexture(String namespacedId) → BufferedImage
  - AssetManager.getBlockstateJson(String blockId) → JsonNode
  - AssetManager.getModelJson(String modelPath) → JsonNode
  - AssetManager.getColormap(String name) → BufferedImage

## Agent: SIDECAR-RENDER
Scope: sidecar/src/main/java/com/replayplugin/sidecar/model/, sidecar/src/main/java/com/replayplugin/sidecar/render/
Responsibilities: Resolve block models. Project blocks isometrically (45° horizontal, ~26.57° vertical dimetric). Apply per-frame frustum occlusion. Apply directional lighting (top 1.0, N/S 0.8, E/W 0.6, bottom 0.5). Apply biome tints. Render player sprite with pose and equipment. Interpolate camera Y with dampened lerp.
Dependencies: SIDECAR-ASSET
Must not touch: GIF encoding, output dispatch, queue watcher
Output contract:
  - IsometricRenderer.renderFrame(WorldSnapshot, EntityState, RenderConfig) → BufferedImage

## Agent: SIDECAR-GIF
Scope: sidecar/src/main/java/com/replayplugin/sidecar/gif/
Responsibilities: Per-frame local palette quantization (256 colors, no dithering). Encode frames to GIF89a at configured fps. Write to output path.
Dependencies: SIDECAR-RENDER (List<BufferedImage> input)
Must not touch: rendering, asset loading
Output contract:
  - GifEncoder.encode(List<BufferedImage> frames, int fps, Path outputPath) → void

## Agent: SIDECAR-OUTPUT
Scope: sidecar/src/main/java/com/replayplugin/sidecar/output/, sidecar/src/main/java/com/replayplugin/sidecar/queue/
Responsibilities: Watch queue directory for .json job files (poll every 500ms). Deserialize jobs per IPC-CONTRACT.md. Process jobs FIFO. Dispatch completed GIFs to disk, Discord webhook (retry once after 5s), and in-game (no-op in sidecar; handled by plugin). Detect SHUTDOWN sentinel and exit cleanly.
Dependencies: SIDECAR-RENDER, SIDECAR-GIF, SIDECAR-ASSET
Must not touch: plugin code
Output contract:
  - QueueWatcher.run(Path queueDir, Path outputDir) — main loop
  - OutputDispatcher.dispatch(Path gifPath, List<Destination> destinations, String playerUuid)
  - DiscordWebhookSender.send(String webhookUrl, Path gifPath) → boolean

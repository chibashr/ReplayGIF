# ReplayGif

ReplayGif is a Paper plugin that keeps a rolling buffer of world snapshots around each online player. When a trigger fires (player death, API call, event from another plugin, or HTTP webhook), the plugin slices a time window from that player's buffer, renders it as an isometric animated GIF, and dispatches the result to configured outputs such as Discord webhooks or the server filesystem. Triggers are configured per event type with independent pre/post seconds and output profiles.

## Requirements

- **Java:** 21
- **Server:** Paper 1.18 or newer (api-version 1.18). Tested against Paper 1.18.2.
- **Server specs:** Rendering and encoding run on a configurable async thread pool. For a few concurrent jobs, 2 threads and default buffer (e.g. 30 s at 10 FPS) are usually enough. Increase `async_threads` and ensure enough RAM for larger buffers and higher FPS if you run many triggers at once.

## Installation

1. Build the plugin: `./gradlew jar` (or `gradlew.bat jar` on Windows). The JAR is produced in `build/libs/ReplayGif-<version>.jar`.
2. Copy the JAR into your server's `plugins` folder.
3. Start or restart the server. ReplayGif will create its data folder and default config files on first run.
4. Edit the config files as needed (see Configuration), then use `/replaygif reload` or restart the server to apply changes.

## Configuration

All config files live in `plugins/ReplayGif/`. The bundled files are self-documenting with comments; only an overview is given here.

- **config.yml** — Core behaviour: buffer length (`buffer_seconds`), capture rate (`fps`), spectator capture window and pause (`spectator_capture_seconds`), size of the render thread pool (`async_threads`), whether API/event triggers are allowed (`allow_api_triggers`), and the inbound webhook server (enabled, port, secret). Edit when tuning performance or enabling the webhook or external API triggers.

- **renderer.yml** — Visual and asset options: isometric tile size (`tile_width`, `tile_height`), capture volume edge length (`volume_size`), cut plane offset (`cut_offset`), optional path to a Minecraft client JAR for entity and item textures (`client_jar_path`), player skin rendering and cache TTL, the block colors file name, and whether to use bundled 1.21 block textures (`block_textures_enabled`). Edit when changing how the GIF looks or where assets are loaded from.

### Bundled 1.21 block textures

For realistic block appearance (like in-game), you can bundle vanilla 1.21 block textures into the plugin. Without them, blocks are drawn using solid colors from `block_colors.json`.

1. Obtain a Minecraft 1.21 client JAR (e.g. from `.minecraft/versions/1.21/<version>.jar`).
2. Run: `./gradlew extractBlockTextures -PclientJar=/path/to/1.21.jar` (Windows: `gradlew.bat extractBlockTextures -PclientJar=C:\path\to\1.21.jar`).
3. Rebuild the plugin: `./gradlew jar`. The extracted textures are included in the JAR.
4. Ensure `block_textures_enabled: true` in `renderer.yml` (default).

### Entity and item textures from client JAR

Entity sprites (zombie, creeper, fish, etc.) and item icons (dropped items in the scene) use vanilla textures when `client_jar_path` in `renderer.yml` points to a Minecraft client JAR (e.g. `.minecraft/versions/1.21/<version>.jar`). Subfolder paths (e.g. `entity/fish/`) are supported. Without the JAR, entities use bundled sprites or marker colors from `entity_bounds.json`; items use a gray fallback.

- **outputs.yml** — Defines named output profiles; each profile lists one or more targets (e.g. Discord webhook URL, generic webhook, or filesystem path template). Edit when adding or changing where GIFs are sent or saved. Profile names are referenced from `triggers.yml`.

- **triggers.yml** — Defines when a render is triggered and with what parameters. It has three parts: **internal** rules (e.g. player_death or other Bukkit event class names with getter chains for subject and label), **inbound** rules (event_key and subject_path for HTTP webhook requests), and **api** defaults (default pre/post seconds and output profiles for API and ReplayGifTriggerEvent callers). Edit when adding or changing trigger events, time windows, or which profiles a trigger uses.

## Triggering from another plugin

You can trigger a render in two ways: with a compile-time dependency on ReplayGif (hard dep) or without it (soft dep via a Bukkit event).

**Soft dependency (no ReplayGif on the classpath):** Fire ReplayGif's custom event. ReplayGif must be installed and `allow_api_triggers` in `config.yml` must be true.

```java
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
// ReplayGifTriggerEvent is in me.replaygif.api — use reflection or add ReplayGif as provided dependency to get the class

// When your condition is met (e.g. custom kill, achievement):
Player subject = ...; // the player whose replay to capture
ReplayGifTriggerEvent event = new ReplayGifTriggerEvent(
    subject,
    "Custom Kill",           // event label (used in templates)
    -1.0,                    // preSeconds: -1 = use ReplayGif config default
    -1.0,                    // postSeconds: -1 = use default
    null,                    // outputProfileNames: null = use default
    null                     // metadata: null = none
);
Bukkit.getPluginManager().callEvent(event);
```

**Hard dependency:** Add ReplayGif as a dependency and use the API. You get a job ID for log correlation.

```java
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import me.replaygif.api.ReplayGifAPI;

// In your plugin code, when you want to trigger:
ServicesManager sm = Bukkit.getServicesManager();
var registration = sm.getRegistration(ReplayGifAPI.class);
if (registration != null) {
    ReplayGifAPI api = registration.getProvider();
    Player subject = ...;
    java.util.UUID jobId = api.trigger(subject, "Custom Kill", -1, -1, null, null);
    // jobId appears in ReplayGif log lines for this job
}
```

## Triggering via HTTP webhook

ReplayGif can run an inbound HTTP server that accepts POST requests to trigger a render. Enable it in `config.yml` with `webhook_server.enabled: true`, set `webhook_server.port` (e.g. 8765), and set `webhook_server.secret` to a non-default value.

**Request**

- **Method:** POST  
- **Path:** `/trigger`  
- **Headers:**  
  - `X-ReplayGif-Secret`: must match `webhook_server.secret` (exact string). Wrong or missing header returns 401.  
  - `Content-Type`: must be `application/json`.  
- **Body (JSON):**  
  - `event_key` (required): string that matches an inbound rule in `triggers.yml`, or (if `inbound.use_default_for_unmatched` is true) any value to use inbound defaults.  
  - Subject: the player who will be the replay subject. The key is defined by the matched rule’s `subject_path` (or the default `subject_path` when using defaults). Typically a key like `player` with value equal to the player’s UUID string or exact username.  
  - Optional: `pre_seconds`, `post_seconds`, `output_profile` (string or array of strings), `metadata` (object). When omitted, the matched rule’s or default values are used.

**Response**

- 202: Accepted. Body contains `{"job_id": "<uuid>"}`.  
- 400: Bad request (e.g. missing `event_key`, invalid JSON, subject not resolvable at path).  
- 401: Unauthorized (secret missing or wrong).  
- 404: Path not `/trigger` or player not found/not online.  
- 415: Content-Type not application/json.

Example (rule expects `event_key: "player_death"` and `subject_path: "player"`):

```bash
curl -X POST http://localhost:8765/trigger \
  -H "X-ReplayGif-Secret: your-secret" \
  -H "Content-Type: application/json" \
  -d '{"event_key":"player_death","player":"a1b2c3d4-e5f6-7890-abcd-ef1234567890"}'
```

## all-the-webhooks integration

all-the-webhooks (or any plugin that can send HTTP requests on Minecraft events) can trigger ReplayGif by POSTing to ReplayGif’s inbound webhook. There is no plugin dependency: integration is entirely via HTTP.

1. In ReplayGif: set `webhook_server.enabled: true`, choose a `webhook_server.port`, and set `webhook_server.secret` to a secure value. In `triggers.yml` under `inbound.rules`, add a rule with an `event_key` that you will send (e.g. `player_death`), `subject_path` (e.g. `player`), `output_profiles`, and optional `pre_seconds` / `post_seconds` / `label_path` / `label_fallback`.
2. In all-the-webhooks: create a webhook target whose URL is `http://<server>:<port>/trigger` (use `localhost` if the plugins run on the same machine). Configure the request to use method POST, header `X-ReplayGif-Secret: <your-secret>`, and `Content-Type: application/json`. Set the body to a JSON object that includes at least `event_key` (matching the ReplayGif inbound rule) and the key expected by `subject_path` (e.g. `"player": "<uuid-or-username>"`). Map the event (e.g. player death) to that webhook so it fires when the event occurs. The subject must be online and have an active ReplayGif buffer when the request is processed.

## Troubleshooting

These are common misconfigurations and how they appear in the console.

1. **Trigger ignored, no buffer**  
   Log: `[<jobId>] No buffer for player <name>. Trigger ignored.`  
   Cause: The player has no snapshot buffer (e.g. they joined after the scheduler started but buffer creation failed, or they were never online while the scheduler was running). Ensure the player is online and that the plugin has been running long enough for at least one frame to be written; after a reload, buffers are recreated for current players.

2. **Output profile not found**  
   Log: `[<jobId>] Output profile '<name>' not found or has no targets. Skipping.`  
   Cause: A trigger rule (in `triggers.yml` internal or inbound, or the api default list) references an output profile that does not exist in `outputs.yml` or has no targets. Fix the profile name in the rule or add a profile with that name and at least one target in `outputs.yml`.

3. **Webhook returns 401**  
   Log: `Inbound webhook: unauthorized request from <address>`  
   Cause: The request either did not include the header `X-ReplayGif-Secret` or the value did not match `webhook_server.secret` in `config.yml`. Use the exact same string (case-sensitive) in the client and in config.

4. **API or event trigger does nothing**  
   Cause: `allow_api_triggers` in `config.yml` is false. ReplayGif will not process `ReplayGifTriggerEvent` or the programmatic API trigger. Set `allow_api_triggers: true` if you want other plugins or the API to trigger renders.

5. **Slice returned no frames**  
   Log: `[<jobId>] Slice returned no frames. Job failed.`  
   Cause: The requested time window (trigger time ± pre/post seconds) contained no frames in the player’s buffer. Typical causes: buffer_seconds or FPS too low so the window is not covered, trigger fired before enough time had passed after the player joined, or clock/trigger timestamp mismatch. Increase `buffer_seconds` or ensure the player has been online and in range of the scheduler for at least the pre window before the trigger.

6. **Entities as gray or colored boxes; player as oversized face**  
   Cause: Missing or invalid `client_jar_path` (for entity textures), or skin fetch failing. Configure `client_jar_path` in `renderer.yml` to a 1.21+ client JAR for proper entity sprites (fish, etc.). For players, skins are fetched from Mojang; ensure `skin_rendering_enabled: true` and outbound HTTPS works. Players render as full-body figures (head, body, limbs) from their skin; if skins fail to load, a placeholder body is used.

If the console shows `webhook_server.enabled is true but webhook_server.secret is still 'changeme'`, change the secret in production to avoid unauthorized use.

## License

See the project license file.

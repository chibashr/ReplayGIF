# Test Spec: Minecraft Replay Plugin

**Date:** 2026-03-14
**Project:** Minecraft Replay Plugin (Paper + Sidecar Renderer)
**Version:** 2.0
**Status:** Final

---

## Overview

This project is a Paper Minecraft plugin paired with a managed sidecar JVM process that captures rolling world-state snapshots and renders isometric GIF replays. The test environment must cover the plugin (Paper JVM), the sidecar renderer (separate JVM), their IPC contract (file-based JSON queue), the rendering pipeline, and all failure/recovery behaviors. No existing tests are in place — this spec establishes the full testing strategy from scratch.

---

## Stack Summary

| Component | Language | Runtime | Key Dependencies |
|-----------|----------|---------|-----------------|
| Plugin | Java | Paper 1.21.x JVM | Paper API, LuckPerms API |
| Sidecar | Java | JVM (same version as server) | None beyond JDK |
| IPC | File-based JSON | OS filesystem | None |
| Asset pipeline | File I/O | JVM | Minecraft client jar (`cache/mojang_<version>.jar`) |
| GIF output | Binary file | JVM | Custom encoder |
| Discord delivery | HTTP | JVM | Discord Webhook API |

**Build system:** Gradle. Paper's own tooling (Paperweight) is Gradle-native and MockBukkit's documented setup is Gradle-first. All commands in this spec use Gradle.

---

## Deployment Targets

- **Local dev machine:** Primary target. Plugin tests run against a mocked Paper API (MockBukkit). Sidecar tests run as plain JUnit — no server needed.
- **Docker / container:** Used for integration tests that exercise the full plugin↔sidecar IPC loop with a real filesystem queue directory.
- **No hardware-in-the-loop:** Not applicable.

---

## Existing Test Coverage

None. This spec defines coverage from scratch.

| Area | Status |
|------|--------|
| Plugin event capture | Missing |
| Sidecar lifecycle management | Missing |
| IPC (file queue) contract | Missing |
| Rendering pipeline | Missing |
| GIF encoder | Missing |
| Asset extraction | Missing |
| Command handling | Missing |
| Failure/recovery behaviors | Missing |
| Config loading | Missing |
| Discord webhook delivery | Missing |

---

## Test Levels

### 1. Unit Tests

**Framework:** JUnit 5 + Mockito  
**Plugin tests:** MockBukkit (Paper API mock layer)  
**Location:** `plugin/src/test/java/` and `sidecar/src/test/java/`  
**Run command:** `./gradlew test`

---

#### 1.1 Config Loading

| ID | Test | Pass Condition |
|----|------|----------------|
| CFG-001 | Load valid `config.yml` with all fields present | All fields parsed correctly; no exceptions |
| CFG-002 | Load config with missing optional fields | Defaults applied for all missing optional fields |
| CFG-003 | Load config with unknown event class name in trigger | Startup rejects the trigger; descriptive error logged to console; other valid triggers still registered |
| CFG-004 | Load config with `capture_rate_ticks: 2` | Snapshot interval stored as 2 ticks |
| CFG-005 | Load config with per-trigger overrides (e.g. different `fps`, `radius_chunks`) | Per-trigger values override global defaults correctly |
| CFG-006 | Load config with `sidecar_max_heap_mb: 256` | Sidecar launch command includes `-Xmx256m` |
| CFG-007 | Load config with `max_queue_size: 5` | Queue refuses job #6, logs drop event |
| CFG-008 | `/replay reload` with a changed config file | New values take effect; triggers re-registered |
| CFG-009 | `/replay reload` with a syntax error in config.yml | Reload fails gracefully; previous config retained; error logged |

---

#### 1.2 Trigger Registration

| ID | Test | Pass Condition |
|----|------|----------------|
| TRG-001 | Register `PlayerDeathEvent` (default config) | Listener registered; no errors |
| TRG-002 | Register a valid non-default event (`BlockBreakEvent`) | Listener registered; no errors |
| TRG-003 | Register an event class not in the extraction map | Startup logs descriptive error; trigger skipped; other triggers unaffected |
| TRG-004 | Disable a trigger via `/replay trigger disable PlayerDeathEvent` | Listener de-registered; deaths no longer enqueue jobs |
| TRG-005 | Re-enable a trigger via `/replay trigger enable PlayerDeathEvent` | Listener re-registered; deaths enqueue jobs again |
| TRG-006 | Fire a disabled trigger | No job enqueued |

---

#### 1.3 Capture Buffer

| ID | Test | Pass Condition |
|----|------|----------------|
| BUF-001 | Player moves for 10 seconds at 2-tick capture rate | Buffer contains exactly `10s * (20tps/2) = 100` snapshots, dropping oldest beyond configured pre-window |
| BUF-002 | Trigger fires; pre_seconds=5, post_seconds=2 | Job contains 50 pre-frames (5s at 10fps) and 20 post-frames (2s at 10fps) |
| BUF-003 | Player disconnects during post-event window | Capture stops immediately; job submitted with truncated post-frames |
| BUF-004 | Chunk not in capture buffer at event time — retry once after 1 tick | If available after retry: job enqueued. If not: job dropped, error logged |
| BUF-005 | Snapshot correctly stores block states within configured radius | Block state grid dimensions match `(radius_chunks*2+1)^2` chunks |
| BUF-006 | Snapshot correctly stores entity state fields (position, yaw, pitch, pose, equipment, skin URL) | All fields present and correctly typed in serialized snapshot |
| BUF-007 | Capture rate override per trigger (`capture_rate_ticks: 4`) | Buffer samples every 4 ticks |

---

#### 1.4 Per-Player and Global Cooldowns

| ID | Test | Pass Condition |
|----|------|----------------|
| COO-001 | Player dies twice within `per_player_seconds: 30` | Second death does not enqueue a job |
| COO-002 | Player dies after cooldown expires | Job enqueued normally |
| COO-003 | Two different players die within `global_seconds: 60` | Second player's death does not enqueue a job |
| COO-004 | `per_player_seconds: 0` (disabled) | Rapid successive deaths each enqueue a job |
| COO-005 | `global_seconds: 0` (disabled) | Rapid deaths from different players each enqueue jobs |

---

#### 1.5 Job Serialization

| ID | Test | Pass Condition |
|----|------|----------------|
| SER-001 | Serialize a render job to JSON | Output is valid JSON; all required fields present (`player_uuid`, `event_type`, `timestamp`, `pre_frames`, `post_frames`, `render_config`) |
| SER-002 | Deserialize the same JSON back | Round-trip produces an object equal to the original |
| SER-003 | `render_config` in serialized job reflects per-trigger overrides | Values from trigger config block, not global defaults |
| SER-004 | GIF output filename format | Filename matches `<player>_<event>_<timestamp>.gif` pattern (e.g. `steve_PlayerDeathEvent_20260314-153042.gif`) |

---

#### 1.6 Render Queue

| ID | Test | Pass Condition |
|----|------|----------------|
| QUE-001 | Enqueue job when sidecar is running | JSON file written to `queue/` directory |
| QUE-002 | Enqueue when queue is at `max_queue_size` | Job dropped; console log entry written; in-game admin notification sent |
| QUE-003 | Two simultaneous trigger events | Both jobs enqueued; FIFO order preserved (timestamps reflect arrival order) |
| QUE-004 | `/replay queue` output when one job rendering, two pending | Output shows current job (player name, event type, elapsed time) and "2 pending" |
| QUE-005 | `/replay queue clear` | All pending job files removed from queue directory; currently-rendering job unaffected |
| QUE-006 | `/replay trigger <player> <event>` (force trigger) | Job file created; matches same format as event-triggered jobs |

---

#### 1.7 Sidecar Lifecycle (Plugin side)

| ID | Test | Pass Condition |
|----|------|----------------|
| SLC-001 | Plugin enable with sidecar JAR present | Sidecar process started; process handle stored |
| SLC-002 | Sidecar JAR not yet extracted | JAR extracted to `plugins/ReplayPlugin/sidecar/` before launch |
| SLC-003 | Sidecar fails to start | All triggers disabled; error logged; no NPE or crash in plugin |
| SLC-004 | Sidecar process exits unexpectedly | Plugin detects exit; logs error; notifies in-game admins; restarts sidecar |
| SLC-005 | Server shutdown signal | Plugin writes sentinel file to queue dir; waits for current render to finish; kills sidecar process if still running after timeout |
| SLC-006 | Sidecar restart after crash while queue is non-empty | After restart, existing queue files remain and are picked up by sidecar |

---

#### 1.8 Commands and Permissions

| ID | Test | Pass Condition |
|----|------|----------------|
| CMD-001 | `/replay reload` with `replay.command.reload` | Reload executes |
| CMD-002 | `/replay reload` without permission | Command rejected; permission-denied message shown |
| CMD-003 | `/replay trigger <player> <event>` with `replay.command.trigger` | Job enqueued |
| CMD-004 | `/replay queue` with `replay.command.queue` | Queue status returned |
| CMD-005 | `/replay queue clear` with `replay.command.queue.clear` | Queue cleared |
| CMD-006 | `/replay trigger enable/disable` with `replay.command.trigger.toggle` | Trigger state toggled |
| CMD-007 | `replay.admin` node grants access to all commands | All command checks pass |
| CMD-008 | In-game admin notification on render queue overflow | Player with `replay.admin` receives message; player without does not |

---

#### 1.9 In-Game Delivery

| ID | Test | Pass Condition |
|----|------|----------------|
| DLV-001 | GIF job completes; triggering player is online | Player receives a clickable chat link to the output GIF |
| DLV-002 | GIF job completes; triggering player has disconnected | No delivery attempted; no error thrown |
| DLV-003 | Destination config does not include `in_game` | No chat message sent |

---

### 2. Sidecar Unit Tests

**Framework:** JUnit 5 + Mockito  
**Location:** `sidecar/src/test/java/`  
**Run command:** `./gradlew :sidecar:test`

---

#### 2.1 Asset Extraction and Cache

| ID | Test | Pass Condition |
|----|------|----------------|
| AST-001 | Extract assets from a real (or test-fixture) Minecraft client jar | `assets/minecraft/textures/block/`, `blockstates/`, `models/block/`, `textures/colormap/` directories populated |
| AST-002 | `cache-version.txt` written after extraction | File contains the Minecraft version string |
| AST-003 | Sidecar starts; `cache-version.txt` matches current version | Asset extraction skipped; existing cache used |
| AST-004 | Sidecar starts; `cache-version.txt` version does not match | Full re-extraction performed |
| AST-005 | Sidecar starts; `cache-version.txt` missing | Full extraction performed |
| AST-006 | Local Minecraft client jar not found | Warning logged; sidecar falls back to mcasset.cloud per-asset fetching |
| AST-007 | mcasset.cloud unreachable and asset not in local cache | Render fails gracefully; error logged with missing asset identifier |

---

#### 2.2 Block Model Resolution

| ID | Test | Pass Condition |
|----|------|----------------|
| BLK-001 | Resolve a full-cube block (e.g. `stone`) | Correct blockstate JSON loaded; correct model JSON loaded; top texture path resolved |
| BLK-002 | Resolve a slab (half-slab variant) | Correct blockstate variant selected; geometry reflects half-height |
| BLK-003 | Resolve stairs | Correct variant (facing, shape) selected from blockstate JSON |
| BLK-004 | Resolve fence | Multipart model composed correctly |
| BLK-005 | Resolve wall | Multipart model composed correctly |
| BLK-006 | Resolve door (lower/upper half) | Both halves render correctly |
| BLK-007 | Resolve trapdoor (open/closed, top/bottom) | Correct variant geometry |
| BLK-008 | Resolve glass pane | Pane geometry + connected face detection |
| BLK-009 | Resolve iron bars | Same as glass pane |
| BLK-010 | Block type not in supported set | Fallback: render as solid cube tinted with average color of top texture |
| BLK-011 | Biome-tinted block (grass) | Tint applied via colormap lookup; output pixel color within expected tint range |
| BLK-012 | Biome-tinted block (leaves) | Tint applied correctly |
| BLK-013 | Biome-tinted block (water) | Tint applied correctly |
| BLK-014 | Animated texture (fire) | First animation frame used; no `.mcmeta` parsing error |
| BLK-015 | Animated texture (lava) | First frame used |
| BLK-016 | Animated texture (water surface) | First frame used |
| BLK-017 | Animated texture (nether portal) | First frame used |

---

#### 2.3 Isometric Projection

| ID | Test | Pass Condition |
|----|------|----------------|
| ISO-001 | Project a single block at origin (0,0,0) | Screen coordinates match expected 45°/26.57° dimetric formula |
| ISO-002 | Project a block at (1,0,0) vs (0,0,1) | Correctly offset horizontally (right vs left in isometric view) |
| ISO-003 | Project a block at (0,1,0) | Correctly offset vertically upward |
| ISO-004 | Two blocks at same XZ but different Y | Higher block renders above lower block in output image |
| ISO-005 | Block element with non-unit geometry (slab: 0–8px height) | Screen-space height is half the full-cube height |

---

#### 2.4 Occlusion Culling

| ID | Test | Pass Condition |
|----|------|----------------|
| OCC-001 | Block directly between camera and player | Block marked as occluded; not rendered |
| OCC-002 | Block behind player (away from camera) | Block not occluded; rendered normally |
| OCC-003 | Block adjacent to occlusion line but not intersecting | Block not occluded |
| OCC-004 | Player visible in final frame despite surrounding blocks | Player sprite composited on top layer; always present |

---

#### 2.5 Lighting

| ID | Test | Pass Condition |
|----|------|----------------|
| LGT-001 | Top face of a stone block | Pixel brightness = base texture brightness × 1.0 |
| LGT-002 | North/south face | Pixel brightness = base × 0.8 |
| LGT-003 | East/west face | Pixel brightness = base × 0.6 |
| LGT-004 | Bottom face | Pixel brightness = base × 0.5 |
| LGT-005 | Lighting applied after biome tint | Tint and shading both present in output pixel |

---

#### 2.6 Player Entity Sprite

| ID | Test | Pass Condition |
|----|------|----------------|
| ENT-001 | Render player in STANDING pose | Sprite matches expected standing geometry |
| ENT-002 | Render player in SNEAKING pose | Sprite uses correct sneaking geometry |
| ENT-003 | Render player in SWIMMING pose | Correct pose |
| ENT-004 | Render player in SLEEPING pose | Correct pose |
| ENT-005 | Render player in FALL_FLYING (elytra) pose | Correct pose |
| ENT-006 | Apply equipment: helmet visible on head | Helmet item texture composited over skin |
| ENT-007 | Apply equipment: held item in main hand | Item visible at expected hand position |
| ENT-008 | Player skin texture URL resolved and applied | Skin texture used; not default skin |
| ENT-009 | Player sprite projected to correct screen position | Screen coordinates match player's world position through isometric transform |

---

#### 2.7 Camera Interpolation

| ID | Test | Pass Condition |
|----|------|----------------|
| CAM-001 | Player Y changes by 2 blocks between two consecutive snapshots | Camera Y in output smoothly interpolated (dampened lerp); no hard jump |
| CAM-002 | Player Y is constant across all frames | Camera Y is constant; no drift |
| CAM-003 | Player XZ changes | Camera follows XZ of triggering player |

---

#### 2.8 GIF Encoding

| ID | Test | Pass Condition |
|----|------|----------------|
| GIF-001 | Encode 3 frames at 10fps | Valid GIF89a file; frame delay = 100ms (10fps) |
| GIF-002 | Encode at custom fps (e.g. 15fps) | Frame delay = ~67ms |
| GIF-003 | Per-frame local palette quantization: 256-color limit | Each frame's palette ≤ 256 entries |
| GIF-004 | No dithering | Output pixels are exact palette-matched colors; no dither pattern visible |
| GIF-005 | GIF file is valid and renderable | Open with any GIF decoder without error |
| GIF-006 | Output filename matches pattern | `<player>_<event>_<timestamp>.gif` |
| GIF-007 | GIF written to `plugins/ReplayPlugin/replays/output/` | File present at correct path post-render |

---

#### 2.9 Discord Webhook Delivery

| ID | Test | Pass Condition |
|----|------|----------------|
| DSC-001 | POST to webhook URL succeeds on first attempt | No retry; success logged |
| DSC-002 | POST fails; retry after 5 seconds succeeds | Single retry fires at ~5s; success logged |
| DSC-003 | POST fails; retry also fails | Error logged with job ID; job not re-queued for delivery |
| DSC-004 | Destination config does not include `discord_webhook` | No HTTP request made |

---

#### 2.10 Sidecar Queue Processing

| ID | Test | Pass Condition |
|----|------|----------------|
| QWR-001 | Job JSON file placed in queue directory | Sidecar picks it up and begins rendering |
| QWR-002 | Job file picked up | File removed from queue directory after successful render |
| QWR-003 | Two job files present | Processed in FIFO order (by file creation timestamp) |
| QWR-004 | Non-`.json` file in queue directory | Ignored by sidecar |
| QWR-005 | Sentinel shutdown file received mid-idle | Sidecar exits cleanly |
| QWR-006 | Sentinel received while rendering | Sidecar finishes current job, then exits |

---

### 3. Integration Tests

**Framework:** JUnit 5  
**Infrastructure:** Docker Compose (shared filesystem volume between plugin and sidecar containers)  
**Run command:** `./gradlew integrationTest` (requires Docker)  
**Location:** `integration-tests/src/test/java/`

These tests run the real sidecar JAR against real job files, using a shared temp directory as the queue.

---

#### 3.1 Full IPC Round-Trip

| ID | Test | Pass Condition |
|----|------|----------------|
| IPC-001 | Plugin writes a valid job JSON to queue dir; sidecar running | Sidecar produces a `.gif` in output dir within a timeout (e.g. 60s) |
| IPC-002 | Job JSON contains all required fields | Sidecar renders without error |
| IPC-003 | Job JSON is malformed | Sidecar logs error; skips job; does not crash |
| IPC-004 | Plugin writes 5 jobs in rapid succession | All 5 GIFs produced in FIFO order |
| IPC-005 | Sidecar killed mid-render; plugin detects exit and restarts it | Job file re-queued at front; re-rendered after restart; GIF produced |

---

#### 3.2 Asset Pipeline Integration

| ID | Test | Pass Condition |
|----|------|----------------|
| AIP-001 | First sidecar run with no asset cache | Assets extracted from fixture client JAR; `cache-version.txt` written |
| AIP-002 | Second sidecar run with correct cache version | No re-extraction; startup faster |
| AIP-003 | Second run after version change in `cache-version.txt` | Full re-extraction performed |

---

#### 3.3 Output Destinations Integration

| ID | Test | Pass Condition |
|----|------|----------------|
| OUT-001 | Destination: `disk` | GIF present at `output/` path |
| OUT-002 | Destination: `discord_webhook` with test webhook URL | HTTP POST received at mock webhook endpoint |
| OUT-003 | Discord webhook POST fails both attempts | Error logged; no crash; `output/` GIF still written |

---

### 4. End-to-End / Smoke Tests

**Format:** Manual checklist (runnable by a human on a real test Paper server, or scripted via RCON)  
**Required setup:** Paper 1.21.x server with plugin `.jar` installed, a connected Minecraft client

---

#### 4.1 Install and Startup

| ID | Step | Expected |
|----|------|----------|
| E2E-001 | Drop plugin `.jar` into `/plugins`; start server | No errors in console; sidecar extracted and launched; triggers registered |
| E2E-002 | Check console for unknown event errors | None (default config is valid) |
| E2E-003 | Check `plugins/ReplayPlugin/sidecar/` | Sidecar JAR present |
| E2E-004 | Check `plugins/ReplayPlugin/assets/` | Asset directories present after first sidecar run |

#### 4.2 Death Trigger (Core Flow)

| ID | Step | Expected |
|----|------|----------|
| E2E-010 | Player dies in game (e.g. fall damage) | Job file appears in `queue/` directory |
| E2E-011 | Wait for render to complete | GIF appears in `output/`; filename matches pattern |
| E2E-012 | Open GIF | Isometric view; player visible; textures correct; animation plays |
| E2E-013 | TPS during render | TPS remains at 20 (no drop) |
| E2E-014 | Player receives in-game link (if `in_game` destination configured) | Clickable link in chat |

#### 4.3 Failure Recovery

| ID | Step | Expected |
|----|------|----------|
| E2E-020 | Kill sidecar process manually; wait 5 seconds | Console shows crash detected; sidecar restarted; in-game admin notified |
| E2E-021 | Enqueue 11 jobs (queue max = 10) | 11th job dropped; console log entry; in-game admin notification |
| E2E-022 | Run `/replay queue` while rendering | Shows current job (player, event, elapsed) + pending count |
| E2E-023 | Run `/replay queue clear` | Pending jobs removed; current job continues |
| E2E-024 | Stop server while rendering | Server waits for current render to finish before shutting down |

#### 4.4 Config Reload

| ID | Step | Expected |
|----|------|----------|
| E2E-030 | Edit `config.yml`; run `/replay reload` | New config active; triggers re-registered |
| E2E-031 | Add unknown event class to config; reload | Error logged; bad trigger skipped; valid triggers intact |

---

## Environment Isolation

### Test config file (`config.test.yml`)
Used for unit/integration tests. Override values:
```yaml
sidecar_max_heap_mb: 256
max_queue_size: 5
triggers:
  player_death:
    event: PlayerDeathEvent
    enabled: true
    pre_seconds: 2
    post_seconds: 1
    cooldown:
      per_player_seconds: 0
      global_seconds: 0
    destinations:
      - type: disk
```

### Environment variables (integration tests)

```
# integration-tests/.env.test
QUEUE_DIR=/tmp/replay-test/queue
OUTPUT_DIR=/tmp/replay-test/output
SIDECAR_JAR_PATH=../../sidecar/build/libs/sidecar.jar
MC_CLIENT_JAR_PATH=./fixtures/mock-client.jar
# WireMock runs in-process — no MOCK_WEBHOOK_URL needed
```

### Secrets strategy

No real credentials needed for most tests. Discord webhook integration tests use a local mock HTTP server (e.g. WireMock or a simple Undertow handler). No real webhook URL is required.

---

## Mock / Stub Strategy

| External Dependency | Test Level | Strategy |
|--------------------|------------|----------|
| Paper API (Bukkit) | Unit | MockBukkit |
| Minecraft client JAR | Unit/Integration | Fixture JAR containing subset of real assets |
| mcasset.cloud | Unit | Mockito HTTP mock; return fixture texture bytes |
| Discord webhook | Unit | Mockito HTTP mock |
| Discord webhook | Integration | WireMock local server |
| File system (queue dir) | Unit | `@TempDir` JUnit 5 |
| Sidecar process | Plugin unit tests | Mockito mock of process manager interface |
| Paper event bus | Unit | MockBukkit event firing |

---

## Test Data and Fixtures

| Fixture | Location | Description |
|---------|----------|-------------|
| `mock-client.jar` | `sidecar/src/test/fixtures/` | Minimal JAR containing 5–10 real block textures, blockstate JSON, model JSON, and colormap — sufficient to render a test scene |
| `sample-job.json` | `integration-tests/src/test/fixtures/` | Valid render job JSON for a 3-frame death scene |
| `malformed-job.json` | `integration-tests/src/test/fixtures/` | Invalid JSON for error-path testing |
| `config.test.yml` | `plugin/src/test/resources/` | Test config (reduced windows, no cooldowns) |
| Expected GIF frames | `sidecar/src/test/fixtures/expected/` | Reference pixel outputs for projection/lighting assertions (compare pixel samples, not entire frames) |

**Cleanup strategy:** All tests use `@TempDir` or explicitly delete test queue/output dirs in `@AfterEach`. No shared mutable state between tests.

---

## CI / GitHub Actions

### Workflow file: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, dev]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Run unit tests
        run: ./gradlew test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: '**/build/reports/tests/test/'

  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    # WireMock runs in-process via @BeforeAll/@AfterAll — no Docker service needed
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Build sidecar JAR
        run: ./gradlew :sidecar:jar --no-daemon

      - name: Run integration tests
        run: ./gradlew integrationTest --no-daemon
        env:
          QUEUE_DIR: /tmp/replay-test/queue
          OUTPUT_DIR: /tmp/replay-test/output
          MC_CLIENT_JAR_PATH: sidecar/src/test/fixtures/mock-client.jar
          # WireMock runs in-process; no external service or URL needed

      - name: Upload integration test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-results
          path: '**/build/reports/tests/integrationTest/'

  build:
    name: Build Plugin JAR
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests]
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build plugin JAR
        run: ./gradlew :plugin:shadowJar --no-daemon

      - name: Upload plugin JAR
        uses: actions/upload-artifact@v4
        with:
          name: replay-plugin
          path: plugin/build/libs/ReplayPlugin-*.jar
```

---

## Gradle Test Configuration

Add to `plugin/build.gradle` and `sidecar/build.gradle`:

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation 'org.mockito:mockito-core:5.11.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.11.0'
    // Plugin only:
    testImplementation 'org.mockbukkit.mockbukkit:mockbukkit-v1.21:3.107.0'
    // WireMock in-process (integration tests):
    testImplementation 'com.github.tomakehurst:wiremock:3.0.1'
}

tasks.named('test', Test) {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'skipped', 'failed'
        showStandardStreams = true
    }
}
```

Add to root `build.gradle` (or `integration-tests/build.gradle`):

```groovy
sourceSets {
    integrationTest {
        java.srcDir 'src/integrationTest/java'
        resources.srcDir 'src/integrationTest/resources'
        compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

tasks.register('integrationTest', Test) {
    description = 'Runs integration tests'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter test
}

check.dependsOn integrationTest
```

---

## Setup Script

**File:** `scripts/setup-test-env.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "==> Checking dependencies..."

command -v java >/dev/null 2>&1 || { echo "ERROR: Java not found. Install JDK 21."; exit 1; }
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[ "$JAVA_VERSION" -ge 21 ] || { echo "ERROR: Java 21+ required. Found: $JAVA_VERSION"; exit 1; }

command -v ./gradlew >/dev/null 2>&1 || { echo "ERROR: gradlew not found. Run from the repo root."; exit 1; }

echo "==> Creating test directories..."
mkdir -p /tmp/replay-test/queue
mkdir -p /tmp/replay-test/output
mkdir -p sidecar/src/test/fixtures

echo "==> Copying test env file..."
if [ ! -f integration-tests/.env.test ]; then
    cp integration-tests/.env.test.example integration-tests/.env.test
    echo "    Copied .env.test.example -> .env.test"
else
    echo "    .env.test already exists, skipping."
fi

echo "==> Building project..."
./gradlew assemble --no-daemon -q

echo "==> Smoke test: running unit tests..."
./gradlew test --no-daemon -q

echo ""
echo "==> Test environment ready."
echo ""
echo "    Run unit tests:        ./gradlew test"
echo "    Run integration tests: ./gradlew integrationTest"
echo "    Run all tests:         ./gradlew check"
echo "    Run CI locally:        act  (requires 'act' installed: https://github.com/nektos/act)"
```

Make executable: `chmod +x scripts/setup-test-env.sh`

---

## .gitignore Additions

```
# --- Test environment ---
/tmp/replay-test/
integration-tests/.env.test
integration-tests/.env.test.local
**/build/reports/
**/build/test-results/
.gradle/
sidecar/src/test/fixtures/extracted/
```

---

## Test Artifacts (Do Not Commit)

| Path | Reason |
|------|--------|
| `/tmp/replay-test/` | Runtime queue and output dirs for integration tests |
| `integration-tests/.env.test` | Contains local paths and mock URLs |
| `**/build/reports/` | Generated test HTML reports |
| `**/build/test-results/` | JUnit XML results |
| `sidecar/src/test/fixtures/extracted/` | Assets extracted from fixture JAR during tests |

---

## Coverage Goals (not enforced in CI v1)

| Component | Target |
|-----------|--------|
| Config loading | 100% of fields and error paths |
| Trigger registration | All known Paper events in extraction map |
| Capture buffer | Pre/post window boundaries, disconnect edge case |
| Job serialization | Round-trip fidelity |
| Block model resolution | All Tier 1/2 block types + fallback |
| GIF encoder | Valid output, fps, palette constraints |
| Failure modes | All rows in the spec failure table |

---

## Out of Scope (v1 Testing)

- Visual regression testing (pixel-perfect GIF comparison across Minecraft versions)
- Load/performance testing of the sidecar renderer under sustained queue pressure
- Testing on Windows/macOS (Linux assumed for both dev and CI)
- Testing with a live Minecraft game client connected to the test server
- GIF retention / disk management (no automatic deletion means no code to test)

---

## Resolved Decisions

All four original open questions are resolved. No open questions remain.

| # | Decision | Resolution |
|---|----------|------------|
| 1 | Build system | **Gradle.** Paper's Paperweight tooling and MockBukkit are both Gradle-native. No Maven. |
| 2 | MockBukkit version | **`org.mockbukkit.mockbukkit:mockbukkit-v1.21:3.107.0` or later.** Pin to a recent 3.x release to get the `ChunkMock#getSnapshot` biome bug fix. Do not construct `ItemStack` directly in tests — go through MockBukkit's entity mocking due to Paper 1.21's `ItemStack` redirecting to `CraftItemStack` internally. This affects BUF-006, ENT-006, ENT-007. |
| 3 | Fixture client JAR contents | **~25–30 textures, ~15 blockstate JSONs, ~25 model JSONs.** Required blocks: `stone`, `grass_block`, `oak_leaves`, `water`, `oak_slab`, `oak_stairs`, `oak_fence`, `cobblestone_wall`, `oak_door`, `oak_trapdoor`, `glass_pane`, `iron_bars`, `fire`, `lava`, `nether_portal`. Plus `textures/colormap/grass.png` and `foliage.png`. One block not in the Tier 1/2 set for the fallback path (BLK-010). Extract from a real client JAR and repack — do not fabricate textures, real pixel data is needed for colormap and average-color assertions. |
| 4 | WireMock in CI | **In-process WireMock, not the Docker service.** Add `com.github.tomakehurst:wiremock:3.x` as a `testImplementation` dependency. Start in `@BeforeAll`, tear down in `@AfterAll`. Remove the `services` block from the CI workflow. No Docker dependency for integration tests; `@TempDir` covers the queue directory. |

---

## Multi-Version Compatibility Testing

This plugin must be tested against all active Paper 1.21.x releases to produce a supported versions list for publication.

### Target Versions

The full 1.21.x release history on Java Edition (note: 1.21.2 was never released on Java):

| Version | Paper API artifact version | Notes |
|---------|---------------------------|-------|
| 1.21 | `paper-api:1.21-R0.1-SNAPSHOT` | First 1.21 release (Tricky Trials) |
| 1.21.1 | `paper-api:1.21.1-R0.1-SNAPSHOT` | |
| 1.21.3 | `paper-api:1.21.3-R0.1-SNAPSHOT` | Timings removed (no-op) |
| 1.21.4 | `paper-api:1.21.4-R0.1-SNAPSHOT` | Paper hard fork from Spigot; significant API changes |
| 1.21.5 | `paper-api:1.21.5-R0.1-SNAPSHOT` | |
| 1.21.6 | `paper-api:1.21.6-R0.1-SNAPSHOT` | |
| 1.21.7 | `paper-api:1.21.7-R0.1-SNAPSHOT` | |
| 1.21.8 | `paper-api:1.21.8-R0.1-SNAPSHOT` | |
| 1.21.9 | `paper-api:1.21.9-R0.1-SNAPSHOT` | |
| 1.21.10 | `paper-api:1.21.10-R0.1-SNAPSHOT` | |
| 1.21.11 | `paper-api:1.21.11-R0.1-SNAPSHOT` | Current latest; last before Mojang version scheme change |

**Important:** 1.21.4 is the hard fork point. Versions 1.21–1.21.3 are in a separate archive repository. The source branches for those versions live at the PaperMC archive repo and have different plugin API surfaces in places. Treat 1.21–1.21.3 and 1.21.4+ as two distinct compatibility groups.

### Tiered Test Strategy

Running the full test suite 11 times per PR is not practical. Use this tiered approach:

**Tier 1 — Run on every push (CI required to pass):**
- Latest stable: `1.21.11`
- Hard fork boundary: `1.21.4`

**Tier 2 — Run on release branches and manually before publishing:**
- All remaining versions: `1.21`, `1.21.1`, `1.21.3`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`

**Supported versions list for publication:** Any version that passes Tier 2 testing is listed as supported.

### What Changes Between Versions

Key differences to verify in cross-version runs (not just "does it compile"):

| Area | Versions affected | What to check |
|------|------------------|---------------|
| `ItemStack` API | 1.21+ | Unit tests touching equipment slots must not construct `ItemStack` directly; use MockBukkit entity mocking |
| `ChunkSnapshot` biome data | All — pin MockBukkit ≥ 3.107.0 | BUF-005, BLK-011–013 |
| `PlayerDeathEvent` field names/API | Verify per version | TRG-001, BUF-002 |
| `ChunkSnapshot` block state API | Verify per version | BUF-005, BUF-006 |
| Entity pose enum values | Verify `FALL_FLYING` present | ENT-005 |
| Paper event package paths | 1.21.4+ (hard fork) | TRG-001–006; event class extraction map must be verified per version |
| Asset JAR path (`cache/mojang_<version>.jar`) | All — version string in path changes | AST-001–007; fixture JAR must be renamed per version in version matrix tests |

### Gradle Multi-Version Configuration

Use a version catalog and Gradle test variants to compile and run unit tests against each Paper API version:

```groovy
// In plugin/build.gradle — parameterize the Paper API version
def paperVersion = System.getProperty('paperVersion', '1.21.11-R0.1-SNAPSHOT')

dependencies {
    compileOnly "io.papermc.paper:paper-api:${paperVersion}"
    testImplementation "io.papermc.paper:paper-api:${paperVersion}"
    testImplementation 'org.mockbukkit.mockbukkit:mockbukkit-v1.21:3.107.0'
    // ... other test deps
}
```

Run unit tests against a specific version:
```bash
./gradlew test -DpaperVersion=1.21.4-R0.1-SNAPSHOT
```

### GitHub Actions — Multi-Version Matrix Workflow

**File:** `.github/workflows/version-matrix.yml`

```yaml
name: Version Matrix

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  matrix-test:
    name: Test (${{ matrix.mc-version }})
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        mc-version:
          - "1.21-R0.1-SNAPSHOT"
          - "1.21.1-R0.1-SNAPSHOT"
          - "1.21.3-R0.1-SNAPSHOT"
          - "1.21.4-R0.1-SNAPSHOT"
          - "1.21.5-R0.1-SNAPSHOT"
          - "1.21.6-R0.1-SNAPSHOT"
          - "1.21.7-R0.1-SNAPSHOT"
          - "1.21.8-R0.1-SNAPSHOT"
          - "1.21.9-R0.1-SNAPSHOT"
          - "1.21.10-R0.1-SNAPSHOT"
          - "1.21.11-R0.1-SNAPSHOT"

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ matrix.mc-version }}-${{ hashFiles('**/*.gradle*') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Run unit tests against Paper ${{ matrix.mc-version }}
        run: ./gradlew :plugin:test --no-daemon -DpaperVersion=${{ matrix.mc-version }}

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results-${{ matrix.mc-version }}
          path: plugin/build/reports/tests/test/

  matrix-summary:
    name: Matrix Summary
    runs-on: ubuntu-latest
    needs: matrix-test
    if: always()
    steps:
      - name: Check matrix results
        run: |
          echo "All version matrix jobs complete."
          echo "Check individual job results above to determine supported versions list."
```

**Notes on the matrix workflow:**
- `fail-fast: false` is required — a failure on 1.21.3 should not cancel the 1.21.11 run.
- This workflow runs on every push to `main` and can also be triggered manually (`workflow_dispatch`) before a release.
- Sidecar unit tests are not version-dependent (no Paper API) and do not need to be in the matrix — they run once in the standard `ci.yml`.
- Integration tests are also not in the matrix. The IPC contract and rendering pipeline are independent of the Paper version. Run them once in `ci.yml`.
- If the Paper API version resolves from the archive repository (1.21–1.21.3), ensure the archive repo (`https://repo.papermc.io/repository/maven-public/`) is declared in `repositories {}` — it hosts both current and archived snapshots.

### Compatibility Test Checklist (Manual — Pre-Release)

Run this before publishing a new release or updating the supported versions list:

| Step | Action |
|------|--------|
| 1 | Run `./gradlew :plugin:test -DpaperVersion=<version>` for all Tier 2 versions |
| 2 | For each version, confirm all trigger registration tests pass (TRG-001–006) — event class paths differ across the 1.21.4 hard fork |
| 3 | Confirm entity state capture tests pass (BUF-006, ENT-001–009) — `ItemStack` and `ChunkSnapshot` API surfaces vary |
| 4 | Note any test failures per version; those versions are not listed as supported |
| 5 | Update the supported versions list in README / plugin description |
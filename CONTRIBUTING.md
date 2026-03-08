# Contributing to ReplayGif

## Development environment

- **Java:** 21. Set `JAVA_HOME` or use your IDE’s JDK 21.
- **Gradle:** The project uses the Gradle wrapper. Run `./gradlew` (or `gradlew.bat` on Windows). No need to install Gradle globally.
- **IDE:** Any IDE that supports Java 21 and Gradle is fine. Import the project as a Gradle project so it picks up `build.gradle.kts`. No extra plugins required.

## Running tests

From the project root:

```bash
./gradlew test
```

(or `gradlew.bat test` on Windows). Tests use JUnit 5 and Mockito; see `src/test/java/`. Behavioral scenarios are described in `.planning/testing.md`; implement or extend tests to cover the scenarios that apply to your change.

## The `.planning/` folder

The `.planning/` directory holds design and specification documents that define how the plugin is supposed to behave. It includes architecture, data structures, trigger resolution, the rendering pipeline, build and logging conventions, and decisions (e.g. no Folia, API is trigger-only, buffer never flushes on state change).

These files are the source of truth for behavior and structure. **If you change plugin behavior (e.g. trigger logic, output format, buffer semantics, or config shape), update the relevant planning files first.** That keeps the docs in sync with the code and gives future contributors (and the AI IDE) a single place to look. When in doubt, check `architecture.md` and `specification.md`, then the component-specific docs (`trigger-resolution.md`, `rendering-pipeline.md`, etc.).

## No-hardcoded-events rule

Only one Bukkit event is wired in code: **PlayerDeathEvent**, in `DeathListener`. Every other event (e.g. entity damage, player join, block break) is registered dynamically from config: the event class name and how to get the “subject” player and label from the event are defined in `triggers.yml`, and `DynamicListenerRegistry` uses reflection to register a listener for that class. So we do not add new `@EventHandler` methods or new listener classes for other event types. To support a new trigger event, add or adjust an internal rule in `triggers.yml` (event class name, getter chain for subject, optional conditions and label path). This keeps the event footprint minimal and avoids recompiling for new trigger types.

## Logging convention

Use the plugin’s SLF4J logger (`plugin.getSLF4JLogger()`), not `Bukkit.getLogger()` or `java.util.logging`. Levels:

- **DEBUG:** Routine per-event or per-request detail (e.g. trigger resolution, cache hits). Off by default in production.
- **INFO:** Lifecycle and normal completion (startup, listener registration, “Job submitted”, “status=DONE”).
- **WARN:** Recoverable issues (missing config using default, listener class not found, trigger ignored because no buffer). Plugin keeps running.
- **ERROR:** Non-recoverable failures for a specific job (e.g. dispatch failed, encoder error). Plugin continues; other jobs are unaffected.

Every log line that refers to an active render job must include the job ID so logs can be correlated, e.g.:

`[550e8400-e29b-41d4-a716-446655440000] Dispatch failed: DiscordWebhookOutput — HTTP 413`

See `.planning/build.md` for the full convention.

## Adding a new output target type

1. **Implement the target**  
   In `me.replaygif.output`, add a new class that implements `OutputTarget`. Implement `dispatch(TriggerContext context, byte[] gifBytes)`. Do not throw; log failures and return. Use `TemplateVariableResolver` if you need to resolve template variables from the context.

2. **Register the type in the registry**  
   In `OutputProfileRegistry` (`me.replaygif.config`), open `createTarget()`. Add a new `case "your_type_name":` that reads the config fields you need (e.g. URL, path, headers), validates them, and returns `new YourOutputTarget(...)`. If validation fails, log a WARN and return `null`. Handle the `default` case by logging an unknown type and returning `null`.

3. **Document the config shape**  
   In `src/main/resources/outputs.yml`, add a commented example under the profiles section showing the new `type` and its required/optional keys (e.g. `url`, `path_template`, `headers`). The default config is the main place users see how to configure a target.

4. **Optional: planning**  
   If the new target introduces new concepts (e.g. retries, auth scheme), add a short note in `.planning/` where output behavior is described (e.g. specification or architecture) so future contributors know the intended behavior.

## Adding a new bundled entity sprite

Bundled sprites are used when the client JAR is not configured or not available. They live under `src/main/resources/entity_sprites_default/`.

1. **Add the image file**  
   Add a PNG file named exactly `<entitytype>.png`, where the name is the Bukkit `EntityType` name in lowercase (e.g. `pig.png`, `enderman.png`). The registry loads by iterating `EntityType.values()` and looking for `entity_sprites_default/<name>.png`; no code change is needed for a new sprite as long as the filename matches an `EntityType`.

2. **Optional: bounding box**  
   If you want a custom size for that entity in the isometric view, add or edit an entry in `src/main/resources/entity_bounds.json` with the entity type name (e.g. `"PIG": { "width": 0.9, "height": 0.9 }`). If omitted, the default 0.6×1.8 is used.

3. **Art policy**  
   Bundled stand-in sprites must be original art, not extracted from Minecraft. They should be simple, recognizable shapes (e.g. correct colors and basic form) suitable for the isometric scale. See `.planning/build.md` for the full list and policy.

## Pull request expectations

- **Tests:** All tests must pass (`./gradlew test`). New behavior should be covered by tests where practical; refer to `.planning/testing.md` for the scenarios that apply.

- **Planning docs:** If your change affects plugin behavior, config, or architecture, update the relevant files in `.planning/` (e.g. specification, trigger-resolution, architecture, build) so the docs stay accurate.

- **Dependencies:** Do not add new runtime dependencies without discussion. The project keeps runtime deps minimal (Paper API at runtime, vendored GIF encoder). Propose new dependencies in an issue or PR and explain why they are needed.

- **Code style:** Follow existing style and the package/class naming in `.planning/architecture.md`. Use the logging convention above and include job IDs in any log line that refers to a render job.

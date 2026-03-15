# Contributing to ReplayGIF

## Prerequisites

- **Java:** 21
- **Build:** Gradle (wrapper included: `gradlew` / `gradlew.bat`)
- **Runtime:** Paper 1.18+ server for testing

## Development Setup

1. Clone the repository.
2. Build: `./gradlew jar` (or `gradlew.bat jar` on Windows).
3. Copy `build/libs/ReplayGif-<version>.jar` into your test server's `plugins/` folder.

## Commands Reference

<!-- AUTO-GENERATED from build.gradle.kts -->

| Command | Description |
|---------|-------------|
| `./gradlew build` | Run tests + JAR (recommended before PR) |
| `./gradlew jar` | Production build; JAR in `build/libs/` |
| `./gradlew test` | Run JUnit 5 test suite |
| `./gradlew clean` | Remove build outputs |
| `./gradlew generateCrackPlaceholders` | Generate crack_stage_0–9.png placeholders (run before build if missing) |
| `./gradlew extractBlockTextures -PclientJar=/path/to/1.21.jar` | Extract block textures from Minecraft client JAR into `src/main/resources/block_textures/` |
| `./gradlew extractItemTextures -PclientJar=/path/to/1.21.jar` | Extract item textures into `src/main/resources/hud/item_icons/` |
| `./gradlew extractEntityTextures -PclientJar=/path/to/1.21.jar` | Extract entity textures into `src/main/resources/entity_sprites_default/` |
| `./gradlew extractAllTextures -PclientJar=/path/to/1.21.jar` | Run all three extract tasks |

## Testing

- **Framework:** JUnit 5, Mockito
- **Run:** `./gradlew test`
- **Location:** `src/test/java/me/replaygif/`
- Tests use Paper API as testImplementation (no real server required).

## Code Style

- Java 21.
- Follow existing package layout: `me.replaygif` (api, compat, config, core, encoder, output, renderer, trigger, tools).
- Prefer Paper APIs (Adventure, schedulers) over legacy Bukkit where applicable.

## PR Checklist

- [ ] Tests pass (`./gradlew test`)
- [ ] Build succeeds (`./gradlew jar`)
- [ ] Plugin loads on target Paper version
- [ ] Config changes documented in README if user-facing

# ReplayGIF Dependencies

<!-- Generated: 2026-03-09 | Files scanned: 127 | Token estimate: ~400 -->

## External Services

| Service | Purpose | Required |
|---------|---------|----------|
| Paper API 1.18 | Bukkit/Spigot/Paper server runtime | Yes (compileOnly) |
| mcasset.cloud CDN | Entity, item, block textures (download_assets_version) | No — fallback when no client jar/resource pack |
| sessionserver.mojang.com | Player skin fetching | No — disable skin_rendering_enabled for offline |

## Third-Party Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| Paper API | 1.18.2-R0.1-SNAPSHOT | compileOnly — server runtime |
| Gson | 2.10.1 | JSON (webhook body, config parsing) |
| JUnit 5 | 5.10.2 | Test |
| Mockito | 5.14.2 | Test |

## Integrations (no plugin dependency)

| Integration | Mechanism |
|-------------|-----------|
| all-the-webhooks | HTTP POST to ReplayGif webhook /trigger |
| Other plugins (soft dep) | ReplayGifTriggerEvent (Bukkit event) |
| Other plugins (hard dep) | ReplayGifAPI via ServicesManager |

## Build

- Java 21
- Gradle 7.6+ (Kotlin DSL)
- Repos: Maven Central, Paper MC

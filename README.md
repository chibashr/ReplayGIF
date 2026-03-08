# ReplayGif

A Paper plugin (1.18+) that renders animated GIFs of configurable time windows around triggered events and dispatches them to configured output targets.

## Build

- **Java:** 21  
- **Gradle:** Kotlin DSL (`build.gradle.kts`)

```bash
./gradlew jar
```

Output: `build/libs/ReplayGif-<version>.jar`

If `gradlew` fails (e.g. missing wrapper jar), install [Gradle](https://gradle.org/install/) and run:

```bash
gradle wrapper
./gradlew jar
```

## Author

chibashr

## License

See project license.

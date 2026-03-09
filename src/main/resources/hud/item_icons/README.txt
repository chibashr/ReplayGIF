Item icons for HUD hotbar and armor slots go here. To populate from a Minecraft 1.21 client JAR, run:

  gradlew extractItemTextures -PclientJar=/path/to/1.21.jar

On Windows: gradlew.bat extractItemTextures -PclientJar=C:\path\to\1.21.jar

File names must match material names (e.g. DIAMOND_SWORD.png). Without bundled icons,
the plugin uses client_jar_path, resource_pack_path, or Mojang cache if configured.

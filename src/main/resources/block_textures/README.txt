Bundled 1.21 block textures go here. To populate from a Minecraft 1.21 client JAR, run:

  gradlew extractBlockTextures -PclientJar=/path/to/1.21.jar

On Windows: gradlew.bat extractBlockTextures -PclientJar=C:\path\to\1.21.jar

The client JAR is typically at:
  .minecraft/versions/1.21/<version>.jar

After extraction, PNG files (e.g. stone.png, grass_block_top.png) will appear here
and be included in the plugin JAR. Block texture names are defined in
block_texture_mapping.json; materials not listed there use material.name().toLowerCase().

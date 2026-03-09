import java.util.zip.ZipFile

plugins {
    java
}

group = "me.replaygif"
version = "0.1.4"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    archiveBaseName.set("ReplayGif")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(file("build/libs"))
    exclude("me/replaygif/tools/**")
}

// Generate placeholder crack_stage_0–9.png (16×16) for block break overlay. Run before build.
tasks.register<JavaExec>("generateCrackPlaceholders") {
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("me.replaygif.tools.GenerateCrackPlaceholders")
    args(project.layout.projectDirectory.dir("src/main/resources").toString())
}

// Extract 1.21 block textures from Minecraft client JAR into src/main/resources/block_textures/
// so they are bundled in the plugin. Run: ./gradlew extractBlockTextures -PclientJar=/path/to/1.21.jar
// Client JAR is typically at .minecraft/versions/1.21/<version>.jar
tasks.register("extractBlockTextures") {
    doLast {
        val clientJar = project.findProperty("clientJar") as String?
        if (clientJar.isNullOrBlank()) {
            logger.warn("extractBlockTextures: set -PclientJar=/path/to/1.21.jar to extract block textures")
            return@doLast
        }
        val destDir = file("src/main/resources/block_textures")
        destDir.mkdirs()
        val zip = ZipFile(clientJar.trim())
        try {
            for (entry in zip.entries()) {
                if (!entry.isDirectory && entry.name.startsWith("assets/minecraft/textures/block/") && entry.name.endsWith(".png")) {
                    val name = entry.name.substringAfterLast('/')
                    zip.getInputStream(entry).use { input: java.io.InputStream ->
                        file("$destDir/$name").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } finally {
            zip.close()
        }
        logger.lifecycle("Extracted block textures to {}", destDir)
    }
}

// Extract 1.21 item textures from Minecraft client JAR into src/main/resources/hud/item_icons/
// so they are bundled in the plugin. Run: ./gradlew extractItemTextures -PclientJar=/path/to/1.21.jar
// Item textures provide HUD hotbar/armor icons and dropped item sprites.
tasks.register("extractItemTextures") {
    doLast {
        val clientJar = project.findProperty("clientJar") as String?
        if (clientJar.isNullOrBlank()) {
            logger.warn("extractItemTextures: set -PclientJar=/path/to/1.21.jar to extract item textures")
            return@doLast
        }
        val destDir = file("src/main/resources/hud/item_icons")
        destDir.mkdirs()
        val zip = ZipFile(clientJar.trim())
        try {
            val names = mutableSetOf<String>()
            for (entry in zip.entries()) {
                if (!entry.isDirectory && entry.name.endsWith(".png")) {
                    val name = when {
                        entry.name.startsWith("assets/minecraft/textures/item/") -> {
                            entry.name.substringAfterLast('/').substringBeforeLast('.').replace("-", "_").toUpperCase()
                        }
                        entry.name.startsWith("assets/minecraft/textures/block/") -> {
                            entry.name.substringAfterLast('/').substringBeforeLast('.').replace("-", "_").toUpperCase()
                        }
                        else -> null
                    }
                    val nameVal = name
                    if (nameVal != null && names.add(nameVal)) {
                        zip.getInputStream(entry).use { input: java.io.InputStream ->
                            file("$destDir/$nameVal.png").outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } finally {
            zip.close()
        }
        logger.lifecycle("Extracted item textures to {}", destDir)
    }
}

// Extract 1.21 entity textures from Minecraft client JAR into src/main/resources/entity_sprites_default/
// Run: ./gradlew extractEntityTextures -PclientJar=/path/to/1.21.jar
tasks.register("extractEntityTextures") {
    doLast {
        val clientJar = project.findProperty("clientJar") as String?
        if (clientJar.isNullOrBlank()) {
            logger.warn("extractEntityTextures: set -PclientJar=/path/to/1.21.jar to extract entity textures")
            return@doLast
        }
        val destDir = file("src/main/resources/entity_sprites_default")
        destDir.mkdirs()
        val prefix = "assets/minecraft/textures/entity/"
        val zip = ZipFile(clientJar.trim())
        try {
            for (entry in zip.entries()) {
                if (!entry.isDirectory && entry.name.startsWith(prefix) && entry.name.endsWith(".png")) {
                    val relPath = entry.name.substring(prefix.length)
                    val fileName = relPath.substringAfterLast('/').ifEmpty { relPath }
                    val baseName = fileName.substringBeforeLast('.')
                    val targetName = when (baseName) {
                        "fishing_bobber" -> "fishing_bobber.png"
                        else -> baseName.replace("-", "_") + ".png"
                    }
                    zip.getInputStream(entry).use { input: java.io.InputStream ->
                        file("$destDir/$targetName").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } finally {
            zip.close()
        }
        logger.lifecycle("Extracted entity textures to {}", destDir)
    }
}

// Run all extract tasks: block, item, and entity textures from one client JAR.
tasks.register("extractAllTextures") {
    group = "build"
    dependsOn("extractBlockTextures", "extractItemTextures", "extractEntityTextures")
}

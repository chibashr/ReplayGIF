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

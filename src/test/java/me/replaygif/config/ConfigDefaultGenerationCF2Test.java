package me.replaygif.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CF2 — Default config generation: delete all config files from the plugin directory,
 * then start the plugin (ConfigManager.saveDefaultConfigs + load). All config files
 * are generated with default values. Plugin starts and operates normally.
 */
class ConfigDefaultGenerationCF2Test {

    @TempDir
    Path tempDir;

    private File dataFolder;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        dataFolder = tempDir.toFile();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("ConfigDefaultGenerationCF2Test"));
        doAnswer(inv -> {
            String name = inv.getArgument(0);
            File target = new File(dataFolder, name);
            target.getParentFile().mkdirs();
            Files.writeString(target.toPath(), "# default " + name);
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());
    }

    @Test
    void cf2_defaultConfigGeneration_allConfigFilesCreated() {
        // No config files in data folder (simulating deleted or first run)
        assertFalse(new File(dataFolder, "config.yml").exists());
        assertFalse(new File(dataFolder, "renderer.yml").exists());
        assertFalse(new File(dataFolder, "outputs.yml").exists());
        assertFalse(new File(dataFolder, "triggers.yml").exists());

        ConfigManager configManager = new ConfigManager(plugin);
        configManager.saveDefaultConfigs();
        configManager.load();

        assertTrue(new File(dataFolder, "config.yml").exists(), "config.yml should be generated");
        assertTrue(new File(dataFolder, "renderer.yml").exists(), "renderer.yml should be generated");
        assertTrue(new File(dataFolder, "outputs.yml").exists(), "outputs.yml should be generated");
        assertTrue(new File(dataFolder, "triggers.yml").exists(), "triggers.yml should be generated");
    }
}

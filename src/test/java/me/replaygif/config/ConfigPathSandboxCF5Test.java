package me.replaygif.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CF5 — Filesystem path sandboxing: path_template absolute and outside plugin dir → ERROR logged, target rejected.
 */
class ConfigPathSandboxCF5Test {

    @TempDir
    Path tempDir;

    private File dataFolder;
    private JavaPlugin plugin;
    private Logger mockLogger;

    @BeforeEach
    void setUp() throws Exception {
        dataFolder = tempDir.toFile();
        plugin = mock(JavaPlugin.class);
        mockLogger = mock(Logger.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getSLF4JLogger()).thenReturn(mockLogger);
    }

    @Test
    void cf5_absolutePathOutsidePluginDir_errorLoggedTargetRejected() throws Exception {
        Files.writeString(dataFolder.toPath().resolve("config.yml"), "buffer_seconds: 6\nfps: 10\n");
        Files.writeString(dataFolder.toPath().resolve("renderer.yml"), "");
        Files.writeString(dataFolder.toPath().resolve("triggers.yml"), "internal:\n  player_death:\n    output_profiles: [default]\n");
        Path base = dataFolder.toPath().normalize();
        Path outsidePath = base.getRoot().resolve("tmp").resolve("replaygif_test.gif");
        String pathTemplate = outsidePath.toString().replace("\\", "\\\\");
        Files.writeString(dataFolder.toPath().resolve("outputs.yml"), """
                profiles:
                  default:
                    - type: filesystem
                      path_template: "%s"
                """.formatted(pathTemplate));

        ConfigManager configManager = new ConfigManager(plugin);
        configManager.load();
        OutputProfileRegistry registry = new OutputProfileRegistry(configManager, plugin);

        assertTrue(registry.getProfile("default").isEmpty(),
                "Profile with outside path should have no valid targets");
        verify(mockLogger).error(argThat((String msg) -> msg != null && msg.contains("outside plugin directory")), any(), any(), any());
    }
}

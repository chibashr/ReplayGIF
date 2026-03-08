package me.replaygif.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CF4 — Webhook secret still default: enabled=true and secret="changeme" → WARNING logged, server still starts.
 */
class ConfigDefaultSecretWarningCF4Test {

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
        when(plugin.getResource(anyString())).thenReturn(null);
    }

    @Test
    void cf4_defaultSecret_warningLoggedPluginStarts() throws Exception {
        Files.writeString(dataFolder.toPath().resolve("config.yml"), """
                buffer_seconds: 6
                fps: 10
                webhook_server:
                  enabled: true
                  port: 8765
                  secret: "changeme"
                """);
        Files.writeString(dataFolder.toPath().resolve("renderer.yml"), "");
        Files.writeString(dataFolder.toPath().resolve("outputs.yml"), "profiles:\n  default: []\n");
        Files.writeString(dataFolder.toPath().resolve("triggers.yml"), "internal:\n  player_death:\n    output_profiles: [default]\n");

        ConfigManager configManager = new ConfigManager(plugin);
        assertDoesNotThrow(() -> configManager.load());

        verify(mockLogger).warn(argThat((String msg) -> msg != null && msg.contains("changeme") && msg.contains("webhook_server")));
    }
}

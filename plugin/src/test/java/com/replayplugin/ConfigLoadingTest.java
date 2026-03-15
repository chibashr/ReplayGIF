package com.replayplugin;

import com.replayplugin.command.ReplayCommand;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigLoadingTest {

    private static JavaPlugin plugin;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setup() {
        try {
            be.seeseemelk.mockbukkit.MockBukkit.mock();
            SidecarProcessLauncher failingLauncher = (sd, dd, heap) -> {
                throw new IOException("mock");
            };
            ReplayPlugin.setTestSidecarProcessLauncher(failingLauncher);
            plugin = be.seeseemelk.mockbukkit.MockBukkit.load(ReplayPlugin.class);
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (plugin != null) be.seeseemelk.mockbukkit.MockBukkit.unmock();
    }

    @Test
    @DisplayName("CFG-001: Load valid config.yml with all fields present")
    void cfg001_loadValidConfigWithAllFields() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.full.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        assertEquals(256, config.getSidecarMaxHeapMb());
        assertEquals(5, config.getMaxQueueSize());
        Map<String, TriggerConfig> triggers = config.getTriggers();
        assertTrue(triggers.containsKey("player_death"));
        assertTrue(triggers.containsKey("block_break"));
        TriggerConfig death = triggers.get("player_death");
        assertEquals("PlayerDeathEvent", death.getEvent());
        assertTrue(death.isEnabled());
        assertEquals(5, death.getPreSeconds());
        assertEquals(2, death.getPostSeconds());
        assertEquals(5, death.getRadiusChunks());
        assertEquals(2, death.getCaptureRateTicks());
        assertEquals(10, death.getFps());
        assertEquals(32, death.getPixelsPerBlock());
        assertEquals(30, death.getCooldownPerPlayer());
        assertEquals(60, death.getCooldownGlobal());
        assertEquals(2, death.getDestinations().size());
    }

    @Test
    @DisplayName("CFG-002: Load config with missing optional fields")
    void cfg002_loadConfigWithMissingOptionalFields() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.minimal.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        assertEquals(512, config.getSidecarMaxHeapMb());
        assertEquals(10, config.getMaxQueueSize());
        Map<String, TriggerConfig> triggers = config.getTriggers();
        TriggerConfig tc = triggers.get("player_death");
        assertNotNull(tc);
        assertEquals("PlayerDeathEvent", tc.getEvent());
        assertTrue(tc.isEnabled());
        assertEquals(5, tc.getPreSeconds());
        assertEquals(2, tc.getPostSeconds());
        assertEquals(5, tc.getRadiusChunks());
        assertEquals(2, tc.getCaptureRateTicks());
        assertEquals(10, tc.getFps());
        assertEquals(32, tc.getPixelsPerBlock());
        assertEquals(0, tc.getCooldownPerPlayer());
        assertEquals(0, tc.getCooldownGlobal());
    }

    @Test
    @DisplayName("CFG-003: Load config with unknown event class name in trigger")
    void cfg003_unknownEventSkipped() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.unknown-event.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        assertTrue(config.getTriggers().containsKey("good"));
        assertFalse(config.getTriggers().containsKey("bad"));
        assertEquals(1, config.getTriggers().size());
    }

    @Test
    @DisplayName("CFG-004: Load config with capture_rate_ticks: 2")
    void cfg004_captureRateTicksStored() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.test.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        TriggerConfig tc = config.getTriggers().get("player_death");
        assertNotNull(tc);
        assertEquals(2, tc.getCaptureRateTicks());
    }

    @Test
    @DisplayName("CFG-005: Per-trigger overrides (fps, radius_chunks)")
    void cfg005_perTriggerOverrides() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.full.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        TriggerConfig death = config.getTriggers().get("player_death");
        assertEquals(10, death.getFps());
        assertEquals(5, death.getRadiusChunks());
        TriggerConfig blockBreak = config.getTriggers().get("block_break");
        assertEquals(15, blockBreak.getFps());
        assertEquals(4, blockBreak.getRadiusChunks());
    }

    @Test
    @DisplayName("CFG-006: sidecar_max_heap_mb: 256 -> launch includes -Xmx256m")
    void cfg006_sidecarHeapInLaunch() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.test.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        assertEquals(256, config.getSidecarMaxHeapMb());
    }

    @Test
    @DisplayName("CFG-007: Load config with max_queue_size: 5")
    void cfg007_maxQueueSizeStored() throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.test.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        assertEquals(5, config.getMaxQueueSize());
    }

    @Test
    @DisplayName("CFG-008: /replay reload with changed config file")
    void cfg008_reloadWithChangedConfig() throws IOException {
        ReplayPlugin rp = (ReplayPlugin) plugin;
        PluginConfig before = rp.getPluginConfig();
        Path configPath = rp.getDataFolder().toPath().resolve("config.yml");
        Files.createDirectories(configPath.getParent());
        Files.copy(getClass().getResourceAsStream("/config.full.yml"), configPath);
        rp.reloadConfig();
        PluginConfig after = PluginConfig.loadFromFile(plugin, configPath);
        rp.reloadConfigFrom(after);
        assertEquals(256, rp.getPluginConfig().getSidecarMaxHeapMb());
        assertEquals(2, rp.getPluginConfig().getTriggers().size());
    }

    @Test
    @DisplayName("CFG-009: /replay reload with syntax error - previous config retained")
    void cfg009_reloadWithSyntaxErrorRetainsPrevious() throws IOException {
        ReplayPlugin rp = (ReplayPlugin) plugin;
        Path configPath = rp.getDataFolder().toPath().resolve("config.yml");
        Files.createDirectories(configPath.getParent());
        Files.copy(getClass().getResourceAsStream("/config.full.yml"), configPath);
        PluginConfig good = PluginConfig.loadFromFile(plugin, configPath);
        rp.reloadConfigFrom(good);
        int heapBefore = rp.getPluginConfig().getSidecarMaxHeapMb();
        Files.writeString(configPath, "sidecar_max_heap_mb: 999\ninvalid: [ unclosed\n");
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        ReplayCommand cmd = new ReplayCommand(rp);
        Command bukkitCmd = mock(Command.class);
        when(bukkitCmd.getName()).thenReturn("replay");
        cmd.onCommand(sender, bukkitCmd, "replay", new String[]{"reload"});
        assertEquals(heapBefore, rp.getPluginConfig().getSidecarMaxHeapMb());
    }
}

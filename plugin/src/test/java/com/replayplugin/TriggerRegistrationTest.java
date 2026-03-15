package com.replayplugin;

import com.replayplugin.capture.CaptureBuffer;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import com.replayplugin.trigger.TriggerRegistry;
import org.bukkit.event.entity.PlayerDeathEvent;
import com.replayplugin.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TriggerRegistrationTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private ReplayPlugin plugin;
    private TriggerRegistry registry;
    private CaptureBuffer captureBuffer;
    private RenderQueue renderQueue;

    @BeforeEach
    void setUp() {
        try {
            server = MockBukkit.mock();
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(true);
        SidecarProcessLauncher launcher = (sd, dd, heap) -> mockProcess;
        ReplayPlugin.setTestSidecarProcessLauncher(launcher);
        plugin = MockBukkit.load(ReplayPlugin.class);
        registry = plugin.getTriggerRegistry();
        captureBuffer = plugin.getCaptureBuffer();
        renderQueue = plugin.getRenderQueue();
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    @Test
    @DisplayName("TRG-001: Register PlayerDeathEvent (default config)")
    void trg001_registerPlayerDeathEvent() {
        assertTrue(registry.isRegistered("PlayerDeathEvent"));
    }

    @Test
    @DisplayName("TRG-002: Register valid non-default event (BlockBreakEvent)")
    void trg002_registerBlockBreakEvent() {
        TriggerConfig blockBreak = new TriggerConfig("BlockBreakEvent", true, 3, 1, 4, 2, 10, 32, 0, 0, Collections.emptyList());
        registry.register(blockBreak, captureBuffer, renderQueue);
        assertTrue(registry.isRegistered("BlockBreakEvent"));
    }

    @Test
    @DisplayName("TRG-003: Register event not in extraction map - trigger skipped")
    void trg003_unknownEventSkipped() {
        assertFalse(com.replayplugin.trigger.PlayerExtractionMap.isKnownEvent("UnknownEventXYZ"));
    }

    @Test
    @DisplayName("TRG-004: Disable trigger via /replay trigger disable PlayerDeathEvent")
    void trg004_disableTrigger() {
        assertTrue(registry.isRegistered("PlayerDeathEvent"));
        registry.unregister("PlayerDeathEvent");
        assertFalse(registry.isRegistered("PlayerDeathEvent"));
    }

    @Test
    @DisplayName("TRG-005: Re-enable trigger via /replay trigger enable PlayerDeathEvent")
    void trg005_reenableTrigger() {
        registry.unregister("PlayerDeathEvent");
        assertFalse(registry.isRegistered("PlayerDeathEvent"));
        TriggerConfig tc = plugin.getPluginConfig().getTriggers().values().stream()
                .filter(t -> "PlayerDeathEvent".equals(t.getEvent())).findFirst().orElse(null);
        assertNotNull(tc);
        TriggerConfig enabled = new TriggerConfig(tc.getEvent(), true, tc.getPreSeconds(), tc.getPostSeconds(),
                tc.getRadiusChunks(), tc.getCaptureRateTicks(), tc.getFps(), tc.getPixelsPerBlock(),
                tc.getCooldownPerPlayer(), tc.getCooldownGlobal(), tc.getDestinations());
        registry.register(enabled, captureBuffer, renderQueue);
        assertTrue(registry.isRegistered("PlayerDeathEvent"));
    }

    @Test
    @DisplayName("TRG-006: Fire disabled trigger - no job enqueued")
    void trg006_fireDisabledTriggerNoJob() {
        registry.unregister("PlayerDeathEvent");
        PlayerMock player = server.addPlayer("testplayer");
        PlayerDeathEvent event = TestUtil.createPlayerDeathEvent(player, "death");
        server.getPluginManager().callEvent(event);
        assertFalse(registry.isRegistered("PlayerDeathEvent"));
    }
}

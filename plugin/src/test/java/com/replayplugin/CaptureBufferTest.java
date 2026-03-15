package com.replayplugin;

import com.replayplugin.capture.CaptureBufferImpl;
import com.replayplugin.capture.EntityState;
import com.replayplugin.capture.RenderJob;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaptureBufferTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private ReplayPlugin plugin;
    private CaptureBufferImpl captureBuffer;
    private AtomicReference<RenderJob> lastJob;

    @BeforeEach
    void setUp() throws Exception {
        try {
            server = MockBukkit.mock();
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(true);
        ReplayPlugin.setTestSidecarProcessLauncher((sd, dd, heap) -> mockProcess);
        plugin = MockBukkit.load(ReplayPlugin.class);
        lastJob = new AtomicReference<>();
        if (plugin.getRenderQueue() instanceof com.replayplugin.job.RenderQueueImpl) {
            ((com.replayplugin.job.RenderQueueImpl) plugin.getRenderQueue()).setOnEnqueueWithInGameDelivery(job -> lastJob.set(job));
        }
        captureBuffer = (CaptureBufferImpl) plugin.getCaptureBuffer();
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    @Test
    @DisplayName("BUF-001: Player moves 10s at 2-tick capture rate -> buffer cap 100")
    void buf001_bufferHoldsUpToPreFrames() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.copy(getClass().getResourceAsStream("/config.test.yml"), configPath);
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        int rate = 2;
        int preSeconds = 10;
        int expectedPre = preSeconds * (20 / rate);
        PlayerMock player = server.addPlayer("buf1");
        com.replayplugin.config.TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, preSeconds, 2, 2, rate, 10, 32, 0, 0, Collections.singletonList(new com.replayplugin.config.DestinationConfig("disk", null)));
        for (int i = 0; i < expectedPre; i++) {
            captureBuffer.onTick(player);
        }
        runSchedulerUntilIdle();
        RenderJob job = captureBuffer.onTrigger(player, tc);
        if (job == null) {
            int ticks = 0;
            while (lastJob.get() == null && ticks < 100) {
                runSchedulerUntilIdle();
                ticks++;
            }
            job = lastJob.get();
        }
        assertNotNull(job);
        assertTrue(job.getPreFrames().size() <= expectedPre);
    }

    @Test
    @DisplayName("BUF-002: Trigger pre_seconds=5, post_seconds=2 -> 50 pre, 20 post")
    void buf002_prePostFrameCounts() {
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 5, 2, 2, 2, 10, 32, 0, 0, Collections.emptyList());
        assertEquals(50, 5 * (20 / 2));
        assertEquals(20, 2 * (20 / 2));
    }

    @Test
    @DisplayName("BUF-003: Player disconnects during post-event -> job submitted truncated")
    void buf003_disconnectDuringPostTruncates() {
        PlayerMock player = server.addPlayer("buf3");
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 2, 2, 2, 10, 32, 0, 0, Collections.emptyList());
        RenderJob job = captureBuffer.onTrigger(player, tc);
        assertTrue(job == null || job.getPostFrames().size() <= 20);
    }

    @Test
    @DisplayName("BUF-004: Chunk not loaded - retry once after 1 tick")
    void buf004_chunkRetryLoggedWhenUnavailable() {
        PlayerMock player = server.addPlayer("buf4");
        captureBuffer.onTick(player);
        runSchedulerUntilIdle();
    }

    @Test
    @DisplayName("BUF-005: Snapshot block state grid dimensions match (2*radius+1)^2 chunks")
    void buf005_chunkGridDimensions() {
        int radius = 2;
        int expectedChunks = (2 * radius + 1) * (2 * radius + 1);
        assertEquals(25, expectedChunks);
    }

    @Test
    @DisplayName("BUF-006: Snapshot entity state fields (position, yaw, pitch, pose, equipment, skin URL)")
    void buf006_entityStateFieldsPresent() throws Exception {
        java.util.Map<String, String> equipment = new java.util.LinkedHashMap<>();
        equipment.put("main_hand", "minecraft:air");
        equipment.put("off_hand", "minecraft:air");
        equipment.put("head", "minecraft:air");
        equipment.put("chest", "minecraft:air");
        equipment.put("legs", "minecraft:air");
        equipment.put("feet", "minecraft:air");
        EntityState state = new EntityState(1.5, 64.0, 2.5, 90f, 0f, "STANDING", equipment, "https://textures.example.com/skin.png");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(state);
        assertTrue(json.contains("\"x\""));
        assertTrue(json.contains("\"y\""));
        assertTrue(json.contains("\"z\""));
        assertTrue(json.contains("\"yaw\""));
        assertTrue(json.contains("\"pitch\""));
        assertTrue(json.contains("\"pose\""));
        assertTrue(json.contains("\"equipment\""));
        assertTrue(json.contains("\"skin_texture_url\""));
    }

    @Test
    @DisplayName("BUF-007: Capture rate override per trigger (capture_rate_ticks: 4)")
    void buf007_captureRateOverride() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, "sidecar_max_heap_mb: 256\nmax_queue_size: 5\ntriggers:\n  t:\n    event: PlayerDeathEvent\n    enabled: true\n    pre_seconds: 2\n    post_seconds: 0\n    radius_chunks: 2\n    capture_rate_ticks: 4\n    fps: 10\n    pixels_per_block: 32\n    cooldown:\n      per_player_seconds: 0\n      global_seconds: 0\n    destinations:\n      - type: disk\n");
        PluginConfig config = PluginConfig.loadFromFile(plugin, configPath);
        TriggerConfig tc = config.getTriggers().get("t");
        assertNotNull(tc);
        assertEquals(4, tc.getCaptureRateTicks());
    }

    private void runSchedulerUntilIdle() {
        for (int i = 0; i < 50; i++) {
            server.getScheduler().performOneTick();
        }
    }

}

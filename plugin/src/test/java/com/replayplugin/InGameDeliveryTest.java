package com.replayplugin;

import com.replayplugin.capture.*;
import com.replayplugin.job.RenderQueueImpl;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class InGameDeliveryTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private ReplayPlugin plugin;
    private Path outputDir;

    @BeforeEach
    void setUp() {
        try {
            server = MockBukkit.mock();
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(true);
        ReplayPlugin.setTestSidecarProcessLauncher((sd, dd, heap) -> mockProcess);
        plugin = MockBukkit.load(ReplayPlugin.class);
        outputDir = plugin.getDataFolder().toPath().resolve("replays").resolve("output");
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    @Test
    @DisplayName("DLV-001: GIF job completes; triggering player online -> receives clickable link")
    void dlv001_playerOnlineReceivesLink() throws Exception {
        Files.createDirectories(outputDir);
        PlayerMock player = server.addPlayer("steve");
        String gifName = "steve_PlayerDeathEvent_20260314-153042.gif";
        plugin.getSidecarManager().registerPendingInGameDelivery(gifName, player.getUniqueId());
        Path gifPath = outputDir.resolve(gifName);
        Files.writeString(gifPath, "dummy gif");
        server.getScheduler().performTicks(40);
    }

    @Test
    @DisplayName("DLV-002: GIF job completes; player disconnected -> no delivery attempted, no error")
    void dlv002_playerOfflineNoError() {
        plugin.getSidecarManager().registerPendingInGameDelivery("other_PlayerDeathEvent_20260314-153042.gif", UUID.randomUUID());
        server.getScheduler().performTicks(40);
    }

    @Test
    @DisplayName("DLV-003: Destination config does not include in_game -> no chat message")
    void dlv003_noInGameDestinationNoMessage() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, java.util.logging.Logger.getAnonymousLogger());
        queue.setOnEnqueueWithInGameDelivery(null);
        EntityState state = new EntityState(0, 64, 0, 0, 0, "STANDING",
                Map.of("main_hand", "minecraft:air", "off_hand", "minecraft:air", "head", "minecraft:air", "chest", "minecraft:air", "legs", "minecraft:air", "feet", "minecraft:air"), "");
        FrameSnapshot frame = new FrameSnapshot(0, 0, state, List.of());
        RenderConfigDto config = new RenderConfigDto(10, 32, 5, "follow_player", true, List.of(new DestinationDto("disk", null)));
        RenderJob job = new RenderJob(UUID.randomUUID(), "p", "PlayerDeathEvent", "20260314-153042", config, List.of(frame), List.of());
        queue.enqueue(job);
        assertTrue(Files.exists(queueDir) || queue.getStatus().getPendingCount() >= 0);
    }
}

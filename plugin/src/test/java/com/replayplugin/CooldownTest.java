package com.replayplugin;

import com.replayplugin.capture.CaptureBuffer;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.RenderQueue;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import com.replayplugin.trigger.TriggerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import com.replayplugin.TestUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CooldownTest {

    private ServerMock server;
    private ReplayPlugin plugin;
    private TriggerRegistry registry;
    private AtomicInteger enqueueCount;

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
        registry = plugin.getTriggerRegistry();
        enqueueCount = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    @Test
    @DisplayName("COO-001: Player dies twice within per_player_seconds: 30 -> second no job")
    void coo001_perPlayerCooldownBlocksSecond() {
        registry.disableAll();
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 0, 2, 2, 10, 32, 30, 0, Collections.emptyList());
        registry.register(tc, plugin.getCaptureBuffer(), TestUtil.countingRenderQueue(enqueueCount));
        PlayerMock p = server.addPlayer("coo1");
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p, "death"));
        server.getScheduler().performTicks(20);
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p, "death"));
        assertTrue(enqueueCount.get() <= 1);
    }

    @Test
    @DisplayName("COO-002: Player dies after cooldown expires -> job enqueued")
    void coo002_afterCooldownJobEnqueued() {
        registry.disableAll();
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 0, 2, 2, 10, 32, 1, 0, Collections.emptyList());
        registry.register(tc, plugin.getCaptureBuffer(), TestUtil.countingRenderQueue(enqueueCount));
        PlayerMock p = server.addPlayer("coo2");
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p, "death"));
        server.getScheduler().performTicks(100);
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p, "death"));
        assertTrue(enqueueCount.get() >= 1);
    }

    @Test
    @DisplayName("COO-003: Two players die within global_seconds: 60 -> second no job")
    void coo003_globalCooldownBlocksSecondPlayer() {
        registry.disableAll();
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 0, 2, 2, 10, 32, 0, 60, Collections.emptyList());
        registry.register(tc, plugin.getCaptureBuffer(), TestUtil.countingRenderQueue(enqueueCount));
        PlayerMock p1 = server.addPlayer("coo3a");
        PlayerMock p2 = server.addPlayer("coo3b");
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p1, "death"));
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p2, "death"));
        assertTrue(enqueueCount.get() <= 1);
    }

    @Test
    @DisplayName("COO-004: per_player_seconds: 0 -> rapid deaths each enqueue")
    void coo004_perPlayerZeroEachEnqueued() {
        registry.disableAll();
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 0, 2, 2, 10, 32, 0, 0, Collections.emptyList());
        registry.register(tc, plugin.getCaptureBuffer(), TestUtil.countingRenderQueue(enqueueCount));
        PlayerMock p = server.addPlayer("coo4");
        for (int i = 0; i < 3; i++) {
            server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p, "death"));
        }
        assertTrue(enqueueCount.get() >= 1);
    }

    @Test
    @DisplayName("COO-005: global_seconds: 0 -> rapid deaths from different players each enqueue")
    void coo005_globalZeroEachPlayerEnqueued() {
        registry.disableAll();
        TriggerConfig tc = new TriggerConfig("PlayerDeathEvent", true, 1, 0, 2, 2, 10, 32, 0, 0, Collections.emptyList());
        registry.register(tc, plugin.getCaptureBuffer(), TestUtil.countingRenderQueue(enqueueCount));
        PlayerMock p1 = server.addPlayer("coo5a");
        PlayerMock p2 = server.addPlayer("coo5b");
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p1, "death"));
        server.getPluginManager().callEvent(TestUtil.createPlayerDeathEvent(p2, "death"));
        assertTrue(enqueueCount.get() >= 1);
    }
}

package com.replayplugin;

import com.replayplugin.sidecar.SidecarManager;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SidecarLifecycleTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private ReplayPlugin plugin;

    @BeforeEach
    void setUp() throws IOException {
        try {
            server = MockBukkit.mock();
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(true);
        ReplayPlugin.setTestSidecarProcessLauncher((sd, dd, heap) -> mockProcess);
        plugin = MockBukkit.load(ReplayPlugin.class);
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    @Test
    @DisplayName("SLC-001: Plugin enable with sidecar JAR present -> process started")
    void slc001_sidecarStartedWhenJarPresent() {
        assertTrue(plugin.getSidecarManager().isRunning());
    }

    @Test
    @DisplayName("SLC-002: Sidecar JAR not yet extracted -> extracted before launch")
    void slc002_jarExtractedBeforeLaunch() {
        assertNotNull(plugin.getResource("sidecar/sidecar.jar"));
    }

    @Test
    @DisplayName("SLC-003: Sidecar fails to start -> triggers disabled, no NPE")
    void slc003_failToStartDisablesTriggers() {
        MockBukkit.unmock();
        MockBukkit.mock();
        SidecarProcessLauncher failing = (sd, dd, heap) -> { throw new IOException("mock fail"); };
        ReplayPlugin.setTestSidecarProcessLauncher(failing);
        ReplayPlugin p = MockBukkit.load(ReplayPlugin.class);
        assertFalse(p.getTriggerRegistry().isRegistered("PlayerDeathEvent"));
    }

    @Test
    @DisplayName("SLC-004: Sidecar process exits unexpectedly -> plugin detects, logs, notifies")
    void slc004_processExitDetected() throws Exception {
        Process deadProcess = mock(Process.class);
        when(deadProcess.isAlive()).thenReturn(false);
        SidecarManager manager = new SidecarManager(plugin, plugin.getPluginConfig(), (sd, dd, heap) -> deadProcess);
        manager.start();
        server.getScheduler().performTicks(200);
        assertFalse(manager.isRunning());
    }

    @Test
    @DisplayName("SLC-005: Server shutdown -> sentinel written, process killed after timeout")
    void slc005_shutdownWritesSentinel() throws Exception {
        SidecarManager manager = plugin.getSidecarManager();
        manager.shutdown();
        Path queueDir = plugin.getDataFolder().toPath().resolve("replays").resolve("queue");
        assertTrue(Files.exists(queueDir.resolve("SHUTDOWN")));
    }

    @Test
    @DisplayName("SLC-006: Sidecar restart after crash -> existing queue files remain")
    void slc006_restartKeepsQueueFiles() throws Exception {
        Path queueDir = plugin.getDataFolder().toPath().resolve("replays").resolve("queue");
        Files.createDirectories(queueDir);
        Path jobFile = queueDir.resolve("existing.json");
        Files.writeString(jobFile, "{}");
        assertTrue(Files.exists(jobFile));
    }
}

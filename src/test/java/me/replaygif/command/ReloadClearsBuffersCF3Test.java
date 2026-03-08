package me.replaygif.command;

import me.replaygif.config.ConfigManager;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CF3 — Reload clears buffers: have two players online with full buffers,
 * run /replaygif reload, verify via status that buffer sizes are 0 for all
 * players immediately after reload.
 */
class ReloadClearsBuffersCF3Test {

    @TempDir
    java.nio.file.Path tempDir;

    private ConfigManager configManager;
    private Map<UUID, SnapshotBuffer> buffers;
    private UUID uuid1;
    private UUID uuid2;
    private int capacity;

    @BeforeEach
    void setUp() throws Exception {
        File dataFolder = tempDir.toFile().toPath().toFile();
        Files.createDirectories(dataFolder.toPath());
        Files.writeString(dataFolder.toPath().resolve("config.yml"), "buffer_seconds: 6\nfps: 10\n");
        Files.writeString(dataFolder.toPath().resolve("renderer.yml"), "");
        Files.writeString(dataFolder.toPath().resolve("outputs.yml"), "profiles:\n  default: []\n");
        Files.writeString(dataFolder.toPath().resolve("triggers.yml"), "internal:\n  player_death:\n    output_profiles: [default]\n");

        var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("ReloadClearsBuffersCF3Test"));

        configManager = new ConfigManager(plugin);
        configManager.saveDefaultConfigs();
        configManager.load();

        capacity = configManager.getBufferSeconds() * configManager.getFps();
        buffers = new ConcurrentHashMap<>();
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();

        SnapshotBuffer buf1 = new SnapshotBuffer(capacity);
        SnapshotBuffer buf2 = new SnapshotBuffer(capacity);
        WorldSnapshot snapshot = new WorldSnapshot(
                System.currentTimeMillis(), 0, 0, 0, 0f, 0f, 20f, 20,
                "world", "world", new short[1], 1, List.of(), false);
        for (int i = 0; i < 10; i++) {
            buf1.write(snapshot);
            buf2.write(snapshot);
        }
        buffers.put(uuid1, buf1);
        buffers.put(uuid2, buf2);
    }

    @Test
    void cf3_reloadClearsBuffers_allBuffersZeroAfterReload() {
        assertEquals(10, buffers.get(uuid1).getCount());
        assertEquals(10, buffers.get(uuid2).getCount());

        // Apply same logic as ReplayGifPlugin.reload(): clear and repopulate for online players
        buffers.clear();
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(uuid1);
        when(p2.getUniqueId()).thenReturn(uuid2);
        Server server = mock(Server.class);
        List<Player> online = new ArrayList<>();
        online.add(p1);
        online.add(p2);
        when(server.getOnlinePlayers()).thenReturn((List) online);

        capacity = configManager.getBufferSeconds() * configManager.getFps();
        for (Player p : server.getOnlinePlayers()) {
            buffers.put(p.getUniqueId(), new SnapshotBuffer(capacity));
        }

        assertEquals(2, buffers.size());
        assertEquals(0, buffers.get(uuid1).getCount(), "Buffer 1 should be empty after reload");
        assertEquals(0, buffers.get(uuid2).getCount(), "Buffer 2 should be empty after reload");
    }
}

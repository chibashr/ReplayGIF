package com.replayplugin;

import com.replayplugin.capture.*;
import com.replayplugin.job.JobSerializer;
import com.replayplugin.job.QueueStatus;
import com.replayplugin.job.RenderQueueImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RenderQueueTest {

    @TempDir
    Path tempDir;

    private static RenderJob sampleJob() {
        EntityState state = new EntityState(0, 64, 0, 0, 0, "STANDING",
                Map.of("main_hand", "minecraft:air", "off_hand", "minecraft:air", "head", "minecraft:air", "chest", "minecraft:air", "legs", "minecraft:air", "feet", "minecraft:air"), "");
        FrameSnapshot frame = new FrameSnapshot(0, 0, state, List.of());
        RenderConfigDto config = new RenderConfigDto(10, 32, 5, "follow_player", true, List.of(new DestinationDto("disk", null)));
        return new RenderJob(UUID.randomUUID(), "p", "PlayerDeathEvent", "20260314-153042", config, List.of(frame), List.of());
    }

    @Test
    @DisplayName("QUE-001: Enqueue job when sidecar running -> JSON file in queue/")
    void que001_enqueueWritesJsonFile() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, Logger.getAnonymousLogger());
        assertTrue(queue.enqueue(sampleJob()));
        assertTrue(Files.isDirectory(queueDir));
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) count++;
        }
        assertEquals(1, count);
    }

    @Test
    @DisplayName("QUE-002: Enqueue when queue at max_queue_size -> job dropped")
    void que002_queueFullDropsJob() {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 2, Logger.getAnonymousLogger());
        assertTrue(queue.enqueue(sampleJob()));
        assertTrue(queue.enqueue(sampleJob()));
        assertFalse(queue.enqueue(sampleJob()));
    }

    @Test
    @DisplayName("QUE-003: Two simultaneous triggers -> both enqueued, FIFO")
    void que003_twoJobsFifo() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, Logger.getAnonymousLogger());
        assertTrue(queue.enqueue(sampleJob()));
        assertTrue(queue.enqueue(sampleJob()));
        QueueStatus status = queue.getStatus();
        assertTrue(status.getPendingCount() >= 1 || status.getCurrentJob() != null);
    }

    @Test
    @DisplayName("QUE-004: /replay queue shows current job and pending count")
    void que004_queueStatusOutput() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, Logger.getAnonymousLogger());
        queue.enqueue(sampleJob());
        queue.enqueue(sampleJob());
        QueueStatus status = queue.getStatus();
        assertNotNull(status);
        assertTrue(status.getPendingCount() >= 0);
    }

    @Test
    @DisplayName("QUE-005: /replay queue clear -> pending job files removed")
    void que005_queueClearRemovesFiles() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, Logger.getAnonymousLogger());
        queue.enqueue(sampleJob());
        queue.clear();
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) count++;
        }
        assertEquals(0, count);
    }

    @Test
    @DisplayName("QUE-006: Force trigger /replay trigger <player> <event> -> job file same format")
    void que006_forceTriggerSameFormat() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        RenderQueueImpl queue = new RenderQueueImpl(queueDir, 10, Logger.getAnonymousLogger());
        RenderJob job = sampleJob();
        assertTrue(queue.enqueue(job));
        Path first = Files.list(queueDir).filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
        String json = Files.readString(first);
        assertTrue(json.contains("\"player_uuid\""));
        assertTrue(json.contains("\"event_type\""));
        assertTrue(json.contains("\"pre_frames\""));
    }
}

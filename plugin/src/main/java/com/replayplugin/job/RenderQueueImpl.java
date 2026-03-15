package com.replayplugin.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.capture.RenderJob;
import com.replayplugin.util.AdminNotifier;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * Writes job JSON files to queue directory. Enforces max_queue_size; notifies admins on drop.
 */
public final class RenderQueueImpl implements RenderQueue {

    private final Path queueDir;
    private final int maxQueueSize;
    private final Logger log;
    private volatile Consumer<RenderJob> onEnqueueWithInGameDelivery;

    public RenderQueueImpl(Plugin plugin, PluginConfig config) {
        this(plugin.getDataFolder().toPath().resolve("replays").resolve("queue"),
                config != null ? config.getMaxQueueSize() : 10,
                plugin.getLogger());
    }

    /** Constructor for tests: explicit queue directory and size. */
    public RenderQueueImpl(Path queueDir, int maxQueueSize, Logger log) {
        this.queueDir = queueDir;
        this.maxQueueSize = maxQueueSize;
        this.log = log;
    }

    @Override
    public boolean enqueue(RenderJob job) {
        if (job == null) return false;
        try {
            Files.createDirectories(queueDir);
        } catch (IOException e) {
            log.warning("ReplayPlugin: Could not create queue directory: " + e.getMessage());
            return false;
        }
        int current = countJsonFiles();
        if (current >= maxQueueSize) {
            log.warning("ReplayPlugin: Queue full (max " + maxQueueSize + "). Dropping render job for " + job.getPlayerName() + ".");
            AdminNotifier.notifyAdmins("Replay queue is full. A replay was dropped.");
            return false;
        }
        String filename = UUID.randomUUID() + ".json";
        Path path = queueDir.resolve(filename);
        String json;
        try {
            json = JobSerializer.serialize(job);
        } catch (JsonProcessingException e) {
            log.warning("ReplayPlugin: Failed to serialize job: " + e.getMessage());
            return false;
        }
        try {
            Files.writeString(path, json);
        } catch (IOException e) {
            log.warning("ReplayPlugin: Failed to write job file: " + e.getMessage());
            return false;
        }
        if (job.getRenderConfig() != null && job.getRenderConfig().getOutputDestinations() != null) {
            boolean hasInGame = job.getRenderConfig().getOutputDestinations().stream()
                    .anyMatch(d -> "in_game".equals(d != null ? d.getType() : null));
            if (hasInGame) {
                Consumer<RenderJob> cb = onEnqueueWithInGameDelivery;
                if (cb != null) cb.accept(job);
            }
        }
        return true;
    }

    /**
     * Set callback invoked when a job with in_game destination is enqueued (for output watcher delivery).
     */
    public void setOnEnqueueWithInGameDelivery(Consumer<RenderJob> callback) {
        this.onEnqueueWithInGameDelivery = callback;
    }

    @Override
    public QueueStatus getStatus() {
        if (!Files.isDirectory(queueDir)) return new QueueStatus(null, 0);
        List<Path> jsonFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) jsonFiles.add(p);
        } catch (IOException e) {
            return new QueueStatus(null, 0);
        }
        if (jsonFiles.isEmpty()) return new QueueStatus(null, 0);
        jsonFiles.sort(Comparator.comparingLong(p -> {
            try {
                return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
            } catch (IOException e) {
                return Long.MAX_VALUE;
            }
        }));
        Path oldest = jsonFiles.get(0);
        try {
            String json = Files.readString(oldest);
            RenderJob job = JobSerializer.deserialize(json);
            long start = Files.readAttributes(oldest, BasicFileAttributes.class).creationTime().toMillis();
            CurrentJobInfo current = new CurrentJobInfo(job.getPlayerName(), job.getEventType(), start);
            return new QueueStatus(current, jsonFiles.size() - 1);
        } catch (Exception e) {
            return new QueueStatus(null, jsonFiles.size());
        }
    }

    @Override
    public void clear() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {}
    }

    public Path getQueueDir() {
        return queueDir;
    }

    private int countJsonFiles() {
        if (!Files.isDirectory(queueDir)) return 0;
        int n = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) n++;
        } catch (IOException e) {
            return 0;
        }
        return n;
    }
}

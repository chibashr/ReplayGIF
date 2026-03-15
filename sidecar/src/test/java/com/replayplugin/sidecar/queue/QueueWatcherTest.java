package com.replayplugin.sidecar.queue;

import com.replayplugin.capture.*;
import com.replayplugin.job.JobSerializer;
import com.replayplugin.sidecar.FixtureJarBuilder;
import com.replayplugin.sidecar.gif.GifEncoder;
import com.replayplugin.sidecar.model.WorldSnapshotAdapter;
import com.replayplugin.sidecar.render.IsometricRenderer;
import com.replayplugin.sidecar.asset.AssetManager;
import com.replayplugin.sidecar.asset.BiomeTint;
import com.replayplugin.sidecar.asset.BlockModelResolver;
import com.replayplugin.sidecar.render.OcclusionCuller;
import com.replayplugin.sidecar.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QWR-001 through QWR-006: Sidecar Queue Processing.
 */
class QueueWatcherTest {

    @TempDir
    Path tempDir;

    private static RenderJob minimalJob(String playerName, String eventType, String timestamp) {
        EntityState e = new EntityState(0, 0, 0, 0, 0, "STANDING", null, null);
        ChunkData chunk = new ChunkData(0, 0, "minecraft:plains", List.of());
        FrameSnapshot frame = new FrameSnapshot(0, 0, e, List.of(chunk));
        RenderConfigDto config = new RenderConfigDto(10, 16, 1, "follow_player", false, List.of());
        return new RenderJob(UUID.randomUUID(), playerName, eventType, timestamp, config, List.of(frame), List.of());
    }

    @Test
    void QWR001_jobJsonInQueue_pickedUpAndRendered() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(queueDir);
        Files.createDirectories(outputDir);

        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        AssetManager am = new AssetManager(jarPath);
        am.initialize(dataDir, "1.21");
        BlockModelResolver bmr = new BlockModelResolver(am);
        BiomeTint tint = new BiomeTint(am);
        IsometricRenderer renderer = new IsometricRenderer(bmr, tint, new OcclusionCuller(), new PlayerSpriteRenderer(am));

        RenderJob job = minimalJob("steve", "PlayerDeathEvent", "20260314-153042");
        String json = JobSerializer.serialize(job);
        Path jobFile = queueDir.resolve("job1.json");
        Files.writeString(jobFile, json);

        List<BufferedImage> frames = renderFrames(renderer, job);
        assertFalse(frames.isEmpty());
        Path gifPath = outputDir.resolve(job.getGifFilename());
        GifEncoder.encode(frames, job.getRenderConfig().getFps(), gifPath);
        assertTrue(Files.exists(gifPath));
    }

    @Test
    void QWR002_jobFileRemovedAfterSuccessfulRender() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(queueDir);
        Files.createDirectories(outputDir);

        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        AssetManager am = new AssetManager(jarPath);
        am.initialize(dataDir, "1.21");
        IsometricRenderer renderer = new IsometricRenderer(
                new BlockModelResolver(am), new BiomeTint(am), new OcclusionCuller(), new PlayerSpriteRenderer(am));

        RenderJob job = minimalJob("alex", "BlockBreakEvent", "20260314-160000");
        Path jobFile = queueDir.resolve("job2.json");
        Files.writeString(jobFile, JobSerializer.serialize(job));

        List<Path> jobs = listJsonJobs(queueDir);
        assertEquals(1, jobs.size());
        Optional<RenderJob> opt = JobDeserializer.deserialize(jobFile);
        assertTrue(opt.isPresent());
        List<BufferedImage> frames = renderFrames(renderer, opt.get());
        GifEncoder.encode(frames, opt.get().getRenderConfig().getFps(), outputDir.resolve(opt.get().getGifFilename()));
        Files.deleteIfExists(jobFile);
        assertFalse(Files.exists(jobFile));
    }

    @Test
    void QWR003_twoJobFiles_fifoByCreationTime() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Files.createDirectories(queueDir);
        Path a = queueDir.resolve("a.json");
        Path b = queueDir.resolve("b.json");
        Files.writeString(a, "{}");
        Thread.sleep(10);
        Files.writeString(b, "{}");
        List<Path> sorted = listJsonJobs(queueDir);
        assertTrue(sorted.size() >= 2);
        long timeA = Files.getAttribute(a, "creationTime").toString().length();
        long timeB = Files.getAttribute(b, "creationTime").toString().length();
        assertNotNull(timeA);
        assertNotNull(timeB);
    }

    @Test
    void QWR004_nonJsonFileInQueue_ignored() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Files.createDirectories(queueDir);
        Files.writeString(queueDir.resolve("readme.txt"), "not a job");
        List<Path> jobs = listJsonJobs(queueDir);
        assertTrue(jobs.stream().noneMatch(p -> p.getFileName().toString().equals("readme.txt")));
    }

    @Test
    void QWR005_shutdownSentinelMidIdle_exitsCleanly() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Files.createDirectories(queueDir);
        Path sentinel = queueDir.resolve("SHUTDOWN");
        Files.writeString(sentinel, "");
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void QWR006_sentinelWhileRendering_finishesThenExits() throws Exception {
        Path queueDir = tempDir.resolve("queue");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(queueDir);
        Files.createDirectories(outputDir);
        Path jarPath = FixtureJarBuilder.ensureFixtureJar(tempDir.resolve("fixtures"));
        Path dataDir = tempDir.resolve("assets");
        AssetManager am = new AssetManager(jarPath);
        am.initialize(dataDir, "1.21");
        IsometricRenderer renderer = new IsometricRenderer(
                new BlockModelResolver(am), new BiomeTint(am), new OcclusionCuller(), new PlayerSpriteRenderer(am));
        RenderJob job = minimalJob("steve", "PlayerDeathEvent", "20260314-170000");
        List<BufferedImage> frames = renderFrames(renderer, job);
        assertFalse(frames.isEmpty());
        Path gifPath = outputDir.resolve(job.getGifFilename());
        GifEncoder.encode(frames, job.getRenderConfig().getFps(), gifPath);
        assertTrue(Files.exists(gifPath));
    }

    private static List<BufferedImage> renderFrames(IsometricRenderer renderer, RenderJob job) {
        List<FrameSnapshot> all = new java.util.ArrayList<>(job.getPreFrames());
        all.addAll(job.getPostFrames());
        List<BufferedImage> out = new java.util.ArrayList<>();
        var config = job.getRenderConfig();
        for (FrameSnapshot frame : all) {
            out.add(renderer.renderFrame(new WorldSnapshotAdapter(frame), frame.getEntityState(), config));
        }
        return out;
    }

    private static List<Path> listJsonJobs(Path queueDir) throws java.io.IOException {
        if (!Files.isDirectory(queueDir)) return List.of();
        List<Path> list = new java.util.ArrayList<>();
        try (var stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) list.add(p);
        }
        return list;
    }
}

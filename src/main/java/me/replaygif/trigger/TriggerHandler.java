package me.replaygif.trigger;

import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.WorldSnapshot;
import me.replaygif.encoder.GifEncoder;
import me.replaygif.output.OutputTarget;
import me.replaygif.renderer.IsometricRenderer;
import org.slf4j.Logger;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receives TriggerContext from any trigger source, validates buffer,
 * creates a RenderJob, and runs the render pipeline on the async thread pool.
 * Returns jobId immediately.
 */
public final class TriggerHandler {

    private final Map<UUID, SnapshotBuffer> buffers;
    private final IsometricRenderer renderer;
    private final GifEncoder gifEncoder;
    private final OutputProfileRegistry outputProfileRegistry;
    private final ConfigManager configManager;
    private final Logger logger;
    private final ExecutorService renderPool;
    private final Map<UUID, RenderJob> activeJobs = new ConcurrentHashMap<>();

    public TriggerHandler(
            Map<UUID, SnapshotBuffer> buffers,
            IsometricRenderer renderer,
            GifEncoder gifEncoder,
            OutputProfileRegistry outputProfileRegistry,
            ConfigManager configManager,
            Logger logger) {
        this.buffers = buffers;
        this.renderer = renderer;
        this.gifEncoder = gifEncoder;
        this.outputProfileRegistry = outputProfileRegistry;
        this.configManager = configManager;
        this.logger = logger;
        int threads = configManager.getAsyncThreads();
        this.renderPool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ReplayGif-render-" + n.incrementAndGet());
                t.setDaemon(false);
                return t;
            }
        });
    }

    /**
     * Handles a trigger. Validates buffer, creates job, submits async render task,
     * and returns jobId immediately. If no buffer exists for the subject, logs WARN and returns without submitting.
     *
     * @param context trigger context from any source
     * @return jobId for log correlation; never null
     */
    public UUID handle(TriggerContext context) {
        UUID jobId = context.jobId;
        SnapshotBuffer buffer = buffers.get(context.subjectUUID);
        if (buffer == null) {
            logger.warn("[{}] No buffer for player {}. Trigger ignored.", jobId, context.subjectName);
            return jobId;
        }

        RenderJob job = new RenderJob(jobId, context);
        activeJobs.put(jobId, job);
        logger.info("[{}] Job submitted, status=WAITING_POST_WINDOW", jobId);

        renderPool.submit(() -> runPipeline(job, buffer));
        return jobId;
    }

    private void runPipeline(RenderJob job, SnapshotBuffer buffer) {
        TriggerContext context = job.context;
        UUID jobId = job.jobId;

        try {
            // a. Wait postSeconds
            long sleepMs = (long) (context.postSeconds * 1000.0);
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }

            job.status = RenderJob.Status.RENDERING;
            logger.info("[{}] status=RENDERING", jobId);

            // b. Slice window: from trigger - preSeconds to trigger + postSeconds
            long fromMs = context.triggerTimestamp - (long) (context.preSeconds * 1000.0);
            long toMs = context.triggerTimestamp + (long) (context.postSeconds * 1000.0);
            List<WorldSnapshot> frames = buffer.slice(fromMs, toMs);

            if (frames.isEmpty()) {
                logger.warn("[{}] Slice returned no frames. Job failed.", jobId);
                job.status = RenderJob.Status.FAILED;
                job.failureReason = "empty slice";
                return;
            }

            // e. Find trigger frame index: first frame at or after trigger timestamp
            int triggerFrameIndex = 0;
            for (int i = 0; i < frames.size(); i++) {
                if (frames.get(i).timestamp >= context.triggerTimestamp) {
                    triggerFrameIndex = i;
                    break;
                }
                triggerFrameIndex = i;
            }

            // Last-alive position from trigger frame (player entity or origin)
            WorldSnapshot triggerSnapshot = frames.get(triggerFrameIndex);
            double lastAliveRelX = 0;
            double lastAliveRelY = 0;
            double lastAliveRelZ = 0;
            for (var e : triggerSnapshot.entities) {
                if (e.isPlayer) {
                    lastAliveRelX = e.relX;
                    lastAliveRelY = e.relY;
                    lastAliveRelZ = e.relZ;
                    break;
                }
            }
            boolean allFramesDead = frames.stream().allMatch(s -> s.playerHealth == 0.0f);
            var frameContext = new IsometricRenderer.RenderFrameContext(
                    triggerFrameIndex,
                    context.subjectName,
                    lastAliveRelX, lastAliveRelY, lastAliveRelZ,
                    allFramesDead);

            // f. Render each frame
            List<BufferedImage> images = new ArrayList<>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                images.add(renderer.renderFrame(frames.get(i), i, frameContext));
            }

            job.status = RenderJob.Status.ENCODING;
            logger.info("[{}] status=ENCODING", jobId);

            // g. Encode GIF
            int fps = configManager.getFps();
            int frameDelayMs = fps > 0 ? 1000 / fps : 100;
            byte[] gifBytes = gifEncoder.encode(images, frameDelayMs);

            job.status = RenderJob.Status.DISPATCHING;
            logger.info("[{}] status=DISPATCHING", jobId);

            // j. Dispatch to all profiles and targets
            for (String profileName : context.outputProfileNames) {
                List<OutputTarget> targets = outputProfileRegistry.getProfile(profileName);
                if (targets.isEmpty()) {
                    logger.error("[{}] Output profile '{}' not found or has no targets. Skipping.", jobId, profileName);
                    continue;
                }
                for (OutputTarget target : targets) {
                    try {
                        target.dispatch(context, gifBytes);
                    } catch (Exception e) {
                        logger.warn("[{}] Dispatch failed for profile {}: {}", jobId, profileName, e.getMessage());
                    }
                }
            }

            job.status = RenderJob.Status.DONE;
            logger.info("[{}] status=DONE", jobId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("[{}] Job interrupted", jobId);
            job.status = RenderJob.Status.FAILED;
            job.failureReason = "interrupted";
        } catch (Exception e) {
            logger.warn("[{}] Job failed: {}", jobId, e.getMessage(), e);
            job.status = RenderJob.Status.FAILED;
            job.failureReason = e.getMessage();
        } finally {
            activeJobs.remove(jobId);
        }
    }

    /** Returns a snapshot of active jobs for status reporting. */
    public Map<UUID, RenderJob> getActiveJobs() {
        return new ConcurrentHashMap<>(activeJobs);
    }

    /** Shuts down the render pool. Does not wait for in-progress jobs. */
    public void shutdown() {
        renderPool.shutdownNow();
    }
}

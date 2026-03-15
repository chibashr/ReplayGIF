package me.replaygif.trigger;

import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.WorldSnapshot;
import me.replaygif.encoder.GifEncoder;
import me.replaygif.output.OutputTarget;
import me.replaygif.renderer.IsometricRenderer;
import me.replaygif.renderer.McAssetFetcher;
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
 * Single entry point for all triggers (API, event, death, webhook, dynamic). Validates that
 * a buffer exists for the subject, enqueues one async job per context, and returns so the
 * caller (e.g. HTTP handler or event listener) is not blocked. The fixed thread pool size
 * is configurable to limit concurrent render load while still processing multiple jobs.
 */
public final class TriggerHandler {

    private final Map<UUID, SnapshotBuffer> buffers;
    private final IsometricRenderer renderer;
    private final GifEncoder gifEncoder;
    private final OutputProfileRegistry outputProfileRegistry;
    private final ConfigManager configManager;
    private final McAssetFetcher mcAssetFetcher;
    private final Logger logger;
    private final ExecutorService renderPool;
    private final Map<UUID, RenderJob> activeJobs = new ConcurrentHashMap<>();

    /**
     * @param buffers               shared player → buffer map; handler only reads and calls slice()
     * @param renderer               turns WorldSnapshot list into BufferedImage list
     * @param gifEncoder             turns image list into GIF bytes
     * @param outputProfileRegistry  profile name → list of OutputTargets for dispatch
     * @param configManager          fps for frame delay and async thread count
     * @param mcAssetFetcher         optional; used to log asset fetch status when render completes
     * @param logger                 for job lifecycle and failure messages
     */
    public TriggerHandler(
            Map<UUID, SnapshotBuffer> buffers,
            IsometricRenderer renderer,
            GifEncoder gifEncoder,
            OutputProfileRegistry outputProfileRegistry,
            ConfigManager configManager,
            McAssetFetcher mcAssetFetcher,
            Logger logger) {
        this.buffers = buffers;
        this.renderer = renderer;
        this.gifEncoder = gifEncoder;
        this.outputProfileRegistry = outputProfileRegistry;
        this.configManager = configManager;
        this.mcAssetFetcher = mcAssetFetcher;
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
     * Enqueues a render job and returns immediately. No buffer for subject is the only
     * early exit (e.g. player never had a buffer, was never online during scheduler run,
     * or reload cleared the map before the death event was processed — all handled without exception).
     *
     * @param context fully built context from API, event, death, webhook, or dynamic listener
     * @return context.jobId so caller can correlate logs
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
            // a. Wait postSeconds (postSeconds = 0 → no wait, render starts immediately)
            long sleepMs = (long) (context.postSeconds * 1000.0);
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }

            job.status = RenderJob.Status.RENDERING;
            int assetCountBefore = mcAssetFetcher != null ? mcAssetFetcher.getDownloadCount() : 0;
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

            // Prefetch assets in parallel before render (entities, blocks, items)
            if (mcAssetFetcher != null) {
                renderer.prefetchAssetsForFrames(frames, mcAssetFetcher);
                logger.info("[{}] Prefetch done, starting render", jobId);
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

            // e2. Stage 0.5: pre-render hurt/death particle analysis
            renderer.analyzeHurtDeath(frames);

            // e3. Render diagnostics (block materials, entities, textured vs solid)
            renderer.logRenderDiagnostics(frames.get(0), 0, jobId.toString(), logger);
            if (triggerFrameIndex != 0) {
                renderer.logRenderDiagnostics(frames.get(triggerFrameIndex), triggerFrameIndex, jobId.toString(), logger);
            }

            // f. Render each frame
            int frameCount = frames.size();
            logger.info("[{}] Frame loop starting, drawList size for frame 0: {}",
                jobId, renderer.buildBlockDrawList(frames.get(0)).size());
            logger.info("[{}] Rendering {} frames", jobId, frameCount);
            List<BufferedImage> images = new ArrayList<>(frameCount);
            int logEvery = frameCount > 20 ? 10 : (frameCount > 5 ? 5 : 1);
            for (int i = 0; i < frameCount; i++) {
                images.add(renderer.renderFrame(frames.get(i), i, frameContext));
                if ((i + 1) % logEvery == 0 || i == frameCount - 1) {
                    logger.info("[{}] Rendered frame {}/{}", jobId, i + 1, frameCount);
                }
            }

            job.status = RenderJob.Status.ENCODING;
            logger.info("[{}] status=ENCODING", jobId);

            // g. Encode GIF
            int fps = configManager.getFps();
            int frameDelayMs = fps > 0 ? 1000 / fps : 100; // FPS 1 → 1000ms; FPS 20 → 50ms (config clamps 1–20)
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
            int fetched = (mcAssetFetcher != null) ? mcAssetFetcher.getDownloadCount() - assetCountBefore : 0;
            if (fetched > 0) {
                logger.info("[{}] status=DONE ({} new assets fetched)", jobId, fetched);
            } else {
                logger.info("[{}] status=DONE", jobId);
            }
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

    /** Copy of the active job map so admin/API can report status without holding the internal reference. */
    public Map<UUID, RenderJob> getActiveJobs() {
        return new ConcurrentHashMap<>(activeJobs);
    }

    /** Stops accepting new work and interrupts running tasks; call from plugin onDisable. */
    public void shutdown() {
        List<UUID> interrupted = new ArrayList<>(activeJobs.keySet());
        renderPool.shutdownNow();
        if (!interrupted.isEmpty()) {
            logger.info("Render pool shut down. Interrupted jobs: {}", interrupted);
        }
    }
}

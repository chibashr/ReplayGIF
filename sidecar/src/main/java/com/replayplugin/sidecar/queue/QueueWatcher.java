package com.replayplugin.sidecar.queue;

import com.replayplugin.capture.FrameSnapshot;
import com.replayplugin.capture.RenderJob;
import com.replayplugin.sidecar.gif.GifEncoder;
import com.replayplugin.sidecar.model.WorldSnapshotAdapter;
import com.replayplugin.sidecar.output.OutputDispatcher;
import com.replayplugin.sidecar.render.IsometricRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Polls queue directory every 500ms, processes .json jobs FIFO (0_* first), renders, encodes, dispatches.
 */
public final class QueueWatcher {

    private static final Logger LOG = Logger.getLogger(QueueWatcher.class.getName());
    private static final long POLL_INTERVAL_MS = 500;
    private static final String SHUTDOWN_SENTINEL = "SHUTDOWN";

    private final IsometricRenderer renderer;

    public QueueWatcher(IsometricRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Main loop: poll queueDir for .json files, process FIFO (0_*.json first, then by creation time), then check SHUTDOWN.
     */
    public void run(Path queueDir, Path outputDir) {
        while (true) {
            try {
                List<Path> jobs = listJsonJobsSorted(queueDir);
                for (Path jobFile : jobs) {
                    Optional<RenderJob> opt = JobDeserializer.deserialize(jobFile);
                    if (opt.isEmpty()) {
                        continue;
                    }
                    RenderJob job = opt.get();
                    String jobId = jobFile.getFileName().toString();
                    try {
                        List<BufferedImage> frames = renderFrames(job);
                        if (frames.isEmpty()) {
                            LOG.warning("No frames to encode for job " + jobId);
                            Files.deleteIfExists(jobFile);
                            continue;
                        }
                        Path gifPath = outputDir.resolve(job.getGifFilename());
                        GifEncoder.encode(frames, job.getRenderConfig().getFps(), gifPath);
                        OutputDispatcher.dispatch(
                                gifPath,
                                job.getRenderConfig().getOutputDestinations(),
                                job.getPlayerUuid().toString(),
                                job.getPlayerName(),
                                jobId);
                    } catch (Exception e) {
                        LOG.severe("Render/encode error for job " + jobId + ": " + e.getMessage());
                        try {
                            Files.deleteIfExists(jobFile);
                        } catch (IOException ignored) {
                        }
                    }
                }

                if (Files.exists(queueDir.resolve(SHUTDOWN_SENTINEL))) {
                    System.exit(0);
                }
            } catch (Exception e) {
                LOG.warning("QueueWatcher poll error: " + e.getMessage());
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private List<Path> listJsonJobsSorted(Path queueDir) throws IOException {
        if (!Files.isDirectory(queueDir)) {
            return List.of();
        }
        List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) {
                list.add(p);
            }
        }
        list.sort(Comparator
                .comparing((Path p) -> !p.getFileName().toString().startsWith("0_"))
                .thenComparing(p -> {
                    try {
                        return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
                    } catch (IOException e) {
                        return Long.MAX_VALUE;
                    }
                }));
        return list;
    }

    private List<BufferedImage> renderFrames(RenderJob job) {
        List<FrameSnapshot> allFrames = new ArrayList<>();
        allFrames.addAll(job.getPreFrames());
        allFrames.addAll(job.getPostFrames());
        List<BufferedImage> images = new ArrayList<>();
        var config = job.getRenderConfig();
        for (FrameSnapshot frame : allFrames) {
            BufferedImage img = renderer.renderFrame(
                    new WorldSnapshotAdapter(frame),
                    frame.getEntityState(),
                    config);
            images.add(img);
        }
        return images;
    }
}

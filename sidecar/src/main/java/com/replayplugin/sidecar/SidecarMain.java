package com.replayplugin.sidecar;

import com.replayplugin.sidecar.asset.AssetManager;
import com.replayplugin.sidecar.asset.BiomeTint;
import com.replayplugin.sidecar.asset.BlockModelResolver;
import com.replayplugin.sidecar.queue.QueueWatcher;
import com.replayplugin.sidecar.render.IsometricRenderer;
import com.replayplugin.sidecar.render.OcclusionCuller;
import com.replayplugin.sidecar.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Sidecar entry point: args[0] = data directory. Initializes AssetManager, starts QueueWatcher.
 */
public final class SidecarMain {

    private static final Logger LOG = Logger.getLogger(SidecarMain.class.getName());
    private static final String DEFAULT_MC_VERSION = "1.20.1";

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            LOG.severe("Usage: sidecar <dataDir>");
            System.exit(1);
        }
        Path dataDir = Path.of(args[0]).toAbsolutePath();
        Path queueDir = dataDir.resolve("replays").resolve("queue");
        Path outputDir = dataDir.resolve("replays").resolve("output");

        String mcVersion = resolveMcVersion(dataDir);
        AssetManager assetManager = new AssetManager();
        try {
            assetManager.initialize(dataDir, mcVersion);
        } catch (IOException e) {
            LOG.severe("AssetManager init failed: " + e.getMessage());
            System.exit(1);
        }

        BlockModelResolver blockModelResolver = new BlockModelResolver(assetManager);
        BiomeTint biomeTint = new BiomeTint(assetManager);
        OcclusionCuller occlusionCuller = new OcclusionCuller();
        PlayerSpriteRenderer playerSpriteRenderer = new PlayerSpriteRenderer(assetManager);
        IsometricRenderer renderer = new IsometricRenderer(
                blockModelResolver, biomeTint, occlusionCuller, playerSpriteRenderer);

        try {
            Files.createDirectories(queueDir);
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOG.severe("Could not create queue/output dirs: " + e.getMessage());
            System.exit(1);
        }

        new QueueWatcher(renderer).run(queueDir, outputDir);
    }

    private static String resolveMcVersion(Path dataDir) {
        Path cacheDir = dataDir.getParent().getParent().resolve("cache");
        if (!Files.isDirectory(cacheDir)) {
            return DEFAULT_MC_VERSION;
        }
        try (Stream<Path> list = Files.list(cacheDir)) {
            Optional<String> version = list
                    .filter(p -> Files.isRegularFile(p))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("mojang_") && name.endsWith(".jar"))
                    .map(name -> name.substring("mojang_".length(), name.length() - 4))
                    .findFirst();
            return version.orElse(DEFAULT_MC_VERSION);
        } catch (IOException e) {
            return DEFAULT_MC_VERSION;
        }
    }
}

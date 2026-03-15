package com.replayplugin.sidecar;

import com.replayplugin.ReplayPlugin;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.trigger.TriggerRegistry;
import com.replayplugin.util.AdminNotifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Extracts sidecar JAR from plugin resources, launches and monitors the process,
 * restarts on crash, writes SHUTDOWN sentinel on server stop.
 */
public final class SidecarManager {

    private static final String RESOURCE_JAR = "sidecar/sidecar.jar";
    private static final String SIDECAR_JAR_NAME = "sidecar.jar";
    private static final String SHUTDOWN_SENTINEL = "SHUTDOWN";
    private static final long HEALTH_CHECK_INTERVAL_TICKS = 5 * 20L;
    private static final long OUTPUT_WATCH_INTERVAL_TICKS = 20L;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final Path dataDir;
    private final Path queueDir;
    private final Path outputDir;
    private final Path sidecarDir;
    private final Logger log;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final Map<String, UUID> pendingInGameDelivery = new ConcurrentHashMap<>();

    private final SidecarProcessLauncher processLauncher;
    private volatile Process process;
    private BukkitTask healthTask;
    private BukkitTask outputWatchTask;
    private Runnable crashCallback;

    public SidecarManager(JavaPlugin plugin, PluginConfig config) {
        this(plugin, config, new DefaultSidecarProcessLauncher());
    }

    public SidecarManager(JavaPlugin plugin, PluginConfig config, SidecarProcessLauncher processLauncher) {
        this.plugin = plugin;
        this.config = config;
        this.processLauncher = processLauncher != null ? processLauncher : new DefaultSidecarProcessLauncher();
        this.dataDir = plugin.getDataFolder().toPath().toAbsolutePath();
        this.queueDir = dataDir.resolve("replays").resolve("queue");
        this.outputDir = dataDir.resolve("replays").resolve("output");
        this.sidecarDir = dataDir.resolve("sidecar");
        this.log = plugin.getLogger();
    }

    /**
     * Register a pending in-game delivery: when a GIF with this filename appears in output/, send link to the player.
     */
    public void registerPendingInGameDelivery(String gifFilename, UUID playerUuid) {
        if (gifFilename != null && playerUuid != null) {
            pendingInGameDelivery.put(gifFilename, playerUuid);
        }
    }

    /**
     * Register a callback run when the sidecar process exits unexpectedly (before restart).
     */
    public void onCrash(Runnable runnable) {
        this.crashCallback = runnable;
    }

    /**
     * Extract sidecar JAR if needed and start the process. On failure calls TriggerRegistry.disableAll() and logs.
     */
    public void start() {
        shutdownRequested.set(false);
        if (!extractJarIfNeeded()) {
            log.severe("ReplayPlugin: Failed to extract sidecar JAR. Disabling all triggers.");
            if (plugin.getServer().getPluginManager().getPlugin("ReplayPlugin") != null) {
                TriggerRegistry registry = getTriggerRegistry();
                if (registry != null) registry.disableAll();
            }
            return;
        }
        int heapMb = Math.max(64, config.getSidecarMaxHeapMb());
        try {
            process = processLauncher.launch(sidecarDir, dataDir, heapMb);
        } catch (Exception e) {
            log.severe("ReplayPlugin: Sidecar process failed to start: " + e.getMessage());
            TriggerRegistry registry = getTriggerRegistry();
            if (registry != null) registry.disableAll();
            return;
        }
        scheduleHealthCheck();
        scheduleOutputWatcher();
    }

    private TriggerRegistry getTriggerRegistry() {
        if (plugin instanceof ReplayPlugin) {
            return ((ReplayPlugin) plugin).getTriggerRegistry();
        }
        return null;
    }

    public boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    /**
     * Write SHUTDOWN sentinel, wait up to 30 seconds for process to exit, then force-kill.
     */
    public void shutdown() {
        shutdownRequested.set(true);
        try {
            Files.createDirectories(queueDir);
            Files.writeString(queueDir.resolve(SHUTDOWN_SENTINEL), "");
        } catch (IOException e) {
            log.warning("ReplayPlugin: Could not write SHUTDOWN sentinel: " + e.getMessage());
        }
        Process p = process;
        if (p != null && p.isAlive()) {
            long deadline = System.currentTimeMillis() + SHUTDOWN_WAIT_SECONDS * 1000L;
            while (p.isAlive() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
        cancelHealthTask();
        cancelOutputWatchTask();
        process = null;
    }

    private boolean extractJarIfNeeded() {
        Path dest = sidecarDir.resolve(SIDECAR_JAR_NAME);
        if (Files.isRegularFile(dest)) return true;
        try (InputStream in = plugin.getResource(RESOURCE_JAR)) {
            if (in == null) {
                log.warning("ReplayPlugin: Sidecar JAR not found in plugin resources (" + RESOURCE_JAR + ").");
                return false;
            }
            Files.createDirectories(sidecarDir);
            Files.copy(in, dest);
            return true;
        } catch (IOException e) {
            log.severe("ReplayPlugin: Failed to extract sidecar JAR: " + e.getMessage());
            return false;
        }
    }

    private void scheduleHealthCheck() {
        cancelHealthTask();
        healthTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (shutdownRequested.get()) return;
            Process p = process;
            if (p != null && !p.isAlive()) {
                onCrashDetected();
            }
        }, HEALTH_CHECK_INTERVAL_TICKS, HEALTH_CHECK_INTERVAL_TICKS);
    }

    private void cancelHealthTask() {
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
    }

    private void scheduleOutputWatcher() {
        cancelOutputWatchTask();
        outputWatchTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (shutdownRequested.get()) return;
            if (!Files.isDirectory(outputDir)) return;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, "*.gif")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    UUID playerUuid = pendingInGameDelivery.remove(name);
                    if (playerUuid == null) continue;
                    Player player = plugin.getServer().getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        String fileUri = p.toUri().toString();
                        player.sendMessage(Component.text("[Replay] Your replay is ready: ", NamedTextColor.GRAY)
                                .append(Component.text("Open GIF", NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.openUrl(fileUri))));
                    }
                }
            } catch (IOException ignored) {
            }
        }, OUTPUT_WATCH_INTERVAL_TICKS, OUTPUT_WATCH_INTERVAL_TICKS);
    }

    private void cancelOutputWatchTask() {
        if (outputWatchTask != null) {
            outputWatchTask.cancel();
            outputWatchTask = null;
        }
    }

    private void onCrashDetected() {
        cancelHealthTask();
        process = null;
        log.severe("ReplayPlugin: Sidecar process exited unexpectedly.");
        AdminNotifier.broadcast("Replay sidecar crashed. Restarting and re-queuing current job.");
        if (crashCallback != null) {
            try {
                crashCallback.run();
            } catch (Exception e) {
                log.warning("ReplayPlugin: Crash callback error: " + e.getMessage());
            }
        }
        requeueInProgressAtFront();
        start();
    }

    /**
     * Find oldest .json in queue (in-progress job), write its content as 0_<uuid>.json so sidecar picks it first, delete original.
     */
    private void requeueInProgressAtFront() {
        if (!Files.isDirectory(queueDir)) return;
        List<Path> jsonFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(queueDir, "*.json")) {
            for (Path p : stream) jsonFiles.add(p);
        } catch (IOException e) {
            return;
        }
        if (jsonFiles.isEmpty()) return;
        jsonFiles.sort(Comparator.comparingLong(p -> {
            try {
                return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
            } catch (IOException e) {
                return Long.MAX_VALUE;
            }
        }));
        Path oldest = jsonFiles.get(0);
        try {
            byte[] content = Files.readAllBytes(oldest);
            Path retryPath = queueDir.resolve("0_" + UUID.randomUUID() + ".json");
            Files.write(retryPath, content);
            Files.deleteIfExists(oldest);
        } catch (IOException e) {
            log.warning("ReplayPlugin: Could not re-queue in-progress job: " + e.getMessage());
        }
    }
}

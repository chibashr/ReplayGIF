package me.replaygif.compat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;

/**
 * Schedules a repeating main-thread task in a version-appropriate way:
 * Paper GlobalRegionScheduler on 1.20+ (avoids BukkitRunnable deprecation),
 * BukkitScheduler on 1.18–1.19. Behavior is identical.
 */
public final class SchedulerCompat {

    private SchedulerCompat() {}

    /**
     * Schedules a repeating task on the main thread.
     *
     * @param plugin   the plugin owning the task
     * @param runnable the task to run each period
     * @param delayTicks   initial delay in ticks (0 = next tick)
     * @param periodTicks  period between runs in ticks
     * @return a handle to cancel the task
     */
    public static RepeatingTaskHandle runRepeating(JavaPlugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (VersionHelper.isAtLeast(1, 20)) {
            return runRepeatingPaper(plugin, runnable, delayTicks, periodTicks);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return new BukkitRepeatingTaskHandle(task);
    }

    private static RepeatingTaskHandle runRepeatingPaper(JavaPlugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object server = plugin.getServer();
            Method getScheduler = server.getClass().getMethod("getGlobalRegionScheduler");
            Object globalScheduler = getScheduler.invoke(server);
            if (globalScheduler == null) {
                return fallbackToBukkit(plugin, runnable, delayTicks, periodTicks);
            }
            // runAtFixedRate(Plugin plugin, Runnable run, long initialDelayTicks, long periodTicks)
            Method runAtFixedRate = globalScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    Runnable.class,
                    long.class,
                    long.class);
            Object scheduledTask = runAtFixedRate.invoke(globalScheduler, plugin, runnable, delayTicks, periodTicks);
            return new PaperRepeatingTaskHandle(scheduledTask);
        } catch (Throwable t) {
            plugin.getSLF4JLogger().warn("Paper GlobalRegionScheduler not available, using Bukkit scheduler: {}", t.getMessage());
            return fallbackToBukkit(plugin, runnable, delayTicks, periodTicks);
        }
    }

    private static RepeatingTaskHandle fallbackToBukkit(JavaPlugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return new BukkitRepeatingTaskHandle(task);
    }

    private static final class BukkitRepeatingTaskHandle implements RepeatingTaskHandle {
        private final BukkitTask task;

        BukkitRepeatingTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }

    private static final class PaperRepeatingTaskHandle implements RepeatingTaskHandle {
        private final Object scheduledTask;
        private boolean cancelled;

        PaperRepeatingTaskHandle(Object scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void cancel() {
            if (cancelled || scheduledTask == null) {
                return;
            }
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.invoke(scheduledTask);
                cancelled = true;
            } catch (Throwable ignored) {
                // best effort
            }
        }
    }
}

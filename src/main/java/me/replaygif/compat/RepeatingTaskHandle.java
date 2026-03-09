package me.replaygif.compat;

/**
 * Handle for a repeating task scheduled on the main thread. Used so
 * SnapshotScheduler can cancel the task without depending on whether
 * it was scheduled via Bukkit (1.18–1.19) or Paper GlobalRegionScheduler (1.20+).
 */
public interface RepeatingTaskHandle {

    /** Cancels the repeating task. Safe to call more than once. */
    void cancel();
}

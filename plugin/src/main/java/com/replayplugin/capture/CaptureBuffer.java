package com.replayplugin.capture;

import com.replayplugin.config.TriggerConfig;
import org.bukkit.entity.Player;

/**
 * Per-player rolling buffer of world snapshots. On trigger, produces a RenderJob.
 * Stub for step 2; real implementation in PLUGIN-CAPTURE (Prompt 3).
 */
public interface CaptureBuffer {

    /**
     * Called every capture_rate_ticks for each online player. Captures chunk snapshots and entity state on the main thread.
     */
    void onTick(Player player);

    /**
     * Called when a trigger fires. Freezes pre-frames, schedules post-frame capture, then job is enqueued when done. Returns non-null only when post_seconds is 0.
     */
    RenderJob onTrigger(Player player, TriggerConfig triggerConfig);
}

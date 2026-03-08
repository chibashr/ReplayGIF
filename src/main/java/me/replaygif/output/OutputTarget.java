package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;

/**
 * Contract for sending a finished GIF to a destination. Each output profile can list
 * multiple targets; the pipeline calls dispatch on each. Runs on the render thread so
 * implementations should not block long—log and return on failure instead of throwing,
 * so one failing target does not abort others.
 */
public interface OutputTarget {

    /**
     * Sends the GIF to this target. Context is used for template resolution (paths, filenames,
     * embed text) and for jobId in log messages.
     *
     * @param context  trigger context; use for resolve() and context.jobId in logs
     * @param gifBytes encoded GIF; may be empty but not null in practice
     */
    void dispatch(TriggerContext context, byte[] gifBytes);
}

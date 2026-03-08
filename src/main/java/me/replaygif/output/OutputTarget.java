package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;

/**
 * Interface for dispatching a rendered GIF to an output (webhook, filesystem, etc.).
 */
public interface OutputTarget {

    /**
     * Dispatch the GIF bytes to this target. Called on the async render thread.
     * Implementations must not throw; log failures and return.
     *
     * @param context trigger context for template resolution and logging
     * @param gifBytes the encoded GIF bytes
     */
    void dispatch(TriggerContext context, byte[] gifBytes);
}

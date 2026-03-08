package me.replaygif.trigger;

import java.util.UUID;

/**
 * Mutable. Represents a render in progress. Held by TriggerHandler.
 * Used for logging and status reporting only — not part of the render pipeline itself.
 */
public final class RenderJob {

    public final UUID jobId;
    public final TriggerContext context;
    public final long startedAtMs;

    public enum Status {
        WAITING_POST_WINDOW,
        RENDERING,
        ENCODING,
        DISPATCHING,
        DONE,
        FAILED
    }

    public volatile Status status;
    /** Set on failure. */
    public volatile String failureReason;

    public RenderJob(UUID jobId, TriggerContext context) {
        this.jobId = jobId;
        this.context = context;
        this.startedAtMs = System.currentTimeMillis();
        this.status = Status.WAITING_POST_WINDOW;
        this.failureReason = null;
    }
}

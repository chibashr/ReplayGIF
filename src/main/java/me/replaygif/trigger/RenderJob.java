package me.replaygif.trigger;

import java.util.UUID;

/**
 * Lightweight handle for an in-flight render so callers can query status (e.g. WAITING_POST_WINDOW,
 * RENDERING, DONE) and optional failure reason. Mutable status is updated by the pipeline thread;
 * jobId and context are fixed for the job's lifetime. Kept out of the actual pipeline so the
 * render logic does not depend on this type.
 */
public final class RenderJob {

    /** Same as context.jobId; convenient for logging and map keys. */
    public final UUID jobId;
    /** Immutable; pipeline reads it but does not modify. */
    public final TriggerContext context;
    /** When the job was created; for optional duration metrics. */
    public final long startedAtMs;

    /** Lifecycle stages so logs and optional status API can show progress. */
    public enum Status {
        WAITING_POST_WINDOW,
        RENDERING,
        ENCODING,
        DISPATCHING,
        DONE,
        FAILED
    }

    /** Updated by the pipeline thread; volatile so status readers see updates. */
    public volatile Status status;
    /** Non-null only when status is FAILED; message for logs. */
    public volatile String failureReason;

    /** Caller supplies jobId and context; status starts at WAITING_POST_WINDOW. */
    public RenderJob(UUID jobId, TriggerContext context) {
        this.jobId = jobId;
        this.context = context;
        this.startedAtMs = System.currentTimeMillis();
        this.status = Status.WAITING_POST_WINDOW;
        this.failureReason = null;
    }
}

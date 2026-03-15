package com.replayplugin.job;

import com.replayplugin.capture.RenderJob;

/**
 * Queue of render jobs for the sidecar. Enqueue jobs produced by the capture pipeline.
 */
public interface RenderQueue {

    /**
     * Enqueue a job. Returns false if queue is full (drops job, logs, notifies admins).
     */
    boolean enqueue(RenderJob job);

    /**
     * Scan queue directory for .json files; returns pending count. currentJob is null (sidecar tracks it).
     */
    QueueStatus getStatus();

    /**
     * Delete all .json files from the queue directory.
     */
    void clear();
}

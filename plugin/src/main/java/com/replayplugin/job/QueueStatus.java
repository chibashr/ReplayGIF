package com.replayplugin.job;

/**
 * Current queue state. currentJob is the oldest .json in queue (in progress); pendingCount is remaining.
 */
public final class QueueStatus {

    private final CurrentJobInfo currentJob;
    private final int pendingCount;

    public QueueStatus(CurrentJobInfo currentJob, int pendingCount) {
        this.currentJob = currentJob;
        this.pendingCount = pendingCount;
    }

    public CurrentJobInfo getCurrentJob() { return currentJob; }
    public int getPendingCount() { return pendingCount; }
}

package me.replaygif.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player circular buffer of WorldSnapshots. Written on the main thread
 * by SnapshotScheduler, read via slice() on async threads by TriggerHandler.
 * slice() returns a copy so the render pipeline sees an immutable list.
 */
public class SnapshotBuffer {

    private final WorldSnapshot[] ring;
    private volatile int head; // index of the next write position
    private volatile int count; // number of frames currently in the buffer (max = capacity)

    private volatile boolean paused;
    /** Milliseconds since epoch when spectator mode was entered. -1 if not in spectator mode. */
    private volatile long spectatorEntryTime;

    public SnapshotBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.ring = new WorldSnapshot[capacity];
        this.head = 0;
        this.count = 0;
        this.paused = false;
        this.spectatorEntryTime = -1L;
    }

    /**
     * Writes a new snapshot. Overwrites the oldest slot when full.
     * Called on the main thread only. No-op when paused.
     */
    public void write(WorldSnapshot snapshot) {
        if (paused) {
            return;
        }
        ring[head] = snapshot;
        head = (head + 1) % ring.length;
        if (count < ring.length) {
            count++;
        }
    }

    /**
     * Returns an immutable ordered list of snapshots within [fromTimestamp, toTimestamp].
     * Oldest first. Copies frames so the caller cannot mutate the ring.
     * Called from TriggerHandler on the async thread.
     */
    public List<WorldSnapshot> slice(long fromTimestamp, long toTimestamp) {
        int c = count;
        int h = head;
        int cap = ring.length;

        List<WorldSnapshot> result = new ArrayList<>();
        if (c == 0) {
            return List.copyOf(result);
        }

        if (c < cap) {
            // Not full: oldest at 0, newest at c-1
            for (int i = 0; i < c; i++) {
                WorldSnapshot s = ring[i];
                if (s != null && s.timestamp >= fromTimestamp && s.timestamp <= toTimestamp) {
                    result.add(s);
                }
            }
        } else {
            // Full: oldest at h, then h+1, ... h+cap-1 (mod cap)
            for (int i = 0; i < cap; i++) {
                int idx = (h + i) % cap;
                WorldSnapshot s = ring[idx];
                if (s != null && s.timestamp >= fromTimestamp && s.timestamp <= toTimestamp) {
                    result.add(s);
                }
            }
        }
        return List.copyOf(result);
    }

    /** Pauses writes. Called when spectator window expires. */
    public void pause() {
        this.paused = true;
    }

    /** Resumes writes. Called on spectator mode exit. */
    public void resume() {
        this.paused = false;
    }

    /** Sets spectator entry time (ms since epoch). -1 when not in spectator mode. */
    public void setSpectatorEntryTime(long timeMs) {
        this.spectatorEntryTime = timeMs;
    }

    /** Milliseconds since epoch when spectator mode was entered; -1 if not in spectator. */
    public long getSpectatorEntryTime() {
        return spectatorEntryTime;
    }

    /** Whether writes are currently paused. */
    public boolean isPaused() {
        return paused;
    }

    /** Number of frames currently in the buffer (0 to capacity). */
    public int getCount() {
        return count;
    }

    /** Buffer capacity (buffer_seconds * fps). */
    public int getCapacity() {
        return ring.length;
    }

    /** Most recently written snapshot, or null if buffer is empty. For admin inspection only. */
    public WorldSnapshot getLatestSnapshot() {
        if (count == 0) {
            return null;
        }
        int lastIdx = (head - 1 + ring.length) % ring.length;
        return ring[lastIdx];
    }
}

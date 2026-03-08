package me.replaygif.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player ring buffer of world frames so we never block the main thread on I/O.
 * Writes happen only on the main tick (SnapshotScheduler); reads (slice) happen on the
 * render thread. Returning a copy from slice() guarantees the render pipeline never sees
 * the ring mutate mid-iteration and keeps WorldSnapshot list effectively immutable.
 */
public class SnapshotBuffer {

    private final WorldSnapshot[] ring;
    private volatile int head; // index of the next write position
    private volatile int count; // number of frames currently in the buffer (max = capacity)

    private volatile boolean paused;
    /** Milliseconds since epoch when spectator mode was entered. -1 if not in spectator mode. */
    private volatile long spectatorEntryTime;

    /** Capacity is typically buffer_seconds * fps; fixed at creation so the ring size is stable. */
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
     * Appends one frame; when full the oldest is overwritten so we always keep the latest window.
     * Main-thread only; no-op when paused so spectator-time window does not keep growing.
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
     * Time-window slice for the render pipeline; oldest-first order matches replay chronology.
     * Returns a copy so async readers never observe the ring being overwritten during iteration.
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

    /** Stops new frames from being written once the spectator capture window is exhausted. */
    public void pause() {
        this.paused = true;
    }

    /** Resumes writing when the player leaves spectator so the buffer fills again. */
    public void resume() {
        this.paused = false;
    }

    /** Records when the player entered spectator; used to enforce spectator_capture_seconds then pause. */
    public void setSpectatorEntryTime(long timeMs) {
        this.spectatorEntryTime = timeMs;
    }

    /** When spectator capture started; -1 means not in spectator (used by scheduler for pause/resume). */
    public long getSpectatorEntryTime() {
        return spectatorEntryTime;
    }

    /** True when buffer is paused (e.g. spectator window exhausted); scheduler skips write. */
    public boolean isPaused() {
        return paused;
    }

    /** Current frame count; only reaches capacity after the ring has been filled once. */
    public int getCount() {
        return count;
    }

    /** Maximum frames held; matches the configured buffer window. */
    public int getCapacity() {
        return ring.length;
    }

    /** Latest frame in the ring; null if none yet. Used for diagnostics, not the pipeline. */
    public WorldSnapshot getLatestSnapshot() {
        if (count == 0) {
            return null;
        }
        int lastIdx = (head - 1 + ring.length) % ring.length;
        return ring[lastIdx];
    }
}

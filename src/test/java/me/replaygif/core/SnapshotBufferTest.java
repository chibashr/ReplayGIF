package me.replaygif.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SnapshotBuffer covering scenarios B1–B6 from .planning/testing.md.
 */
class SnapshotBufferTest {

    private static WorldSnapshot snapshotAt(long timestamp) {
        return new WorldSnapshot(
                timestamp,
                0, 0, 0,
                0f, 0f,
                20f, 20,
                "minecraft:overworld",
                "world",
                new short[8],
                2,
                List.of(),
                false);
    }

    /** B1 — Circular overwrite: fill to capacity, write one more; oldest overwritten; slice reflects new state. */
    @Test
    void b1_circularOverwrite() {
        int capacity = 5;
        SnapshotBuffer buffer = new SnapshotBuffer(capacity);
        for (int i = 0; i < capacity; i++) {
            buffer.write(snapshotAt(1000L * i));
        }
        List<WorldSnapshot> before = buffer.slice(0, 100_000);
        assertEquals(capacity, before.size());
        assertEquals(0, before.get(0).timestamp);
        assertEquals((capacity - 1) * 1000L, before.get(capacity - 1).timestamp);

        // Write one more; oldest (timestamp 0) is overwritten
        buffer.write(snapshotAt(5000L));
        List<WorldSnapshot> after = buffer.slice(0, 100_000);
        assertEquals(capacity, after.size());
        // Oldest should now be 1000, newest 5000
        assertEquals(1000L, after.get(0).timestamp);
        assertEquals(5000L, after.get(capacity - 1).timestamp);
        assertFalse(after.stream().anyMatch(s -> s.timestamp == 0));
    }

    /** B2 — Slice by timestamp: 100 frames with known timestamps; slice T to T+3000 returns correct range, oldest first. */
    @Test
    void b2_sliceByTimestamp() {
        SnapshotBuffer buffer = new SnapshotBuffer(200);
        long base = 10_000L;
        for (int i = 0; i < 100; i++) {
            buffer.write(snapshotAt(base + i * 100L));
        }
        long from = base + 500L;
        long to = base + 3000L;
        List<WorldSnapshot> slice = buffer.slice(from, to);
        assertFalse(slice.isEmpty());
        for (WorldSnapshot s : slice) {
            assertTrue(s.timestamp >= from && s.timestamp <= to, "timestamp " + s.timestamp + " not in [" + from + "," + to + "]");
        }
        for (int i = 1; i < slice.size(); i++) {
            assertTrue(slice.get(i).timestamp >= slice.get(i - 1).timestamp, "ordered oldest first");
        }
        // Exact set: 500,600,...,3000 -> 26 values (500,600,...,3000)
        assertEquals(26, slice.size());
        assertEquals(base + 500L, slice.get(0).timestamp);
        assertEquals(base + 3000L, slice.get(slice.size() - 1).timestamp);
    }

    /** B3 — Slice on empty buffer: returns empty list, no exception. */
    @Test
    void b3_sliceOnEmptyBuffer() {
        SnapshotBuffer buffer = new SnapshotBuffer(10);
        List<WorldSnapshot> slice = buffer.slice(0, 100_000);
        assertNotNull(slice);
        assertTrue(slice.isEmpty());
    }

    /** B4 — Pause and resume: write 10, pause, write 10 more (dropped), slice = first 10; resume, write 10, slice = first 10 + last 10 with gap. */
    @Test
    void b4_pauseAndResume() {
        SnapshotBuffer buffer = new SnapshotBuffer(50);
        for (int i = 0; i < 10; i++) {
            buffer.write(snapshotAt(1000L * i));
        }
        buffer.pause();
        for (int i = 0; i < 10; i++) {
            buffer.write(snapshotAt(10_000 + i * 1000L)); // would be 10_000–19_000 if stored
        }
        List<WorldSnapshot> whilePaused = buffer.slice(0, 100_000);
        assertEquals(10, whilePaused.size());
        assertEquals(0, whilePaused.get(0).timestamp);
        assertEquals(9000L, whilePaused.get(9).timestamp);

        buffer.resume();
        for (int i = 0; i < 10; i++) {
            buffer.write(snapshotAt(20_000 + i * 1000L));
        }
        List<WorldSnapshot> afterResume = buffer.slice(0, 100_000);
        assertEquals(20, afterResume.size());
        assertEquals(0, afterResume.get(0).timestamp);
        assertEquals(9000L, afterResume.get(9).timestamp);
        assertEquals(20_000L, afterResume.get(10).timestamp);
        assertEquals(29_000L, afterResume.get(19).timestamp);
        // No frames in 10_000–19_000 range
        assertTrue(afterResume.stream().noneMatch(s -> s.timestamp >= 10_000 && s.timestamp <= 19_000));
    }

    /** B5 — Spectator window: after 20 frames, pause; 25 total writes → only 20 stored. */
    @Test
    void b5_spectatorWindow() {
        int capacity = 60;
        SnapshotBuffer buffer = new SnapshotBuffer(capacity);
        buffer.setSpectatorEntryTime(System.currentTimeMillis());
        for (int i = 0; i < 20; i++) {
            buffer.write(snapshotAt(1000L * i));
        }
        buffer.pause();
        for (int i = 0; i < 5; i++) {
            buffer.write(snapshotAt(20_000 + i * 1000L));
        }
        List<WorldSnapshot> slice = buffer.slice(0, 100_000);
        assertEquals(20, slice.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(1000L * i, slice.get(i).timestamp);
        }
    }

    /** TH1 scenario: 6 seconds at 10fps = 60 frames; buffer must contain 60 frames for pipeline. */
    @Test
    void th1_sixtyFramesAt10fps() {
        int fps = 10;
        int bufferSeconds = 6;
        int capacity = bufferSeconds * fps; // 60
        SnapshotBuffer buffer = new SnapshotBuffer(capacity);
        long base = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            buffer.write(snapshotAt(base + i * 100L));
        }
        assertEquals(60, buffer.getCount(), "buffer must contain 60 frames after 6s at 10fps");
        assertEquals(60, buffer.getCapacity());
        WorldSnapshot latest = buffer.getLatestSnapshot();
        assertNotNull(latest);
        assertEquals(base + 59 * 100L, latest.timestamp);
        assertEquals(60, buffer.slice(base, base + 10_000).size());
    }

    /** B6 — Thread safety: writer thread writes continuously; reader thread calls slice() 1000 times; no CME, slices internally consistent. */
    @Test
    void b6_threadSafetyOfSlice() throws InterruptedException {
        int capacity = 64;
        SnapshotBuffer buffer = new SnapshotBuffer(capacity);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    buffer.write(snapshotAt(i));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 1000; i++) {
                    List<WorldSnapshot> slice = buffer.slice(0, Long.MAX_VALUE);
                    // Internally consistent: ordered by timestamp
                    for (int j = 1; j < slice.size(); j++) {
                        assertTrue(slice.get(j).timestamp >= slice.get(j - 1).timestamp);
                    }
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });

        writer.start();
        reader.start();
        start.countDown();
        done.await();
        assertNull(error.get(), error.get() != null ? error.get().getMessage() : null);
    }
}

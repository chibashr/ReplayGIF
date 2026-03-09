package me.replaygif.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorldSnapshot block-breaking fields (BB2, BB3 from .planning/testing.md).
 * Verifies constructor contract: breaking state is stored; 14-arg uses sentinels.
 */
class WorldSnapshotBlockBreakTest {

    /** BB2 — Snapshot built with breaking state has breakingBlockX/Y/Z and breakingStage set. */
    @Test
    void bb2_snapshotWithBreakingState_hasBreakingCoordsAndStage() {
        short[] blocks = new short[16 * 16 * 16];
        blocks[8 * 16 * 16 + 8 * 16 + 8] = 1; // one block
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world", blocks, 16, List.of(), false,
                null, List.of(),
                100, 70, -200, 5);
        assertEquals(100, snapshot.breakingBlockX);
        assertEquals(70, snapshot.breakingBlockY);
        assertEquals(-200, snapshot.breakingBlockZ);
        assertTrue(snapshot.breakingStage >= 0);
        assertEquals(5, snapshot.breakingStage);
    }

    /** BB3 — Snapshot from 14-arg constructor (no breaking): breakingStage = -1, coords sentinel. */
    @Test
    void bb3_snapshotWithoutBreaking_sentinelValues() {
        WorldSnapshot snapshot = new WorldSnapshot(
                0L, 0, 0, 0, 0f, 0f, 20f, 20,
                "minecraft:overworld", "world",
                new short[8 * 8 * 8], 8, List.of(), false);
        assertEquals(-999999, snapshot.breakingBlockX);
        assertEquals(-999999, snapshot.breakingBlockY);
        assertEquals(-999999, snapshot.breakingBlockZ);
        assertEquals(-1, snapshot.breakingStage);
    }
}

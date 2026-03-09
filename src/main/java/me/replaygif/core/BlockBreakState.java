package me.replaygif.core;

/**
 * Immutable state for one block being broken by a player.
 * Stage 0–9; 9 = almost broken.
 */
public record BlockBreakState(int blockX, int blockY, int blockZ, int stage) {
    /** Sentinel stage when not breaking. */
    public static final int NOT_BREAKING = -1;

    public BlockBreakState {
        if (stage < 0 || stage > 9) {
            throw new IllegalArgumentException("stage must be 0–9, got " + stage);
        }
    }
}

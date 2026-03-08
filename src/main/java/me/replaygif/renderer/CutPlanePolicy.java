package me.replaygif.renderer;

/**
 * Determines which blocks are culled for the dollhouse effect. A block at
 * (relX, relY, relZ) is culled when (relX + relZ) > (playerRelX + playerRelZ + cutOffset).
 * With player at origin, this simplifies to (relX + relZ) > cutOffset.
 * See rendering-pipeline.md 1c.
 */
public final class CutPlanePolicy {

    private CutPlanePolicy() {}

    /**
     * Returns true if the block at the given relative position should be
     * culled (not drawn). Player is at origin (0, 0, 0).
     */
    public static boolean isCulled(int relX, int relZ, int cutOffset) {
        return (relX + relZ) > cutOffset;
    }
}

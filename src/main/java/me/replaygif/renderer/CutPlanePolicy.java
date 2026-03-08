package me.replaygif.renderer;

/**
 * Cut plane for the isometric "dollhouse" view: we only draw blocks in front of the plane
 * (relX + relZ <= cutOffset) so the back half of the volume is hidden and the view is readable.
 * Offset is configurable so servers can tune how much of the volume is visible.
 */
public final class CutPlanePolicy {

    private CutPlanePolicy() {}

    /** True if this block should be skipped (behind the cut plane). Origin is the player position. */
    public static boolean isCulled(int relX, int relZ, int cutOffset) {
        return (relX + relZ) > cutOffset;
    }
}

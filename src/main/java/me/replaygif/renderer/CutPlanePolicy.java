package me.replaygif.renderer;

/**
 * Cut plane for the isometric "dollhouse" view: we only draw blocks in front of the plane
 * (relX + relZ <= cutOffset) so the back half of the volume is hidden — except for ground.
 * Blocks at relY <= groundFullRelY are never culled, giving a full diamond-shaped ground with
 * a cutout only above and to the sides of the subject.
 */
public final class CutPlanePolicy {

    private CutPlanePolicy() {}

    /**
     * True if this block should be skipped (behind the cut plane). Ground (relY <= groundFullRelY)
     * is never culled; only blocks above get the cut.
     */
    public static boolean isCulled(int relX, int relY, int relZ, int cutOffset, int groundFullRelY) {
        if (relY <= groundFullRelY) {
            return false; // full ground — never cull
        }
        return (relX + relZ) > cutOffset;
    }
}

package com.replayplugin.sidecar.render;

import java.awt.geom.Point2D;

/**
 * Dimetric projection: 45° horizontal, arctan(1/2) ≈ 26.57° vertical.
 * World-to-screen: screenX = (worldX - worldZ) * cos(45°) * ppb,
 *                  screenY = (worldX + worldZ) * sin(26.57°) * ppb - worldY * ppb.
 */
public final class IsoProjection {

    private static final double COS_45 = Math.cos(Math.toRadians(45));
    private static final double SIN_2657 = Math.sin(Math.atan(0.5));

    /**
     * Project world coordinates to screen (origin at top-left; Y increases downward).
     */
    public static Point2D project(double wx, double wy, double wz, int pixelsPerBlock) {
        double scale = pixelsPerBlock;
        double screenX = (wx - wz) * COS_45 * scale;
        double screenY = (wx + wz) * SIN_2657 * scale - wy * scale;
        return new Point2D.Double(screenX, screenY);
    }
}

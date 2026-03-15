package com.replayplugin.sidecar.render;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISO-001 through ISO-005: Isometric Projection.
 */
class IsoProjectionTest {

    private static final int PPB = 16;
    private static final double COS_45 = Math.cos(Math.toRadians(45));
    private static final double SIN_2657 = Math.sin(Math.atan(0.5));

    @Test
    void ISO001_projectSingleBlockAtOrigin() {
        Point2D p = IsoProjection.project(0, 0, 0, PPB);
        assertEquals(0.0, p.getX(), 1e-6);
        assertEquals(0.0, p.getY(), 1e-6);
    }

    @Test
    void ISO002_project_1_0_0_vs_0_0_1() {
        Point2D right = IsoProjection.project(1, 0, 0, PPB);
        Point2D left = IsoProjection.project(0, 0, 1, PPB);
        assertEquals((1 - 0) * COS_45 * PPB, right.getX(), 1.0);
        assertEquals((0 - 1) * COS_45 * PPB, left.getX(), 1.0);
        assertTrue(right.getX() > left.getX());
    }

    @Test
    void ISO003_project_0_1_0_verticalOffset() {
        Point2D p = IsoProjection.project(0, 1, 0, PPB);
        assertEquals(0.0, p.getX(), 1.0);
        assertEquals(-1 * PPB, p.getY(), 1.0);
    }

    @Test
    void ISO004_twoBlocksSameXZDifferentY_higherRendersAbove() {
        Point2D lower = IsoProjection.project(0, 0, 0, PPB);
        Point2D higher = IsoProjection.project(0, 1, 0, PPB);
        assertTrue(higher.getY() < lower.getY(), "Y down in screen coords: higher block has smaller screen Y");
    }

    @Test
    void ISO005_slabHalfHeight_screenHeightHalf() {
        Point2D fullTop = IsoProjection.project(0.5, 1, 0.5, PPB);
        Point2D fullBottom = IsoProjection.project(0.5, 0, 0.5, PPB);
        Point2D slabTop = IsoProjection.project(0.5, 0.5, 0.5, PPB);
        Point2D slabBottom = IsoProjection.project(0.5, 0, 0.5, PPB);
        double fullScreenH = Math.abs(fullTop.getY() - fullBottom.getY());
        double slabScreenH = Math.abs(slabTop.getY() - slabBottom.getY());
        assertEquals(fullScreenH * 0.5, slabScreenH, 2.0);
    }
}

package com.replayplugin.sidecar.render;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LGT-001 through LGT-005: Lighting. Assert relative brightness ratios between faces.
 */
class LightingApplierTest {

    @Test
    void faceMultipliers() {
        assertEquals(1.0, LightingApplier.multiplierForFace("up"));
        assertEquals(0.5, LightingApplier.multiplierForFace("down"));
        assertEquals(0.8, LightingApplier.multiplierForFace("north"));
        assertEquals(0.8, LightingApplier.multiplierForFace("south"));
        assertEquals(0.6, LightingApplier.multiplierForFace("east"));
        assertEquals(0.6, LightingApplier.multiplierForFace("west"));
    }

    @Test
    void LGT001_topFace_baseTimes1() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "up");
        assertEquals(0xff808080, out.getRGB(0, 0));
    }

    @Test
    void applyScalesTopFace() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "up");
        assertEquals(0xff808080, out.getRGB(0, 0));
    }

    @Test
    void LGT002_northSouth_baseTimes08() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "north");
        int r = (out.getRGB(0, 0) >> 16) & 0xff;
        assertEquals(102, r, 2);
    }

    @Test
    void LGT003_eastWest_baseTimes06() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "east");
        int r = (out.getRGB(0, 0) >> 16) & 0xff;
        assertEquals(76, r, 2);
    }

    @Test
    void LGT004_bottomFace_baseTimes05() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "down");
        int r = (out.getRGB(0, 0) >> 16) & 0xff;
        assertEquals(64, r);
    }

    @Test
    void applyScalesBottomFace() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        BufferedImage out = LightingApplier.apply(in, "down");
        int rgb = out.getRGB(0, 0);
        int r = (rgb >> 16) & 0xff;
        assertEquals(64, r);
    }

    @Test
    void LGT005_relativeBrightnessRatios() {
        BufferedImage in = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, 0xff808080);
        int top = (LightingApplier.apply(in, "up").getRGB(0, 0) >> 16) & 0xff;
        int ns = (LightingApplier.apply(in, "north").getRGB(0, 0) >> 16) & 0xff;
        int ew = (LightingApplier.apply(in, "east").getRGB(0, 0) >> 16) & 0xff;
        int down = (LightingApplier.apply(in, "down").getRGB(0, 0) >> 16) & 0xff;
        assertTrue(top >= ns && ns >= ew && ew >= down);
        assertEquals(128, top);
        assertTrue(Math.abs(ns - 102) <= 2);
        assertTrue(Math.abs(ew - 76) <= 2);
        assertEquals(64, down);
    }
}

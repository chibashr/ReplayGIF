package com.replayplugin.sidecar.render;

import java.awt.image.BufferedImage;

/**
 * Face-based shading: top × 1.0, north/south × 0.8, east/west × 0.6, bottom × 0.5.
 * Multiplies each RGB channel by the multiplier (clamp 0–255).
 */
public final class LightingApplier {

    public static final double TOP = 1.0;
    public static final double NORTH_SOUTH = 0.8;
    public static final double EAST_WEST = 0.6;
    public static final double BOTTOM = 0.5;

    /**
     * Returns a new image with lighting applied. Does not modify the original.
     */
    public static BufferedImage apply(BufferedImage faceTexture, String faceName) {
        double mult = multiplierForFace(faceName);
        int w = faceTexture.getWidth();
        int h = faceTexture.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = faceTexture.getRGB(x, y);
                int a = (rgb >> 24) & 0xff;
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                r = clamp((int) (r * mult));
                g = clamp((int) (g * mult));
                b = clamp((int) (b * mult));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    public static double multiplierForFace(String faceName) {
        if (faceName == null) return EAST_WEST;
        switch (faceName) {
            case "up": return TOP;
            case "down": return BOTTOM;
            case "north":
            case "south": return NORTH_SOUTH;
            case "east":
            case "west": return EAST_WEST;
            default: return EAST_WEST;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}

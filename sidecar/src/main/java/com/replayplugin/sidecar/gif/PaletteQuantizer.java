package com.replayplugin.sidecar.gif;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Per-frame local palette via median-cut; 256 colors max, no dithering.
 * Maps each pixel to nearest palette entry (Euclidean distance in RGB).
 */
public final class PaletteQuantizer {

    private static final int MAX_COLORS = 256;

    /**
     * Quantize frame to at most maxColors. Returns palette (packed RGB) and indexed pixels (byte, 0..palette.length-1).
     */
    public static QuantizedFrame quantize(BufferedImage frame, int maxColors) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        int[] pixels = new int[w * h];
        frame.getRGB(0, 0, w, h, pixels, 0, w);

        List<int[]> rgbList = new ArrayList<>();
        for (int p : pixels) {
            int a = (p >> 24) & 0xff;
            if (a < 128) continue;
            rgbList.add(new int[]{(p >> 16) & 0xff, (p >> 8) & 0xff, p & 0xff});
        }
        if (rgbList.isEmpty()) {
            rgbList.add(new int[]{0, 0, 0});
        }

        int target = Math.min(maxColors, Math.min(MAX_COLORS, 1 << ceilLog2(rgbList.size())));
        if (target < 1) target = 1;
        List<Box> boxes = medianCut(rgbList, target);

        int[] palette = new int[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            int r = 0, g = 0, b_ = 0;
            for (int[] c : b.colors) {
                r += c[0];
                g += c[1];
                b_ += c[2];
            }
            int n = b.colors.size();
            palette[i] = ((r / n) << 16) | ((g / n) << 8) | (b_ / n);
        }

        byte[] indexed = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
            int best = 0;
            long bestD = Long.MAX_VALUE;
            for (int j = 0; j < palette.length; j++) {
                int pr = (palette[j] >> 16) & 0xff;
                int pg = (palette[j] >> 8) & 0xff;
                int pb = palette[j] & 0xff;
                long d = (long) (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb);
                if (d < bestD) {
                    bestD = d;
                    best = j;
                }
            }
            indexed[i] = (byte) (best & 0xff);
        }

        return new QuantizedFrame(palette, indexed, w, h);
    }

    private static int ceilLog2(int n) {
        if (n <= 1) return 0;
        int log = 0;
        int x = 1;
        while (x < n) {
            x *= 2;
            log++;
        }
        return log;
    }

    private static List<Box> medianCut(List<int[]> colors, int target) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(new ArrayList<>(colors)));

        while (boxes.size() < target) {
            Box splitMe = null;
            int maxSpan = -1;
            for (Box b : boxes) {
                if (b.colors.size() < 2) continue;
                int rMin = 255, rMax = 0, gMin = 255, gMax = 0, bMin = 255, bMax = 0;
                for (int[] c : b.colors) {
                    rMin = Math.min(rMin, c[0]);
                    rMax = Math.max(rMax, c[0]);
                    gMin = Math.min(gMin, c[1]);
                    gMax = Math.max(gMax, c[1]);
                    bMin = Math.min(bMin, c[2]);
                    bMax = Math.max(bMax, c[2]);
                }
                int spanR = rMax - rMin, spanG = gMax - gMin, spanB = bMax - bMin;
                int span = Math.max(spanR, Math.max(spanG, spanB));
                if (span > maxSpan) {
                    maxSpan = span;
                    splitMe = b;
                }
            }
            if (splitMe == null || maxSpan <= 0) break;

            int axis = 0;
            int rMin = 255, rMax = 0, gMin = 255, gMax = 0, bMin = 255, bMax = 0;
            for (int[] c : splitMe.colors) {
                rMin = Math.min(rMin, c[0]);
                rMax = Math.max(rMax, c[0]);
                gMin = Math.min(gMin, c[1]);
                gMax = Math.max(gMax, c[1]);
                bMin = Math.min(bMin, c[2]);
                bMax = Math.max(bMax, c[2]);
            }
            int spanR = rMax - rMin, spanG = gMax - gMin, spanB = bMax - bMin;
            if (spanR >= spanG && spanR >= spanB) axis = 0;
            else if (spanG >= spanR && spanG >= spanB) axis = 1;
            else axis = 2;

            final int ax = axis;
            splitMe.colors.sort(Comparator.comparingInt(c -> c[ax]));
            int mid = splitMe.colors.size() / 2;
            List<int[]> right = new ArrayList<>(splitMe.colors.subList(mid, splitMe.colors.size()));
            splitMe.colors.subList(mid, splitMe.colors.size()).clear();
            boxes.add(new Box(right));
        }

        return boxes;
    }

    private static class Box {
        final List<int[]> colors;

        Box(List<int[]> colors) {
            this.colors = colors;
        }
    }

    public static final class QuantizedFrame {
        private final int[] palette;
        private final byte[] indexedPixels;
        private final int width;
        private final int height;

        public QuantizedFrame(int[] palette, byte[] indexedPixels, int width, int height) {
            this.palette = palette;
            this.indexedPixels = indexedPixels;
            this.width = width;
            this.height = height;
        }

        public int[] getPalette() {
            return palette;
        }

        public byte[] getIndexedPixels() {
            return indexedPixels;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}

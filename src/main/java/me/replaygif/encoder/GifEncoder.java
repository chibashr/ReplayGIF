package me.replaygif.encoder;

import me.replaygif.config.ConfigManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around AnimatedGifEncoder: BufferedImage list → byte[] animated GIF.
 * Background and transparency are read from renderer config at encode time.
 */
public class GifEncoder {

    /**
     * Chroma key for transparent areas. Magic pink chosen to avoid matching common blocks.
     */
    private static final Color TRANSPARENT_CHROMA = new Color(254, 0, 255);

    private final ConfigManager configManager;

    public GifEncoder(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Encodes the given frames as an animated GIF with the specified delay between frames.
     *
     * @param frames       list of frames in order (oldest first); null or empty returns empty array
     * @param frameDelayMs delay between frames in milliseconds
     * @return GIF bytes; never null
     */
    public byte[] encode(List<BufferedImage> frames, int frameDelayMs) {
        if (frames == null || frames.isEmpty()) {
            return new byte[0];
        }
        String bgConfig = configManager != null ? configManager.getGifBackground() : "#F5F5F5";
        Color bgColor = parseBackgroundColor(bgConfig);
        boolean transparent = "transparent".equalsIgnoreCase(bgConfig.trim());

        int quality = configManager != null ? configManager.getGifQuality() : 20;
        int n = frames.size();

        // Build palette from multiple frames so colorful frames (sand, water, leaves) are represented.
        // Using only frame 0 can yield a grey-heavy palette when the first frame is mostly stone/dirt.
        byte[] palette = buildPaletteFromFrames(frames, bgColor, quality);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        try {
            encoder.start(baos);
            encoder.setDelay(frameDelayMs);
            encoder.setRepeat(0); // loop forever
            encoder.setBackground(bgColor);
            if (transparent) {
                encoder.setTransparent(bgColor);
            }
            encoder.setQuality(quality);
            if (palette != null) {
                encoder.setInitialPalette(palette);
            }
            int logged = 0;
            for (int i = 0; i < n; i++) {
                BufferedImage frame = frames.get(i);
                if (frame != null) {
                    encoder.addFrame(frame);
                    int done = i + 1;
                    if (n > 10 && (done % 10 == 0 || done == n) && done > logged) {
                        logged = done;
                        if (configManager != null && configManager.getLogger() != null) {
                            configManager.getLogger().info("GIF encoding: {}/{} frames", done, n);
                        }
                    }
                }
            }
        } finally {
            encoder.finish();
        }
        return baos.toByteArray();
    }

    /**
     * Samples pixels from multiple frames (start, quarter, mid, three-quarter, end) and runs
     * NeuQuant to build a 256-color palette that represents the full animation, not just frame 0.
     * This prevents grayscale output when the first frame is mostly grey blocks.
     */
    private static byte[] buildPaletteFromFrames(List<BufferedImage> frames, Color bgColor, int sample) {
        if (frames == null || frames.isEmpty()) return null;
        BufferedImage first = frames.get(0);
        int w = first.getWidth();
        int h = first.getHeight();
        if (w < 1 || h < 1) return null;

        // Sample from up to 5 frames spread across the animation
        List<Integer> indices = new ArrayList<>();
        int n = frames.size();
        if (n <= 3) {
            for (int i = 0; i < n; i++) indices.add(i);
        } else {
            indices.add(0);
            indices.add(n / 4);
            indices.add(n / 2);
            indices.add(3 * n / 4);
            indices.add(n - 1);
        }

        List<byte[]> pixelChunks = new ArrayList<>();
        BufferedImage bgr = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = bgr.createGraphics();
        try {
            g.setColor(bgColor);
            for (int idx : indices) {
                BufferedImage f = frames.get(idx);
                if (f == null) continue;
                g.fillRect(0, 0, w, h);
                g.drawImage(f, 0, 0, null);
                byte[] pixels = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
                pixelChunks.add(pixels.clone());
            }
        } finally {
            g.dispose();
        }

        int totalLen = pixelChunks.stream().mapToInt(arr -> arr.length).sum();
        if (totalLen < 3 * 499) return null; // NeuQuant minpicturebytes

        byte[] combined = new byte[totalLen];
        int pos = 0;
        for (byte[] chunk : pixelChunks) {
            System.arraycopy(chunk, 0, combined, pos, chunk.length);
            pos += chunk.length;
        }

        NeuQuant nq = new NeuQuant(combined, totalLen, sample);
        byte[] colorTab = nq.process();
        for (int i = 0; i < colorTab.length; i += 3) {
            byte t = colorTab[i];
            colorTab[i] = colorTab[i + 2];
            colorTab[i + 2] = t;
        }
        return colorTab;
    }

    private static Color parseBackgroundColor(String value) {
        if (value == null) return TRANSPARENT_CHROMA;
        String s = value.trim();
        switch (s.toLowerCase()) {
            case "transparent":
                return TRANSPARENT_CHROMA;
            case "white":
                return Color.WHITE;
            case "black":
                return Color.BLACK;
            default:
                if (s.startsWith("#") && s.length() == 7) {
                    try {
                        int rgb = Integer.parseInt(s.substring(1), 16);
                        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                    } catch (NumberFormatException ignored) { /* fall through */ }
                }
                return TRANSPARENT_CHROMA;
        }
    }
}

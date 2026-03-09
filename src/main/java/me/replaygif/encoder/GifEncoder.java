package me.replaygif.encoder;

import me.replaygif.config.ConfigManager;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        String bgConfig = configManager != null ? configManager.getGifBackground() : "transparent";
        Color bgColor = parseBackgroundColor(bgConfig);
        boolean transparent = "transparent".equalsIgnoreCase(bgConfig.trim());

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
            for (BufferedImage frame : frames) {
                if (frame != null) {
                    encoder.addFrame(frame);
                }
            }
        } finally {
            encoder.finish();
        }
        return baos.toByteArray();
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

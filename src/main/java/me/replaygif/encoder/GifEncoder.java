package me.replaygif.encoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Wrapper around AnimatedGifEncoder: BufferedImage list → byte[] animated GIF.
 */
public class GifEncoder {

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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        try {
            encoder.start(baos);
            encoder.setDelay(frameDelayMs);
            encoder.setRepeat(0); // loop forever
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
}

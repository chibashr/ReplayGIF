package me.replaygif.encoder;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GifEncoder covering GE1–GE5 from .planning/testing.md.
 */
class GifEncoderTest {

    private static final byte[] GIF89A_HEADER = new byte[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 };

    private static List<BufferedImage> frames(int count) {
        List<BufferedImage> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB));
        }
        return list;
    }

    /** GE1 — Valid GIF output: magic bytes GIF89a, parseable by ImageIO. */
    @Test
    void ge1_validGifOutput_magicBytesAndParseable() throws IOException {
        GifEncoder encoder = new GifEncoder(null);
        byte[] out = encoder.encode(frames(5), 100);
        assertNotNull(out);
        assertTrue(out.length >= 6);
        for (int i = 0; i < 6; i++) {
            assertEquals(GIF89A_HEADER[i], out[i], "GIF89a magic byte at " + i);
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(out)) {
            assertNotNull(ImageIO.read(bais), "ImageIO must parse the output as GIF");
        }
    }

    /** GE2 — Frame count: encode N frames, parser sees exactly N frames. */
    @Test
    void ge2_frameCount_exactlyN() throws IOException {
        GifEncoder encoder = new GifEncoder(null);
        int n = 7;
        byte[] out = encoder.encode(frames(n), 100);
        int count = countGifFrames(out);
        assertEquals(n, count, "Output must contain exactly " + n + " frames");
    }

    /** GE3 — Frame delay: 10fps = 100ms, 5fps = 200ms, 20fps = 50ms (in hundredths in GIF). */
    @Test
    void ge3_frameDelay_correctHundredths() throws IOException {
        GifEncoder encoder = new GifEncoder(null);
        byte[] out10 = encoder.encode(frames(3), 100);
        assertGifFrameDelayHundredths(out10, 10);

        byte[] out5 = encoder.encode(frames(2), 200);
        assertGifFrameDelayHundredths(out5, 20);

        byte[] out20 = encoder.encode(frames(2), 50);
        assertGifFrameDelayHundredths(out20, 5);
    }

    /** GE4 — Looping: Netscape Application Extension with repeat = 0 (loop forever). */
    @Test
    void ge4_looping_netscapeRepeatZero() {
        GifEncoder encoder = new GifEncoder(null);
        byte[] out = encoder.encode(frames(3), 100);
        assertTrue(containsNetscapeLoop(out), "GIF must contain Netscape extension with repeat=0");
    }

    /** GE5 — Single frame: encode 1 frame, valid GIF, no exception. */
    @Test
    void ge5_singleFrame_validGifNoException() throws IOException {
        GifEncoder encoder = new GifEncoder(null);
        byte[] out = encoder.encode(frames(1), 100);
        assertNotNull(out);
        assertTrue(out.length >= 6);
        for (int i = 0; i < 6; i++) {
            assertEquals(GIF89A_HEADER[i], out[i]);
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(out)) {
            assertNotNull(ImageIO.read(bais));
        }
    }

    private static int countGifFrames(byte[] gif) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return 0;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false);
                return reader.getNumImages(true);
            } finally {
                reader.dispose();
            }
        }
    }

    /** GIF stores delay in Graphic Control Extension in hundredths of a second (little-endian). */
    private static void assertGifFrameDelayHundredths(byte[] gif, int expectedHundredths) {
        // Find 0x21 0xF9 (extension label + GCE) then skip to byte 4-5 (delay)
        boolean found = false;
        for (int i = 0; i < gif.length - 6; i++) {
            if (gif[i] == 0x21 && gif[i + 1] == (byte) 0xF9 && gif[i + 2] == 0x04) {
                int low = gif[i + 4] & 0xFF;
                int high = gif[i + 5] & 0xFF;
                int hundredths = low | (high << 8);
                assertEquals(expectedHundredths, hundredths, "GCE at offset " + i + " delay hundredths");
                found = true;
                break;
            }
        }
        assertTrue(found, "GIF should contain at least one Graphic Control Extension with delay");
    }

    /** Netscape Application Extension: 0x21 0xFF 0x0B "NETSCAPE2.0" 0x03 0x01 [repeat lo] [repeat hi]. repeat=0 = loop forever. */
    private static boolean containsNetscapeLoop(byte[] gif) {
        byte[] ns = "NETSCAPE2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < gif.length - 19; i++) {
            if (gif[i] == 0x21 && gif[i + 1] == (byte) 0xFF && gif[i + 2] == 0x0B) {
                boolean match = true;
                for (int j = 0; j < ns.length && match; j++) {
                    if (gif[i + 3 + j] != ns[j]) match = false;
                }
                if (match && gif[i + 14] == 0x03 && gif[i + 15] == 0x01) {
                    int repeatLo = gif[i + 16] & 0xFF;
                    int repeatHi = gif[i + 17] & 0xFF;
                    if ((repeatLo | (repeatHi << 8)) == 0) return true;
                }
            }
        }
        return false;
    }
}

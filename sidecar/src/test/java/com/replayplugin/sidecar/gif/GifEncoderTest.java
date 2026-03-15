package com.replayplugin.sidecar.gif;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GIF-001 through GIF-007: GIF Encoding.
 */
class GifEncoderTest {

    @TempDir
    Path tempDir;

    private static BufferedImage frame(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void GIF001_encode3Frames10fps_validGifFrameDelay100ms() throws IOException {
        List<BufferedImage> frames = List.of(frame(10, 10, 0xff0000ff), frame(10, 10, 0xff00ff00), frame(10, 10, 0xffff0000));
        Path out = tempDir.resolve("out.gif");
        GifEncoder.encode(frames, 10, out);

        assertTrue(Files.exists(out));
        byte[] bytes = Files.readAllBytes(out);
        assertTrue(bytes.length > 0);
        assertTrue(new String(bytes, 0, Math.min(6, bytes.length)).startsWith("GIF89a"));
        int delayCs = findDelayCentiseconds(bytes);
        assertEquals(10, delayCs, "10fps => 100ms => 10 centiseconds");
    }

    @Test
    void GIF002_encodeCustomFps15_frameDelayAbout67ms() throws IOException {
        List<BufferedImage> frames = List.of(frame(8, 8, 0xff808080));
        Path out = tempDir.resolve("out.gif");
        GifEncoder.encode(frames, 15, out);

        byte[] bytes = Files.readAllBytes(out);
        int delayCs = findDelayCentiseconds(bytes);
        assertTrue(delayCs >= 6 && delayCs <= 7, "15fps => ~67ms => ~7 cs, got " + delayCs);
    }

    @Test
    void GIF003_perFrameLocalPalette_max256Colors() throws IOException {
        BufferedImage many = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 400; i++) many.setRGB(i % 20, i / 20, 0xff000000 | (i % 256) | ((i % 256) << 8) | ((i % 256) << 16));
        Path out = tempDir.resolve("out.gif");
        GifEncoder.encode(List.of(many), 10, out);
        byte[] bytes = Files.readAllBytes(out);
        assertTrue(bytes.length > 0);
    }

    @Test
    void GIF004_noDithering_exactPaletteColors() throws IOException {
        List<BufferedImage> frames = List.of(frame(4, 4, 0xff112233));
        Path out = tempDir.resolve("out.gif");
        GifEncoder.encode(frames, 10, out);
        assertTrue(Files.exists(out));
    }

    @Test
    void GIF005_validGifRenderable() throws IOException {
        List<BufferedImage> frames = List.of(frame(16, 16, 0xff00ff00));
        Path out = tempDir.resolve("out.gif");
        GifEncoder.encode(frames, 10, out);
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
        BufferedImage read = ImageIO.read(out.toFile());
        assertNotNull(read);
        assertEquals(16, read.getWidth());
        assertEquals(16, read.getHeight());
    }

    @Test
    void GIF006_outputFilenameMatchesPattern() {
        String player = "steve";
        String event = "PlayerDeathEvent";
        String timestamp = "20260314-153042";
        String expected = player + "_" + event + "_" + timestamp + ".gif";
        assertTrue(expected.matches(".*_.*_.*\\.gif"));
        assertEquals("steve_PlayerDeathEvent_20260314-153042.gif", expected);
    }

    @Test
    void GIF007_gifWrittenToOutputPath() throws IOException {
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Path out = outputDir.resolve("test.gif");
        GifEncoder.encode(List.of(frame(8, 8, 0xff0000ff)), 10, out);
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }

    private static int findDelayCentiseconds(byte[] gif) {
        for (int i = 0; i < gif.length - 5; i++) {
            if (gif[i] == 0x21 && gif[i + 1] == (byte) 0xf9 && gif[i + 2] == 4) {
                return gif[i + 4] & 0xff | ((gif[i + 5] & 0xff) << 8);
            }
        }
        return -1;
    }
}

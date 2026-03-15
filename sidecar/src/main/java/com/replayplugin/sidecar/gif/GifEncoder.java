package com.replayplugin.sidecar.gif;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GIF89a encoder with per-frame local color tables and LZW from scratch.
 */
public final class GifEncoder {

    private static final int CLEAR_CODE = 256;
    private static final int END_CODE = 257;

    /**
     * Encode frames to GIF89a at given fps; write to outputPath.
     */
    public static void encode(List<BufferedImage> frames, int fps, Path outputPath) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
        int delayCs = (int) Math.round(100.0 / fps);
        if (delayCs < 1) delayCs = 1;

        try (OutputStream out = Files.newOutputStream(outputPath)) {
            int w = frames.get(0).getWidth();
            int h = frames.get(0).getHeight();

            writeHeader(out);
            writeLogicalScreenDescriptor(out, w, h, 0, 0, 0);
            writeNetscapeLooping(out);

            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                PaletteQuantizer.QuantizedFrame q = PaletteQuantizer.quantize(frame, 256);
                writeGraphicControlExtension(out, delayCs, 0);
                writeImageDescriptor(out, 0, 0, q.getWidth(), q.getHeight(), true, q.getPalette().length);
                writeLocalColorTable(out, q.getPalette());
                writeLzwImageData(out, q.getIndexedPixels(), q.getWidth(), q.getHeight(), q.getPalette().length);
            }

            out.write(0x3B);
        }
    }

    private static void writeHeader(OutputStream out) throws IOException {
        out.write("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeLogicalScreenDescriptor(OutputStream out, int w, int h, int globalColorTableSize, int bgIndex, int pixelAspect) throws IOException {
        out.write(w & 0xff);
        out.write((w >> 8) & 0xff);
        out.write(h & 0xff);
        out.write((h >> 8) & 0xff);
        int packed = (pixelAspect & 0x0f) << 4;
        if (globalColorTableSize > 0) {
            packed |= 0x80;
            packed |= colorTableSizeToBits(globalColorTableSize) & 7;
        }
        out.write(packed);
        out.write(bgIndex & 0xff);
        out.write(0);
    }

    private static void writeNetscapeLooping(OutputStream out) throws IOException {
        out.write(0x21);
        out.write(0xff);
        out.write(11);
        out.write("NETSCAPE2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.write(3);
        out.write(1);
        out.write(0);
        out.write(0);
        out.write(0);
    }

    private static void writeGraphicControlExtension(OutputStream out, int delayCentiseconds, int transparentColorIndex) throws IOException {
        out.write(0x21);
        out.write(0xf9);
        out.write(4);
        int flags = 0;
        if (transparentColorIndex >= 0) {
            flags = 1;
        }
        out.write(flags);
        out.write(delayCentiseconds & 0xff);
        out.write((delayCentiseconds >> 8) & 0xff);
        out.write(transparentColorIndex >= 0 ? transparentColorIndex & 0xff : 0);
        out.write(0);
    }

    private static void writeImageDescriptor(OutputStream out, int left, int top, int w, int h, boolean localColorTable, int colorCount) throws IOException {
        out.write(0x2c);
        out.write(left & 0xff);
        out.write((left >> 8) & 0xff);
        out.write(top & 0xff);
        out.write((top >> 8) & 0xff);
        out.write(w & 0xff);
        out.write((w >> 8) & 0xff);
        out.write(h & 0xff);
        out.write((h >> 8) & 0xff);
        int packed = 0;
        if (localColorTable && colorCount > 0) {
            int bits = colorTableSizeToBits(colorCount);
            packed = 0x80 | (Math.max(0, bits - 1));
        }
        out.write(packed);
    }

    private static int colorTableSizeToBits(int colorCount) {
        int n = 1;
        int bits = 0;
        while (n < colorCount && bits < 8) {
            n *= 2;
            bits++;
        }
        return Math.max(1, bits);
    }

    private static void writeLocalColorTable(OutputStream out, int[] palette) throws IOException {
        int bits = colorTableSizeToBits(palette.length);
        int size = 1 << bits;
        for (int i = 0; i < size; i++) {
            if (i < palette.length) {
                int p = palette[i];
                out.write((p >> 16) & 0xff);
                out.write((p >> 8) & 0xff);
                out.write(p & 0xff);
            } else {
                out.write(0);
                out.write(0);
                out.write(0);
            }
        }
    }

    private static void writeLzwImageData(OutputStream out, byte[] indexedPixels, int w, int h, int colorCount) throws IOException {
        int minCodeSize = Math.max(2, colorTableSizeToBits(colorCount));
        out.write(minCodeSize);

        int clearCode = 1 << minCodeSize;
        int endCode = clearCode + 1;
        List<Integer> codes = new ArrayList<>();
        codes.add(clearCode);

        Map<Long, Integer> dict = new HashMap<>();
        int nextCode = endCode + 1;
        int codeSize = minCodeSize + 1;
        int maxCode = (1 << codeSize) - 1;

        int n = w * h;
        if (n == 0) {
            codes.add(endCode);
            writeLzwSubBlocks(out, codes, codeSize);
            return;
        }

        int prefix = indexedPixels[0] & 0xff;
        for (int i = 1; i < n; i++) {
            int k = indexedPixels[i] & 0xff;
            long key = ((long) prefix << 8) | k;
            Integer existing = dict.get(key);
            if (existing != null) {
                prefix = existing;
                continue;
            }
            codes.add(prefix);
            if (nextCode < 4096) {
                dict.put(key, nextCode);
                nextCode++;
                if (nextCode > maxCode && codeSize < 12) {
                    codeSize++;
                    maxCode = (1 << codeSize) - 1;
                }
            } else {
                codes.add(clearCode);
                dict.clear();
                nextCode = endCode + 1;
                codeSize = minCodeSize + 1;
                maxCode = (1 << codeSize) - 1;
            }
            prefix = k;
        }
        codes.add(prefix);
        codes.add(endCode);

        writeLzwSubBlocks(out, codes, codeSize);
    }

    private static void writeLzwSubBlocks(OutputStream out, List<Integer> codes, int initialCodeSize) throws IOException {
        int codeSize = initialCodeSize;
        int bitMask = (1 << codeSize) - 1;
        long bitBuf = 0;
        int bitCount = 0;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        for (int code : codes) {
            bitBuf |= ((long) code & bitMask) << bitCount;
            bitCount += codeSize;
            while (bitCount >= 8) {
                buf.write((int) (bitBuf & 0xff));
                bitBuf >>= 8;
                bitCount -= 8;
            }
            if (code == CLEAR_CODE) {
                codeSize = initialCodeSize;
                bitMask = (1 << codeSize) - 1;
            } else if (code != END_CODE && codeSize < 12 && code == (1 << codeSize) - 1) {
                codeSize++;
                bitMask = (1 << codeSize) - 1;
            }
        }
        if (bitCount > 0) {
            buf.write((int) (bitBuf & 0xff));
        }

        byte[] packed = buf.toByteArray();
        int off = 0;
        while (off < packed.length) {
            int subLen = Math.min(255, packed.length - off);
            out.write(subLen);
            out.write(packed, off, subLen);
            off += subLen;
        }
        out.write(0);
    }
}

package me.replaygif.renderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Utilities for loading images with correct color handling. ImageIO can return grayscale
 * or indexed BufferedImages on some JVMs, causing block textures to appear desaturated.
 * This forces RGB/ARGB output when loading PNGs.
 */
public final class ImageLoadUtil {

    private ImageLoadUtil() {}

    /**
     * Loads a PNG from the given stream and returns a BufferedImage in TYPE_INT_ARGB.
     * Uses ImageReader with explicit destination type when supported to avoid grayscale
     * or indexed output that can occur with plain ImageIO.read() on some systems.
     */
    public static BufferedImage loadPngAsArgb(InputStream input) throws IOException {
        if (input == null) return null;
        byte[] bytes = readAllBytes(input);
        if (bytes.length == 0) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) return ImageIO.read(new ByteArrayInputStream(bytes));
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) return ImageIO.read(new ByteArrayInputStream(bytes));
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false);
                ImageReadParam param = reader.getDefaultReadParam();
                ImageTypeSpecifier destType = findArgbType(reader);
                if (destType != null) {
                    param.setDestinationType(destType);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        }
    }

    /**
     * Loads a PNG from the given file. Same as loadPngAsArgb(InputStream) but avoids
     * buffering the whole file when reading from disk.
     */
    public static BufferedImage loadPngAsArgb(File file) throws IOException {
        if (file == null || !file.isFile()) return null;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return loadPngAsArgb(in);
        }
    }

    /**
     * Loads a PNG from the given path.
     */
    public static BufferedImage loadPngAsArgb(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return null;
        try (InputStream in = Files.newInputStream(path)) {
            return loadPngAsArgb(in);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static ImageTypeSpecifier findArgbType(ImageReader reader) throws IOException {
        Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
        while (types.hasNext()) {
            ImageTypeSpecifier spec = types.next();
            int type = spec.getBufferedImageType();
            if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB) {
                return spec;
            }
        }
        return null;
    }
}

package me.replaygif.tools;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates placeholder crack_stage_0.png through crack_stage_9.png (16×16) for block break overlay.
 * Run from project root: gradlew generateCrackPlaceholders
 * Output: src/main/resources/crack_stage_0.png ... crack_stage_9.png
 */
public final class GenerateCrackPlaceholders {

    public static void main(String[] args) throws Exception {
        Path outDir = args.length > 0 ? Paths.get(args[0]) : Paths.get("src/main/resources");
        Files.createDirectories(outDir);
        int size = 16;
        for (int stage = 0; stage < 10; stage++) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                float intensity = 0.15f + (stage / 9.0f) * 0.75f;
                int gray = (int) (intensity * 255);
                Color c = new Color(gray, gray, gray, 180 + stage * 7);
                g.setColor(c);
                g.fillRect(0, 0, size, size);
                g.setColor(new Color(0, 0, 0, 80 + stage * 15));
                int lines = 2 + stage;
                for (int i = 1; i < size; i += size / Math.max(1, lines)) {
                    g.drawLine(i, 0, i, size);
                    g.drawLine(0, i, size, i);
                }
            } finally {
                g.dispose();
            }
            Path path = outDir.resolve("crack_stage_" + stage + ".png");
            ImageIO.write(img, "PNG", path.toFile());
            System.out.println("Wrote " + path);
        }
    }
}

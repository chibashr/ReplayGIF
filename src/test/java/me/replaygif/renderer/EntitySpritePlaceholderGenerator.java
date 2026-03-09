package me.replaygif.renderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-off: generates minimal placeholder PNGs for hostile mobs that need bundled sprites.
 * Run and copy from build/entity_sprites_placeholders/ to src/main/resources/entity_sprites_default/.
 * Placeholders are solid-color rectangles (correct hue) so the registry has a sprite; replace with proper art later.
 */
public final class EntitySpritePlaceholderGenerator {

    private static final int W = 16;
    private static final int H = 32;

    public static void main(String[] args) throws Exception {
        Path out = Paths.get("build", "entity_sprites_placeholders");
        Files.createDirectories(out);
        writePlaceholder(out, "warden.png", new Color(0x2C, 0x2C, 0x2C));
        writePlaceholder(out, "breeze.png", new Color(0x87, 0xCE, 0xEB));
        writePlaceholder(out, "bogged.png", new Color(0x6B, 0x8E, 0x23));
        System.out.println("Wrote placeholders to " + out.toAbsolutePath());
    }

    private static void writePlaceholder(Path dir, String name, Color color) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, W, H);
        g.dispose();
        ImageIO.write(img, "PNG", dir.resolve(name).toFile());
    }
}

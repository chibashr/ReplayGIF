package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Bundled HUD sprites and armor values. Loads from classpath (hud/). Missing sprites
 * are replaced with 9×9 placeholder images of the correct color.
 */
public class HudResources {

    private static final int SPRITE_SIZE = 9;
    private static final Color HEART_FULL = new Color(0xFF0000);
    private static final Color HEART_OUTLINE = new Color(0x555555);
    private static final Color HEART_GOLDEN = new Color(0xFFAA00);
    private static final Color ARMOR_FULL = new Color(0x7F7F7F);
    private static final Color ARMOR_EMPTY = new Color(0x3A3A3A);
    private static final Color FOOD_FULL = new Color(0x9B6133);
    private static final Color FOOD_EMPTY = new Color(0x3A3A3A);

    private final Class<?> resourceAnchor;
    private final BufferedImage heartFull;
    private final BufferedImage heartHalf;
    private final BufferedImage heartEmpty;
    private final BufferedImage armorFull;
    private final BufferedImage armorEmpty;
    private final BufferedImage foodFull;
    private final BufferedImage foodEmpty;
    private final Map<String, Integer> armorValues;

    public HudResources(Class<?> resourceAnchor) throws IOException {
        this.resourceAnchor = resourceAnchor != null ? resourceAnchor : HudResources.class;
        this.heartFull = loadOrPlaceholder("hud/heart_full.png", HEART_FULL, HEART_OUTLINE);
        this.heartHalf = loadOrPlaceholder("hud/heart_half.png", HEART_FULL, HEART_OUTLINE);
        this.heartEmpty = loadOrPlaceholder("hud/heart_empty.png", HEART_OUTLINE);
        this.armorFull = loadOrPlaceholder("hud/armor_full.png", ARMOR_FULL);
        this.armorEmpty = loadOrPlaceholder("hud/armor_empty.png", ARMOR_EMPTY);
        this.foodFull = loadOrPlaceholder("hud/food_full.png", FOOD_FULL);
        this.foodEmpty = loadOrPlaceholder("hud/food_empty.png", FOOD_EMPTY);
        this.armorValues = loadArmorValues();
    }

    private BufferedImage loadOrPlaceholder(String path, Color fill, Color outline) {
        BufferedImage img = loadImage(path);
        if (img != null) return img;
        return createPlaceholder(fill, outline);
    }

    private BufferedImage loadOrPlaceholder(String path, Color fill) {
        BufferedImage img = loadImage(path);
        if (img != null) return img;
        return createPlaceholder(fill, null);
    }

    private BufferedImage loadImage(String path) {
        try (InputStream is = resourceAnchor.getResourceAsStream("/" + path)) {
            return is != null ? ImageIO.read(is) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage createPlaceholder(Color fill, Color outline) {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            if (outline != null) {
                g.setColor(outline);
                g.fillRect(0, 0, SPRITE_SIZE, SPRITE_SIZE);
                g.setColor(fill);
                g.fillRect(1, 1, SPRITE_SIZE - 2, SPRITE_SIZE - 2);
            } else {
                g.setColor(fill);
                g.fillRect(0, 0, SPRITE_SIZE, SPRITE_SIZE);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private Map<String, Integer> loadArmorValues() throws IOException {
        try (InputStream is = resourceAnchor.getResourceAsStream("/hud/armor_values.json")) {
            if (is == null) return Collections.emptyMap();
            try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Map<String, Integer> map = new Gson().fromJson(r, new TypeToken<Map<String, Integer>>() {}.getType());
                return map != null ? map : Collections.emptyMap();
            }
        }
    }

    public BufferedImage getHeartFull() { return heartFull; }
    public BufferedImage getHeartHalf() { return heartHalf; }
    public BufferedImage getHeartEmpty() { return heartEmpty; }
    public BufferedImage getArmorFull() { return armorFull; }
    public BufferedImage getArmorEmpty() { return armorEmpty; }
    public BufferedImage getFoodFull() { return foodFull; }
    public BufferedImage getFoodEmpty() { return foodEmpty; }
    public Map<String, Integer> getArmorValues() { return armorValues; }
    public int getSpriteSize() { return SPRITE_SIZE; }
}

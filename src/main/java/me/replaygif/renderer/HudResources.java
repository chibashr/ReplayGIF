package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * HUD sprites (hearts, food, armor) and armor values. Load order: resource pack →
 * client jar → mcasset.cloud → bundled (hud/). Minecraft Java Edition paths (1.20+):
 * assets/minecraft/textures/gui/sprites/hud/heart/, hud/food/, hud/armor/.
 */
public class HudResources {

    private static final int SPRITE_SIZE = 9;
    private static final String GUI_SPRITES_PREFIX = "assets/minecraft/textures/gui/sprites/hud/";
    private static final Color HEART_FULL = new Color(0xFF0000);
    private static final Color HEART_OUTLINE = new Color(0x555555);
    private static final Color HEART_GOLDEN = new Color(0xFFAA00);
    private static final Color ARMOR_FULL = new Color(0x7F7F7F);
    private static final Color ARMOR_EMPTY = new Color(0x3A3A3A);
    private static final Color FOOD_FULL = new Color(0x9B6133);
    private static final Color FOOD_EMPTY = new Color(0x3A3A3A);

    private final Class<?> resourceAnchor;
    private final String resourcePackPath;
    private final String clientJarPath;
    private final McAssetFetcher mcAssetFetcher;
    private final BufferedImage heartFull;
    private final BufferedImage heartHalf;
    private final BufferedImage heartEmpty;
    private final BufferedImage armorFull;
    private final BufferedImage armorEmpty;
    private final BufferedImage foodFull;
    private final BufferedImage foodEmpty;
    private final Map<String, Integer> armorValues;

    public HudResources(Class<?> resourceAnchor) throws IOException {
        this(resourceAnchor, null, null, null);
    }

    /** Without mcasset fetcher. */
    public HudResources(Class<?> resourceAnchor, String resourcePackPath, String clientJarPath) throws IOException {
        this(resourceAnchor, resourcePackPath, clientJarPath, null);
    }

    /**
     * @param resourcePackPath path to Minecraft Java Edition resource pack (zip or folder)
     * @param clientJarPath    path to Minecraft client JAR for HUD sprites
     * @param mcAssetFetcher   optional fetcher for mcasset.cloud (HUD sprites)
     */
    public HudResources(Class<?> resourceAnchor, String resourcePackPath, String clientJarPath,
                        McAssetFetcher mcAssetFetcher) throws IOException {
        this.resourceAnchor = resourceAnchor != null ? resourceAnchor : HudResources.class;
        this.resourcePackPath = (resourcePackPath != null && !resourcePackPath.isBlank()) ? resourcePackPath.trim() : null;
        this.clientJarPath = (clientJarPath != null && !clientJarPath.isBlank()) ? clientJarPath.trim() : null;
        this.mcAssetFetcher = mcAssetFetcher;
        /* 1.21 paths: heart/ is subfolder (full.png, half.png, container.png); armor and food are flat in hud/ */
        this.heartFull = loadOrPlaceholder("heart/full.png", "hud/heart_full.png", HEART_FULL, HEART_OUTLINE);
        this.heartHalf = loadOrPlaceholder("heart/half.png", "hud/heart_half.png", HEART_FULL, HEART_OUTLINE);
        this.heartEmpty = loadOrPlaceholder("heart/container.png", "hud/heart_empty.png", HEART_OUTLINE);
        this.armorFull = loadOrPlaceholder("armor_full.png", "hud/armor_full.png", ARMOR_FULL);
        this.armorEmpty = loadOrPlaceholder("armor_empty.png", "hud/armor_empty.png", ARMOR_EMPTY);
        this.foodFull = loadOrPlaceholder("food_full.png", "hud/food_full.png", FOOD_FULL);
        this.foodEmpty = loadOrPlaceholder("food_empty.png", "hud/food_empty.png", FOOD_EMPTY);
        this.armorValues = loadArmorValues();
    }

    private BufferedImage loadOrPlaceholder(String mcPath, String bundledPath, Color fill, Color outline) {
        BufferedImage img = loadFromExternal(GUI_SPRITES_PREFIX + mcPath);
        if (img == null) img = loadImage(bundledPath);
        if (img != null) return img;
        return createPlaceholder(fill, outline);
    }

    private BufferedImage loadOrPlaceholder(String mcPath, String bundledPath, Color fill) {
        BufferedImage img = loadFromExternal(GUI_SPRITES_PREFIX + mcPath);
        if (img == null) img = loadImage(bundledPath);
        if (img != null) return img;
        return createPlaceholder(fill, null);
    }

    private BufferedImage loadFromExternal(String pathSuffix) {
        String suffix = pathSuffix.toLowerCase().replace('\\', '/');
        if (resourcePackPath != null) {
            BufferedImage img = loadFromPath(resourcePackPath, suffix);
            if (img != null) return img;
        }
        if (clientJarPath != null) {
            BufferedImage img = loadFromPath(clientJarPath, suffix);
            if (img != null) return img;
        }
        if (mcAssetFetcher != null) {
            BufferedImage img = mcAssetFetcher.fetchGui(suffix).orElse(null);
            if (img != null) return img;
        }
        return null;
    }

    private BufferedImage loadFromPath(String path, String pathSuffix) {
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            if (f.isDirectory()) {
                Path p = f.toPath().resolve(pathSuffix);
                if (Files.isRegularFile(p)) {
                    return ImageIO.read(p.toFile());
                }
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(path)) {
                    ZipEntry entry = findZipEntry(zip, pathSuffix);
                    if (entry != null) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            return ImageIO.read(is);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ZipEntry findZipEntry(ZipFile zip, String pathSuffix) {
        String suffix = pathSuffix.toLowerCase().replace('\\', '/');
        ZipEntry direct = zip.getEntry(pathSuffix);
        if (direct != null && !direct.isDirectory()) return direct;
        direct = zip.getEntry(suffix);
        if (direct != null && !direct.isDirectory()) return direct;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;
            String name = e.getName().toLowerCase().replace('\\', '/');
            if (name.equals(suffix) || name.endsWith("/" + suffix)) return e;
        }
        return null;
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

package me.replaygif.renderer;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides item sprites for HUD hotbar and dropped items. Prefer client jar textures
 * (assets/minecraft/textures/item/) when configured; fallback to bundled resources
 * (hud/item_icons/MATERIAL.png). Returns empty when no texture is available; renderer
 * uses a colored fallback rectangle.
 */
public class ItemTextureCache {

    private static final String CLIENT_ITEM_PREFIX = "assets/minecraft/textures/item/";
    private static final String BUNDLED_ICON_PREFIX = "hud/item_icons/";
    private static final String ICON_SUFFIX = ".png";

    private final JavaPlugin plugin;
    private final String clientJarPath;
    private final Class<?> resourceAnchor;
    private final ConcurrentHashMap<String, BufferedImage> cache = new ConcurrentHashMap<>();
    private volatile ZipFile clientJar;

    /**
     * Production constructor: loads from client JAR when path is set, else bundled.
     */
    public ItemTextureCache(JavaPlugin plugin, String clientJarPath) {
        this.plugin = plugin;
        this.clientJarPath = (clientJarPath != null && !clientJarPath.isBlank()) ? clientJarPath.trim() : null;
        this.resourceAnchor = plugin != null ? plugin.getClass() : getClass();
    }

    /**
     * Test constructor: loads only from bundled resources via the given class.
     */
    public ItemTextureCache(Class<?> resourceAnchor) {
        this.plugin = null;
        this.clientJarPath = null;
        this.resourceAnchor = resourceAnchor != null ? resourceAnchor : getClass();
    }

    /**
     * Returns a cached or loaded texture for the given material name (e.g. "DIAMOND_SWORD").
     * Empty if the resource is missing.
     */
    public Optional<BufferedImage> getTexture(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.computeIfAbsent(materialName, this::loadIcon));
    }

    private BufferedImage loadIcon(String materialName) {
        if (clientJarPath != null) {
            BufferedImage fromJar = loadFromClientJar(materialName.toLowerCase());
            if (fromJar != null) return fromJar;
        }
        String path = BUNDLED_ICON_PREFIX + materialName + ICON_SUFFIX;
        try (InputStream is = resourceAnchor.getResourceAsStream("/" + path)) {
            return is != null ? ImageIO.read(is) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage loadFromClientJar(String materialLower) {
        try {
            ZipFile zip = getClientJar();
            if (zip == null) return null;
            String path = CLIENT_ITEM_PREFIX + materialLower + ICON_SUFFIX;
            ZipEntry entry = zip.getEntry(path);
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private ZipFile getClientJar() {
        if (clientJarPath == null) return null;
        ZipFile z = clientJar;
        if (z == null) {
            synchronized (this) {
                z = clientJar;
                if (z == null) {
                    try {
                        clientJar = new ZipFile(clientJarPath);
                        z = clientJar;
                        if (plugin != null) {
                            plugin.getSLF4JLogger().info("ItemTextureCache: using client jar at {}", clientJarPath);
                        }
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return z;
    }
}

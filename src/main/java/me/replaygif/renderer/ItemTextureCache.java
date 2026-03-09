package me.replaygif.renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides item sprites for HUD hotbar. Loads from bundled resources (e.g. hud/item_icons/MATERIAL.png).
 * Returns empty when no texture is available; renderer uses a colored fallback rectangle.
 */
public class ItemTextureCache {

    private static final String ICON_PREFIX = "hud/item_icons/";
    private static final String ICON_SUFFIX = ".png";

    private final Class<?> resourceAnchor;
    private final ConcurrentHashMap<String, BufferedImage> cache = new ConcurrentHashMap<>();

    public ItemTextureCache(Class<?> resourceAnchor) {
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
        String path = ICON_PREFIX + materialName + ICON_SUFFIX;
        try (InputStream is = resourceAnchor.getResourceAsStream("/" + path)) {
            return is != null ? ImageIO.read(is) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides a sprite image per EntityType for the isometric entity pass. Prefer client jar
 * textures when configured so entities match Minecraft visuals; fallback to bundled sprites
 * when the jar is missing or not set. Bounds (width/height) come from entity_bounds.json so
 * sprite scaling matches entity size; default 0.6×1.8 covers players and humanoids.
 */
public class EntitySpriteRegistry {

    private static final String ENTITY_TEXTURES_PREFIX = "assets/minecraft/textures/entity/";
    private static final String BUNDLED_SPRITES_PREFIX = "entity_sprites_default/";
    private static final double DEFAULT_WIDTH = 0.6;
    private static final double DEFAULT_HEIGHT = 1.8;

    private final JavaPlugin plugin;
    private final Map<EntityType, BufferedImage> spriteByType = new HashMap<>();
    private final Map<EntityType, BoundingBox> boundsByType = new HashMap<>();
    private volatile BufferedImage fireOverlay;
    private volatile BufferedImage gravestone;

    /**
     * Loads bounds and sprites at construction. clientJarPath optional; if valid we read
     * from assets/minecraft/textures/entity/ in the jar, else we use plugin resources.
     *
     * @param plugin        for getResource() and logger
     * @param clientJarPath path to client jar or null/empty to use bundled sprites only
     */
    public EntitySpriteRegistry(JavaPlugin plugin, String clientJarPath) throws IOException {
        this.plugin = plugin;
        loadBounds(plugin.getResource("entity_bounds.json"));
        loadSprites(plugin, clientJarPath);
    }

    private void loadBounds(InputStream in) throws IOException {
        if (in == null) {
            return;
        }
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Map<String, BoundsEntry> raw = new Gson().fromJson(r, new TypeToken<Map<String, BoundsEntry>>() {}.getType());
            if (raw != null) {
                for (Map.Entry<String, BoundsEntry> e : raw.entrySet()) {
                    try {
                        EntityType type = EntityType.valueOf(e.getKey());
                        BoundsEntry b = e.getValue();
                        if (b != null && b.width > 0 && b.height > 0) {
                            boundsByType.put(type, new BoundingBox(b.width, b.height));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // unknown entity type name, skip
                    }
                }
            }
        }
    }

    private void loadSprites(JavaPlugin plugin, String clientJarPath) throws IOException {
        boolean fromJar = false;
        if (clientJarPath != null && !clientJarPath.isBlank()) {
            try (ZipFile zip = new ZipFile(clientJarPath.trim())) {
                for (EntityType type : EntityType.values()) {
                    String name = type.name().toLowerCase();
                    String path = ENTITY_TEXTURES_PREFIX + name + ".png";
                    ZipEntry entry = zip.getEntry(path);
                    if (entry != null && !entry.isDirectory()) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            BufferedImage img = ImageIO.read(is);
                            if (img != null) {
                                spriteByType.put(type, img);
                            }
                        }
                    }
                }
                fromJar = true;
            } catch (IOException ignored) {
                // fall through to bundled
            }
        }
        if (!fromJar) {
            for (EntityType type : EntityType.values()) {
                String name = type.name().toLowerCase();
                String path = BUNDLED_SPRITES_PREFIX + name + ".png";
                try (InputStream is = plugin.getResource(path)) {
                    if (is != null) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            spriteByType.put(type, img);
                        }
                    }
                }
            }
        }
        if (fromJar) {
            plugin.getSLF4JLogger().info("EntitySpriteRegistry: using client jar at {}", clientJarPath.trim());
        } else {
            plugin.getSLF4JLogger().info("EntitySpriteRegistry: using bundled sprites (client jar not found or not configured).");
        }
    }

    /** Sprite for this entity type; empty if not found (renderer uses placeholder). */
    public Optional<BufferedImage> getSprite(EntityType type) {
        return Optional.ofNullable(spriteByType.get(type));
    }

    /** Width and height in blocks for sprite scaling; default 0.6×1.8 when not in bounds file. */
    public BoundingBox getBounds(EntityType type) {
        return boundsByType.getOrDefault(type, new BoundingBox(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    }

    /** Lazy-loaded fire overlay for entities with onFire; null if resource missing. */
    public BufferedImage getFireOverlay() {
        if (fireOverlay == null) {
            synchronized (this) {
                if (fireOverlay == null) {
                    fireOverlay = loadImage(BUNDLED_SPRITES_PREFIX + "fire_overlay.png");
                }
            }
        }
        return fireOverlay;
    }

    /** Lazy-loaded gravestone for death overlay; null if resource missing. */
    public BufferedImage getGravestone() {
        if (gravestone == null) {
            synchronized (this) {
                if (gravestone == null) {
                    gravestone = loadImage(BUNDLED_SPRITES_PREFIX + "gravestone.png");
                }
            }
        }
        return gravestone;
    }

    private BufferedImage loadImage(String path) {
        try (InputStream is = plugin.getResource(path)) {
            return is != null ? ImageIO.read(is) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static class BoundsEntry {
        double width;
        double height;
    }
}

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
 * EntityType → BufferedImage sprite. At startup: if client jar path is set and
 * present, extracts entity textures from assets/minecraft/textures/entity/ via
 * ZipFile and caches per EntityType; otherwise loads bundled stand-in sprites
 * from plugin resources. Also loads entity_bounds.json for getBounds(); fallback
 * 0.6×1.8 for humanoids.
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
     * @param plugin         for resource loading (entity_bounds.json, bundled sprites)
     * @param clientJarPath  configured path to client jar; if null/empty or file missing, use bundled only
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
    }

    /**
     * Returns the sprite for the given entity type, if available.
     */
    public Optional<BufferedImage> getSprite(EntityType type) {
        return Optional.ofNullable(spriteByType.get(type));
    }

    /**
     * Returns bounding box for the entity type. Fallback 0.6×1.8 for unknown types.
     */
    public BoundingBox getBounds(EntityType type) {
        return boundsByType.getOrDefault(type, new BoundingBox(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    }

    /** Bundled fire overlay sprite (composited over entities on fire). May be null if resource missing. */
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

    /** Bundled gravestone sprite (death marker). May be null if resource missing. */
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

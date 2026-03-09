package me.replaygif.renderer;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides item sprites for HUD hotbar, armor slots, and dropped items. Load order:
 * resource_pack_path → client_jar_path → Mojang cache → bundled (hud/item_icons/).
 * Returns empty when no texture is available; renderer uses a colored fallback rectangle.
 */
public class ItemTextureCache {

    private static final String CLIENT_ITEM_PREFIX = "assets/minecraft/textures/item/";
    private static final String CLIENT_BLOCK_PREFIX = "assets/minecraft/textures/block/";
    private static final String BUNDLED_ICON_PREFIX = "hud/item_icons/";
    private static final String ICON_SUFFIX = ".png";

    private final JavaPlugin plugin;
    private final String resourcePackPath;
    private final String clientJarPath;
    private final Path mojangCacheDir;
    private final Class<?> resourceAnchor;
    private final ConcurrentHashMap<String, BufferedImage> cache = new ConcurrentHashMap<>();
    private volatile ZipFile clientJar;
    private volatile ZipFile resourcePackZip;

    /**
     * Production constructor with configurable sources. Null/empty paths are skipped.
     *
     * @param resourcePackPath path to resource pack folder or zip; checked first
     * @param clientJarPath    path to Minecraft client JAR
     * @param mojangCacheDir   path to Mojang-downloaded texture cache (e.g. texture_cache/items)
     */
    public ItemTextureCache(JavaPlugin plugin, String resourcePackPath, String clientJarPath, Path mojangCacheDir) {
        this.plugin = plugin;
        this.resourcePackPath = (resourcePackPath != null && !resourcePackPath.isBlank()) ? resourcePackPath.trim() : null;
        this.clientJarPath = (clientJarPath != null && !clientJarPath.isBlank()) ? clientJarPath.trim() : null;
        this.mojangCacheDir = mojangCacheDir;
        this.resourceAnchor = plugin != null ? plugin.getClass() : getClass();
    }

    /**
     * Simplified constructor: client JAR only, no resource pack or Mojang cache.
     */
    public ItemTextureCache(JavaPlugin plugin, String clientJarPath) {
        this(plugin, null, clientJarPath, null);
    }

    /**
     * Test constructor: loads only from bundled resources via the given class.
     */
    public ItemTextureCache(Class<?> resourceAnchor) {
        this.plugin = null;
        this.resourcePackPath = null;
        this.clientJarPath = null;
        this.mojangCacheDir = null;
        this.resourceAnchor = resourceAnchor != null ? resourceAnchor : getClass();
    }

    /**
     * Returns a cached or loaded texture for the given material name (e.g. "DIAMOND_SWORD").
     * Empty if the resource is missing. Does not cache misses so textures that appear later
     * (e.g. after Mojang download completes) can be found.
     */
    public Optional<BufferedImage> getTexture(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return Optional.empty();
        }
        BufferedImage cached = cache.get(materialName);
        if (cached != null) return Optional.of(cached);
        BufferedImage loaded = loadIcon(materialName);
        if (loaded != null) {
            cache.put(materialName, loaded);
            return Optional.of(loaded);
        }
        return Optional.empty();
    }

    private BufferedImage loadIcon(String materialName) {
        String materialLower = materialName.toLowerCase();
        // 1. Resource pack (folder or zip)
        if (resourcePackPath != null) {
            BufferedImage img = loadFromResourcePack(materialLower);
            if (img != null) return img;
        }
        // 2. Client JAR
        if (clientJarPath != null) {
            BufferedImage img = loadFromClientJar(materialLower);
            if (img != null) return img;
        }
        // 3. Mojang cache
        if (mojangCacheDir != null && Files.isDirectory(mojangCacheDir)) {
            BufferedImage img = loadFromMojangCache(materialName);
            if (img != null) return img;
        }
        // 4. Bundled
        return loadFromBundled(materialName);
    }

    private BufferedImage loadFromResourcePack(String materialLower) {
        File f = new File(resourcePackPath);
        if (!f.exists()) return null;
        if (f.isDirectory()) {
            Path itemPath = f.toPath().resolve("assets/minecraft/textures/item").resolve(materialLower + ICON_SUFFIX);
            Path blockPath = f.toPath().resolve("assets/minecraft/textures/block").resolve(materialLower + ICON_SUFFIX);
            try {
                if (Files.isRegularFile(itemPath)) {
                    return ImageIO.read(itemPath.toFile());
                }
                if (Files.isRegularFile(blockPath)) {
                    return ImageIO.read(blockPath.toFile());
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        if (f.getName().toLowerCase().endsWith(".zip")) {
            try {
                ZipFile zip = getResourcePackZip();
                if (zip == null) return null;
                String itemEntry = "assets/minecraft/textures/item/" + materialLower + ICON_SUFFIX;
                ZipEntry entry = zip.getEntry(itemEntry);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) return img;
                    }
                }
                String blockEntry = "assets/minecraft/textures/block/" + materialLower + ICON_SUFFIX;
                entry = zip.getEntry(blockEntry);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        return ImageIO.read(is);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private ZipFile getResourcePackZip() {
        if (resourcePackPath == null) return null;
        ZipFile z = resourcePackZip;
        if (z == null) {
            synchronized (this) {
                z = resourcePackZip;
                if (z == null) {
                    try {
                        resourcePackZip = new ZipFile(resourcePackPath);
                        z = resourcePackZip;
                        if (plugin != null) {
                            plugin.getSLF4JLogger().info("ItemTextureCache: using resource pack at {}", resourcePackPath);
                        }
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return z;
    }

    private BufferedImage loadFromClientJar(String materialLower) {
        try {
            ZipFile zip = getClientJar();
            if (zip == null) return null;
            String itemPath = CLIENT_ITEM_PREFIX + materialLower + ICON_SUFFIX;
            ZipEntry entry = zip.getEntry(itemPath);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream is = zip.getInputStream(entry)) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) return img;
                }
            }
            String blockPath = CLIENT_BLOCK_PREFIX + materialLower + ICON_SUFFIX;
            entry = zip.getEntry(blockPath);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream is = zip.getInputStream(entry)) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    private BufferedImage loadFromMojangCache(String materialName) {
        try {
            Path p = mojangCacheDir.resolve(materialName + ICON_SUFFIX);
            if (Files.isRegularFile(p)) {
                return ImageIO.read(p.toFile());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private BufferedImage loadFromBundled(String materialName) {
        String path = BUNDLED_ICON_PREFIX + materialName + ICON_SUFFIX;
        try (InputStream is = resourceAnchor.getResourceAsStream("/" + path)) {
            return is != null ? ImageIO.read(is) : null;
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

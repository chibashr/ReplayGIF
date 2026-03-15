package me.replaygif.renderer;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides item sprites for HUD hotbar, armor slots, and dropped items. Load order:
 * resource_pack_path → mcasset.cloud → client_jar_path → Mojang cache → bundled (hud/item_icons/).
 * McAsset is prioritized for reliable, version-specific textures when no resource pack overrides.
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
    private final McAssetFetcher mcAssetFetcher;
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
     * @param mcAssetFetcher   optional fetcher for mcasset.cloud; used when other sources miss
     */
    public ItemTextureCache(JavaPlugin plugin, String resourcePackPath, String clientJarPath, Path mojangCacheDir,
                            McAssetFetcher mcAssetFetcher) {
        this.plugin = plugin;
        this.resourcePackPath = (resourcePackPath != null && !resourcePackPath.isBlank()) ? resourcePackPath.trim() : null;
        this.clientJarPath = (clientJarPath != null && !clientJarPath.isBlank()) ? clientJarPath.trim() : null;
        this.mojangCacheDir = mojangCacheDir;
        this.mcAssetFetcher = mcAssetFetcher;
        this.resourceAnchor = plugin != null ? plugin.getClass() : getClass();
    }

    /** Without mcasset fetcher. */
    public ItemTextureCache(JavaPlugin plugin, String resourcePackPath, String clientJarPath, Path mojangCacheDir) {
        this(plugin, resourcePackPath, clientJarPath, mojangCacheDir, null);
    }

    /**
     * Simplified constructor: client JAR only, no resource pack or Mojang cache.
     */
    public ItemTextureCache(JavaPlugin plugin, String clientJarPath) {
        this(plugin, null, clientJarPath, null, null);
    }

    /**
     * Test constructor: loads only from bundled resources via the given class.
     */
    public ItemTextureCache(Class<?> resourceAnchor) {
        this.plugin = null;
        this.resourcePackPath = null;
        this.clientJarPath = null;
        this.mojangCacheDir = null;
        this.mcAssetFetcher = null;
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
        // 2. mcasset.cloud (version-specific, reliable CDN)
        if (mcAssetFetcher != null) {
            BufferedImage img = mcAssetFetcher.fetchItemOrBlock(materialName).orElse(null);
            if (img != null) return img;
        }
        // 3. Client JAR
        if (clientJarPath != null) {
            BufferedImage img = loadFromClientJar(materialLower);
            if (img != null) return img;
        }
        // 4. Mojang cache
        if (mojangCacheDir != null && Files.isDirectory(mojangCacheDir)) {
            BufferedImage img = loadFromMojangCache(materialName);
            if (img != null) return img;
        }
        // 5. Bundled
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
                    return ImageLoadUtil.loadPngAsArgb(itemPath);
                }
                if (Files.isRegularFile(blockPath)) {
                    return ImageLoadUtil.loadPngAsArgb(blockPath);
                }
            } catch (IOException ignored) {
            }
            return null;
        }
        if (f.getName().toLowerCase().endsWith(".zip")) {
            try {
                ZipFile zip = getResourcePackZip();
                if (zip == null) return null;
                // Minecraft Java Edition standard paths (assets at zip root or under pack folder)
                String itemSuffix = "assets/minecraft/textures/item/" + materialLower + ICON_SUFFIX;
                ZipEntry entry = findZipEntry(zip, itemSuffix);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        BufferedImage img = ImageLoadUtil.loadPngAsArgb(is);
                        if (img != null) return img;
                    }
                }
                String blockSuffix = "assets/minecraft/textures/block/" + materialLower + ICON_SUFFIX;
                entry = findZipEntry(zip, blockSuffix);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        return ImageLoadUtil.loadPngAsArgb(is);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Finds a ZipEntry matching the path. Handles Minecraft Java Edition resource pack structure:
     * - Standard: assets/minecraft/textures/item/foo.png
     * - With root folder: MyPack/assets/minecraft/textures/item/foo.png (common when zipping a folder)
     * Uses case-insensitive matching.
     */
    private ZipEntry findZipEntry(ZipFile zip, String pathSuffix) {
        String suffix = pathSuffix.toLowerCase().replace('\\', '/');
        ZipEntry direct = zip.getEntry(pathSuffix);
        if (direct != null && !direct.isDirectory()) {
            return direct;
        }
        direct = zip.getEntry(suffix);
        if (direct != null && !direct.isDirectory()) {
            return direct;
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;
            String name = e.getName().toLowerCase().replace('\\', '/');
            if (name.equals(suffix) || name.endsWith("/" + suffix)) {
                return e;
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
                        if (plugin != null) {
                            plugin.getSLF4JLogger().warn("ItemTextureCache: resource pack at {} failed: {}. Using client jar or bundled.", resourcePackPath, e.getMessage());
                        }
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
                    BufferedImage img = ImageLoadUtil.loadPngAsArgb(is);
                    if (img != null) return img;
                }
            }
            String blockPath = CLIENT_BLOCK_PREFIX + materialLower + ICON_SUFFIX;
            entry = zip.getEntry(blockPath);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream is = zip.getInputStream(entry)) {
                    return ImageLoadUtil.loadPngAsArgb(is);
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
                return ImageLoadUtil.loadPngAsArgb(p);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private BufferedImage loadFromBundled(String materialName) {
        String path = BUNDLED_ICON_PREFIX + materialName + ICON_SUFFIX;
        try (InputStream is = resourceAnchor.getResourceAsStream("/" + path)) {
            return is != null ? ImageLoadUtil.loadPngAsArgb(is) : null;
        } catch (IOException e) {
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
                        if (plugin != null) {
                            plugin.getSLF4JLogger().warn("ItemTextureCache: client jar at {} failed: {}. Using Mojang cache or bundled.", clientJarPath, e.getMessage());
                        }
                    }
                }
            }
        }
        return z;
    }
}

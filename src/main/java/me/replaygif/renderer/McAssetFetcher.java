package me.replaygif.renderer;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches Minecraft textures from mcasset.cloud CDN. Version-driven and non–hardcoded.
 * Used as default when no client JAR or resource pack is configured.
 * Caches fetched assets on disk under texture_cache/mcasset/{version}/ to avoid re-downloads
 * and memory use; no in-memory cache.
 * <p>
 * Handles: entity sprites, item textures, block textures, GUI sprites (HUD hearts, armor, food).
 * URL format: {@code https://assets.mcasset.cloud/{version}/assets/minecraft/textures/{category}/{path}.png}
 * where version comes from config (e.g. 1.21, latest, snapshot).
 */
public final class McAssetFetcher {

    private static final String BASE_URL = "https://assets.mcasset.cloud/";
    private static final String USER_AGENT = "ReplayGif/1.0 (Minecraft server plugin)";

    private static final int PROGRESS_LOG_INTERVAL = 5;
    private static final int PREFETCH_THREADS = 12;
    private static final int PREFETCH_TIMEOUT_SEC = 90;

    private final JavaPlugin plugin;
    private final String version;
    private final Path cacheDir;
    private final HttpClient httpClient;
    private final ExecutorService prefetchPool;
    private final AtomicInteger downloadCount = new AtomicInteger(0);
    private volatile String lastDownloadedPath;

    public McAssetFetcher(JavaPlugin plugin, String version) {
        this.plugin = plugin;
        this.version = (version != null && !version.isBlank()) ? version.trim() : "1.21";
        this.cacheDir = plugin.getDataFolder().toPath().resolve("texture_cache").resolve("mcasset").resolve(sanitizeVersion(this.version));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.prefetchPool = Executors.newFixedThreadPool(PREFETCH_THREADS, r -> {
            Thread t = new Thread(r, "ReplayGif-McAssetPrefetch");
            t.setDaemon(true);
            return t;
        });
        plugin.getSLF4JLogger().info("McAssetFetcher: cache at {}, version {}, parallel prefetch enabled", cacheDir.toAbsolutePath(), this.version);
    }

    private static String sanitizeVersion(String v) {
        if (v == null) return "1.21";
        return v.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Reads an image from the disk cache only. No HTTP request is made.
     * Use during render to avoid blocking the render thread.
     *
     * @param assetPath full path within assets (no leading slash)
     * @return the image or empty if not in cache
     */
    public Optional<BufferedImage> fetchCachedOnly(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return Optional.empty();
        }
        String path = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        Path cacheFile = cacheDir.resolve(path);
        try {
            if (Files.isRegularFile(cacheFile)) {
                BufferedImage img = ImageLoadUtil.loadPngAsArgb(cacheFile);
                if (img != null) return Optional.of(img);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * Fetches an image from mcasset. Path is relative to assets root, e.g.
     * {@code assets/minecraft/textures/entity/zombie/zombie.png}.
     * Uses disk cache; does not hold images in memory.
     *
     * @param assetPath full path within assets (no leading slash)
     * @return the image or empty on failure
     */
    public Optional<BufferedImage> fetch(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return Optional.empty();
        }
        String path = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        Path cacheFile = cacheDir.resolve(path);
        try {
            if (Files.isRegularFile(cacheFile)) {
                BufferedImage img = ImageLoadUtil.loadPngAsArgb(cacheFile);
                if (img != null) {
                    img = ensureArgb(img);
                    plugin.getSLF4JLogger().debug("McAsset cache hit: {}", assetPath);
                    return Optional.of(img);
                }
            }
        } catch (Exception e) {
            plugin.getSLF4JLogger().debug("McAsset cache read failed for {}: {}", assetPath, e.getMessage());
        }
        plugin.getSLF4JLogger().debug("McAsset fetching: {}", assetPath);
        String url = BASE_URL + version + "/" + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                plugin.getSLF4JLogger().debug("McAsset HTTP {} for {}", response.statusCode(), assetPath);
                return Optional.empty();
            }
            try (InputStream in = response.body()) {
                BufferedImage img = ImageLoadUtil.loadPngAsArgb(in);
                if (img != null) {
                    img = ensureArgb(img);
                    Files.createDirectories(cacheFile.getParent());
                    ImageIO.write(img, "png", cacheFile.toFile());
                    int n = downloadCount.incrementAndGet();
                    lastDownloadedPath = path.substring(Math.max(0, path.length() - 40));
                    if (n % PROGRESS_LOG_INTERVAL == 1 || n <= 3) {
                        plugin.getSLF4JLogger().info("McAsset: {} asset{} downloaded (latest: {})",
                                n, n == 1 ? "" : "s", lastDownloadedPath);
                    } else {
                        plugin.getSLF4JLogger().debug("McAsset cached: {}", assetPath);
                    }
                    return Optional.of(img);
                }
            }
        } catch (Exception e) {
            plugin.getSLF4JLogger().warn("McAsset fetch failed for {}: {}", assetPath, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Fetches entity texture. Uses vanilla path: entity/{type}/{type}.png or entity/{type}.png.
     * Fishing hook uses fishing_bobber in vanilla assets.
     */
    public Optional<BufferedImage> fetchEntity(org.bukkit.entity.EntityType type) {
        String baseName = type == org.bukkit.entity.EntityType.FISHING_HOOK ? "fishing_bobber" : type.name().toLowerCase();
        Optional<BufferedImage> img = fetch("assets/minecraft/textures/entity/" + baseName + "/" + baseName + ".png");
        if (img.isEmpty()) {
            img = fetch("assets/minecraft/textures/entity/" + baseName + ".png");
        }
        if (img.isEmpty() && type == org.bukkit.entity.EntityType.FISHING_HOOK) {
            img = fetch("assets/minecraft/textures/entity/fishing_hook.png");
        }
        return img;
    }

    /**
     * Fetches item or block texture. Tries item first, then block.
     */
    public Optional<BufferedImage> fetchItemOrBlock(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return Optional.empty();
        }
        String name = materialName.toLowerCase();
        Optional<BufferedImage> img = fetch("assets/minecraft/textures/item/" + name + ".png");
        if (img.isEmpty()) {
            img = fetch("assets/minecraft/textures/block/" + name + ".png");
        }
        return img;
    }

    /**
     * Fetches block texture by exact name (e.g. grass_block_top, dirt).
     */
    public Optional<BufferedImage> fetchBlock(String textureName) {
        if (textureName == null || textureName.isEmpty()) {
            return Optional.empty();
        }
        return fetch("assets/minecraft/textures/block/" + textureName + ".png");
    }

    /**
     * Fetches GUI texture (HUD hearts, armor, food, etc.). Path is relative to textures/gui/,
     * e.g. {@code sprites/hud/heart/full.png}.
     */
    public Optional<BufferedImage> fetchGui(String guiPath) {
        if (guiPath == null || guiPath.isEmpty()) {
            return Optional.empty();
        }
        String path = guiPath.startsWith("assets/") ? guiPath : "assets/minecraft/textures/gui/" + guiPath;
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return fetch(path);
    }

    /** Ensures ARGB format for correct color handling; ImageIO can return grayscale on some JVMs. */
    private static BufferedImage ensureArgb(BufferedImage src) {
        if (src == null) return null;
        int t = src.getType();
        if (t == BufferedImage.TYPE_INT_ARGB || t == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Version used for URLs (e.g. 1.21, latest). */
    public String getVersion() {
        return version;
    }

    /** Total assets downloaded this session (not cache hits). For status/progress. */
    public int getDownloadCount() {
        return downloadCount.get();
    }

    /** HUD sprite paths (hearts, armor, food) for prefetch. 1.21: heart/ subfolder; armor/food flat in hud/. */
    private static final String[] HUD_SPRITE_PATHS = {
            "assets/minecraft/textures/gui/sprites/hud/heart/full.png",
            "assets/minecraft/textures/gui/sprites/hud/heart/half.png",
            "assets/minecraft/textures/gui/sprites/hud/heart/container.png",
            "assets/minecraft/textures/gui/sprites/hud/armor_full.png",
            "assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
            "assets/minecraft/textures/gui/sprites/hud/food_full.png",
            "assets/minecraft/textures/gui/sprites/hud/food_empty.png"
    };

    /** Common items always prefetched so hotbar/inventory sprites are ready. */
    private static final String[] COMMON_ITEM_PREFETCH = {
            "arrow", "bow", "crossbow", "spectral_arrow", "tipped_arrow"
    };

    /**
     * Prefetches HUD sprites (hearts, hunger/food bar, armor) and common items from mcasset.
     * Call at startup so HudResources can load from cache.
     */
    public void prefetchHudSprites() {
        for (String path : HUD_SPRITE_PATHS) {
            fetch(path);
        }
        for (String item : COMMON_ITEM_PREFETCH) {
            fetchItemOrBlock(item);
        }
    }

    /**
     * Prefetches entities, block textures, and items in parallel before render.
     * Dramatically faster than sequential fetches during render.
     */
    public void prefetchInParallel(
            Collection<org.bukkit.entity.EntityType> entityTypes,
            Collection<String> blockTextureNames,
            Collection<String> itemMaterialNames) {
        Set<org.bukkit.entity.EntityType> entities = entityTypes != null ? new HashSet<>(entityTypes) : Set.of();
        Set<String> blocks = blockTextureNames != null ? new HashSet<>(blockTextureNames) : Set.of();
        Set<String> items = new HashSet<>();
        if (itemMaterialNames != null) {
            for (String m : itemMaterialNames) {
                if (m != null && !m.isEmpty()) items.add(m.toLowerCase());
            }
        }
        entities.remove(org.bukkit.entity.EntityType.PLAYER);
        if (entities.isEmpty() && blocks.isEmpty() && items.isEmpty()) return;

        int total = entities.size() + blocks.size() + items.size();
        plugin.getSLF4JLogger().info("McAsset prefetch starting: {} entities, {} blocks, {} items ({} total)",
                entities.size(), blocks.size(), items.size(), total);

        List<Future<?>> futures = new ArrayList<>();
        for (org.bukkit.entity.EntityType t : entities) {
            futures.add(prefetchPool.submit(() -> fetchEntity(t)));
        }
        for (String name : blocks) {
            if (name == null || name.isEmpty()) continue;
            String n = name;
            futures.add(prefetchPool.submit(() -> fetchBlock(n)));
        }
        for (String name : items) {
            if (name == null || name.isEmpty()) continue;
            String n = name.toLowerCase();
            futures.add(prefetchPool.submit(() -> fetchItemOrBlock(n)));
        }
        int completed = 0;
        int timedOut = 0;
        try {
            for (Future<?> f : futures) {
                try {
                    f.get(PREFETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                    completed++;
                } catch (TimeoutException e) {
                    f.cancel(true);
                    timedOut++;
                    plugin.getSLF4JLogger().warn("Prefetch task timed out after {}s ({} completed, {} timed out so far)",
                            PREFETCH_TIMEOUT_SEC, completed, timedOut + 1);
                }
            }
        } catch (Exception e) {
            plugin.getSLF4JLogger().warn("Prefetch failed: {}", e.getMessage());
        }
        plugin.getSLF4JLogger().info("McAsset prefetch complete: {} succeeded, {} timed out", completed, timedOut);
    }

    /** Shuts down the prefetch executor; call from plugin onDisable. */
    public void shutdown() {
        prefetchPool.shutdownNow();
    }
}

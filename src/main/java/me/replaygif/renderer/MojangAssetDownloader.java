package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Downloads Minecraft item and block textures from Mojang's assets on first load when
 * no client JAR or resource pack path is configured. Caches to plugin data folder.
 * Runs async so it does not block server startup.
 */
public final class MojangAssetDownloader {

    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ASSET_BASE = "https://resources.download.minecraft.net/";
    private static final String USER_AGENT = "ReplayGif/1.0 (Minecraft server plugin)";
    private static final List<String> ITEM_PREFIXES = List.of(
            "minecraft/textures/item/",
            "minecraft/textures/block/"
    );

    private final JavaPlugin plugin;
    private final Path cacheDir;
    private final String assetsVersion;
    private final HttpClient httpClient;
    private volatile boolean completed;
    private volatile boolean failed;

    public MojangAssetDownloader(JavaPlugin plugin, String assetsVersion) {
        this.plugin = plugin;
        this.assetsVersion = assetsVersion != null && !assetsVersion.isBlank() ? assetsVersion.trim() : "1.21";
        this.cacheDir = plugin.getDataFolder().toPath().resolve("texture_cache");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Starts async download of item/block textures to cache. Safe to call multiple times;
     * skips if already completed or cache has content. Does not block.
     */
    public void ensureTexturesAsync(Executor executor) {
        if (completed || failed) {
            return;
        }
        executor.execute(() -> {
            try {
                doDownload();
            } catch (Exception e) {
                failed = true;
                plugin.getSLF4JLogger().warn("Mojang asset download failed (using bundled textures): {}", e.getMessage());
            }
        });
    }

    private void doDownload() throws IOException, InterruptedException {
        Path itemsDir = cacheDir.resolve("items");
        if (Files.exists(itemsDir) && hasCachedFiles(itemsDir)) {
            plugin.getSLF4JLogger().info("Texture cache already populated, skipping Mojang download.");
            completed = true;
            return;
        }
        Files.createDirectories(itemsDir);

        String versionUrl = fetchVersionUrl(assetsVersion);
        if (versionUrl == null) {
            throw new IOException("Version " + assetsVersion + " not found in manifest");
        }
        String assetIndexUrl = fetchAssetIndexUrl(versionUrl);
        if (assetIndexUrl == null) {
            throw new IOException("Asset index URL not found for version " + assetsVersion);
        }
        JsonObject objects = fetchAssetIndex(assetIndexUrl);
        if (objects == null) {
            throw new IOException("Asset index has no objects");
        }

        int count = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> e : objects.entrySet()) {
            String path = e.getKey();
            if (!path.endsWith(".png")) continue;
            boolean match = ITEM_PREFIXES.stream().anyMatch(path::startsWith);
            if (!match) continue;
            JsonObject obj = e.getValue().getAsJsonObject();
            String hash = obj.has("hash") ? obj.get("hash").getAsString() : null;
            if (hash == null || hash.length() < 4) continue;
            String shortPath = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
            String fileName = shortPath.toUpperCase(Locale.ROOT).replace("-", "_") + ".png";
            Path target = itemsDir.resolve(fileName);
            if (Files.exists(target)) continue;
            String url = ASSET_BASE + hash.substring(0, 2) + "/" + hash;
            try {
                if (downloadToFile(url, target)) {
                    count++;
                }
            } catch (Exception ex) {
                plugin.getSLF4JLogger().debug("Failed to download {}: {}", path, ex.getMessage());
            }
        }
        completed = true;
        plugin.getSLF4JLogger().info("Mojang asset download complete: {} item textures cached.", count);
    }

    private boolean hasCachedFiles(Path dir) throws IOException {
        try (var s = Files.list(dir)) {
            return s.anyMatch(p -> p.toString().endsWith(".png"));
        }
    }

    private String fetchVersionUrl(String versionId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(VERSION_MANIFEST))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Manifest returned " + resp.statusCode());
        }
        JsonObject manifest = new Gson().fromJson(resp.body(), JsonObject.class);
        if (manifest == null || !manifest.has("versions")) {
            throw new IOException("Invalid manifest");
        }
        var versions = manifest.getAsJsonArray("versions");
        for (var v : versions) {
            JsonObject o = v.getAsJsonObject();
            if (versionId.equals(o.has("id") ? o.get("id").getAsString() : null)) {
                return o.has("url") ? o.get("url").getAsString() : null;
            }
        }
        return null;
    }

    private String fetchAssetIndexUrl(String versionUrl) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(versionUrl))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Version JSON returned " + resp.statusCode());
        }
        JsonObject versionJson = new Gson().fromJson(resp.body(), JsonObject.class);
        if (versionJson == null || !versionJson.has("assetIndex")) {
            return null;
        }
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
        return assetIndex.has("url") ? assetIndex.get("url").getAsString() : null;
    }

    private JsonObject fetchAssetIndex(String assetIndexUrl) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(assetIndexUrl))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Asset index returned " + resp.statusCode());
        }
        JsonObject idx = new Gson().fromJson(resp.body(), JsonObject.class);
        return idx != null && idx.has("objects") ? idx.getAsJsonObject("objects") : null;
    }

    private boolean downloadToFile(String url, Path target) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            return false;
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, target);
        }
        return true;
    }

    /** Path to the texture cache directory (items subfolder). */
    public Path getCacheDir() {
        return cacheDir;
    }

    /** True when download has completed (success or skip). */
    public boolean isCompleted() {
        return completed;
    }

    /** True when download failed. */
    public boolean hasFailed() {
        return failed;
    }
}

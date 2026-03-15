package com.replayplugin.sidecar.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Manages the asset cache: extraction from Minecraft client jar, local cache, and mcasset.cloud fallback.
 */
public final class AssetManager {

    private static final Logger LOG = Logger.getLogger(AssetManager.class.getName());
    private static final String ASSETS_PREFIX = "assets/minecraft/";
    private static final String TEXTURES_BLOCK = ASSETS_PREFIX + "textures/block/";
    private static final String BLOCKSTATES = ASSETS_PREFIX + "blockstates/";
    private static final String MODELS_BLOCK = ASSETS_PREFIX + "models/block/";
    private static final String COLORMAP = ASSETS_PREFIX + "textures/colormap/";
    private static final String MCASSET_BASE = "https://assets.mcasset.cloud/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path jarPathOverride;
    private Path dataDir;
    private String mcVersion;
    private boolean fallbackMode;
    private final ConcurrentHashMap<String, BufferedImage> textureMemoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> blockstateMemoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> modelMemoryCache = new ConcurrentHashMap<>();

    /**
     * @param jarPathOverride optional path to mojang_&lt;version&gt;.jar; if null, jar is discovered relative to dataDir parent in initialize()
     */
    public AssetManager(Path jarPathOverride) {
        this.jarPathOverride = jarPathOverride;
    }

    /**
     * Default constructor: jar path will be discovered in initialize().
     */
    public AssetManager() {
        this(null);
    }

    /**
     * Initializes the asset cache. If cache-version.txt is missing or does not match mcVersion, runs full extraction.
     * If the Minecraft jar is not found, logs a warning and sets fallback mode (per-asset fetch from mcasset.cloud).
     */
    public void initialize(Path dataDir, String mcVersion) throws IOException {
        this.dataDir = dataDir;
        this.mcVersion = mcVersion;
        this.fallbackMode = false;

        Path cacheVersionFile = dataDir.resolve("cache-version.txt");
        String existingVersion = null;
        if (Files.exists(cacheVersionFile)) {
            existingVersion = Files.readString(cacheVersionFile, StandardCharsets.UTF_8).trim();
        }

        if (existingVersion != null && existingVersion.equals(mcVersion)) {
            return;
        }

        Path jarPath = jarPathOverride != null
                ? jarPathOverride
                : dataDir.getParent().getParent().getParent().resolve("cache").resolve("mojang_" + mcVersion + ".jar");

        if (!Files.exists(jarPath)) {
            LOG.warning("Local Minecraft client jar not found at " + jarPath + "; falling back to mcasset.cloud per-asset fetching.");
            this.fallbackMode = true;
            if (existingVersion == null) {
                Files.createDirectories(dataDir);
                Files.writeString(cacheVersionFile, mcVersion, StandardCharsets.UTF_8);
            }
            return;
        }

        extractFromJar(jarPath);
        Files.createDirectories(dataDir);
        Files.writeString(cacheVersionFile, mcVersion, StandardCharsets.UTF_8);
    }

    private void extractFromJar(Path jarPath) throws IOException {
        Set<String> prefixes = new HashSet<>(Arrays.asList(TEXTURES_BLOCK, BLOCKSTATES, MODELS_BLOCK, COLORMAP));
        byte[] buffer = new byte[8192];
        Path normalizedJar = jarPath.toAbsolutePath().normalize();

        try (ZipFile zip = new ZipFile(normalizedJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String matched = null;
                for (String prefix : prefixes) {
                    if (name.startsWith(prefix)) {
                        matched = prefix;
                        break;
                    }
                }
                if (matched == null) continue;

                Path outFile = dataDir.resolve(name);
                Files.createDirectories(outFile.getParent());
                try (InputStream in = zip.getInputStream(entry);
                     OutputStreamToPath out = new OutputStreamToPath(outFile)) {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    /**
     * Load PNG from asset cache. In fallback mode, fetches from mcasset.cloud and caches. If unreachable, throws AssetNotFoundException.
     * Animated textures (when .mcmeta is present): returns only the first frame cropped from the texture strip.
     */
    public BufferedImage getTexture(String namespacedId) throws AssetNotFoundException {
        String pathKey = texturePathFromNamespacedId(namespacedId);
        String cachePath = ASSETS_PREFIX + "textures/block/" + pathKey + ".png";
        Path local = dataDir.resolve(cachePath);

        if (textureMemoryCache.containsKey(pathKey)) {
            return textureMemoryCache.get(pathKey);
        }

        BufferedImage img = loadTextureFromFile(local, pathKey, namespacedId);
        if (img != null) {
            img = firstFrameIfAnimated(local, img);
            textureMemoryCache.put(pathKey, img);
            return img;
        }

        if (fallbackMode) {
            img = fetchTextureFromMcAsset(pathKey);
            if (img != null) {
                try {
                    Files.createDirectories(local.getParent());
                    ImageIO.write(img, "png", local.toFile());
                } catch (IOException e) {
                    LOG.fine("Could not cache fetched texture to " + local + ": " + e.getMessage());
                }
                textureMemoryCache.put(pathKey, img);
                return img;
            }
            throw new AssetNotFoundException(namespacedId);
        }

        throw new AssetNotFoundException(namespacedId);
    }

    private String texturePathFromNamespacedId(String namespacedId) {
        if (namespacedId.contains(":")) {
            return namespacedId.substring(namespacedId.indexOf(':') + 1).replace("block/", "");
        }
        return namespacedId.replace("block/", "");
    }

    private BufferedImage loadTextureFromFile(Path local, String pathKey, String namespacedId) {
        if (!Files.exists(local)) return null;
        try {
            return ImageIO.read(local.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private BufferedImage firstFrameIfAnimated(Path pngPath, BufferedImage full) {
        Path mcmeta = pngPath.getParent().resolve(pngPath.getFileName().toString().replace(".png", ".png.mcmeta"));
        if (!Files.exists(mcmeta)) return full;
        try {
            String content = Files.readString(mcmeta, StandardCharsets.UTF_8);
            int frameHeight = parseFirstFrameHeightFromMcMeta(content);
            if (frameHeight <= 0 || frameHeight >= full.getHeight()) return full;
            return full.getSubimage(0, 0, full.getWidth(), frameHeight);
        } catch (Exception e) {
            return full;
        }
    }

    private static int parseFirstFrameHeightFromMcMeta(String content) {
        if (content == null) return -1;
        int idx = content.indexOf("\"height\":");
        if (idx == -1) return -1;
        int start = content.indexOf(':', idx) + 1;
        int end = start;
        while (end < content.length() && (Character.isDigit(content.charAt(end)) || content.charAt(end) == ' ')) end++;
        try {
            return Integer.parseInt(content.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private BufferedImage fetchTextureFromMcAsset(String pathKey) {
        String url = MCASSET_BASE + mcVersion + "/" + ASSETS_PREFIX + "textures/block/" + pathKey + ".png";
        try (InputStream in = URI.create(url).toURL().openStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            LOG.fine("Failed to fetch texture from mcasset.cloud: " + url + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Load blockstate JSON. In fallback mode, fetches from mcasset.cloud. Throws AssetNotFoundException if unreachable.
     */
    public JsonNode getBlockstateJson(String blockId) throws AssetNotFoundException {
        String key = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        if (blockstateMemoryCache.containsKey(key)) {
            return blockstateMemoryCache.get(key);
        }
        String relPath = "blockstates/" + key + ".json";
        Path local = dataDir.resolve(ASSETS_PREFIX).resolve(relPath);
        JsonNode node = loadJsonFromFile(local);
        if (node != null) {
            blockstateMemoryCache.put(key, node);
            return node;
        }
        if (fallbackMode) {
            node = fetchJsonFromMcAsset(relPath);
            if (node != null) {
                try {
                    Files.createDirectories(local.getParent());
                    Files.writeString(local, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOG.fine("Could not cache blockstate to " + local + ": " + e.getMessage());
                }
                blockstateMemoryCache.put(key, node);
                return node;
            }
            throw new AssetNotFoundException(blockId);
        }
        throw new AssetNotFoundException(blockId);
    }

    /**
     * Load model JSON. modelPath is e.g. "block/stone" or "minecraft:block/stone". Fallback same as blockstate.
     */
    public JsonNode getModelJson(String modelPath) throws AssetNotFoundException {
        String path = modelPath.contains(":") ? modelPath.substring(modelPath.indexOf(':') + 1) : modelPath;
        if (!path.startsWith("block/")) path = "block/" + path;
        if (modelMemoryCache.containsKey(path)) {
            return modelMemoryCache.get(path);
        }
        String relPath = "models/" + path + ".json";
        Path local = dataDir.resolve(ASSETS_PREFIX).resolve(relPath);
        JsonNode node = loadJsonFromFile(local);
        if (node != null) {
            modelMemoryCache.put(path, node);
            return node;
        }
        if (fallbackMode) {
            node = fetchJsonFromMcAsset(relPath);
            if (node != null) {
                try {
                    Files.createDirectories(local.getParent());
                    Files.writeString(local, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOG.fine("Could not cache model to " + local + ": " + e.getMessage());
                }
                modelMemoryCache.put(path, node);
                return node;
            }
            throw new AssetNotFoundException(modelPath);
        }
        throw new AssetNotFoundException(modelPath);
    }

    /**
     * Load colormap image (e.g. "grass", "foliage"). From cache; in fallback mode fetches from mcasset.cloud.
     */
    public BufferedImage getColormap(String name) throws AssetNotFoundException {
        String relPath = "textures/colormap/" + name + ".png";
        Path local = dataDir.resolve(ASSETS_PREFIX).resolve(relPath);
        if (Files.exists(local)) {
            try {
                return ImageIO.read(local.toFile());
            } catch (IOException e) {
                // fall through to fallback
            }
        }
        if (fallbackMode) {
            String url = MCASSET_BASE + mcVersion + "/" + ASSETS_PREFIX + relPath;
            try (InputStream in = URI.create(url).toURL().openStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    Files.createDirectories(local.getParent());
                    ImageIO.write(img, "png", local.toFile());
                    return img;
                }
            } catch (IOException e) {
                LOG.fine("Failed to fetch colormap from mcasset.cloud: " + url + " - " + e.getMessage());
            }
            throw new AssetNotFoundException("colormap/" + name);
        }
        throw new AssetNotFoundException("colormap/" + name);
    }

    public boolean isFallbackMode() {
        return fallbackMode;
    }

    private JsonNode loadJsonFromFile(Path path) {
        if (!Files.exists(path)) return null;
        try {
            return JSON.readTree(Files.newInputStream(path));
        } catch (IOException e) {
            return null;
        }
    }

    private JsonNode fetchJsonFromMcAsset(String relPath) {
        String url = MCASSET_BASE + mcVersion + "/" + ASSETS_PREFIX + relPath;
        try (InputStream in = URI.create(url).toURL().openStream()) {
            return JSON.readTree(in);
        } catch (IOException e) {
            LOG.fine("Failed to fetch JSON from mcasset.cloud: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private static final class OutputStreamToPath extends java.io.OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final Path path;

        OutputStreamToPath(Path path) {
            this.path = path;
        }

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buf.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            Files.write(path, buf.toByteArray());
        }
    }
}

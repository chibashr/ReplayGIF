package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.replaygif.core.BlockRegistry;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads and caches block face textures lazily on first use (during render). Load order:
 * resource pack → mcasset.cloud → bundled block_textures/*.png. Texture names come from
 * block_texture_mapping.json; missing entries default to material.name().toLowerCase().
 * Lazy loading defers fetches until blocks are actually drawn, reducing startup time.
 */
public class BlockTextureRegistry {

    private static final BlockFaceTextures MISSING = new BlockFaceTextures(null, null);

    private static final String MAPPING_RESOURCE = "block_texture_mapping.json";
    private static final String TEXTURES_PREFIX = "block_textures/";
    private static final String RP_BLOCK_PATH = "assets/minecraft/textures/block/";

    private final JavaPlugin plugin;
    private final BlockRegistry blockRegistry;
    private final String resourcePackPath;
    private final McAssetFetcher mcAssetFetcher;
    private ZipFile resourcePackZip;
    /** Lazy-built index for zip lookups when direct getEntry fails; avoids full scan per texture. */
    private Map<String, ZipEntry> zipEntryIndex;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    /** Ordinal -> (topName, sideName, bottomName); side used for both left and right. */
    private final String[][] textureNamesByOrdinal;
    /** Ordinal -> cached BlockFaceTextures or null if not yet loaded or any texture missing. */
    private final BlockFaceTextures[] facesByOrdinal;

    public BlockTextureRegistry(JavaPlugin plugin, BlockRegistry blockRegistry) throws IOException {
        this(plugin, blockRegistry, null, null);
    }

    public BlockTextureRegistry(JavaPlugin plugin, BlockRegistry blockRegistry, String resourcePackPath) throws IOException {
        this(plugin, blockRegistry, resourcePackPath, null);
    }

    public BlockTextureRegistry(JavaPlugin plugin, BlockRegistry blockRegistry, String resourcePackPath,
                                McAssetFetcher mcAssetFetcher) throws IOException {
        this.plugin = plugin;
        this.blockRegistry = blockRegistry;
        this.resourcePackPath = (resourcePackPath != null && !resourcePackPath.isBlank()) ? resourcePackPath.trim() : null;
        this.mcAssetFetcher = mcAssetFetcher;
        Map<String, FaceNames> mapping = loadMapping(plugin.getResource(MAPPING_RESOURCE));
        int n = blockRegistry.getOrdinalCount();
        this.textureNamesByOrdinal = new String[n][3];
        this.facesByOrdinal = new BlockFaceTextures[n];

        int configured = 0;
        for (int i = 0; i < n; i++) {
            Material m = blockRegistry.getMaterial((short) i);
            if (m == null || m == Material.AIR) {
                continue;
            }
            String key = m.name();
            FaceNames names = mapping != null && mapping.containsKey(key)
                    ? mapping.get(key)
                    : defaultNames(key);
            textureNamesByOrdinal[i] = new String[]{ names.top, names.side, names.bottom };
            configured++;
        }

        String source = resourcePackPath != null ? "resource pack" : (mcAssetFetcher != null ? "mcasset" : "bundled");
        plugin.getSLF4JLogger().info("BlockTextureRegistry: lazy loading for {} block types ({})", configured, source);
    }

    private static Map<String, FaceNames> loadMapping(InputStream in) throws IOException {
        if (in == null) return null;
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Map<String, Map<String, String>> raw = new Gson().fromJson(r,
                    new TypeToken<Map<String, Map<String, String>>>() {}.getType());
            if (raw == null) return null;
            Map<String, FaceNames> out = new HashMap<>();
            for (Map.Entry<String, Map<String, String>> e : raw.entrySet()) {
                Map<String, String> v = e.getValue();
                if (v == null) continue;
                out.put(e.getKey(), new FaceNames(
                        v.getOrDefault("top", e.getKey().toLowerCase()),
                        v.getOrDefault("side", e.getKey().toLowerCase()),
                        v.getOrDefault("bottom", e.getKey().toLowerCase())));
            }
            return out;
        }
    }

    private static FaceNames defaultNames(String materialName) {
        String lower = materialName.toLowerCase();
        return new FaceNames(lower, lower, lower);
    }

    private BufferedImage loadImage(JavaPlugin plugin, String textureName) {
        return loadImage(plugin, textureName, true);
    }

    private BufferedImage loadImage(JavaPlugin plugin, String textureName, boolean allowNetworkFetch) {
        if (textureName == null || textureName.isEmpty()) return null;
        if (imageCache.containsKey(textureName)) {
            return imageCache.get(textureName);
        }
        BufferedImage img = loadFromResourcePack(textureName);
        if (img == null && mcAssetFetcher != null) {
            if (allowNetworkFetch) {
                img = mcAssetFetcher.fetchBlock(textureName).orElse(null);
            } else {
                img = mcAssetFetcher.fetchCachedOnly("assets/minecraft/textures/block/" + textureName + ".png").orElse(null);
            }
        }
        if (img == null) {
            img = loadFromBundled(plugin, textureName);
        }
        if (img != null) {
            img = ensureArgbColor(img, textureName);
            img = stripMagentaPixels(img);
            imageCache.put(textureName, img);
        }
        return img;
    }

    private BufferedImage loadFromResourcePack(String textureName) {
        if (resourcePackPath == null) return null;
        File f = new File(resourcePackPath);
        if (!f.exists()) return null;
        String pathSuffix = RP_BLOCK_PATH + textureName + ".png";
        try {
            if (f.isDirectory()) {
                Path p = f.toPath().resolve(pathSuffix);
                if (Files.isRegularFile(p)) {
                    return ImageLoadUtil.loadPngAsArgb(p);
                }
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                ZipFile zip = getResourcePackZip();
                if (zip != null) {
                    ZipEntry entry = findZipEntry(zip, pathSuffix);
                    if (entry != null) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            return ImageLoadUtil.loadPngAsArgb(is);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ZipFile getResourcePackZip() {
        if (resourcePackPath == null) return null;
        ZipFile z = resourcePackZip;
        if (z == null) {
            try {
                File f = new File(resourcePackPath);
                if (f.exists() && f.getName().toLowerCase().endsWith(".zip")) {
                    resourcePackZip = new ZipFile(resourcePackPath);
                    z = resourcePackZip;
                }
            } catch (Exception ignored) {
            }
        }
        return z;
    }

    private ZipEntry findZipEntry(ZipFile zip, String pathSuffix) {
        String suffix = pathSuffix.toLowerCase().replace('\\', '/');
        ZipEntry direct = zip.getEntry(pathSuffix);
        if (direct != null && !direct.isDirectory()) return direct;
        direct = zip.getEntry(suffix);
        if (direct != null && !direct.isDirectory()) return direct;
        Map<String, ZipEntry> index = getZipEntryIndex(zip);
        ZipEntry e = index.get(suffix);
        if (e != null) return e;
        for (Map.Entry<String, ZipEntry> ent : index.entrySet()) {
            if (ent.getKey().endsWith("/" + suffix)) return ent.getValue();
        }
        return null;
    }

    private Map<String, ZipEntry> getZipEntryIndex(ZipFile zip) {
        Map<String, ZipEntry> idx = zipEntryIndex;
        if (idx == null) {
            idx = new HashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName().toLowerCase().replace('\\', '/');
                idx.putIfAbsent(name, e);
            }
            zipEntryIndex = idx;
        }
        return idx;
    }

    /**
     * Converts loaded images to TYPE_INT_ARGB so color is preserved. ImageIO can return grayscale or
     * indexed images (e.g. on some Java versions) that draw incorrectly. This forces a proper sRGB
     * representation for consistent rendering.
     */
    private BufferedImage ensureArgbColor(BufferedImage src, String textureName) {
        if (src == null) return null;
        int type = src.getType();
        if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        if (type == BufferedImage.TYPE_BYTE_GRAY) {
            plugin.getSLF4JLogger().warn("Block texture {} loaded as grayscale; check source or cache", textureName);
        }
        return out;
    }

    /**
     * Replaces Minecraft "missing texture" magenta pixels with dark gray (opaque).
     * Using opaque instead of transparent avoids chroma-key pink bleed in the GIF output.
     */
    private static BufferedImage stripMagentaPixels(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int darkGray = 0xFF1a1a1a;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a > 0 && r >= 250 && g <= 10 && b >= 250) {
                    out.setRGB(x, y, darkGray);
                } else {
                    out.setRGB(x, y, argb);
                }
            }
        }
        return out;
    }

    private BufferedImage loadFromBundled(JavaPlugin plugin, String textureName) {
        String path = TEXTURES_PREFIX + textureName + ".png";
        try (InputStream is = plugin.getResource(path)) {
            if (is == null) return null;
            return ImageLoadUtil.loadPngAsArgb(is);
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Returns textured faces for this block ordinal if all required textures were loaded;
     * otherwise empty so the renderer uses BlockColorMap. Loads textures lazily on first access.
     * Uses mcasset as fallback when resource pack and bundled textures are missing.
     */
    public Optional<BlockFaceTextures> getFaces(short ordinal) {
        if (ordinal < 0 || ordinal >= facesByOrdinal.length) {
            return Optional.empty();
        }
        BlockFaceTextures f = facesByOrdinal[ordinal];
        if (f == null) {
            f = loadFacesForOrdinal(ordinal, true);
        }
        return (f == null || f == MISSING) ? Optional.empty() : Optional.of(f);
    }

    private synchronized BlockFaceTextures loadFacesForOrdinal(int ordinal, boolean allowNetworkFetch) {
        if (facesByOrdinal[ordinal] != null) return facesByOrdinal[ordinal];
        String[] names = textureNamesByOrdinal[ordinal];
        if (names == null) {
            facesByOrdinal[ordinal] = MISSING;
            return MISSING;
        }
        BufferedImage top = loadImage(plugin, names[0], allowNetworkFetch);
        BufferedImage side = loadImage(plugin, names[1], allowNetworkFetch);
        BufferedImage bottom = loadImage(plugin, names[2], allowNetworkFetch);
        if (top != null && side != null && bottom != null) {
            BlockFaceTextures result = new BlockFaceTextures(top, side);
            facesByOrdinal[ordinal] = result;
            return result;
        }
        facesByOrdinal[ordinal] = MISSING;
        return MISSING;
    }

    /**
     * Preloads BlockFaceTextures for all block ordinals that appear in the given frames.
     * Call after McAssetFetcher.prefetchInParallel so textures are on disk; this populates
     * facesByOrdinal so getFaces() returns immediately during render (no lazy I/O).
     */
    public void prefetchFacesForFrames(List<WorldSnapshot> frames) {
        if (frames == null || frames.isEmpty()) return;
        Set<Short> ordinals = new HashSet<>();
        for (WorldSnapshot snap : frames) {
            if (snap.blocks != null) {
                for (short ord : snap.blocks) {
                    if (ord > 0 && ord < textureNamesByOrdinal.length && textureNamesByOrdinal[ord] != null) {
                        ordinals.add(ord);
                    }
                }
            }
        }
        for (Short ord : ordinals) {
            loadFacesForOrdinal(ord, true);
        }
    }

    /**
     * Collects texture names (top, side, bottom) for all block ordinals in the given array.
     * Used for prefetching block textures before render.
     */
    public void collectTextureNamesForBlocks(short[] blocks, Set<String> out) {
        if (blocks == null || out == null) return;
        Set<Short> seen = new HashSet<>();
        for (short ord : blocks) {
            if (ord <= 0 || ord >= textureNamesByOrdinal.length) continue;
            if (!seen.add(ord)) continue;
            String[] names = textureNamesByOrdinal[ord];
            if (names != null) {
                for (String n : names) {
                    if (n != null && !n.isEmpty()) out.add(n);
                }
            }
        }
    }

    private static final class FaceNames {
        final String top, side, bottom;
        FaceNames(String top, String side, String bottom) {
            this.top = top;
            this.side = side;
            this.bottom = bottom;
        }
    }
}

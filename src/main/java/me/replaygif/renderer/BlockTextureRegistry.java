package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.replaygif.core.BlockRegistry;
import org.bukkit.Material;
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

/**
 * Loads and caches block face textures from bundled resources (block_textures/*.png).
 * Texture names are resolved via block_texture_mapping.json; missing entries default to
 * material.name().toLowerCase() for all three faces. When textures are present, the
 * isometric renderer uses them for realistic block appearance; otherwise it falls back
 * to BlockColorMap solid colors.
 */
public class BlockTextureRegistry {

    private static final String MAPPING_RESOURCE = "block_texture_mapping.json";
    private static final String TEXTURES_PREFIX = "block_textures/";

    private final BlockRegistry blockRegistry;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    /** Ordinal -> (topName, sideName, bottomName); side used for both left and right. */
    private final String[][] textureNamesByOrdinal;
    /** Ordinal -> cached BlockFaceTextures or null if any texture missing. */
    private final BlockFaceTextures[] facesByOrdinal;

    public BlockTextureRegistry(JavaPlugin plugin, BlockRegistry blockRegistry) throws IOException {
        this.blockRegistry = blockRegistry;
        Map<String, FaceNames> mapping = loadMapping(plugin.getResource(MAPPING_RESOURCE));
        int n = blockRegistry.getOrdinalCount();
        this.textureNamesByOrdinal = new String[n][3];
        this.facesByOrdinal = new BlockFaceTextures[n];

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
        }

        // Preload and cache faces for all ordinals that have textures available
        for (int i = 0; i < n; i++) {
            String[] names = textureNamesByOrdinal[i];
            if (names == null) continue;
            BufferedImage top = loadImage(plugin, names[0]);
            BufferedImage side = loadImage(plugin, names[1]);
            BufferedImage bottom = loadImage(plugin, names[2]);
            if (top != null && side != null && bottom != null) {
                facesByOrdinal[i] = new BlockFaceTextures(top, side);
            }
        }

        int count = 0;
        for (BlockFaceTextures f : facesByOrdinal) {
            if (f != null) count++;
        }
        plugin.getSLF4JLogger().info("BlockTextureRegistry: {} block types using bundled textures.", count);
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
        if (textureName == null || textureName.isEmpty()) return null;
        if (imageCache.containsKey(textureName)) {
            return imageCache.get(textureName);
        }
        String path = TEXTURES_PREFIX + textureName + ".png";
        try (InputStream is = plugin.getResource(path)) {
            if (is == null) return null;
            BufferedImage img = ImageIO.read(is);
            if (img != null) {
                imageCache.put(textureName, img);
                return img;
            }
        } catch (IOException ignored) {
            // skip
        }
        return null;
    }

    /**
     * Returns textured faces for this block ordinal if all required textures were loaded;
     * otherwise empty so the renderer uses BlockColorMap.
     */
    public Optional<BlockFaceTextures> getFaces(short ordinal) {
        if (ordinal < 0 || ordinal >= facesByOrdinal.length) {
            return Optional.empty();
        }
        BlockFaceTextures f = facesByOrdinal[ordinal];
        return f != null ? Optional.of(f) : Optional.empty();
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

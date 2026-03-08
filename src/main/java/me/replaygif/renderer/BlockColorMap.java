package me.replaygif.renderer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.replaygif.core.BlockRegistry;
import org.bukkit.Material;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Material ordinal → {top, left, right} Color. Loads block_colors_defaults.json
 * from plugin resources; on first run generates block_colors.json in the plugin
 * data folder by merging BlockRegistry materials with defaults (missing → #808080).
 * Loads the generated/existing block_colors.json for runtime. Unknown ordinals
 * return gray (#808080) faces, never null.
 */
public class BlockColorMap {

    private static final String DEFAULT_GRAY = "#808080";
    private static final Color GRAY = parseHex(DEFAULT_GRAY);
    private static final double LEFT_BRIGHTNESS = 0.75;
    private static final double RIGHT_BRIGHTNESS = 0.55;
    private static final double EMISSIVE_TOP_BRIGHTNESS = 1.20;

    private static final Set<String> EMISSIVE_MATERIALS = Set.of(
            "LAVA", "GLOWSTONE", "FIRE", "TORCH", "WALL_TORCH",
            "REDSTONE_TORCH", "REDSTONE_WALL_TORCH", "SOUL_TORCH", "SOUL_WALL_TORCH",
            "SEA_LANTERN", "SHROOMLIGHT", "MAGMA_BLOCK", "BEACON", "END_ROD",
            "GLOW_LICHEN", "SCULK_CATALYST", "AMETHYST_CLUSTER",
            "LARGE_AMETHYST_BUD", "MEDIUM_AMETHYST_BUD", "SMALL_AMETHYST_BUD"
    );

    private final int ordinalCount;
    private final Color[] baseColorByOrdinal;
    private final boolean[] emissiveByOrdinal;

    /**
     * @param dataFolder        plugin data folder (e.g. plugin.getDataFolder())
     * @param blockColorsFileName filename for generated file (e.g. "block_colors.json")
     * @param blockRegistry     registry to merge materials from
     * @param defaultsResource  stream of block_colors_defaults.json (e.g. plugin.getResource("block_colors_defaults.json"))
     */
    public BlockColorMap(File dataFolder, String blockColorsFileName,
                         BlockRegistry blockRegistry, InputStream defaultsResource) throws IOException {
        this.ordinalCount = blockRegistry.getOrdinalCount();
        Map<String, String> defaults = loadDefaults(defaultsResource);
        File blockColorsFile = new File(dataFolder, blockColorsFileName);
        if (!blockColorsFile.exists()) {
            dataFolder.mkdirs();
            generateBlockColorsFile(blockColorsFile, blockRegistry, defaults);
        }
        Map<String, String> runtime = loadJsonFile(blockColorsFile);
        this.baseColorByOrdinal = new Color[ordinalCount];
        this.emissiveByOrdinal = new boolean[ordinalCount];
        for (int i = 0; i < ordinalCount; i++) {
            Material m = blockRegistry.getMaterial((short) i);
            String hex = runtime.getOrDefault(m.name(), DEFAULT_GRAY);
            baseColorByOrdinal[i] = parseHex(hex);
            emissiveByOrdinal[i] = EMISSIVE_MATERIALS.contains(m.name());
        }
    }

    private static Map<String, String> loadDefaults(InputStream in) throws IOException {
        if (in == null) {
            return Map.of();
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Map<String, String> map = new Gson().fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
            return map != null ? map : Map.of();
        }
    }

    private static Map<String, String> loadJsonFile(File file) throws IOException {
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, String> map = new Gson().fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
            return map != null ? map : Map.of();
        }
    }

    private static void generateBlockColorsFile(File target, BlockRegistry blockRegistry,
                                                Map<String, String> defaults) throws IOException {
        Map<String, String> merged = new TreeMap<>();
        for (int i = 0; i < blockRegistry.getOrdinalCount(); i++) {
            Material m = blockRegistry.getMaterial((short) i);
            merged.put(m.name(), defaults.getOrDefault(m.name(), DEFAULT_GRAY));
        }
        String json = new Gson().newBuilder().setPrettyPrinting().create().toJson(merged);
        target.getParentFile().mkdirs();
        Files.writeString(target.toPath(), json, StandardCharsets.UTF_8);
    }

    private static Color parseHex(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return GRAY;
        }
        String s = hex.substring(1);
        if (s.length() != 6) {
            return GRAY;
        }
        try {
            int rgb = Integer.parseInt(s, 16);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException e) {
            return GRAY;
        }
    }

    private static Color applyBrightness(Color base, double factor) {
        factor = Math.min(factor, 1.0);
        int r = clamp((int) (base.getRed() * factor));
        int g = clamp((int) (base.getGreen() * factor));
        int b = clamp((int) (base.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static Color applyBrightnessEmissiveTop(Color base) {
        double factor = EMISSIVE_TOP_BRIGHTNESS;
        int r = clamp((int) (base.getRed() * factor));
        int g = clamp((int) (base.getGreen() * factor));
        int b = clamp((int) (base.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Returns true if the material is emissive (glow, 120% top face). */
    public boolean isEmissive(short ordinal) {
        if (ordinal < 0 || ordinal >= ordinalCount) {
            return false;
        }
        return emissiveByOrdinal[ordinal];
    }

    /**
     * Returns face colors for the given material ordinal. Unknown ordinals
     * return gray (#808080) for all three faces. Never returns null.
     * Shade: top 100%, left 75%, right 55%. Emissive: top 120% (clamped to 255).
     */
    public BlockFaceColors getFaces(short ordinal) {
        if (ordinal < 0 || ordinal >= ordinalCount) {
            return new BlockFaceColors(GRAY, GRAY, GRAY);
        }
        Color base = baseColorByOrdinal[ordinal];
        boolean emissive = emissiveByOrdinal[ordinal];
        Color top = emissive ? applyBrightnessEmissiveTop(base) : applyBrightness(base, 1.0);
        Color left = applyBrightness(base, LEFT_BRIGHTNESS);
        Color right = applyBrightness(base, RIGHT_BRIGHTNESS);
        return new BlockFaceColors(top, left, right);
    }
}

package com.replayplugin.sidecar.asset;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Resolves biome tint color from colormap using biome temperature and humidity.
 * Uses a hardcoded biome→climate map for biomes covered by the fixture JAR.
 */
public final class BiomeTint {

    public enum TintType {
        GRASS,
        FOLIAGE,
        WATER
    }

    private static final Map<String, Climate> BIOME_CLIMATE = Map.ofEntries(
            mapEntry("minecraft:plains", 0.8, 0.4),
            mapEntry("minecraft:sunflower_plains", 0.8, 0.4),
            mapEntry("minecraft:snowy_plains", 0.0, 0.5),
            mapEntry("minecraft:ice_spikes", 0.0, 0.5),
            mapEntry("minecraft:desert", 2.0, 0.0),
            mapEntry("minecraft:swamp", 0.8, 0.9),
            mapEntry("minecraft:forest", 0.7, 0.8),
            mapEntry("minecraft:flower_forest", 0.7, 0.8),
            mapEntry("minecraft:birch_forest", 0.6, 0.6),
            mapEntry("minecraft:dark_forest", 0.7, 0.8),
            mapEntry("minecraft:old_growth_birch_forest", 0.6, 0.6),
            mapEntry("minecraft:old_growth_pine_taiga", 0.3, 0.8),
            mapEntry("minecraft:old_growth_spruce_taiga", 0.25, 0.8),
            mapEntry("minecraft:taiga", 0.25, 0.8),
            mapEntry("minecraft:snowy_taiga", 0.0, 0.4),
            mapEntry("minecraft:jungle", 0.95, 0.9),
            mapEntry("minecraft:sparse_jungle", 0.95, 0.8),
            mapEntry("minecraft:bamboo_jungle", 0.95, 0.9),
            mapEntry("minecraft:badlands", 2.0, 0.0),
            mapEntry("minecraft:wooded_badlands", 2.0, 0.0),
            mapEntry("minecraft:meadow", 0.5, 0.8),
            mapEntry("minecraft:grove", 0.0, 0.8),
            mapEntry("minecraft:snowy_slopes", 0.0, 0.3),
            mapEntry("minecraft:frozen_peaks", 0.0, 0.0),
            mapEntry("minecraft:jagged_peaks", 0.0, 0.0),
            mapEntry("minecraft:stony_peaks", 1.0, 0.3),
            mapEntry("minecraft:river", 0.5, 0.5),
            mapEntry("minecraft:frozen_river", 0.0, 0.5),
            mapEntry("minecraft:beach", 0.8, 0.4),
            mapEntry("minecraft:snowy_beach", 0.05, 0.3),
            mapEntry("minecraft:stony_shore", 0.2, 0.3),
            mapEntry("minecraft:warm_ocean", 0.5, 0.5),
            mapEntry("minecraft:lukewarm_ocean", 0.5, 0.5),
            mapEntry("minecraft:deep_lukewarm_ocean", 0.5, 0.5),
            mapEntry("minecraft:ocean", 0.5, 0.5),
            mapEntry("minecraft:deep_ocean", 0.5, 0.5),
            mapEntry("minecraft:cold_ocean", 0.5, 0.5),
            mapEntry("minecraft:deep_cold_ocean", 0.5, 0.5),
            mapEntry("minecraft:frozen_ocean", 0.0, 0.5),
            mapEntry("minecraft:deep_frozen_ocean", 0.0, 0.5),
            mapEntry("minecraft:mangrove_swamp", 0.8, 0.9),
            mapEntry("minecraft:cherry_grove", 0.5, 0.8),
            mapEntry("minecraft:savanna", 1.2, 0.0),
            mapEntry("minecraft:savanna_plateau", 1.0, 0.0),
            mapEntry("minecraft:windswept_savanna", 1.1, 0.0),
            mapEntry("minecraft:windswept_forest", 0.2, 0.3),
            mapEntry("minecraft:windswept_gravelly_hills", 0.2, 0.3),
            mapEntry("minecraft:windswept_hills", 0.2, 0.3),
            mapEntry("minecraft:windswept_wooded_badlands", 2.0, 0.0)
    );

    private static Map.Entry<String, Climate> mapEntry(String id, double temp, double humidity) {
        return new java.util.AbstractMap.SimpleEntry<>(id, new Climate(temp, humidity));
    }

    private static final class Climate {
        final double temperature;
        final double humidity;

        Climate(double temperature, double humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
        }
    }

    private final AssetManager assetManager;

    public BiomeTint(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Returns the tint color for the given biome and tint type by sampling the colormap at (temperature, humidity).
     * Temperature and humidity are clamped to [0,1] for colormap lookup. Biomes not in the hardcoded map use (0.5, 0.5).
     */
    public Color getTint(String biomeId, TintType type) throws AssetNotFoundException {
        String colormapName = type == TintType.GRASS ? "grass" : type == TintType.FOLIAGE ? "foliage" : "water";
        BufferedImage colormap = assetManager.getColormap(colormapName);
        if (colormap == null) return new Color(255, 255, 255);

        Climate climate = BIOME_CLIMATE.get(biomeId != null && biomeId.contains(":") ? biomeId : "minecraft:" + biomeId);
        double temp = climate != null ? climate.temperature : 0.5;
        double humidity = climate != null ? climate.humidity : 0.5;
        temp = clamp((temp + 1) / 2, 0, 1);
        humidity = clamp(humidity, 0, 1);

        int w = colormap.getWidth();
        int h = colormap.getHeight();
        int x = (int) (humidity * (w - 1));
        int y = (int) ((1 - temp) * (h - 1));
        x = Math.max(0, Math.min(x, w - 1));
        y = Math.max(0, Math.min(y, h - 1));

        int rgb = colormap.getRGB(x, y);
        return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

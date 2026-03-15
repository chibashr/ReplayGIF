package com.replayplugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and holds config.yml (and config.test.yml) as typed POJOs.
 * Validates trigger event names against PlayerExtractionMap; invalid triggers are skipped with a log.
 */
public class PluginConfig {

    private static final int DEFAULT_SIDECAR_MAX_HEAP_MB = 512;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 10;
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_PRE_SECONDS = 5;
    private static final int DEFAULT_POST_SECONDS = 2;
    private static final int DEFAULT_RADIUS_CHUNKS = 5;
    private static final int DEFAULT_CAPTURE_RATE_TICKS = 2;
    private static final int DEFAULT_FPS = 10;
    private static final int DEFAULT_PIXELS_PER_BLOCK = 32;
    private static final int DEFAULT_COOLDOWN_PER_PLAYER = 0;
    private static final int DEFAULT_COOLDOWN_GLOBAL = 0;

    private final int sidecarMaxHeapMb;
    private final int maxQueueSize;
    private final Map<String, TriggerConfig> triggers;

    public PluginConfig(int sidecarMaxHeapMb, int maxQueueSize, Map<String, TriggerConfig> triggers) {
        this.sidecarMaxHeapMb = sidecarMaxHeapMb;
        this.maxQueueSize = maxQueueSize;
        this.triggers = triggers == null ? Collections.emptyMap() : Map.copyOf(triggers);
    }

    /**
     * Load config from the plugin's config file. Saves default config if missing.
     * Validates each trigger's event against PlayerExtractionMap; unknown events are logged and skipped.
     */
    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        return parse(plugin.getConfig(), plugin.getLogger());
    }

    /**
     * Load config from a specific file path (e.g. for tests). Validates triggers same as load().
     */
    public static PluginConfig loadFromFile(JavaPlugin plugin, Path configPath) {
        FileConfiguration fc = YamlConfiguration.loadConfiguration(configPath.toFile());
        return parse(fc, plugin.getLogger());
    }

    private static PluginConfig parse(FileConfiguration fc, Logger log) {
        int sidecarMaxHeapMb = fc.getInt("sidecar_max_heap_mb", DEFAULT_SIDECAR_MAX_HEAP_MB);
        int maxQueueSize = fc.getInt("max_queue_size", DEFAULT_MAX_QUEUE_SIZE);

        Map<String, TriggerConfig> triggers = new HashMap<>();
        ConfigurationSection triggersSection = fc.getConfigurationSection("triggers");
        if (triggersSection != null) {
            for (String key : triggersSection.getKeys(false)) {
                ConfigurationSection sec = triggersSection.getConfigurationSection(key);
                if (sec == null) continue;
                TriggerConfig tc = parseTriggerConfig(sec);
                if (!com.replayplugin.trigger.PlayerExtractionMap.isKnownEvent(tc.getEvent())) {
                    log.warning("ReplayPlugin: Unknown event class '" + tc.getEvent() + "' in trigger '" + key + "'. Skipping this trigger.");
                    continue;
                }
                triggers.put(key, tc);
            }
        }

        return new PluginConfig(sidecarMaxHeapMb, maxQueueSize, triggers);
    }

    private static TriggerConfig parseTriggerConfig(ConfigurationSection sec) {
        String event = sec.getString("event", "");
        boolean enabled = sec.getBoolean("enabled", DEFAULT_ENABLED);
        int preSeconds = sec.getInt("pre_seconds", DEFAULT_PRE_SECONDS);
        int postSeconds = sec.getInt("post_seconds", DEFAULT_POST_SECONDS);
        int radiusChunks = sec.getInt("radius_chunks", DEFAULT_RADIUS_CHUNKS);
        int captureRateTicks = sec.getInt("capture_rate_ticks", DEFAULT_CAPTURE_RATE_TICKS);
        int fps = sec.getInt("fps", DEFAULT_FPS);
        int pixelsPerBlock = sec.getInt("pixels_per_block", DEFAULT_PIXELS_PER_BLOCK);

        int cooldownPerPlayer = DEFAULT_COOLDOWN_PER_PLAYER;
        int cooldownGlobal = DEFAULT_COOLDOWN_GLOBAL;
        ConfigurationSection cooldown = sec.getConfigurationSection("cooldown");
        if (cooldown != null) {
            cooldownPerPlayer = cooldown.getInt("per_player_seconds", DEFAULT_COOLDOWN_PER_PLAYER);
            cooldownGlobal = cooldown.getInt("global_seconds", DEFAULT_COOLDOWN_GLOBAL);
        }

        List<DestinationConfig> destinations = parseDestinations(sec.getMapList("destinations"));

        return new TriggerConfig(
                event,
                enabled,
                preSeconds,
                postSeconds,
                radiusChunks,
                captureRateTicks,
                fps,
                pixelsPerBlock,
                cooldownPerPlayer,
                cooldownGlobal,
                destinations);
    }

    private static List<DestinationConfig> parseDestinations(List<?> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<DestinationConfig> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            String type = m.get("type") != null ? m.get("type").toString() : "";
            String url = m.get("url") != null ? m.get("url").toString() : null;
            out.add(new DestinationConfig(type, url));
        }
        return out;
    }

    public int getSidecarMaxHeapMb() {
        return sidecarMaxHeapMb;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public Map<String, TriggerConfig> getTriggers() {
        return triggers;
    }
}

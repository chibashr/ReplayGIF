package me.replaygif.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads and validates all config files (config.yml, renderer.yml, outputs.yml, triggers.yml).
 * Copies defaults from plugin jar on first run if missing. Never throws on bad values — applies
 * defaults and logs WARN. No cross-file validation (handled elsewhere).
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;

    private FileConfiguration config;
    private FileConfiguration rendererConfig;
    private FileConfiguration outputsConfig;
    private FileConfiguration triggersConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
    }

    /**
     * Copy default config files from the plugin jar to the data folder if they do not exist.
     */
    public void saveDefaultConfigs() {
        saveDefaultIfMissing("config.yml");
        saveDefaultIfMissing("renderer.yml");
        saveDefaultIfMissing("outputs.yml");
        saveDefaultIfMissing("triggers.yml");
    }

    private void saveDefaultIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    /**
     * Load (or reload) all config files from the plugin data folder.
     * Apply defaults and log WARN for missing or invalid values. Never throws.
     */
    public void load() {
        config = loadFile("config.yml");
        rendererConfig = loadFile("renderer.yml");
        outputsConfig = loadFile("outputs.yml");
        triggersConfig = loadFile("triggers.yml");

        if (getWebhookServerEnabled() && "changeme".equals(getWebhookServerSecret())) {
            logger.warn("webhook_server.enabled is true but webhook_server.secret is still 'changeme'. Change the secret in production.");
        }
        logger.info("Config loaded and validated.");
    }

    private FileConfiguration loadFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            logger.warn("Config file {} not found; using defaults for all values.", name);
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // --- Helpers: typed read with WARN on missing/invalid, never throw ---

    private int getInt(FileConfiguration c, String path, int defaultVal, int min, int max, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            logger.warn("{}: missing '{}', using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Number)) {
            logger.warn("{}: invalid '{}' (not a number), using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        int v = ((Number) o).intValue();
        if (min != max && (v < min || v > max)) {
            logger.warn("{}: '{}' out of range (got {}, valid {}-{}), using default {}", fileLabel, path, v, min, max, defaultVal);
            return defaultVal;
        }
        return v;
    }

    private int getInt(FileConfiguration c, String path, int defaultVal, String fileLabel) {
        return getInt(c, path, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE, fileLabel);
    }

    private double getDouble(FileConfiguration c, String path, double defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            logger.warn("{}: missing '{}', using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Number)) {
            logger.warn("{}: invalid '{}' (not a number), using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        return ((Number) o).doubleValue();
    }

    private String getString(FileConfiguration c, String path, String defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            logger.warn("{}: missing '{}', using default '{}'", fileLabel, path, defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (o == null) {
            logger.warn("{}: invalid '{}' (null), using default '{}'", fileLabel, path, defaultVal);
            return defaultVal;
        }
        return o.toString();
    }

    private boolean getBoolean(FileConfiguration c, String path, boolean defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            logger.warn("{}: missing '{}', using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Boolean)) {
            logger.warn("{}: invalid '{}' (not a boolean), using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        return (Boolean) o;
    }

    private List<String> getStringList(FileConfiguration c, String path, List<String> defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            logger.warn("{}: missing '{}', using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof List)) {
            logger.warn("{}: invalid '{}' (not a list), using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
        List<?> list = (List<?>) o;
        try {
            return list.stream()
                    .map(e -> e == null ? "" : e.toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("{}: invalid '{}', using default {}", fileLabel, path, defaultVal);
            return defaultVal;
        }
    }

    private static final String CONFIG = "config.yml";
    private static final String RENDERER = "renderer.yml";
    private static final String OUTPUTS = "outputs.yml";
    private static final String TRIGGERS = "triggers.yml";

    // ---------- config.yml ----------

    public int getBufferSeconds() {
        return getInt(config, "buffer_seconds", 30, 1, Integer.MAX_VALUE, CONFIG);
    }

    public int getFps() {
        return getInt(config, "fps", 10, 1, 20, CONFIG);
    }

    public int getSpectatorCaptureSeconds() {
        return getInt(config, "spectator_capture_seconds", 0, -1, Integer.MAX_VALUE, CONFIG);
    }

    public int getAsyncThreads() {
        return getInt(config, "async_threads", 2, 1, Integer.MAX_VALUE, CONFIG);
    }

    public boolean getAllowApiTriggers() {
        return getBoolean(config, "allow_api_triggers", true, CONFIG);
    }

    public boolean getWebhookServerEnabled() {
        return getBoolean(config, "webhook_server.enabled", false, CONFIG);
    }

    public int getWebhookServerPort() {
        return getInt(config, "webhook_server.port", 8765, 1, 65535, CONFIG);
    }

    public String getWebhookServerSecret() {
        return getString(config, "webhook_server.secret", "changeme", CONFIG);
    }

    // ---------- renderer.yml ----------

    public int getTileWidth() {
        return getInt(rendererConfig, "tile_width", 16, 1, Integer.MAX_VALUE, RENDERER);
    }

    public int getTileHeight() {
        return getInt(rendererConfig, "tile_height", 8, 1, Integer.MAX_VALUE, RENDERER);
    }

    public int getVolumeSize() {
        return getInt(rendererConfig, "volume_size", 32, 1, Integer.MAX_VALUE, RENDERER);
    }

    public int getCutOffset() {
        return getInt(rendererConfig, "cut_offset", 4, 0, 12, RENDERER);
    }

    public String getClientJarPath() {
        return getString(rendererConfig, "client_jar_path", "", RENDERER);
    }

    public boolean getSkinRenderingEnabled() {
        return getBoolean(rendererConfig, "skin_rendering_enabled", true, RENDERER);
    }

    public int getSkinCacheTtlSeconds() {
        return getInt(rendererConfig, "skin_cache_ttl_seconds", 3600, 1, Integer.MAX_VALUE, RENDERER);
    }

    public String getBlockColorsPath() {
        return getString(rendererConfig, "block_colors_path", "block_colors.json", RENDERER);
    }

    // ---------- outputs.yml ----------

    /**
     * Returns the "profiles" configuration section. Callers (e.g. OutputProfileRegistry) read
     * profile names and target lists from here. May be null if the file has no "profiles" key.
     */
    public ConfigurationSection getOutputProfilesSection() {
        if (outputsConfig == null) {
            return null;
        }
        return outputsConfig.getConfigurationSection("profiles");
    }

    // ---------- triggers.yml ----------

    /**
     * Returns the "internal" configuration section (rule id → rule config). May be null.
     */
    public ConfigurationSection getTriggerInternalSection() {
        if (triggersConfig == null) {
            return null;
        }
        return triggersConfig.getConfigurationSection("internal");
    }

    public boolean getTriggerInboundUseDefaultForUnmatched() {
        return getBoolean(triggersConfig, "inbound.use_default_for_unmatched", false, TRIGGERS);
    }

    public double getTriggerInboundDefaultPreSeconds() {
        return getDouble(triggersConfig, "inbound.defaults.default_pre_seconds", 4.0, TRIGGERS);
    }

    public double getTriggerInboundDefaultPostSeconds() {
        return getDouble(triggersConfig, "inbound.defaults.default_post_seconds", 1.0, TRIGGERS);
    }

    public List<String> getTriggerInboundDefaultOutputProfiles() {
        return getStringList(triggersConfig, "inbound.defaults.default_output_profiles",
                List.of("default"), TRIGGERS);
    }

    /** Dot-notation path into JSON for subject when use_default_for_unmatched is true. */
    public String getTriggerInboundDefaultSubjectPath() {
        return getString(triggersConfig, "inbound.defaults.subject_path", "player", TRIGGERS);
    }

    /**
     * Returns the list of "inbound.rules" (each entry is a ConfigurationSection). Empty list if missing or invalid.
     */
    public List<?> getTriggerInboundRules() {
        if (triggersConfig == null || !triggersConfig.contains("inbound.rules")) {
            return Collections.emptyList();
        }
        Object o = triggersConfig.get("inbound.rules");
        if (!(o instanceof List)) {
            logger.warn("{}: invalid 'inbound.rules' (not a list), using empty list", TRIGGERS);
            return Collections.emptyList();
        }
        return (List<?>) o;
    }

    public double getTriggerApiDefaultPreSeconds() {
        return getDouble(triggersConfig, "api.default_pre_seconds", 4.0, TRIGGERS);
    }

    public double getTriggerApiDefaultPostSeconds() {
        return getDouble(triggersConfig, "api.default_post_seconds", 1.0, TRIGGERS);
    }

    public List<String> getTriggerApiDefaultOutputProfiles() {
        return getStringList(triggersConfig, "api.default_output_profiles", List.of("default"), TRIGGERS);
    }
}

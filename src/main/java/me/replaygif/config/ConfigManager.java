package me.replaygif.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * Loads and validates all config files (config.yml, renderer.yml, outputs.yml, triggers.yml).
 * Copies defaults from plugin jar on first run if missing. Never throws on bad values — applies
 * defaults and logs WARN. No cross-file validation (handled elsewhere).
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    /** Tracks (fileLabel:path) already warned for missing/invalid; cleared on load() so reload warns again. */
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    private FileConfiguration config;
    private FileConfiguration rendererConfig;
    private FileConfiguration outputsConfig;
    private FileConfiguration triggersConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
    }

    /** Logger for components that use ConfigManager (e.g. GifEncoder progress). */
    public Logger getLogger() {
        return logger;
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
            try {
                plugin.saveResource(name, false);
            } catch (Exception e) {
                logger.warn("Could not save default {} (e.g. read-only plugin directory): {}", name, e.getMessage());
            }
        }
    }

    /**
     * Load (or reload) all config files from the plugin data folder.
     * Apply defaults and log WARN for missing or invalid values. Never throws.
     */
    public void load() {
        warnedKeys.clear();
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
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            logger.error("Cannot load {}: {}. Using defaults for all values in this file.", name, e.getMessage());
            return new YamlConfiguration();
        }
    }

    // --- Helpers: typed read with WARN on missing/invalid, never throw ---

    private void warnOnce(String fileLabel, String path, String reason, Object defaultVal) {
        String key = fileLabel + ":" + path + ":" + reason;
        if (warnedKeys.add(key)) {
            logger.warn("{}: {} '{}', using default {}", fileLabel, reason, path, defaultVal);
        }
    }

    private int getInt(FileConfiguration c, String path, int defaultVal, int min, int max, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            warnOnce(fileLabel, path, "missing", defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Number)) {
            warnOnce(fileLabel, path, "invalid", defaultVal);
            return defaultVal;
        }
        int v = ((Number) o).intValue();
        if (min != max && (v < min || v > max)) {
            warnOnce(fileLabel, path, "range", defaultVal);
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
            warnOnce(fileLabel, path, "missing", defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Number)) {
            warnOnce(fileLabel, path, "invalid", defaultVal);
            return defaultVal;
        }
        return ((Number) o).doubleValue();
    }

    private String getString(FileConfiguration c, String path, String defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            warnOnce(fileLabel, path, "missing", defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (o == null) {
            warnOnce(fileLabel, path, "invalid", defaultVal);
            return defaultVal;
        }
        return o.toString();
    }

    private boolean getBoolean(FileConfiguration c, String path, boolean defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            warnOnce(fileLabel, path, "missing", defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof Boolean)) {
            warnOnce(fileLabel, path, "invalid", defaultVal);
            return defaultVal;
        }
        return (Boolean) o;
    }

    private List<String> getStringList(FileConfiguration c, String path, List<String> defaultVal, String fileLabel) {
        if (c == null) {
            return defaultVal;
        }
        if (!c.contains(path)) {
            warnOnce(fileLabel, path, "missing", defaultVal);
            return defaultVal;
        }
        Object o = c.get(path);
        if (!(o instanceof List)) {
            warnOnce(fileLabel, path, "invalid", defaultVal);
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

    /** Blocks at relY <= this are never culled; full ground. Only blocks above get the cut. Default -1. */
    public int getGroundFullRelY() {
        return getInt(rendererConfig, "ground_full_rel_y", -1, -16, 16, RENDERER);
    }

    public int getViewCenterOffsetY() {
        return getInt(rendererConfig, "view_center_offset_y", 0, 0, 8, RENDERER);
    }

    public String getResourcePackPath() {
        return getString(rendererConfig, "resource_pack_path", "packs", RENDERER);
    }

    public String getClientJarPath() {
        String path = getString(rendererConfig, "client_jar_path", "", RENDERER);
        if (path != null && path.isBlank()) {
            return getDefaultClientJarPath();
        }
        return path;
    }

    /** True if the client jar path points to an existing file. Used to decide whether to run Mojang asset download. */
    public boolean isClientJarPathValid() {
        String path = getClientJarPath();
        return path != null && !path.isBlank() && new File(path.trim()).isFile();
    }

    /** Platform-specific default path to Minecraft 1.21 client jar. Used when client_jar_path is empty. */
    private static String getDefaultClientJarPath() {
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            String base = (appData != null && !appData.isBlank()) ? appData : (home + "\\AppData\\Roaming");
            return base + "\\.minecraft\\versions\\1.21\\1.21.jar";
        }
        if (os.contains("mac")) {
            return home + "/Library/Application Support/minecraft/versions/1.21/1.21.jar";
        }
        return home + "/.minecraft/versions/1.21/1.21.jar";
    }

    /** Minecraft version for Mojang asset download (e.g. "1.21"). Used when no other texture source. */
    public String getDownloadAssetsVersion() {
        return getString(rendererConfig, "download_assets_version", "1.21", RENDERER);
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

    public boolean getBlockTexturesEnabled() {
        return getBoolean(rendererConfig, "block_textures_enabled", true, RENDERER);
    }

    /** Whether to cull occluded block faces (only draw visible ones). False = draw all 3 faces. */
    public boolean getBlockFaceCulling() {
        return getBoolean(rendererConfig, "block_face_culling", true, RENDERER);
    }

    /** "transparent", "white", "black", or hex e.g. "#F5F5F5". Default off-white. */
    public String getGifBackground() {
        return getString(rendererConfig, "gif_background", "#F5F5F5", RENDERER);
    }

    /** GIF encoding quality/speed. Higher = faster (sample factor). 1–30, default 20. */
    public int getGifQuality() {
        return getInt(rendererConfig, "gif_quality", 20, 1, 30, RENDERER);
    }

    /** Whether to draw full HUD (hearts, armor, food, XP, hotbar). False = action bar and boss bars only. */
    public boolean getHudEnabled() {
        return getBoolean(rendererConfig, "hud_enabled", true, RENDERER);
    }

    /** HUD opacity 0–100. 100 = fully opaque. */
    public int getHudOpacity() {
        return getInt(rendererConfig, "hud_opacity", 100, 0, 100, RENDERER);
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

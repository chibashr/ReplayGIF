package me.replaygif.config;

import me.replaygif.output.DiscordWebhookOutput;
import me.replaygif.output.FilesystemOutput;
import me.replaygif.output.GenericWebhookOutput;
import me.replaygif.output.OutputTarget;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Name → List&lt;OutputTarget&gt;, built from outputs.yml via ConfigManager.
 * Profiles with no valid targets are excluded and logged at WARN.
 */
public class OutputProfileRegistry {

    private final ConfigManager configManager;
    private final File pluginDataFolder;
    private final Logger logger;
    private final Map<String, List<OutputTarget>> profiles = new HashMap<>();

    public OutputProfileRegistry(ConfigManager configManager, JavaPlugin plugin) {
        this.configManager = configManager;
        this.pluginDataFolder = plugin.getDataFolder();
        this.logger = plugin.getSLF4JLogger();
        load();
    }

    private void load() {
        profiles.clear();
        ConfigurationSection section = configManager.getOutputProfilesSection();
        if (section == null) {
            return;
        }
        for (String profileName : section.getKeys(false)) {
            List<OutputTarget> targets = parseTargets(profileName, section.getList(profileName));
            if (targets.isEmpty()) {
                logger.warn("Output profile '{}' has no valid targets; excluding from registry.", profileName);
                continue;
            }
            profiles.put(profileName, targets);
        }
    }

    @SuppressWarnings("unchecked")
    private List<OutputTarget> parseTargets(String profileName, List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<OutputTarget> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            String type;
            String url;
            String pathTemplate;
            Map<String, String> headers;
            if (entry instanceof ConfigurationSection) {
                ConfigurationSection sec = (ConfigurationSection) entry;
                type = sec.getString("type");
                url = sec.getString("url");
                pathTemplate = sec.getString("path_template");
                headers = getHeadersFromSection(sec.getConfigurationSection("headers"));
            } else if (entry instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) entry;
                type = getString(map, "type");
                url = getString(map, "url");
                pathTemplate = getString(map, "path_template");
                headers = getStringMap(map, "headers");
            } else {
                logger.warn("Output profile '{}' entry {} is not a map; skipping.", profileName, i);
                continue;
            }
            if (type == null || type.isBlank()) {
                logger.warn("Output profile '{}' entry {} has no 'type'; skipping.", profileName, i);
                continue;
            }
            OutputTarget target = createTarget(type, url, pathTemplate, headers, profileName, i);
            if (target != null) {
                result.add(target);
            }
        }
        return result;
    }

    private static Map<String, String> getHeadersFromSection(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Object> values = section.getValues(false);
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getValue() != null) {
                out.put(e.getKey(), e.getValue().toString());
            }
        }
        return out;
    }

    private OutputTarget createTarget(String type, String url, String pathTemplate, Map<String, String> headers, String profileName, int index) {
        switch (type) {
            case "discord_webhook": {
                if (url == null || url.isBlank()) {
                    logger.warn("Output profile '{}' entry {} (discord_webhook) has no 'url'; skipping.", profileName, index);
                    return null;
                }
                return new DiscordWebhookOutput(url, logger);
            }
            case "generic_webhook": {
                if (url == null || url.isBlank()) {
                    logger.warn("Output profile '{}' entry {} (generic_webhook) has no 'url'; skipping.", profileName, index);
                    return null;
                }
                return new GenericWebhookOutput(url, headers != null ? headers : Map.of(), logger);
            }
            case "filesystem": {
                if (pathTemplate == null || pathTemplate.isBlank()) {
                    logger.warn("Output profile '{}' entry {} (filesystem) has no 'path_template'; skipping.", profileName, index);
                    return null;
                }
                return new FilesystemOutput(pathTemplate, pluginDataFolder, logger);
            }
            default:
                logger.warn("Output profile '{}' entry {} has unknown type '{}'; skipping.", profileName, index, type);
                return null;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o == null) {
            return Map.of();
        }
        if (!(o instanceof Map)) {
            return Map.of();
        }
        Map<?, ?> raw = (Map<?, ?>) o;
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString(), e.getValue().toString());
            }
        }
        return out;
    }

    /**
     * Returns the list of output targets for the given profile name.
     * Returns an empty list for unknown or excluded profile names.
     */
    public List<OutputTarget> getProfile(String name) {
        if (name == null) {
            return List.of();
        }
        List<OutputTarget> list = profiles.get(name);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}

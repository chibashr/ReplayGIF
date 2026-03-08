package me.replaygif.config;

import org.bukkit.configuration.ConfigurationSection;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Inbound event key → TriggerRule, built from triggers.yml.
 * Parses internal and inbound sections; validates profile refs against OutputProfileRegistry.
 */
public class TriggerRuleRegistry {

    private static final String PLAYER_DEATH_EVENT_CLASS = "org.bukkit.event.entity.PlayerDeathEvent";

    private final List<TriggerRule> internalRules = new ArrayList<>();
    private final List<TriggerRule> inboundRules = new ArrayList<>();
    private final Logger logger;

    public TriggerRuleRegistry(ConfigManager configManager, OutputProfileRegistry outputRegistry, Logger logger) {
        this.logger = logger;
        loadInternal(configManager);
        loadInbound(configManager);
        removeRulesWithMissingProfiles(outputRegistry);
    }

    private void loadInternal(ConfigManager configManager) {
        ConfigurationSection section = configManager.getTriggerInternalSection();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection ruleSec = section.getConfigurationSection(key);
            if (ruleSec == null) {
                continue;
            }
            TriggerRule rule = parseInternalRule(key, ruleSec);
            if (rule != null) {
                internalRules.add(rule);
            }
        }
    }

    private TriggerRule parseInternalRule(String ruleId, ConfigurationSection sec) {
        List<String> profiles = getStringList(sec, "output_profiles");
        if (profiles == null || profiles.isEmpty()) {
            logger.warn("triggers.yml internal rule '{}' has no output_profiles; skipping.", ruleId);
            return null;
        }
        double pre = sec.getDouble("pre_seconds", 4.0);
        double post = sec.getDouble("post_seconds", 1.0);
        boolean enabled = sec.getBoolean("enabled", true);
        String labelPath = sec.getString("label_path");
        String labelFallback = sec.getString("label_fallback");
        if (labelFallback == null || labelFallback.isEmpty()) {
            labelFallback = "{player}";
        }

        String pattern;
        List<String> getterChain;

        if ("player_death".equals(ruleId)) {
            pattern = PLAYER_DEATH_EVENT_CLASS;
            getterChain = List.of("getEntity");
        } else {
            String eventClass = sec.getString("event_class");
            if (eventClass == null || eventClass.isBlank()) {
                logger.warn("triggers.yml internal rule '{}' has no event_class; skipping.", ruleId);
                return null;
            }
            pattern = eventClass;
            String resolver = sec.getString("resolver");
            if (resolver == null || resolver.isBlank()) {
                resolver = "getPlayer";
            }
            getterChain = List.of(resolver.split("\\."));
        }

        List<TriggerCondition> conditions = parseConditions(sec.getMapList("conditions"));
        return new TriggerRule(
                pattern,
                null,
                getterChain,
                profiles,
                pre,
                post,
                labelPath,
                labelFallback,
                conditions,
                enabled);
    }

    @SuppressWarnings("unchecked")
    private List<TriggerCondition> parseConditions(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<TriggerCondition> out = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) entry;
            String methodChain = getString(map, "method_chain");
            String opStr = getString(map, "operator");
            String expected = getString(map, "expected_value");
            if (methodChain == null || opStr == null) {
                continue;
            }
            TriggerCondition.Operator op;
            try {
                op = TriggerCondition.Operator.valueOf(opStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }
            out.add(new TriggerCondition(methodChain, op, expected != null ? expected : ""));
        }
        return out;
    }

    private void loadInbound(ConfigManager configManager) {
        List<?> list = configManager.getTriggerInboundRules();
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            TriggerRule rule = parseInboundRuleEntry(entry, i);
            if (rule != null) {
                inboundRules.add(rule);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private TriggerRule parseInboundRuleEntry(Object entry, int index) {
        String eventKey;
        String subjectPath;
        List<String> profiles;
        double pre;
        double post;
        String labelPath;
        String labelFallback;
        boolean enabled;

        if (entry instanceof ConfigurationSection) {
            ConfigurationSection sec = (ConfigurationSection) entry;
            eventKey = sec.getString("event_key");
            subjectPath = sec.getString("subject_path");
            profiles = getStringList(sec, "output_profiles");
            pre = sec.getDouble("pre_seconds", 4.0);
            post = sec.getDouble("post_seconds", 1.0);
            labelPath = sec.getString("label_path");
            labelFallback = sec.getString("label_fallback");
            enabled = sec.getBoolean("enabled", true);
        } else if (entry instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) entry;
            eventKey = getString(map, "event_key");
            subjectPath = getString(map, "subject_path");
            profiles = getStringListFromMap(map, "output_profiles");
            pre = getDouble(map, "pre_seconds", 4.0);
            post = getDouble(map, "post_seconds", 1.0);
            labelPath = getString(map, "label_path");
            labelFallback = getString(map, "label_fallback");
            enabled = getBoolean(map, "enabled", true);
        } else {
            logger.warn("triggers.yml inbound rule {} is not a map; skipping.", index);
            return null;
        }

        if (eventKey == null || eventKey.isBlank()) {
            logger.warn("triggers.yml inbound rule {} has no event_key; skipping.", index);
            return null;
        }
        if (subjectPath == null || subjectPath.isBlank()) {
            logger.warn("triggers.yml inbound rule {} has no subject_path; skipping.", index);
            return null;
        }
        if (profiles == null || profiles.isEmpty()) {
            logger.warn("triggers.yml inbound rule {} has no output_profiles; skipping.", index);
            return null;
        }
        if (labelFallback == null) {
            labelFallback = "{player}";
        }

        return new TriggerRule(
                eventKey,
                subjectPath,
                null,
                profiles,
                pre,
                post,
                labelPath,
                labelFallback,
                List.of(),
                enabled);
    }

    private List<String> getStringList(ConfigurationSection sec, String path) {
        if (sec == null || !sec.contains(path)) {
            return null;
        }
        List<?> list = sec.getList(path);
        if (list == null) return null;
        return list.stream().map(o -> o == null ? "" : o.toString()).collect(Collectors.toList());
    }

    private static String getString(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringListFromMap(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o == null || !(o instanceof List)) return null;
        List<?> list = (List<?>) o;
        return list.stream().map(x -> x == null ? "" : x.toString()).collect(Collectors.toList());
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object o = map.get(key);
        if (o instanceof Number) return ((Number) o).doubleValue();
        return def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object o = map.get(key);
        if (o instanceof Boolean) return (Boolean) o;
        return def;
    }

    private void removeRulesWithMissingProfiles(OutputProfileRegistry outputRegistry) {
        removeMissingFromList(internalRules, "internal", outputRegistry);
        removeMissingFromList(inboundRules, "inbound", outputRegistry);
    }

    private void removeMissingFromList(List<TriggerRule> rules, String source, OutputProfileRegistry outputRegistry) {
        rules.removeIf(rule -> {
            for (String profileName : rule.outputProfileNames) {
                if (outputRegistry.getProfile(profileName).isEmpty()) {
                    logger.error("Trigger rule references output profile '{}' which does not exist in outputs.yml. Rule ({} pattern '{}') skipped.",
                            profileName, source, rule.pattern);
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Match an inbound event key against inbound rules. Exact match first, then wildcard rules
     * ordered by specificity (most specific first). Specificity = number of non-wildcard segments.
     */
    public Optional<TriggerRule> matchInbound(String eventKey) {
        if (eventKey == null || eventKey.isEmpty()) {
            return Optional.empty();
        }
        for (TriggerRule rule : inboundRules) {
            if (!rule.enabled) continue;
            if (rule.pattern.equals(eventKey)) {
                return Optional.of(rule);
            }
        }
        List<TriggerRule> wildcardMatches = new ArrayList<>();
        for (TriggerRule rule : inboundRules) {
            if (!rule.enabled) continue;
            if (!rule.pattern.endsWith(".*")) continue;
            String prefixWithoutDot = rule.pattern.substring(0, rule.pattern.length() - 2);
            if (eventKey.equals(prefixWithoutDot) || eventKey.startsWith(prefixWithoutDot + ".")) {
                wildcardMatches.add(rule);
            }
        }
        if (wildcardMatches.isEmpty()) {
            return Optional.empty();
        }
        return wildcardMatches.stream()
                .max(Comparator.comparingInt(TriggerRuleRegistry::specificity));
    }

    private static int specificity(TriggerRule r) {
        if (!r.pattern.endsWith(".*")) return 0;
        String withoutWildcard = r.pattern.substring(0, r.pattern.length() - 2);
        return (int) withoutWildcard.chars().filter(c -> c == '.').count() + 1;
    }

    /**
     * Returns all internal (Bukkit listener) rules. Unmodifiable.
     */
    public List<TriggerRule> getInternalRules() {
        return Collections.unmodifiableList(new ArrayList<>(internalRules));
    }

    /**
     * Returns the player_death internal rule if present (pattern = PlayerDeathEvent class name).
     */
    public Optional<TriggerRule> getPlayerDeathRule() {
        return internalRules.stream()
                .filter(r -> PLAYER_DEATH_EVENT_CLASS.equals(r.pattern))
                .findFirst();
    }
}

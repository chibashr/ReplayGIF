package me.replaygif.trigger;

import me.replaygif.config.TriggerCondition;
import me.replaygif.config.TriggerRule;
import me.replaygif.config.TriggerRuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registers one EventExecutor per internal trigger rule (except player_death, which has its own
 * DeathListener). Uses reflection so we can support any Bukkit event class by name without
 * compiling against every event type. Subject and label are resolved via rule's getter chains
 * and label path so one config drives many event types.
 */
public final class DynamicListenerRegistry {

    private static final String PLAYER_DEATH_EVENT_CLASS = "org.bukkit.event.entity.PlayerDeathEvent";

    private final TriggerHandler triggerHandler;
    private final TriggerRuleRegistry ruleRegistry;
    private final Plugin plugin;
    private final PluginManager pluginManager;
    private final Logger logger;
    private final Listener sharedListener = new Listener() {};

    /** Convenience constructor using Bukkit.getPluginManager(). */
    public DynamicListenerRegistry(TriggerHandler triggerHandler, TriggerRuleRegistry ruleRegistry,
                                   Plugin plugin, Logger logger) {
        this(triggerHandler, ruleRegistry, plugin, Bukkit.getPluginManager(), logger);
    }

    /**
     * @param triggerHandler  receives built context when a registered event fires and rule matches
     * @param ruleRegistry    source of internal rules (event class name, getters, conditions)
     * @param plugin          registering plugin
     * @param pluginManager   for registerEvent
     * @param logger          for registration and resolution failures
     */
    public DynamicListenerRegistry(TriggerHandler triggerHandler, TriggerRuleRegistry ruleRegistry,
                                   Plugin plugin, PluginManager pluginManager, Logger logger) {
        this.triggerHandler = triggerHandler;
        this.ruleRegistry = ruleRegistry;
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.logger = logger;
    }

    /**
     * Registers one listener per internal rule (skipping player_death). Call once on enable
     * so all configured event types (e.g. EntityDamageByEntityEvent) are wired without code changes.
     */
    public void register() {
        for (TriggerRule rule : ruleRegistry.getInternalRules()) {
            if (PLAYER_DEATH_EVENT_CLASS.equals(rule.pattern)) {
                continue;
            }
            try {
                Class<?> eventClass = Class.forName(rule.pattern);
                if (!Event.class.isAssignableFrom(eventClass)) {
                    logger.warn("Dynamic listener: {} does not extend Event. Rule skipped.", rule.pattern);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> castEventClass = (Class<? extends Event>) eventClass;
                EventExecutor executor = (listener, rawEvent) -> {
                    if (!eventClass.isInstance(rawEvent)) {
                        return;
                    }
                    resolveAndHandle(eventClass.cast(rawEvent), rule);
                };
                pluginManager.registerEvent(
                        castEventClass,
                        sharedListener,
                        EventPriority.MONITOR,
                        executor,
                        plugin);
                logger.info("Dynamic listener registered for {}", rule.pattern);
            } catch (ClassNotFoundException e) {
                logger.warn("Dynamic listener: class not found: {}. Rule skipped.", rule.pattern);
            }
        }
        logger.info("DynamicListenerRegistry: registration complete.");
    }

    /** No-op unregister; Bukkit/Paper unregisters by plugin. Exists for symmetric lifecycle logging. */
    public void unregister() {
        logger.info("Dynamic listeners unregistered.");
    }

    private void resolveAndHandle(Object event, TriggerRule rule) {
        if (!rule.enabled) {
            logger.debug("Dynamic trigger rule for {} is disabled; skipping.", rule.pattern);
            return;
        }

        if (!evaluateConditions(event, rule)) {
            return;
        }

        Player player = resolveSubject(event, rule);
        if (player == null) {
            return;
        }

        String eventLabel = resolveLabel(event, rule, player);
        Location loc = player.getLocation();
        World world = loc.getWorld();
        String dimension = "world";
        if (world != null) {
            if (world instanceof org.bukkit.Keyed k && k.getKey() != null) {
                dimension = k.getKey().toString();
            } else {
                dimension = world.getName();
            }
        }
        String worldName = world != null ? world.getName() : "world";

        TriggerContext context = new TriggerContext.Builder()
                .subjectUUID(player.getUniqueId())
                .subjectName(player.getName())
                .eventLabel(eventLabel)
                .preSeconds(rule.preSeconds)
                .postSeconds(rule.postSeconds)
                .outputProfileNames(rule.outputProfileNames)
                .metadata(Map.of())
                .triggerTimestamp(System.currentTimeMillis())
                .jobId(UUID.randomUUID())
                .triggerX(loc.getBlockX())
                .triggerY(loc.getBlockY())
                .triggerZ(loc.getBlockZ())
                .dimension(dimension)
                .worldName(worldName)
                .build();

        triggerHandler.handle(context);
    }

    private boolean evaluateConditions(Object event, TriggerRule rule) {
        for (TriggerCondition condition : rule.conditions) {
            String[] methods = condition.methodChain.split("\\.");
            Object current = event;
            for (String methodName : methods) {
                if (current == null) {
                    logger.debug("Condition method chain returned null; skipping trigger.");
                    return false;
                }
                try {
                    Method m = current.getClass().getMethod(methodName);
                    current = m.invoke(current);
                } catch (NoSuchMethodException e) {
                    logger.warn("Condition resolution failed: {}.{} method not found. Skipping trigger.",
                            current.getClass().getName(), methodName);
                    return false;
                } catch (IllegalAccessException e) {
                    logger.warn("Condition resolution failed: {} cannot access {}. Skipping trigger.",
                            current.getClass().getName(), methodName);
                    return false;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    logger.warn("Condition resolution failed for {}: {}. Skipping trigger.",
                            rule.pattern, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    return false;
                }
            }
            if (current == null) {
                logger.debug("Condition method chain returned null; skipping trigger.");
                return false;
            }
            String result = current.toString();

            switch (condition.operator) {
                case EQUALS -> {
                    if (!result.equals(condition.expectedValue)) {
                        logger.debug("Condition EQUALS not satisfied: got '{}', expected '{}'", result, condition.expectedValue);
                        return false;
                    }
                }
                case NOT_EQUALS -> {
                    if (result.equals(condition.expectedValue)) {
                        logger.debug("Condition NOT_EQUALS not satisfied: got '{}'", result);
                        return false;
                    }
                }
                case GREATER_THAN -> {
                    double a = parseDoubleOrWarn(result, rule.pattern);
                    double b = parseDoubleOrWarn(condition.expectedValue, rule.pattern);
                    if (Double.isNaN(a) || Double.isNaN(b)) {
                        return false;
                    }
                    if (!(a > b)) {
                        return false;
                    }
                }
                case LESS_THAN -> {
                    double a = parseDoubleOrWarn(result, rule.pattern);
                    double b = parseDoubleOrWarn(condition.expectedValue, rule.pattern);
                    if (Double.isNaN(a) || Double.isNaN(b)) {
                        return false;
                    }
                    if (!(a < b)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private double parseDoubleOrWarn(String value, String context) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("Condition numeric comparison: could not parse '{}' as double (rule {}). Skipping trigger.", value, context);
            return Double.NaN;
        }
    }

    private Player resolveSubject(Object event, TriggerRule rule) {
        if (rule.internalGetterChain == null || rule.internalGetterChain.isEmpty()) {
            logger.debug("No internal getter chain; skipping trigger.");
            return null;
        }
        Object current = event;
        for (String methodName : rule.internalGetterChain) {
            if (current == null) {
                logger.debug("Subject getter chain returned null; skipping trigger.");
                return null;
            }
            try {
                Method m = current.getClass().getMethod(methodName);
                current = m.invoke(current);
            } catch (NoSuchMethodException e) {
                logger.warn("Subject resolution failed on {}.{}: method not found. Silently skip.",
                        current.getClass().getName(), methodName);
                return null;
            } catch (IllegalAccessException e) {
                logger.warn("Subject resolution failed: {} cannot access {}. Silently skip.",
                        current.getClass().getName(), methodName);
                return null;
            } catch (java.lang.reflect.InvocationTargetException e) {
                logger.warn("Subject resolution failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                return null;
            }
        }
        if (current == null) {
            logger.debug("Subject getter chain returned null; skipping trigger.");
            return null;
        }
        if (!(current instanceof Player)) {
            logger.debug("Subject is not a Player (got {}); skipping trigger.", current.getClass().getName());
            return null;
        }
        Player player = (Player) current;
        if (!player.isOnline()) {
            logger.debug("Subject player is not online; skipping trigger.");
            return null;
        }
        return player;
    }

    private String resolveLabel(Object event, TriggerRule rule, Player player) {
        if (rule.labelPath == null || rule.labelPath.isBlank()) {
            return substitutePlayer(rule.labelFallback, player.getName());
        }
        String[] parts = rule.labelPath.split("\\.");
        Object current = event;
        for (String methodName : parts) {
            if (current == null) {
                return substitutePlayer(rule.labelFallback, player.getName());
            }
            try {
                Method m = current.getClass().getMethod(methodName);
                current = m.invoke(current);
            } catch (Exception e) {
                logger.debug("Label path resolution failed, using fallback: {}", e.getMessage());
                return substitutePlayer(rule.labelFallback, player.getName());
            }
        }
        if (current == null) {
            return substitutePlayer(rule.labelFallback, player.getName());
        }
        return current.toString();
    }

    private static String substitutePlayer(String template, String playerName) {
        if (template == null) {
            return playerName;
        }
        return template.replace("{player}", playerName != null ? playerName : "");
    }
}

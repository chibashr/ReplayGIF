package me.replaygif.trigger;

import me.replaygif.config.TriggerRule;
import me.replaygif.config.TriggerRuleRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Dedicated listener for player death so we don't rely on reflection or event class names.
 * MONITOR priority so we run after other plugins; we don't cancel the event. Rule comes from
 * TriggerRuleRegistry.getPlayerDeathRule() so a single "player_death" rule drives pre/post and profiles.
 */
public final class DeathListener implements Listener {

    private final TriggerHandler triggerHandler;
    private final TriggerRuleRegistry ruleRegistry;
    private final Logger logger;

    /**
     * @param triggerHandler  receives the built context
     * @param ruleRegistry    provides the player_death rule (pre, post, profiles, enabled)
     * @param logger          for debug when rule missing or disabled
     */
    public DeathListener(TriggerHandler triggerHandler, TriggerRuleRegistry ruleRegistry, Logger logger) {
        this.triggerHandler = triggerHandler;
        this.ruleRegistry = ruleRegistry;
        this.logger = logger;
    }

    /** Fires when a player dies; builds context from entity and death message, then hands to TriggerHandler. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Optional<TriggerRule> ruleOpt = ruleRegistry.getPlayerDeathRule();
        if (ruleOpt.isEmpty()) {
            logger.debug("PlayerDeathEvent fired but no player_death rule configured; skipping.");
            return;
        }
        TriggerRule rule = ruleOpt.get();
        if (!rule.enabled) {
            logger.debug("PlayerDeathEvent fired but player_death rule is disabled; skipping.");
            return;
        }

        Player subject = event.getEntity();
        if (subject == null) {
            return;
        }

        String eventLabel = getDeathMessagePlainText(event, subject);
        Location loc = subject.getLocation();
        World world = loc.getWorld();
        String dimension = "world";
        if (world != null) {
            if (world instanceof org.bukkit.Keyed keyed && keyed.getKey() != null) {
                dimension = keyed.getKey().toString();
            } else {
                dimension = world.getName();
            }
        }
        String worldName = world != null ? world.getName() : "world";

        TriggerContext context = new TriggerContext.Builder()
                .subjectUUID(subject.getUniqueId())
                .subjectName(subject.getName())
                .eventLabel(eventLabel)
                .preSeconds(rule.preSeconds)
                .postSeconds(rule.postSeconds)
                .outputProfileNames(rule.outputProfileNames)
                .metadata(java.util.Map.of())
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

    /** Resolves death message for event label; fallback to "{player} died" so we always have a non-null label. */
    private String getDeathMessagePlainText(PlayerDeathEvent event, Player player) {
        try {
            Component msg = event.deathMessage();
            if (msg == null) {
                return player.getName() + " died";
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(msg);
            if (plain == null || plain.isBlank()) {
                return player.getName() + " died";
            }
            return plain;
        } catch (Throwable t) {
            logger.debug("Could not get death message, using fallback", t);
            return player.getName() + " died";
        }
    }
}

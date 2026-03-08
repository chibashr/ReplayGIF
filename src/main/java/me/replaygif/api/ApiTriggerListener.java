package me.replaygif.api;

import me.replaygif.config.ConfigManager;
import me.replaygif.trigger.TriggerContext;
import me.replaygif.trigger.TriggerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Bridges the soft-dependency event into the same pipeline as the hard-dependency API.
 * MONITOR priority ensures all other logic has run before we consume the event; we do not
 * cancel it. allow_api_triggers gates the feature so server owners can disable external triggers.
 */
public final class ApiTriggerListener implements Listener {

    private final TriggerHandler triggerHandler;
    private final ConfigManager configManager;
    private final Logger logger;

    /**
     * @param triggerHandler receives the built context
     * @param configManager  for allow_api_triggers and api defaults
     * @param logger         for offline-player warnings
     */
    public ApiTriggerListener(TriggerHandler triggerHandler, ConfigManager configManager, Logger logger) {
        this.triggerHandler = triggerHandler;
        this.configManager = configManager;
        this.logger = logger;
    }

    /** Fires when another plugin calls PluginManager#callEvent(ReplayGifTriggerEvent). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onReplayGifTrigger(ReplayGifTriggerEvent event) {
        if (!configManager.getAllowApiTriggers()) {
            return;
        }
        Player subject = event.getSubject();
        if (subject == null) {
            return;
        }
        if (!subject.isOnline()) {
            logger.warn("API trigger for offline player {}, ignored.", subject.getName());
            return;
        }
        double pre = event.getPreSeconds() >= 0 ? event.getPreSeconds() : configManager.getTriggerApiDefaultPreSeconds();
        double post = event.getPostSeconds() >= 0 ? event.getPostSeconds() : configManager.getTriggerApiDefaultPostSeconds();
        var profiles = event.getOutputProfileNames();
        if (profiles == null || profiles.isEmpty()) {
            profiles = configManager.getTriggerApiDefaultOutputProfiles();
        }
        var metadata = event.getMetadata();
        if (metadata == null) {
            metadata = java.util.Collections.emptyMap();
        }
        UUID jobId = UUID.randomUUID();
        TriggerContext context = ReplayGifAPIImpl.buildContextFromApi(
                configManager, subject, event.getEventLabel(),
                pre, post, profiles, metadata, jobId);
        triggerHandler.handle(context);
    }
}

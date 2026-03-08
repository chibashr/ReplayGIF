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
 * Listens for ReplayGifTriggerEvent at MONITOR priority. Respects allow_api_triggers
 * config; validates subject is online; resolves defaults from triggers.yml → api;
 * builds TriggerContext and hands to TriggerHandler.
 */
public final class ApiTriggerListener implements Listener {

    private final TriggerHandler triggerHandler;
    private final ConfigManager configManager;
    private final Logger logger;

    public ApiTriggerListener(TriggerHandler triggerHandler, ConfigManager configManager, Logger logger) {
        this.triggerHandler = triggerHandler;
        this.configManager = configManager;
        this.logger = logger;
    }

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

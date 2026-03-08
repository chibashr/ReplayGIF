package me.replaygif.api;

import me.replaygif.config.ConfigManager;
import me.replaygif.trigger.TriggerContext;
import me.replaygif.trigger.TriggerHandler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link ReplayGifAPI}. Kept in the API package so that the event-based
 * flow can reuse {@link #buildContextFromApi} without duplicating default resolution;
 * only validation and default application live here, not pipeline logic.
 */
public final class ReplayGifAPIImpl implements ReplayGifAPI {

    private final TriggerHandler triggerHandler;
    private final ConfigManager configManager;
    private final Logger logger;

    /**
     * @param triggerHandler pipeline entry point; receives the built context
     * @param configManager  source of api default pre/post seconds and default output profiles
     * @param logger         for warning when subject is null or offline
     */
    public ReplayGifAPIImpl(TriggerHandler triggerHandler, ConfigManager configManager, Logger logger) {
        this.triggerHandler = triggerHandler;
        this.configManager = configManager;
        this.logger = logger;
    }

    @Override
    public UUID trigger(
            Player subject,
            String eventLabel,
            double preSeconds,
            double postSeconds,
            List<String> outputProfileNames,
            Map<String, String> metadata) {
        UUID jobId = UUID.randomUUID();
        if (subject == null) {
            logger.warn("[{}] API trigger with null subject, ignored.", jobId);
            return jobId;
        }
        if (!subject.isOnline()) {
            logger.warn("API trigger for offline player {}, ignored.", subject.getName());
            return jobId;
        }
        String label = eventLabel != null ? eventLabel : "";
        TriggerContext context = buildContextFromApi(
                configManager, subject, label,
                preSeconds, postSeconds, outputProfileNames, metadata,
                jobId);
        triggerHandler.handle(context);
        return context.jobId;
    }

    /**
     * Shared builder for API and event flows so default resolution (pre/post seconds,
     * output profiles, dimension/world names) is defined in one place and stays consistent.
     *
     * @param configManager  used for api default pre/post and default output profiles when caller passes -1 or null
     * @param subject       player; used for UUID, name, location, dimension
     * @param eventLabel    non-null label (empty allowed)
     * @param preSeconds    &lt; 0 → config default, else used as-is
     * @param postSeconds   &lt; 0 → config default, else used as-is
     * @param outputProfileNames null or empty → config default list
     * @param metadata      null → empty map
     * @param jobId         job UUID for this trigger
     * @return fully populated TriggerContext for TriggerHandler
     */
    public static TriggerContext buildContextFromApi(
            ConfigManager configManager,
            Player subject,
            String eventLabel,
            double preSeconds,
            double postSeconds,
            List<String> outputProfileNames,
            Map<String, String> metadata,
            UUID jobId) {
        double pre = preSeconds >= 0 ? preSeconds : configManager.getTriggerApiDefaultPreSeconds();
        double post = postSeconds >= 0 ? postSeconds : configManager.getTriggerApiDefaultPostSeconds();
        List<String> profiles = (outputProfileNames != null && !outputProfileNames.isEmpty())
                ? outputProfileNames
                : configManager.getTriggerApiDefaultOutputProfiles();
        Map<String, String> meta = metadata != null ? metadata : Collections.emptyMap();

        Location loc = subject.getLocation();
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

        return new TriggerContext.Builder()
                .subjectUUID(subject.getUniqueId())
                .subjectName(subject.getName())
                .eventLabel(eventLabel)
                .preSeconds(pre)
                .postSeconds(post)
                .outputProfileNames(profiles)
                .metadata(meta)
                .triggerTimestamp(System.currentTimeMillis())
                .jobId(jobId)
                .triggerX(loc.getBlockX())
                .triggerY(loc.getBlockY())
                .triggerZ(loc.getBlockZ())
                .dimension(dimension)
                .worldName(worldName)
                .build();
    }
}

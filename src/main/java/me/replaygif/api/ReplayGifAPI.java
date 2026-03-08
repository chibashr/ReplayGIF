package me.replaygif.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface registered with ServicesManager for hard-dep callers.
 * Retrieve via {@code Bukkit.getServicesManager().load(ReplayGifAPI.class)}.
 */
public interface ReplayGifAPI {

    /**
     * Triggers a GIF render for the given player.
     *
     * @param subject            The online player whose buffer to render.
     *                           Must be online. If offline, call is ignored
     *                           and a WARN is logged.
     * @param eventLabel         Human-readable event description. Used in
     *                           embed and filename templates.
     * @param preSeconds         Seconds of buffer before trigger. Pass -1
     *                           to use configured default.
     * @param postSeconds        Seconds to capture after trigger. Pass -1
     *                           to use configured default.
     * @param outputProfileNames Profile names to dispatch to. Pass null
     *                           to use configured default.
     * @param metadata           Additional template variables. Pass null
     *                           for empty metadata.
     * @return The job UUID, for log correlation. Never null.
     *         Returns a random UUID even on early failure — the UUID will
     *         appear in the failure log line.
     */
    UUID trigger(
            Player subject,
            String eventLabel,
            double preSeconds,
            double postSeconds,
            List<String> outputProfileNames,
            Map<String, String> metadata
    );
}

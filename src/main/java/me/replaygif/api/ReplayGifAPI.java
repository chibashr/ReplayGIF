package me.replaygif.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public API for triggering ReplayGif renders from other plugins.
 * <p>
 * ReplayGif records a rolling buffer of world snapshots around each online player.
 * Calling {@link #trigger(Player, String, double, double, List, Map)} starts an asynchronous
 * job that: (1) waits {@code postSeconds} for post-trigger capture, (2) slices the buffer
 * from {@code triggerTime - preSeconds} to {@code triggerTime + postSeconds}, (3) renders
 * an isometric GIF, and (4) dispatches it to the configured output profiles (e.g. Discord
 * webhook, filesystem).
 * <p>
 * <b>Retrieval</b><br>
 * The implementation is registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}.
 * Use a hard dependency on ReplayGif and load the service:
 *
 * <pre>{@code
 * ServicesManager sm = Bukkit.getServicesManager();
 * RegisteredServiceProvider<ReplayGifAPI> rsp = sm.getRegistration(ReplayGifAPI.class);
 * if (rsp != null) {
 *     ReplayGifAPI api = rsp.getProvider();
 *     UUID jobId = api.trigger(player, "Custom Event", -1, -1, null, null);
 * }
 * }</pre>
 *
 * <p>
 * <b>Soft dependency (no compile-time dependency)</b><br>
 * Fire {@link ReplayGifTriggerEvent} instead; ReplayGif listens at MONITOR priority and
 * will run the same pipeline when {@code allow_api_triggers} is true in config.
 *
 * <p>
 * <b>Template variables</b><br>
 * {@code eventLabel} and {@code metadata} are available in output templates as
 * {@code {event}} and {@code {key}} for each metadata key. Use them for filenames,
 * Discord embed titles, and custom webhook payloads.
 */
public interface ReplayGifAPI {

    /**
     * Triggers an asynchronous GIF render for the given player using their snapshot buffer.
     * The call returns immediately with a job ID; rendering and dispatch run on a background
     * thread. Use the returned UUID to correlate log lines (e.g. "[jobId] status=DONE").
     *
     * @param subject            the player whose buffer to render; must be online and have
     *                           an active buffer (they have been online while the scheduler runs).
     *                           If null or offline, the call is ignored and a WARN is logged.
     * @param eventLabel         human-readable label for this trigger (e.g. "PvP Kill", "Custom Achievement").
     *                           Used in output templates as {@code {event}}. Never null in practice;
     *                           empty string is allowed.
     * @param preSeconds         seconds of buffer <i>before</i> the trigger moment to include in the GIF.
     *                           Use {@code -1} to use the configured default (see triggers config).
     * @param postSeconds        seconds <i>after</i> the trigger to wait before slicing (capture window).
     *                           Then the buffer is sliced and rendered. Use {@code -1} for configured default.
     * @param outputProfileNames names of output profiles to dispatch to (e.g. "discord", "filesystem").
     *                           Use {@code null} or empty to use the configured default profiles.
     * @param metadata           extra key-value pairs for template resolution (e.g. "killer" -> "Steve").
     *                           Available in templates as {@code {killer}}. Use {@code null} for none.
     * @return the job UUID for this render. Never null. Returned even when the trigger is ignored
     *         (e.g. null/offline subject) so you can correlate with the single WARN log line.
     *
     * @see ReplayGifTriggerEvent
     *
     * <p><b>Example: trigger with defaults</b></p>
     * <pre>{@code
     * ReplayGifAPI api = Bukkit.getServicesManager().getRegistration(ReplayGifAPI.class).getProvider();
     * UUID jobId = api.trigger(player, "Death", -1, -1, null, null);
     * }</pre>
     *
     * <p><b>Example: custom window and profile</b></p>
     * <pre>{@code
     * UUID jobId = api.trigger(player, "Boss Kill", 5.0, 3.0,
     *     List.of("discord-boss"), Map.of("boss", "Ender Dragon"));
     * }</pre>
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

package me.replaygif.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Bukkit event that triggers a ReplayGif render when fired.
 * <p>
 * Use this for a <b>soft dependency</b>: your plugin does not need to depend on ReplayGif
 * at compile time. Call {@link org.bukkit.Bukkit#getPluginManager()}{@code .callEvent(new ReplayGifTriggerEvent(...))}
 * and ReplayGif will handle it when {@code allow_api_triggers} is true.
 * <p>
 * ReplayGif listens at {@link org.bukkit.event.EventPriority#MONITOR} and does not cancel the event.
 * Defaults: pass {@code -1.0} for preSeconds/postSeconds to use configured defaults; pass {@code null}
 * for outputProfileNames/metadata for configured default profiles and empty metadata.
 */
public class ReplayGifTriggerEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player subject;
    private final String eventLabel;
    private final double preSeconds;
    private final double postSeconds;
    private final List<String> outputProfileNames;
    private final Map<String, String> metadata;

    /**
     * Creates a trigger event. After firing, ReplayGif will build a render job from these
     * values (applying config defaults where -1 or null is passed).
     *
     * @param subject            the player whose snapshot buffer to render; must be non-null
     * @param eventLabel         human-readable label for the trigger (e.g. "Arena Win"); used in templates as {@code {event}}
     * @param preSeconds         seconds of buffer before the trigger moment to include; -1.0 = use config default
     * @param postSeconds        seconds to wait after trigger before slicing; -1.0 = use config default
     * @param outputProfileNames output profile names to use; null = use config default list
     * @param metadata           extra template variables (e.g. "killer" -> "Steve"); null = empty
     */
    public ReplayGifTriggerEvent(
            @NotNull Player subject,
            @NotNull String eventLabel,
            double preSeconds,
            double postSeconds,
            @Nullable List<String> outputProfileNames,
            @Nullable Map<String, String> metadata) {
        super(false);
        this.subject = subject;
        this.eventLabel = eventLabel != null ? eventLabel : "";
        this.preSeconds = preSeconds;
        this.postSeconds = postSeconds;
        this.outputProfileNames = outputProfileNames;
        this.metadata = metadata;
    }

    /** The player whose buffer will be rendered. */
    @NotNull
    public Player getSubject() {
        return subject;
    }

    /** Human-readable event label; used in output templates as {@code {event}}. */
    @NotNull
    public String getEventLabel() {
        return eventLabel;
    }

    /** Seconds of buffer before trigger to include; -1.0 means use config default. */
    public double getPreSeconds() {
        return preSeconds;
    }

    /** Seconds after trigger to capture before rendering; -1.0 means use config default. */
    public double getPostSeconds() {
        return postSeconds;
    }

    /** Output profile names to dispatch to; null means use config default. */
    @Nullable
    public List<String> getOutputProfileNames() {
        return outputProfileNames;
    }

    /** Additional template variables; null means empty. */
    @Nullable
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** Required for Bukkit event system. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

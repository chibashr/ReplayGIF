package me.replaygif.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Custom Bukkit event for soft-dep callers. Any plugin can fire this without
 * a compile-time dependency on ReplayGif. ReplayGif listens at MONITOR priority.
 * <p>
 * Defaults: preSeconds/postSeconds -1.0 (use configured default), outputProfileNames
 * and metadata null (use configured default / empty map).
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
     * Creates the event. Pass -1.0 for preSeconds/postSeconds to use configured
     * defaults; pass null for outputProfileNames/metadata for default/empty.
     *
     * @param subject            the player whose buffer to render (never null)
     * @param eventLabel         human-readable event description (never null)
     * @param preSeconds         seconds of buffer before trigger, or -1.0 for default
     * @param postSeconds        seconds after trigger to capture, or -1.0 for default
     * @param outputProfileNames profile names to dispatch to, or null for default
     * @param metadata           additional template variables, or null for empty
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

    @NotNull
    public Player getSubject() {
        return subject;
    }

    @NotNull
    public String getEventLabel() {
        return eventLabel;
    }

    public double getPreSeconds() {
        return preSeconds;
    }

    public double getPostSeconds() {
        return postSeconds;
    }

    @Nullable
    public List<String> getOutputProfileNames() {
        return outputProfileNames;
    }

    @Nullable
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

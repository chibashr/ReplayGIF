package com.replayplugin.config;

import java.util.Collections;
import java.util.List;

/**
 * Per-trigger configuration: event class name, capture window, cooldowns, destinations.
 */
public class TriggerConfig {

    private final String event;
    private final boolean enabled;
    private final int preSeconds;
    private final int postSeconds;
    private final int radiusChunks;
    private final int captureRateTicks;
    private final int fps;
    private final int pixelsPerBlock;
    private final int cooldownPerPlayer;
    private final int cooldownGlobal;
    private final List<DestinationConfig> destinations;

    public TriggerConfig(
            String event,
            boolean enabled,
            int preSeconds,
            int postSeconds,
            int radiusChunks,
            int captureRateTicks,
            int fps,
            int pixelsPerBlock,
            int cooldownPerPlayer,
            int cooldownGlobal,
            List<DestinationConfig> destinations) {
        this.event = event;
        this.enabled = enabled;
        this.preSeconds = preSeconds;
        this.postSeconds = postSeconds;
        this.radiusChunks = radiusChunks;
        this.captureRateTicks = captureRateTicks;
        this.fps = fps;
        this.pixelsPerBlock = pixelsPerBlock;
        this.cooldownPerPlayer = cooldownPerPlayer;
        this.cooldownGlobal = cooldownGlobal;
        this.destinations = destinations == null ? Collections.emptyList() : List.copyOf(destinations);
    }

    public String getEvent() {
        return event;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPreSeconds() {
        return preSeconds;
    }

    public int getPostSeconds() {
        return postSeconds;
    }

    public int getRadiusChunks() {
        return radiusChunks;
    }

    public int getCaptureRateTicks() {
        return captureRateTicks;
    }

    public int getFps() {
        return fps;
    }

    public int getPixelsPerBlock() {
        return pixelsPerBlock;
    }

    public int getCooldownPerPlayer() {
        return cooldownPerPlayer;
    }

    public int getCooldownGlobal() {
        return cooldownGlobal;
    }

    public List<DestinationConfig> getDestinations() {
        return destinations;
    }
}

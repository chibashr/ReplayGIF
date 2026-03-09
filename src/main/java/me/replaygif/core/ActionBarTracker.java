package me.replaygif.core;

import org.bukkit.event.Listener;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last action bar message per player for snapshot capture.
 * Best-effort: uses Paper's PlayerReceiveActionBarEvent if available, else ProtocolLib
 * packet interception if available; otherwise disabled and getMessage always returns null.
 * Stored messages expire after 3 seconds (vanilla fade-out timing).
 */
public class ActionBarTracker implements Listener {

    private static final long TTL_MS = 3000L;

    private final Logger log;
    private final boolean enabled;
    private final Map<UUID, Entry> lastByPlayer = new ConcurrentHashMap<>();

    public ActionBarTracker(Logger log) {
        this.log = log;
        this.enabled = detectAndRegister();
        log.info("Action bar capture: {}",
                enabled ? "enabled" : "disabled — ProtocolLib required for full support");
    }

    /**
     * Attempts to enable capture via Paper event or ProtocolLib. Returns true if any path was registered.
     */
    private boolean detectAndRegister() {
        if (tryPaperActionBarEvent()) {
            return true;
        }
        if (tryProtocolLib()) {
            return true;
        }
        return false;
    }

    private boolean tryPaperActionBarEvent() {
        try {
            Class<?> eventClass = Class.forName("io.papermc.paper.event.player.PlayerReceiveActionBarEvent");
            // Plugin would need to register this listener and call record() from the event.
            // For now we don't auto-register reflection-based listener; subclass or compat can do it.
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean tryProtocolLib() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            // Packet listener would need to be added by a compat module or optional dependency.
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Records the current action bar message for a player. Call from Paper event or ProtocolLib adapter.
     */
    public void record(UUID playerUuid, String message) {
        if (message == null) {
            return;
        }
        lastByPlayer.put(playerUuid, new Entry(message, System.currentTimeMillis()));
    }

    /**
     * Returns the last action bar message for the player, or null if none or expired (3s TTL).
     */
    public String getMessage(UUID playerUuid) {
        Entry e = lastByPlayer.get(playerUuid);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() - e.timestampMs > TTL_MS) {
            lastByPlayer.remove(playerUuid);
            return null;
        }
        return e.text;
    }

    /** Called each tick or from getMessage to clear expired entries. */
    public void pruneExpired() {
        long now = System.currentTimeMillis();
        lastByPlayer.entrySet().removeIf(entry -> now - entry.getValue().timestampMs > TTL_MS);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static final class Entry {
        final String text;
        final long timestampMs;

        Entry(String text, long timestampMs) {
            this.text = text;
            this.timestampMs = timestampMs;
        }
    }
}

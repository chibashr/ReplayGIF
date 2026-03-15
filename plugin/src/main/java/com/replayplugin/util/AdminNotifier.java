package com.replayplugin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Notifies in-game admins (players with replay.admin permission).
 */
public final class AdminNotifier {

    private static final String PERMISSION = "replay.admin";

    /**
     * Broadcast a message to all online players with replay.admin permission.
     */
    public static void broadcast(String message) {
        if (message == null) return;
        try {
            java.util.Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players == null) return;
            for (Player p : players) {
                if (p.hasPermission(PERMISSION)) {
                    p.sendMessage("[ReplayPlugin] " + message);
                }
            }
        } catch (Throwable ignored) {
            /* Bukkit not initialized (e.g. unit test without MockBukkit) */
        }
    }

    /** @deprecated Use {@link #broadcast(String)} */
    @Deprecated
    public static void notifyAdmins(String message) {
        broadcast(message);
    }
}

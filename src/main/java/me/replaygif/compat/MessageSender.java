package me.replaygif.compat;

import org.bukkit.command.CommandSender;

/**
 * Sends a plain text message to a command sender. Version-specific implementations
 * use either {@code sendMessage(String)} (legacy) or {@code sendMessage(Component)} (preferred).
 */
public interface MessageSender {

    /**
     * Sends the given message to the sender.
     */
    void send(CommandSender sender, String message);
}

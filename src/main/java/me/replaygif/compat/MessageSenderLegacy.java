package me.replaygif.compat;

import org.bukkit.command.CommandSender;

/**
 * Sends messages using {@link CommandSender#sendMessage(String)}.
 * Used on servers &lt; 1.20. When compiled against a newer API, this may be deprecated.
 */
public final class MessageSenderLegacy implements MessageSender {

    @Override
    @SuppressWarnings("deprecation")
    public void send(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(message);
        }
    }
}

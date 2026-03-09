package me.replaygif.compat;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Sends messages using {@link CommandSender#sendMessage(net.kyori.adventure.text.Component)}.
 * Used on servers 1.20+ (preferred Adventure API).
 */
public final class MessageSenderModern implements MessageSender {

    @Override
    public void send(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(Component.text(message));
        }
    }
}

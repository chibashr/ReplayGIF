package com.replayplugin.command;

import com.replayplugin.ReplayPlugin;
import com.replayplugin.capture.CaptureBuffer;
import com.replayplugin.capture.RenderJob;
import com.replayplugin.config.PluginConfig;
import com.replayplugin.config.TriggerConfig;
import com.replayplugin.job.CurrentJobInfo;
import com.replayplugin.job.QueueStatus;
import com.replayplugin.job.RenderQueue;
import com.replayplugin.trigger.PlayerExtractionMap;
import com.replayplugin.trigger.TriggerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /replay subcommands: reload, trigger, queue, queue clear.
 */
public final class ReplayCommand implements CommandExecutor {

    private static final String PERM_ADMIN = "replay.admin";
    private static final String PERM_RELOAD = "replay.command.reload";
    private static final String PERM_TRIGGER = "replay.command.trigger";
    private static final String PERM_TRIGGER_TOGGLE = "replay.command.trigger.toggle";
    private static final String PERM_QUEUE = "replay.command.queue";
    private static final String PERM_QUEUE_CLEAR = "replay.command.queue.clear";

    private final ReplayPlugin plugin;

    public ReplayCommand(ReplayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "trigger":
                return handleTrigger(sender, args);
            case "queue":
                return handleQueue(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean hasPermission(CommandSender sender, String perm) {
        return sender.hasPermission(PERM_ADMIN) || sender.hasPermission(perm);
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, PERM_RELOAD)) {
            sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
            return true;
        }
        try {
            PluginConfig newConfig = PluginConfig.load(plugin);
            plugin.reloadConfigFrom(newConfig);
            TriggerRegistry registry = plugin.getTriggerRegistry();
            registry.disableAll();
            for (TriggerConfig tc : newConfig.getTriggers().values()) {
                registry.register(tc, plugin.getCaptureBuffer(), plugin.getRenderQueue());
            }
            sender.sendMessage("[ReplayPlugin] Config reloaded; triggers re-registered.");
        } catch (Exception e) {
            plugin.getLogger().warning("ReplayPlugin: Config reload failed: " + e.getMessage());
            sender.sendMessage("[ReplayPlugin] Reload failed; previous config retained.");
        }
        return true;
    }

    private boolean handleTrigger(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        if ("enable".equalsIgnoreCase(args[1])) {
            if (!hasPermission(sender, PERM_TRIGGER_TOGGLE)) {
                sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("[ReplayPlugin] Usage: /replay trigger enable <event>");
                return true;
            }
            String eventName = args[2];
            TriggerConfig tc = plugin.getPluginConfig().getTriggers().get(eventName);
            if (tc == null) {
                tc = findTriggerByEventClass(plugin.getPluginConfig(), eventName);
            }
            if (tc == null || !PlayerExtractionMap.isKnownEvent(tc.getEvent())) {
                sender.sendMessage("[ReplayPlugin] Unknown trigger or event: " + eventName);
                return true;
            }
            TriggerConfig enabledConfig = new TriggerConfig(tc.getEvent(), true, tc.getPreSeconds(), tc.getPostSeconds(),
                    tc.getRadiusChunks(), tc.getCaptureRateTicks(), tc.getFps(), tc.getPixelsPerBlock(),
                    tc.getCooldownPerPlayer(), tc.getCooldownGlobal(), tc.getDestinations());
            plugin.getTriggerRegistry().register(enabledConfig, plugin.getCaptureBuffer(), plugin.getRenderQueue());
            sender.sendMessage("[ReplayPlugin] Trigger enabled: " + eventName);
            return true;
        }
        if ("disable".equalsIgnoreCase(args[1])) {
            if (!hasPermission(sender, PERM_TRIGGER_TOGGLE)) {
                sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("[ReplayPlugin] Usage: /replay trigger disable <event>");
                return true;
            }
            String eventKey = args[2];
            TriggerConfig tcForDisable = plugin.getPluginConfig().getTriggers().get(eventKey);
            if (tcForDisable == null) tcForDisable = findTriggerByEventClass(plugin.getPluginConfig(), eventKey);
            String eventClassName = tcForDisable != null ? tcForDisable.getEvent() : eventKey;
            plugin.getTriggerRegistry().unregister(eventClassName);
            sender.sendMessage("[ReplayPlugin] Trigger disabled: " + eventKey);
            return true;
        }
        if (!hasPermission(sender, PERM_TRIGGER)) {
            sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("[ReplayPlugin] Usage: /replay trigger <player> <event>");
            return true;
        }
        String playerName = args[1];
        String eventType = args[2];
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            sender.sendMessage("[ReplayPlugin] Player not found or offline: " + playerName);
            return true;
        }
        TriggerConfig tc = plugin.getPluginConfig().getTriggers().get(eventType);
        if (tc == null) {
            tc = findTriggerByEventClass(plugin.getPluginConfig(), eventType);
        }
        if (tc == null || !PlayerExtractionMap.isKnownEvent(tc.getEvent())) {
            sender.sendMessage("[ReplayPlugin] Unknown trigger or event: " + eventType);
            return true;
        }
        RenderJob job = plugin.getCaptureBuffer().onTrigger(player, tc);
        if (job != null && plugin.getRenderQueue().enqueue(job)) {
            sender.sendMessage("[ReplayPlugin] Triggered replay for " + playerName + " (" + eventType + ").");
        } else {
            sender.sendMessage("[ReplayPlugin] Could not enqueue replay (queue full or no frames).");
        }
        return true;
    }

    private TriggerConfig findTriggerByEventClass(PluginConfig config, String eventClassName) {
        for (TriggerConfig tc : config.getTriggers().values()) {
            if (eventClassName.equals(tc.getEvent())) return tc;
        }
        return null;
    }

    private boolean handleQueue(CommandSender sender, String[] args) {
        if (args.length > 1 && "clear".equalsIgnoreCase(args[1])) {
            if (!hasPermission(sender, PERM_QUEUE_CLEAR)) {
                sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
                return true;
            }
            plugin.getRenderQueue().clear();
            sender.sendMessage("[ReplayPlugin] Queue cleared.");
            return true;
        }
        if (!hasPermission(sender, PERM_QUEUE)) {
            sender.sendMessage("[ReplayPlugin] You do not have permission to run this command.");
            return true;
        }
        QueueStatus status = plugin.getRenderQueue().getStatus();
        CurrentJobInfo current = status.getCurrentJob();
        int pending = status.getPendingCount();
        if (current == null && pending == 0) {
            sender.sendMessage("[ReplayPlugin] Queue empty");
            return true;
        }
        if (current != null) {
            long elapsed = (System.currentTimeMillis() - current.getStartTimeMillis()) / 1000;
            sender.sendMessage("[ReplayPlugin] Rendering: " + current.getPlayerName() + " / " + current.getEventType() + " (" + elapsed + "s elapsed) | " + pending + " pending");
        } else {
            sender.sendMessage("[ReplayPlugin] " + pending + " pending");
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("[ReplayPlugin] Usage: /replay reload | trigger <player> <event> | trigger enable|disable <event> | queue | queue clear");
    }
}

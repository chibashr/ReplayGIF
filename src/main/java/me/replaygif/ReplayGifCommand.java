package me.replaygif;

import me.replaygif.api.ReplayGifAPI;
import me.replaygif.compat.MessageSender;
import me.replaygif.trigger.RenderJob;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles /replaygif reload, status, test &lt;player&gt;.
 * Tab completion for subcommands and player names.
 */
public class ReplayGifCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "Usage: /replaygif <reload|status|test [player]|diag>";

    private final ReplayGifPlugin plugin;
    private final MessageSender messageSender;

    public ReplayGifCommand(ReplayGifPlugin plugin, MessageSender messageSender) {
        this.plugin = plugin;
        this.messageSender = messageSender;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            messageSender.send(sender, USAGE);
            return true;
        }
        String sub = args[0].equalsIgnoreCase("reload") ? "reload" :
                args[0].equalsIgnoreCase("status") ? "status" :
                        args[0].equalsIgnoreCase("test") ? "test" :
                                args[0].equalsIgnoreCase("diag") ? "diag" : "";
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "status":
                return handleStatus(sender);
            case "test":
                return handleTest(sender, args.length > 1 ? args[1] : null);
            case "diag":
                return handleDiag(sender);
            default:
                messageSender.send(sender, USAGE);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reload();
        messageSender.send(sender, "ReplayGif config reloaded. Buffers cleared and scheduler restarted.");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        var buffers = plugin.getSnapshotBuffers();
        var triggerHandler = plugin.getTriggerHandler();
        var scheduler = plugin.getSnapshotScheduler();
        var webhook = plugin.getWebhookInboundServer();

        List<String> lines = new ArrayList<>();
        lines.add("--- ReplayGif status ---");
        lines.add("Buffers: " + buffers.size() + " player(s)");
        buffers.forEach((uuid, buf) -> {
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            String display = name != null ? name : uuid.toString();
            lines.add("  " + display + ": " + buf.getCount() + "/" + buf.getCapacity() + " frames"
                    + (buf.isPaused() ? " (paused)" : ""));
        });

        Map<UUID, RenderJob> jobs = triggerHandler != null ? triggerHandler.getActiveJobs() : Map.of();
        lines.add("Active render jobs: " + jobs.size());
        jobs.forEach((jobId, job) -> lines.add("  " + jobId + " " + job.status
                + (job.failureReason != null ? " (" + job.failureReason + ")" : "")));

        boolean schedulerRunning = scheduler != null && !scheduler.isCancelled();
        lines.add("Scheduler: " + (schedulerRunning ? "running" : "stopped"));

        int port = webhook != null ? webhook.getPort() : -1;
        if (port >= 0) {
            lines.add("Webhook server: listening on port " + port);
        } else {
            lines.add("Webhook server: disabled");
        }
        lines.add("------------------------");

        for (String line : lines) {
            messageSender.send(sender, line);
        }
        return true;
    }

    private boolean handleTest(CommandSender sender, String playerName) {
        Player target;
        if (playerName == null || playerName.isEmpty()) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                messageSender.send(sender, "Specify a player: /replaygif test <player>");
                return true;
            }
        } else {
            target = plugin.getServer().getPlayerExact(playerName);
            if (target == null) {
                messageSender.send(sender, "Player '" + playerName + "' is not online.");
                return true;
            }
        }

        ReplayGifAPI api = plugin.getReplayGifAPIImpl();
        if (api == null) {
            messageSender.send(sender, "ReplayGif API not available.");
            return true;
        }
        UUID jobId = api.trigger(target, "test", -1, -1, null, null);
        messageSender.send(sender, "Render triggered for " + target.getName() + ". Job ID: " + jobId);
        return true;
    }

    private boolean handleDiag(CommandSender sender) {
        // Block colors
        var blockRegistry = plugin.getBlockRegistry();
        var blockColorMap = plugin.getBlockColorMap();
        if (blockRegistry != null && blockColorMap != null) {
            List<String> gaps = blockColorMap.getMaterialsWithDefaultGray(blockRegistry);
            Collections.sort(gaps);
            String line = "Block colors: " + gaps.size() + " material(s) with no entry (fallback #808080).";
            plugin.getSLF4JLogger().info("[ReplayGif diag] {}", line);
            messageSender.send(sender, line);
            if (!gaps.isEmpty()) {
                for (String name : gaps) {
                    plugin.getSLF4JLogger().info("[ReplayGif diag]   {}", name);
                }
                messageSender.send(sender, "Gap list (add to block_colors_defaults.json): " + String.join(", ", gaps));
            } else {
                messageSender.send(sender, "No gaps — all block materials have a color entry.");
            }
        } else {
            messageSender.send(sender, "BlockColorMap not available.");
        }

        // Entity sprites: living entities with no sprite and no marker color (gray fallback)
        var entitySpriteRegistry = plugin.getEntitySpriteRegistry();
        if (entitySpriteRegistry != null) {
            List<String> entityGaps = entitySpriteRegistry.getLivingEntityTypesWithGrayFallback();
            Collections.sort(entityGaps);
            String entityLine = "Entity sprites: " + entityGaps.size() + " living entity type(s) with no sprite and no marker color (fallback gray).";
            plugin.getSLF4JLogger().info("[ReplayGif diag] {}", entityLine);
            messageSender.send(sender, entityLine);
            if (!entityGaps.isEmpty()) {
                for (String name : entityGaps) {
                    plugin.getSLF4JLogger().info("[ReplayGif diag]   {}", name);
                }
                messageSender.send(sender, "Entity gap list: add bundled sprite (entity_sprites_default/<name>.png) for hostiles, or color in entity_bounds.json for passive/neutral.");
            } else {
                messageSender.send(sender, "No entity gaps — all living entity types have a sprite or marker color.");
            }
        } else {
            messageSender.send(sender, "EntitySpriteRegistry not available.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String opt : new String[]{"reload", "status", "test", "diag"}) {
                if (opt.startsWith(partial)) {
                    out.add(opt);
                }
            }
            return out;
        }
        if (args.length == 2 && "test".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

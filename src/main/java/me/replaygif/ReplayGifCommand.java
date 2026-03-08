package me.replaygif;

import me.replaygif.core.SnapshotBuffer;
import me.replaygif.core.WorldSnapshot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles /replaygif reload, status, test.
 * test <player> prints buffer stats and WorldSnapshot verification to the console.
 */
public class ReplayGifCommand implements CommandExecutor {

    private final ReplayGifPlugin plugin;

    public ReplayGifCommand(ReplayGifPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /replaygif <reload|status|test [player]>");
            return true;
        }
        switch (args[0].equalsIgnoreCase("reload") ? "reload" :
                args[0].equalsIgnoreCase("status") ? "status" :
                args[0].equalsIgnoreCase("test") ? "test" : "") {
            case "reload":
                return handleReload(sender);
            case "status":
                return handleStatus(sender);
            case "test":
                return handleTest(sender, args.length > 1 ? args[1] : null);
            default:
                sender.sendMessage("Usage: /replaygif <reload|status|test [player]>");
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        sender.sendMessage("ReplayGif reload not fully implemented yet.");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        var buffers = plugin.getSnapshotBuffers();
        if (buffers.isEmpty()) {
            sender.sendMessage("ReplayGif: No players with buffers (no one online).");
            return true;
        }
        StringBuilder sb = new StringBuilder("ReplayGif buffers: ");
        buffers.forEach((uuid, buf) -> {
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            sb.append(name != null ? name : uuid.toString()).append("=")
                    .append(buf.getCount()).append("/").append(buf.getCapacity())
                    .append(buf.isPaused() ? "(paused)" : "").append(" ");
        });
        sender.sendMessage(sb.toString().trim());
        return true;
    }

    private boolean handleTest(CommandSender sender, String playerName) {
        Player target;
        if (playerName == null || playerName.isEmpty()) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("Specify a player: /replaygif test <player>");
                return true;
            }
        } else {
            target = plugin.getServer().getPlayerExact(playerName);
            if (target == null) {
                sender.sendMessage("Player '" + playerName + "' is not online.");
                return true;
            }
        }

        SnapshotBuffer buffer = plugin.getSnapshotBuffers().get(target.getUniqueId());
        if (buffer == null) {
            sender.sendMessage("No buffer for " + target.getName() + " (join/quit race?).");
            return true;
        }

        int count = buffer.getCount();
        int capacity = buffer.getCapacity();
        sender.sendMessage("ReplayGif buffer for " + target.getName() + ": " + count + "/" + capacity
                + " frames" + (buffer.isPaused() ? " (paused)" : "") + ". See console for verification.");

        // Log full verification to console (TH1: 6s at 10fps = 60 frames)
        var logger = plugin.getSLF4JLogger();
        logger.info("--- ReplayGif buffer verification for {} ---", target.getName());
        logger.info("  Frame count: {} (expected 60 for 6s at 10fps)", count);
        logger.info("  Capacity: {}", capacity);
        logger.info("  Paused: {}", buffer.isPaused());

        WorldSnapshot latest = buffer.getLatestSnapshot();
        if (latest == null) {
            logger.info("  Latest snapshot: none (buffer empty)");
            return true;
        }

        // Verify origin matches player position (at capture time we use block position)
        int playerBlockX = target.getLocation().getBlockX();
        int playerBlockY = target.getLocation().getBlockY();
        int playerBlockZ = target.getLocation().getBlockZ();
        logger.info("  Latest snapshot origin: ({}, {}, {}) (current player block: {}, {}, {})",
                latest.originX, latest.originY, latest.originZ,
                playerBlockX, playerBlockY, playerBlockZ);

        int nonAirBlocks = 0;
        for (short ordinal : latest.blocks) {
            if (ordinal != 0) {
                nonAirBlocks++;
            }
        }
        logger.info("  Blocks: {} non-AIR in volume (volumeSize^3 = {})",
                nonAirBlocks, latest.blocks.length);

        logger.info("  Entities in volume: {}", latest.entities.size());
        latest.entities.forEach(e -> logger.info("    - {} at rel ({}, {}, {})",
                e.type, String.format("%.2f", e.relX), String.format("%.2f", e.relY), String.format("%.2f", e.relZ)));

        logger.info("  Player state: health={}, food={}, dimension={}, worldName={}, inSpectator={}",
                latest.playerHealth, latest.playerFood, latest.dimension, latest.worldName, latest.inSpectator);

        // Sanity checks for pipeline (TH1/TH3)
        boolean framesOk = count > 0;
        boolean originOk = latest.originX == playerBlockX && latest.originY == playerBlockY && latest.originZ == playerBlockZ
                || Math.abs(latest.originX - playerBlockX) <= 1 && Math.abs(latest.originY - playerBlockY) <= 1 && Math.abs(latest.originZ - playerBlockZ) <= 1;
        boolean blocksOk = latest.blocks != null && latest.blocks.length == latest.volumeSize * latest.volumeSize * latest.volumeSize;
        logger.info("  Verification: framesPresent={}, originNearPlayer={}, blocksArrayValid={}, nonZeroBlocksNearSpawn={}",
                framesOk, originOk, blocksOk, nonAirBlocks > 0 ? "yes" : "no (spawn may be void)");
        logger.info("--- End verification ---");

        return true;
    }
}

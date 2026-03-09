package me.replaygif.core;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player block break progress for WorldSnapshot capture.
 * Listens to BlockDamageEvent (record/update state), BlockBreakEvent and
 * BlockDamageAbortEvent (clear), and LEFT_CLICK_AIR (clear).
 * Stage 0–9 is approximated from elapsed time, block hardness, and tool.
 */
public class BlockBreakTracker implements Listener {

    private static final long TICKS_PER_MS = 50;

    /** Per-player: (blockX, blockY, blockZ, stage, startTimeMs). */
    private final Map<UUID, BreakingEntry> stateByPlayer = new ConcurrentHashMap<>();

    /**
     * Returns the current break state for the player, or empty if not breaking.
     * Called from SnapshotScheduler on the main thread.
     */
    public Optional<BlockBreakState> getState(UUID playerUuid) {
        BreakingEntry e = stateByPlayer.get(playerUuid);
        if (e == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockBreakState(e.blockX, e.blockY, e.blockZ, e.stage));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        UUID uuid = player.getUniqueId();
        BreakingEntry current = stateByPlayer.get(uuid);

        if (event.getInstaBreak()) {
            stateByPlayer.put(uuid, new BreakingEntry(bx, by, bz, 9, System.currentTimeMillis()));
            return;
        }

        long now = System.currentTimeMillis();
        if (current != null && current.blockX == bx && current.blockY == by && current.blockZ == bz) {
            int stage = computeStage(block.getType(), now - current.startTimeMs, player.getItemInHand());
            stateByPlayer.put(uuid, new BreakingEntry(bx, by, bz, stage, current.startTimeMs));
        } else {
            stateByPlayer.put(uuid, new BreakingEntry(bx, by, bz, 0, now));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        stateByPlayer.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeftClickAir(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR) {
            stateByPlayer.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamageAbort(org.bukkit.event.block.BlockDamageAbortEvent event) {
        stateByPlayer.remove(event.getPlayer().getUniqueId());
    }

    private static int computeStage(Material blockType, long elapsedMs, ItemStack tool) {
        float hardness = blockType.getHardness();
        if (hardness < 0 || Float.isInfinite(hardness)) {
            return 0;
        }
        if (hardness == 0) {
            return 9;
        }
        long ticks = elapsedMs / TICKS_PER_MS;
        double multiplier = toolMultiplier(blockType, tool);
        double progress = (ticks * multiplier) / hardness;
        int stage = (int) (progress * 9);
        if (stage > 9) {
            stage = 9;
        }
        return stage;
    }

    /** Correct tool ≈ 3.0, incorrect ≈ 1.0. */
    private static double toolMultiplier(Material blockType, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0.5;
        }
        Material tool = item.getType();
        String blockName = blockType.name();
        if (tool.name().contains("PICKAXE") && (blockName.contains("STONE") || blockName.contains("ORE") || blockName.contains("COBBLE") || blockName.contains("IRON") || blockName.contains("GOLD") || blockName.contains("DIAMOND") || blockName.contains("ANCIENT") || blockName.contains("DEEPSLATE") || blockName.contains("COAL") || blockName.contains("BRICK") || blockName.contains("CONCRETE") || blockName.contains("TERRACOTTA"))) {
            return 3.0;
        }
        if (tool.name().contains("AXE") && (blockName.contains("LOG") || blockName.contains("WOOD") || blockName.contains("PLANK") || blockName.contains("CRIMSON") || blockName.contains("WARPED"))) {
            return 3.0;
        }
        if (tool.name().contains("SHOVEL") && (blockName.contains("DIRT") || blockName.contains("SAND") || blockName.contains("GRAVEL") || blockName.contains("GRASS") || blockName.contains("SOUL"))) {
            return 3.0;
        }
        return 1.0;
    }

    private static final class BreakingEntry {
        final int blockX, blockY, blockZ;
        final int stage;
        final long startTimeMs;

        BreakingEntry(int blockX, int blockY, int blockZ, int stage, long startTimeMs) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.stage = stage;
            this.startTimeMs = startTimeMs;
        }
    }
}

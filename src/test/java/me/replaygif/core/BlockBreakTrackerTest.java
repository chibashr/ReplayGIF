package me.replaygif.core;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BlockBreakTracker covering BB1 from .planning/testing.md.
 */
class BlockBreakTrackerTest {

    private BlockBreakTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BlockBreakTracker();
    }

    /** BB1 — BlockBreakTracker: simulate BlockDamageEvent for a stone block with iron pickaxe after 5 ticks. Verify stage > 0. */
    @Test
    void bb1_blockDamageEvent_stoneWithIronPickaxe_afterTicks_stageGreaterThanZero() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getItemInHand()).thenReturn(new ItemStack(Material.IRON_PICKAXE));

        Block block = mock(Block.class);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(20);
        when(block.getType()).thenReturn(Material.STONE);

        BlockDamageEvent event = mock(BlockDamageEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);
        when(event.getInstaBreak()).thenReturn(false);

        tracker.onBlockDamage(event);
        Optional<BlockBreakState> state0 = tracker.getState(playerId);
        assertTrue(state0.isPresent());
        assertEquals(10, state0.get().blockX());
        assertEquals(64, state0.get().blockY());
        assertEquals(20, state0.get().blockZ());
        assertEquals(0, state0.get().stage());

        // Simulate ~5 ticks (250ms) passing: fire again for same block so elapsed time is used
        try {
            Thread.sleep(260);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }
        tracker.onBlockDamage(event);
        Optional<BlockBreakState> state1 = tracker.getState(playerId);
        assertTrue(state1.isPresent());
        assertTrue(state1.get().stage() > 0, "stage should be > 0 after ~5 ticks of damage");
    }
}

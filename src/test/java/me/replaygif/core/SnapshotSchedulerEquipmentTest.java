package me.replaygif.core;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EG2 — Capture: player holds enchanted diamond sword → mainHandItem = "DIAMOND_SWORD:enchanted".
 * Full capture tests are @Disabled: captureSnapshot() uses Bukkit.getBossBars() which NPEs without a server.
 * Serialize behavior is covered by ItemSerializerTest.eg2_*.
 */
class SnapshotSchedulerEquipmentTest {

    private static final int VOLUME_SIZE = 32;

    private SnapshotScheduler scheduler;

    @BeforeEach
    void setUp() {
        BlockRegistry blockRegistry = new BlockRegistry();
        JavaPlugin plugin = mock(JavaPlugin.class);
        scheduler = new SnapshotScheduler(
                plugin,
                java.util.Map.of(),
                mock(me.replaygif.config.ConfigManager.class),
                blockRegistry,
                mock(me.replaygif.compat.EntityCustomNameResolver.class),
                null,
                null,
                null);
    }

    @Test
    @Disabled("Requires Bukkit server; Bukkit.getBossBars() NPEs in unit test")
    void eg2_enchantedDiamondSword_mainHandItemHasEnchantedSuffix() throws Exception {
        World world = mockWorld();
        Location loc = mockLocation(world, 0, 64, 0);
        ItemStack enchantedSword = new ItemStack(Material.DIAMOND_SWORD);
        enchantedSword.addUnsafeEnchantment(Enchantment.DURABILITY, 1);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(enchantedSword);
        when(inv.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(inv.getHelmet()).thenReturn(null);
        when(inv.getChestplate()).thenReturn(null);
        when(inv.getLeggings()).thenReturn(null);
        when(inv.getBoots()).thenReturn(null);

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getInventory()).thenReturn(inv);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        Method capture = SnapshotScheduler.class.getDeclaredMethod("captureSnapshot", Player.class, int.class);
        capture.setAccessible(true);
        WorldSnapshot snapshot = (WorldSnapshot) capture.invoke(scheduler, player, VOLUME_SIZE);

        assertNotNull(snapshot);
        assertEquals("DIAMOND_SWORD:enchanted", snapshot.mainHandItem);
    }

    @Test
    @Disabled("Requires Bukkit server; Bukkit.getBossBars() NPEs in unit test")
    void eg2_nonEnchantedSword_mainHandItemNoSuffix() throws Exception {
        World world = mockWorld();
        Location loc = mockLocation(world, 0, 64, 0);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(sword);
        when(inv.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(inv.getHelmet()).thenReturn(null);
        when(inv.getChestplate()).thenReturn(null);
        when(inv.getLeggings()).thenReturn(null);
        when(inv.getBoots()).thenReturn(null);

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getInventory()).thenReturn(inv);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        Method capture = SnapshotScheduler.class.getDeclaredMethod("captureSnapshot", Player.class, int.class);
        capture.setAccessible(true);
        WorldSnapshot snapshot = (WorldSnapshot) capture.invoke(scheduler, player, VOLUME_SIZE);

        assertNotNull(snapshot);
        assertEquals("DIAMOND_SWORD", snapshot.mainHandItem);
    }

    private World mockWorld() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getName()).thenReturn("world");
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getNearbyEntities(any())).thenReturn(Collections.emptyList());
        return world;
    }

    private static Location mockLocation(World world, int blockX, int blockY, int blockZ) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(blockX);
        when(loc.getBlockY()).thenReturn(blockY);
        when(loc.getBlockZ()).thenReturn(blockZ);
        when(loc.getYaw()).thenReturn(0f);
        when(loc.getPitch()).thenReturn(0f);
        return loc;
    }
}

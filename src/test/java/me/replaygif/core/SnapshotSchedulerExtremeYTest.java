package me.replaygif.core;

import me.replaygif.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies SnapshotScheduler handles 1.18+ world height (-64 to 320) correctly:
 * a 32³ volume centered on the player can extend below/above world bounds;
 * any Y &lt; -64 or Y &gt;= 320 must be stored as AIR ordinal (0) without exception.
 *
 * See .planning/decisions.md (1.18 minimum version).
 */
@Execution(ExecutionMode.SAME_THREAD)
class SnapshotSchedulerExtremeYTest {

    private static final int MIN_WORLD_Y = -64;
    private static final int MAX_WORLD_Y = 320; // exclusive upper bound
    private static final int VOLUME_SIZE = 32;

    private BlockRegistry blockRegistry;
    private SnapshotScheduler scheduler;
    private JavaPlugin plugin;
    private ConfigManager configManager;
    private me.replaygif.compat.EntityCustomNameResolver customNameResolver;

    @BeforeEach
    void setUp() {
        blockRegistry = new BlockRegistry();
        plugin = mock(JavaPlugin.class);
        configManager = mock(ConfigManager.class);
        customNameResolver = mock(me.replaygif.compat.EntityCustomNameResolver.class);
        scheduler = new SnapshotScheduler(
                plugin,
                java.util.Map.of(),
                configManager,
                blockRegistry,
                customNameResolver,
                null,
                null,
                null);
    }

    /**
     * Capture snapshot for a player at the given block position. Uses reflection to call
     * private captureSnapshot(Player, int). World is mocked with 1.18 overworld height.
     */
    private WorldSnapshot captureAt(int originX, int originY, int originZ) throws Exception {
        World world = mockWorld();
        Location loc = mockLocation(world, originX, originY, originZ);
        Player player = mockPlayer(loc);

        Method capture = SnapshotScheduler.class.getDeclaredMethod("captureSnapshot", Player.class, int.class);
        capture.setAccessible(true);
        return (WorldSnapshot) capture.invoke(scheduler, player, VOLUME_SIZE);
    }

    private World mockWorld() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(MIN_WORLD_Y);
        when(world.getMaxHeight()).thenReturn(MAX_WORLD_Y);
        when(world.getEnvironment()).thenReturn(org.bukkit.World.Environment.NORMAL);
        when(world.getName()).thenReturn("world");

        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

        when(world.getNearbyEntities(any(org.bukkit.util.BoundingBox.class))).thenReturn(List.of());
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

    private static Player mockPlayer(Location loc) {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getExp()).thenReturn(0f);
        when(player.getLevel()).thenReturn(0);
        when(player.getActivePotionEffects()).thenReturn(List.of());
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack air = new ItemStack(Material.AIR);
        ItemStack[] storage = new ItemStack[36];
        java.util.Arrays.fill(storage, air);
        when(inv.getStorageContents()).thenReturn(storage);
        when(inv.getHeldItemSlot()).thenReturn(4);
        when(inv.getItemInMainHand()).thenReturn(air);
        when(inv.getItemInOffHand()).thenReturn(air);
        when(inv.getHelmet()).thenReturn(air);
        when(inv.getChestplate()).thenReturn(air);
        when(inv.getLeggings()).thenReturn(air);
        when(inv.getBoots()).thenReturn(air);
        when(player.getInventory()).thenReturn(inv);
        return player;
    }

    /** Volume index = dx*vol² + dy*vol + dz; world Y = minY + dy with minY = originY - volumeSize/2. */
    private static int worldYAt(int originY, int volumeSize, int index) {
        int vol2 = volumeSize * volumeSize;
        int dx = index / vol2;
        int remainder = index % vol2;
        int dy = remainder / volumeSize;
        int minY = originY - volumeSize / 2;
        return minY + dy;
    }

    private static void assertOutOfBoundsSlotsAreAir(WorldSnapshot snapshot, int originY) {
        assertNotNull(snapshot);
        short[] blocks = snapshot.blocks;
        int vol = snapshot.volumeSize;
        assertEquals(VOLUME_SIZE, vol);

        for (int i = 0; i < blocks.length; i++) {
            int wy = worldYAt(originY, vol, i);
            if (wy < MIN_WORLD_Y || wy >= MAX_WORLD_Y) {
                assertEquals(0, blocks[i], "Index " + i + " (world Y=" + wy + ") must be AIR ordinal 0");
            }
        }
    }

    @Test
    void captureAtY_min64_bedrockLevel_producesSnapshotWithOutOfBoundsAsAir() throws Exception {
        int originY = -64;
        // Volume Y: -80 to -49. Slots with wy < -64 (i.e. -80..-65) must be AIR.
        WorldSnapshot snapshot = captureAt(0, originY, 0);
        assertNotNull(snapshot);
        assertEquals(originY, snapshot.originY);
        assertOutOfBoundsSlotsAreAir(snapshot, originY);
    }

    @Test
    void captureAtY_min60_playerNearBottom_volumeExtendsBelowWorld_producesSnapshotWithOutOfBoundsAsAir() throws Exception {
        int originY = -60;
        // Volume Y: -76 to -29. wy -76..-65 are below -64 → AIR.
        WorldSnapshot snapshot = captureAt(0, originY, 0);
        assertNotNull(snapshot);
        assertEquals(originY, snapshot.originY);
        assertOutOfBoundsSlotsAreAir(snapshot, originY);
    }

    @Test
    void captureAtY_310_playerNearBuildLimit_volumeExtendsAbove_producesSnapshotWithOutOfBoundsAsAir() throws Exception {
        int originY = 310;
        // Volume Y: 294 to 325. wy 320..325 are >= 320 → AIR.
        WorldSnapshot snapshot = captureAt(0, originY, 0);
        assertNotNull(snapshot);
        assertEquals(originY, snapshot.originY);
        assertOutOfBoundsSlotsAreAir(snapshot, originY);
    }

    @Test
    void captureAtY_0_normalOverworld_bothLimitsSafe_producesSnapshotNoException() throws Exception {
        int originY = 0;
        // Volume Y: -16 to 15; all within -64..320.
        WorldSnapshot snapshot = captureAt(0, originY, 0);
        assertNotNull(snapshot);
        assertEquals(originY, snapshot.originY);
        assertOutOfBoundsSlotsAreAir(snapshot, originY);
        // At Y=0 there are no out-of-bounds slots; assertion still passes (no OOB indices).
    }
}

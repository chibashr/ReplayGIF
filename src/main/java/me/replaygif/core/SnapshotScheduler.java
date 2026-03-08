package me.replaygif.core;

import me.replaygif.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single repeating task that drives all per-player capture so we don't spawn one runnable per player.
 * Runs on the main thread at config FPS; each tick iterates online players and writes one WorldSnapshot
 * per buffer. Spectator logic (capture for N seconds then pause) keeps memory bounded while still
 * allowing a short spectator replay window.
 */
public class SnapshotScheduler extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final Map<UUID, SnapshotBuffer> buffers;
    private final ConfigManager configManager;
    private final BlockRegistry blockRegistry;

    /**
     * @param plugin         for runTaskTimer and getServer().getOnlinePlayers()
     * @param buffers        shared map of player UUID → SnapshotBuffer (scheduler only writes)
     * @param configManager  fps, volume_size, spectator_capture_seconds
     * @param blockRegistry  to encode block types as ordinals in the snapshot
     */
    public SnapshotScheduler(
            JavaPlugin plugin,
            Map<UUID, SnapshotBuffer> buffers,
            ConfigManager configManager,
            BlockRegistry blockRegistry) {
        this.plugin = plugin;
        this.buffers = buffers;
        this.configManager = configManager;
        this.blockRegistry = blockRegistry;
    }

    /**
     * Schedules the capture loop. Interval is 20/fps ticks (min 1) so we don't run faster than the server tick.
     * Call once after buffers and config are ready.
     */
    public void start() {
        int fps = configManager.getFps();
        int intervalTicks = Math.max(1, 20 / fps);
        runTaskTimer(plugin, 0L, intervalTicks);
    }

    @Override
    public void run() {
        int volumeSize = configManager.getVolumeSize();
        int spectatorCaptureSeconds = configManager.getSpectatorCaptureSeconds();
        long nowMs = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            SnapshotBuffer buffer = buffers.get(player.getUniqueId());
            if (buffer == null) {
                continue;
            }

            boolean inSpectator = player.getGameMode() == GameMode.SPECTATOR;

            // Update spectator pause state per data-structures.md
            if (inSpectator) {
                if (buffer.getSpectatorEntryTime() == -1L) {
                    buffer.setSpectatorEntryTime(nowMs);
                }
                if (spectatorCaptureSeconds >= 0) {
                    long elapsedMs = nowMs - buffer.getSpectatorEntryTime();
                    if (elapsedMs > spectatorCaptureSeconds * 1000L) {
                        buffer.pause();
                    }
                }
            } else {
                if (buffer.getSpectatorEntryTime() != -1L) {
                    buffer.resume();
                    buffer.setSpectatorEntryTime(-1L);
                }
            }

            if (buffer.isPaused()) {
                continue;
            }

            WorldSnapshot snapshot = captureSnapshot(player, volumeSize);
            if (snapshot != null) {
                buffer.write(snapshot);
            }
        }
    }

    /**
     * Captures one WorldSnapshot for the player. Returns null if world or location is invalid.
     */
    private WorldSnapshot captureSnapshot(Player player, int volumeSize) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }

        int originX = loc.getBlockX();
        int originY = loc.getBlockY();
        int originZ = loc.getBlockZ();

        long timestamp = System.currentTimeMillis();
        float playerYaw = loc.getYaw();
        float playerPitch = loc.getPitch();
        float playerHealth = (float) player.getHealth();
        int playerFood = player.getFoodLevel();
        String dimension = dimensionKey(world);
        String worldName = world.getName();
        boolean inSpectator = player.getGameMode() == GameMode.SPECTATOR;

        short[] blocks = captureBlocks(world, originX, originY, originZ, volumeSize);
        List<EntitySnapshot> entities = captureEntities(world, originX, originY, originZ, volumeSize);

        return new WorldSnapshot(
                timestamp,
                originX,
                originY,
                originZ,
                playerYaw,
                playerPitch,
                playerHealth,
                playerFood,
                dimension,
                worldName,
                blocks,
                volumeSize,
                entities,
                inSpectator);
    }

    private static String dimensionKey(World world) {
        switch (world.getEnvironment()) {
            case NORMAL:
                return "minecraft:overworld";
            case NETHER:
                return "minecraft:the_nether";
            case THE_END:
                return "minecraft:the_end";
            default:
                return "minecraft:overworld";
        }
    }

    private short[] captureBlocks(World world, int originX, int originY, int originZ, int volumeSize) {
        int minX = originX - volumeSize / 2;
        int minY = originY - volumeSize / 2;
        int minZ = originZ - volumeSize / 2;

        int minWorldY = world.getMinHeight();
        int maxWorldY = world.getMaxHeight();

        short[] blocks = new short[volumeSize * volumeSize * volumeSize];

        for (int dx = 0; dx < volumeSize; dx++) {
            for (int dy = 0; dy < volumeSize; dy++) {
                for (int dz = 0; dz < volumeSize; dz++) {
                    int wx = minX + dx;
                    int wy = minY + dy;
                    int wz = minZ + dz;

                    short ordinal;
                    if (wy < minWorldY || wy >= maxWorldY) {
                        ordinal = 0; // AIR
                    } else {
                        Material type = world.getBlockAt(wx, wy, wz).getType();
                        ordinal = blockRegistry.getOrdinal(type);
                    }

                    int index = dx * volumeSize * volumeSize + dy * volumeSize + dz;
                    blocks[index] = ordinal;
                }
            }
        }
        return blocks;
    }

    private List<EntitySnapshot> captureEntities(World world, int originX, int originY, int originZ, int volumeSize) {
        double half = volumeSize / 2.0;
        double minX = originX - half;
        double minY = originY - half;
        double minZ = originZ - half;
        double maxX = originX + half;
        double maxY = originY + half;
        double maxZ = originZ + half;

        BoundingBox box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        Collection<Entity> nearby = world.getNearbyEntities(box);

        List<EntitySnapshot> result = new ArrayList<>();
        for (Entity entity : nearby) {
            if (entity.isDead()) {
                continue;
            }
            Location eloc = entity.getLocation();
            double ex = eloc.getX();
            double ey = eloc.getY();
            double ez = eloc.getZ();
            // Filter: entity position inside volume (same bounds as block volume in world coords)
            int volMinX = originX - volumeSize / 2;
            int volMinY = originY - volumeSize / 2;
            int volMinZ = originZ - volumeSize / 2;
            if (ex < volMinX || ex >= volMinX + volumeSize ||
                    ey < volMinY || ey >= volMinY + volumeSize ||
                    ez < volMinZ || ez >= volMinZ + volumeSize) {
                continue;
            }

            double relX = ex - originX;
            double relY = ey - originY;
            double relZ = ez - originZ;
            float yaw = eloc.getYaw();
            UUID uuid = entity.getUniqueId();
            boolean isPlayer = entity instanceof Player;
            boolean onFire = entity.getFireTicks() > 0;
            boolean invisible = entity instanceof LivingEntity && ((LivingEntity) entity).isInvisible();
            String customName = entity instanceof org.bukkit.Nameable
                    ? ((org.bukkit.Nameable) entity).getCustomName()
                    : null;
            if (customName != null && customName.isEmpty()) {
                customName = null;
            }

            BoundingBox bbox = entity.getBoundingBox();
            double wX = bbox.getWidthX();
            double wZ = bbox.getWidthZ();
            double h = bbox.getHeight();
            double boundingWidth = Math.max(Math.max(wX, wZ), 1e-6);
            double boundingHeight = Math.max(h, 1e-6);

            result.add(new EntitySnapshot(
                    entity.getType(),
                    relX,
                    relY,
                    relZ,
                    yaw,
                    uuid,
                    isPlayer,
                    onFire,
                    invisible,
                    customName,
                    boundingWidth,
                    boundingHeight));
        }
        return result;
    }
}

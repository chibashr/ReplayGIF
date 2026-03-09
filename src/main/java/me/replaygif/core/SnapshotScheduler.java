package me.replaygif.core;

import me.replaygif.compat.AdventureTextUtil;
import me.replaygif.compat.EntityCustomNameResolver;
import me.replaygif.compat.RepeatingTaskHandle;
import me.replaygif.compat.SchedulerCompat;
import me.replaygif.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single repeating task that drives all per-player capture so we don't spawn one runnable per player.
 * Runs on the main thread at config FPS; each tick iterates online players and writes one WorldSnapshot
 * per buffer. Spectator logic (capture for N seconds then pause) keeps memory bounded while still
 * allowing a short spectator replay window.
 */
public class SnapshotScheduler implements Runnable {

    private static final int BREAKING_SENTINEL = -999999;
    private static final int BREAKING_STAGE_NONE = -1;

    private final JavaPlugin plugin;
    private final Map<UUID, SnapshotBuffer> buffers;
    private final ConfigManager configManager;
    private final BlockRegistry blockRegistry;
    private final EntityCustomNameResolver customNameResolver;
    private final BlockBreakTracker blockBreakTracker;
    private final ActionBarTracker actionBarTracker;
    private final CombatEventTracker combatEventTracker;
    private volatile RepeatingTaskHandle taskHandle;

    /**
     * @param plugin             for scheduling and getServer().getOnlinePlayers()
     * @param buffers            shared map of player UUID → SnapshotBuffer (scheduler only writes)
     * @param configManager      fps, volume_size, spectator_capture_seconds
     * @param blockRegistry      to encode block types as ordinals in the snapshot
     * @param customNameResolver version-appropriate resolver for entity custom names (Nameable)
     * @param blockBreakTracker  per-player block break state; may be null to use sentinel values
     * @param actionBarTracker   per-player action bar text; may be null (then actionBarText always null)
     * @param combatEventTracker attack records per tick; may be null (then attacksThisFrame always empty)
     */
    public SnapshotScheduler(
            JavaPlugin plugin,
            Map<UUID, SnapshotBuffer> buffers,
            ConfigManager configManager,
            BlockRegistry blockRegistry,
            EntityCustomNameResolver customNameResolver,
            BlockBreakTracker blockBreakTracker,
            ActionBarTracker actionBarTracker,
            CombatEventTracker combatEventTracker) {
        this.plugin = plugin;
        this.buffers = buffers;
        this.configManager = configManager;
        this.blockRegistry = blockRegistry;
        this.customNameResolver = customNameResolver;
        this.blockBreakTracker = blockBreakTracker;
        this.actionBarTracker = actionBarTracker;
        this.combatEventTracker = combatEventTracker;
    }

    /**
     * Schedules the capture loop. Interval is 20/fps ticks (min 1) so we don't run faster than the server tick.
     * Call once after buffers and config are ready. Uses Paper GlobalRegionScheduler on 1.20+, Bukkit scheduler on 1.18–1.19.
     */
    public void start() {
        int fps = configManager.getFps();
        int intervalTicks = Math.max(1, 20 / fps);
        taskHandle = SchedulerCompat.runRepeating(plugin, this, 0L, intervalTicks);
    }

    /** Cancels the repeating task. Safe to call if not started or already cancelled. */
    public void cancel() {
        if (taskHandle != null) {
            taskHandle.cancel();
            taskHandle = null;
        }
    }

    /** True if the task is not running (never started or already cancelled). Used by status command. */
    public boolean isCancelled() {
        return taskHandle == null;
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

        int breakingBlockX = BREAKING_SENTINEL;
        int breakingBlockY = BREAKING_SENTINEL;
        int breakingBlockZ = BREAKING_SENTINEL;
        int breakingStage = BREAKING_STAGE_NONE;
        if (blockBreakTracker != null) {
            var stateOpt = blockBreakTracker.getState(player.getUniqueId());
            if (stateOpt.isPresent()) {
                var state = stateOpt.get();
                breakingBlockX = state.blockX();
                breakingBlockY = state.blockY();
                breakingBlockZ = state.blockZ();
                breakingStage = state.stage();
            }
        }

        String actionBarText = actionBarTracker != null ? actionBarTracker.getMessage(player.getUniqueId()) : null;

        List<BossBarRecord> activeBossBars = captureBossBars(player);

        List<AttackRecord> attacksThisFrame = combatEventTracker != null
                ? combatEventTracker.drainAttacksForAttacker(player.getUniqueId(), originX, originY, originZ)
                : List.of();

        var inv = player.getInventory();
        String mainHandItem = ItemSerializer.serialize(inv.getItemInMainHand());
        String offHandItem = ItemSerializer.serialize(inv.getItemInOffHand());
        String helmetItem = ItemSerializer.serialize(inv.getHelmet());
        String chestplateItem = ItemSerializer.serialize(inv.getChestplate());
        String leggingsItem = ItemSerializer.serialize(inv.getLeggings());
        String bootsItem = ItemSerializer.serialize(inv.getBoots());

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
                inSpectator,
                actionBarText,
                activeBossBars,
                breakingBlockX,
                breakingBlockY,
                breakingBlockZ,
                breakingStage,
                mainHandItem,
                offHandItem,
                helmetItem,
                chestplateItem,
                leggingsItem,
                bootsItem,
                attacksThisFrame);
    }

    private List<BossBarRecord> captureBossBars(Player player) {
        List<BossBarRecord> bars = new ArrayList<>();
        Iterator<? extends BossBar> it;
        try {
            it = Bukkit.getBossBars();
        } catch (Exception e) {
            return List.of();
        }
        if (it == null) {
            return List.of();
        }
        while (it.hasNext()) {
            BossBar bar = it.next();
            if (bar.getPlayers().contains(player)) {
                String title = bar.getTitle();
                if (title != null && !title.isEmpty()) {
                    title = AdventureTextUtil.stripLegacyFormatting(title);
                } else {
                    title = "";
                }
                bars.add(new BossBarRecord(title, (float) bar.getProgress(), bar.getColor().name()));
            }
        }
        return bars.isEmpty() ? List.of() : Collections.unmodifiableList(bars);
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
            float pitch = eloc.getPitch();
            String pose = entity.getPose() != null ? entity.getPose().name() : null;
            UUID uuid = entity.getUniqueId();
            boolean isPlayer = entity instanceof Player;
            boolean onFire = entity.getFireTicks() > 0;
            boolean invisible = entity instanceof LivingEntity && ((LivingEntity) entity).isInvisible();
            String customName = entity instanceof org.bukkit.Nameable
                    ? customNameResolver.getCustomName((org.bukkit.Nameable) entity)
                    : null;
            if (customName != null && customName.isEmpty()) {
                customName = null;
            }

            BoundingBox bbox = entity.getBoundingBox();
            double wX = bbox.getWidthX();
            double wZ = bbox.getWidthZ();
            double h = bbox.getHeight();
            double boundingWidth = Math.max(Math.max(wX, wZ), 1e-6);  // Guard: entity with 0-size bbox would cause div-by-zero in renderer
            double boundingHeight = Math.max(h, 1e-6);

            float hurtProgress = -1.0f;
            boolean isDead = false;
            if (entity instanceof LivingEntity living) {
                int noDamageTicks = living.getNoDamageTicks();
                int maxNoDamageTicks = living.getMaximumNoDamageTicks();
                hurtProgress = (noDamageTicks > 0 && maxNoDamageTicks > 0)
                        ? (float) noDamageTicks / maxNoDamageTicks
                        : 0.0f;
                isDead = living.isDead() || living.getHealth() <= 0;
            }

            float aecRadius = -1.0f;
            String aecEffectName = null;
            if (entity instanceof AreaEffectCloud aec) {
                aecRadius = aec.getRadius();
                if (!aec.getCustomEffects().isEmpty()) {
                    PotionEffect primaryEffect = aec.getCustomEffects().iterator().next();
                    aecEffectName = primaryEffect.getType().getName();
                } else if (aec.getBasePotionData() != null && aec.getBasePotionData().getType() != null) {
                    aecEffectName = aec.getBasePotionData().getType().getEffectType().getName();
                }
            }

            String droppedItemMaterial = null;
            if (entity instanceof Item itemEntity) {
                ItemStack stack = itemEntity.getItemStack();
                droppedItemMaterial = ItemSerializer.serialize(stack);
            }

            UUID shooterUUID = null;
            if (entity instanceof org.bukkit.entity.Projectile proj) {
                org.bukkit.projectiles.ProjectileSource src = proj.getShooter();
                if (src instanceof Entity shooterEntity) {
                    shooterUUID = shooterEntity.getUniqueId();
                }
            }

            result.add(new EntitySnapshot(
                    entity.getType(),
                    relX,
                    relY,
                    relZ,
                    yaw,
                    pitch,
                    pose,
                    uuid,
                    isPlayer,
                    onFire,
                    invisible,
                    customName,
                    boundingWidth,
                    boundingHeight,
                    hurtProgress,
                    isDead,
                    aecRadius,
                    aecEffectName,
                    droppedItemMaterial,
                    shooterUUID));
        }
        return result;
    }
}

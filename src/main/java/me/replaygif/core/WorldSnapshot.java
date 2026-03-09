package me.replaygif.core;

import java.util.Collections;
import java.util.List;

/**
 * Immutable single frame: block volume + entities + player state at one moment.
 * Built on the main thread, consumed on the render thread; immutability (no setters,
 * defensively copied entities list) avoids cross-thread visibility issues and allows
 * safe caching of derived data (e.g. draw lists) if needed later.
 */
public final class WorldSnapshot {

    /** Used for slice() time window and to find the trigger frame index. */
    public final long timestamp;

    /** World block coords of the player; volume is centered here and blocks are relative. */
    public final int originX;
    public final int originY;
    public final int originZ;

    /** Player look direction; available for overlays or future use. */
    public final float playerYaw;
    public final float playerPitch;

    /** For death overlay: we show gravestone and tint when health is 0 after trigger frame. */
    public final float playerHealth;

    /** Captured for possible future HUD or metadata. */
    public final int playerFood;

    /** Dimension key for templates and logging. */
    public final String dimension;

    /** World name for templates and logging. */
    public final String worldName;

    /** Flattened volumeSize³ block ordinals; index = x*vol² + y*vol + z. */
    public final short[] blocks;

    /** Cubic volume edge length; same for all snapshots in a run. */
    public final int volumeSize;

    /** Defensively copied so callers cannot mutate the frame's entity list. */
    public final List<EntitySnapshot> entities;

    /** Whether the player was in spectator; can be used for UI or filtering. */
    public final boolean inSpectator;

    /** Maximum health (1.0–1024.0). Used for HUD heart bar. */
    public final float playerMaxHealth;

    /** XP bar fill fraction 0.0–1.0. */
    public final float playerXpProgress;

    /** Total XP level; shown on HUD when > 0. */
    public final int playerXpLevel;

    /** Equipment: ItemSerializer compact string; null = empty. */
    public final String mainHandItem;
    public final String offHandItem;
    public final String helmetItem;
    public final String chestplateItem;
    public final String leggingsItem;
    public final String bootsItem;

    /** Potion effect type names (e.g. "absorption"). Immutable. Never null. */
    public final List<String> activePotionEffects;

    /** Block position being broken. -999999 if not breaking. */
    public final int breakingBlockX;
    public final int breakingBlockY;
    public final int breakingBlockZ;
    /** Break stage 0–9 (9 = almost broken). -1 if not breaking. */
    public final int breakingStage;

    /** Action bar message at capture time. null if none or not available. */
    public final String actionBarText;

    /** Boss bars visible to the player at capture time. Immutable. Never null. */
    public final List<BossBarRecord> activeBossBars;

    /** Combat attacks that occurred between the previous frame and this one. Immutable. Never null. May be empty. */
    public final List<AttackRecord> attacksThisFrame;

    /** All fields final; entities list is a copy so this frame is fully immutable. */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator, null, List.of(), List.of());
    }

    /**
     * Constructor with action bar, boss bars, and attacks. Other HUD/breaking fields use defaults.
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            List<AttackRecord> attacksThisFrame) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator, actionBarText, activeBossBars,
                -999999, -999999, -999999, -1, attacksThisFrame);
    }

    /**
     * Constructor with action bar and boss bars for capture. Other HUD/breaking fields use defaults.
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator, actionBarText, activeBossBars, List.of());
    }

    /**
     * Constructor for capture with action bar, boss bars, and block-breaking state.
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            int breakingBlockX,
            int breakingBlockY,
            int breakingBlockZ,
            int breakingStage) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator, actionBarText, activeBossBars,
                breakingBlockX, breakingBlockY, breakingBlockZ, breakingStage,
                null, null, null, null, null, null);
    }

    /**
     * Constructor for capture with equipment (ItemSerializer format; :enchanted appended when applicable).
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            int breakingBlockX,
            int breakingBlockY,
            int breakingBlockZ,
            int breakingStage,
            String mainHandItem,
            String offHandItem,
            String helmetItem,
            String chestplateItem,
            String leggingsItem,
            String bootsItem) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator,
                20f, 0f, 0, mainHandItem, offHandItem, helmetItem, chestplateItem, leggingsItem, bootsItem, List.of(),
                breakingBlockX, breakingBlockY, breakingBlockZ, breakingStage,
                actionBarText, activeBossBars, List.of());
    }

    /**
     * Constructor for capture with action bar, boss bars, breaking state, and attacks this frame.
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            int breakingBlockX,
            int breakingBlockY,
            int breakingBlockZ,
            int breakingStage,
            List<AttackRecord> attacksThisFrame) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator, actionBarText, activeBossBars,
                breakingBlockX, breakingBlockY, breakingBlockZ, breakingStage,
                null, null, null, null, null, null, attacksThisFrame);
    }

    /**
     * Constructor for capture with equipment (ItemSerializer format; :enchanted appended when applicable) and attacks.
     */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            int breakingBlockX,
            int breakingBlockY,
            int breakingBlockZ,
            int breakingStage,
            String mainHandItem,
            String offHandItem,
            String helmetItem,
            String chestplateItem,
            String leggingsItem,
            String bootsItem,
            List<AttackRecord> attacksThisFrame) {
        this(timestamp, originX, originY, originZ, playerYaw, playerPitch,
                playerHealth, playerFood, dimension, worldName, blocks, volumeSize,
                entities, inSpectator,
                20f, 0f, 0, mainHandItem, offHandItem, helmetItem, chestplateItem, leggingsItem, bootsItem, List.of(),
                breakingBlockX, breakingBlockY, breakingBlockZ, breakingStage,
                actionBarText, activeBossBars, attacksThisFrame);
    }

    /** Full constructor including HUD fields. */
    public WorldSnapshot(
            long timestamp,
            int originX,
            int originY,
            int originZ,
            float playerYaw,
            float playerPitch,
            float playerHealth,
            int playerFood,
            String dimension,
            String worldName,
            short[] blocks,
            int volumeSize,
            List<EntitySnapshot> entities,
            boolean inSpectator,
            float playerMaxHealth,
            float playerXpProgress,
            int playerXpLevel,
            String mainHandItem,
            String offHandItem,
            String helmetItem,
            String chestplateItem,
            String leggingsItem,
            String bootsItem,
            List<String> activePotionEffects,
            int breakingBlockX,
            int breakingBlockY,
            int breakingBlockZ,
            int breakingStage,
            String actionBarText,
            List<BossBarRecord> activeBossBars,
            List<AttackRecord> attacksThisFrame) {
        this.timestamp = timestamp;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.playerYaw = playerYaw;
        this.playerPitch = playerPitch;
        this.playerHealth = playerHealth;
        this.playerFood = playerFood;
        this.dimension = dimension;
        this.worldName = worldName;
        this.blocks = blocks;
        this.volumeSize = volumeSize;
        this.entities = entities != null ? List.copyOf(entities) : List.of();
        this.inSpectator = inSpectator;
        this.playerMaxHealth = playerMaxHealth;
        this.playerXpProgress = playerXpProgress;
        this.playerXpLevel = playerXpLevel;
        this.mainHandItem = mainHandItem;
        this.offHandItem = offHandItem;
        this.helmetItem = helmetItem;
        this.chestplateItem = chestplateItem;
        this.leggingsItem = leggingsItem;
        this.bootsItem = bootsItem;
        this.activePotionEffects = activePotionEffects != null ? List.copyOf(activePotionEffects) : List.of();
        this.breakingBlockX = breakingBlockX;
        this.breakingBlockY = breakingBlockY;
        this.breakingBlockZ = breakingBlockZ;
        this.breakingStage = breakingStage;
        this.actionBarText = actionBarText;
        this.activeBossBars = activeBossBars != null && !activeBossBars.isEmpty()
                ? Collections.unmodifiableList(new java.util.ArrayList<>(activeBossBars))
                : List.of();
        this.attacksThisFrame = attacksThisFrame != null && !attacksThisFrame.isEmpty()
                ? Collections.unmodifiableList(new java.util.ArrayList<>(attacksThisFrame))
                : List.of();
    }
}

package me.replaygif.core;

import java.util.List;

/**
 * Immutable. One captured frame of world state around a player.
 * Created by SnapshotScheduler on the main thread. Read by TriggerHandler
 * and IsometricRenderer on async threads. Must be fully immutable after
 * construction — no setters, no mutable collections.
 */
public final class WorldSnapshot {

    /** Milliseconds since epoch at time of capture. Never null. */
    public final long timestamp;

    /** Absolute world coordinates of the player's block position at time of capture. */
    public final int originX;
    public final int originY;
    public final int originZ;

    /** Player facing direction at time of capture, in degrees (Minecraft convention). */
    public final float playerYaw;
    public final float playerPitch;

    /** Player health at time of capture. Range 0.0–20.0. */
    public final float playerHealth;

    /** Player food level at time of capture. Range 0–20. */
    public final int playerFood;

    /** The dimension key at time of capture. Never null. */
    public final String dimension;

    /** The world name at time of capture. Never null. */
    public final String worldName;

    /** Flattened 3D array of block material ordinals. Size is volumeSize^3. Never null. */
    public final short[] blocks;

    /** Edge length of the cubic volume. */
    public final int volumeSize;

    /** All entities within the capture volume at time of capture. Never null. Immutable list. */
    public final List<EntitySnapshot> entities;

    /** Whether the player was in spectator mode at time of capture. */
    public final boolean inSpectator;

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
    }
}

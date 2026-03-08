package me.replaygif.core;

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

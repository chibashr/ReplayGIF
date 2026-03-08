package me.replaygif.core;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Immutable. One entity within a WorldSnapshot's capture volume.
 */
public final class EntitySnapshot {

    /** The Bukkit EntityType. Never null. */
    public final EntityType type;

    /** Position relative to the snapshot's originX/Y/Z. */
    public final double relX;
    public final double relY;
    public final double relZ;

    /** Entity facing direction in degrees. Same convention as playerYaw. */
    public final float yaw;

    /** UUID of the entity. Never null. */
    public final UUID uuid;

    /** True if this entity is a player at capture time. */
    public final boolean isPlayer;

    /** True if the entity was on fire at capture time. */
    public final boolean onFire;

    /** True if the entity was invisible at capture time. */
    public final boolean invisible;

    /** The entity's display name if set, otherwise null. */
    public final String customName;

    /** Bounding box dimensions in blocks at capture time. All values positive, never zero. */
    public final double boundingWidth;
    public final double boundingHeight;

    public EntitySnapshot(
            EntityType type,
            double relX,
            double relY,
            double relZ,
            float yaw,
            UUID uuid,
            boolean isPlayer,
            boolean onFire,
            boolean invisible,
            String customName,
            double boundingWidth,
            double boundingHeight) {
        this.type = type;
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.yaw = yaw;
        this.uuid = uuid;
        this.isPlayer = isPlayer;
        this.onFire = onFire;
        this.invisible = invisible;
        this.customName = customName;
        this.boundingWidth = boundingWidth;
        this.boundingHeight = boundingHeight;
    }
}

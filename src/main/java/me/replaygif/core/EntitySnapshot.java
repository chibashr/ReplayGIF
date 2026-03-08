package me.replaygif.core;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Immutable snapshot of one entity inside the capture volume at frame time.
 * Stored per-frame so the renderer can draw entities in correct order and apply
 * visibility/fire/name without touching the live world. Relative coordinates keep
 * the frame independent of world origin.
 */
public final class EntitySnapshot {

    /** Bukkit entity type for sprite lookup. */
    public final EntityType type;

    /** Position relative to snapshot origin (player block position) for isometric projection. */
    public final double relX;
    public final double relY;
    public final double relZ;

    /** Facing in degrees (Minecraft convention) for possible future use. */
    public final float yaw;

    /** Used by SkinCache for player face; other entities use type for sprite. */
    public final UUID uuid;

    /** True for players so we use skin face instead of entity sprite and show name. */
    public final boolean isPlayer;

    /** True when we should composite the fire overlay. */
    public final boolean onFire;

    /** True when we draw the entity at reduced opacity. */
    public final boolean invisible;

    /** Custom name for name tag; players use subject name from context. */
    public final String customName;

    /** Used to scale sprite size in the isometric view; clamped to avoid zero. */
    public final double boundingWidth;
    public final double boundingHeight;

    /** All fields set at construction; no mutators so async readers see a stable snapshot. */
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

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

    /** Hurt flash progress 0.0–1.0 (from noDamageTicks); used for hurt particle synthesis. */
    public final float hurtProgress;

    /** True when entity is dead; used for death particle synthesis. */
    public final boolean isDead;

    /** For AREA_EFFECT_CLOUD: radius in blocks. -1.0 for all other types. */
    public final float aecRadius;

    /** For AREA_EFFECT_CLOUD: primary effect name. null for no effect or non-AEC. */
    public final String aecEffectName;

    /** For DROPPED_ITEM: ItemSerializer compact string of the item. null for all other types. */
    public final String droppedItemMaterial;

    /** For projectiles (e.g. FISHING_HOOK): UUID of shooter. null for non-projectiles. */
    public final UUID shooterUUID;

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
        this(type, relX, relY, relZ, yaw, uuid, isPlayer, onFire, invisible, customName, boundingWidth, boundingHeight, 0f, false, -1.0f, null, null, null);
    }

    /** Full constructor including hurt/death state for particle synthesis. */
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
            double boundingHeight,
            float hurtProgress,
            boolean isDead) {
        this(type, relX, relY, relZ, yaw, uuid, isPlayer, onFire, invisible, customName, boundingWidth, boundingHeight, hurtProgress, isDead, -1.0f, null, null, null);
    }

    /** Full constructor including AEC, dropped item, and projectile shooter. */
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
            double boundingHeight,
            float hurtProgress,
            boolean isDead,
            float aecRadius,
            String aecEffectName,
            String droppedItemMaterial,
            UUID shooterUUID) {
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
        this.hurtProgress = hurtProgress;
        this.isDead = isDead;
        this.aecRadius = aecRadius;
        this.aecEffectName = aecEffectName;
        this.droppedItemMaterial = droppedItemMaterial;
        this.shooterUUID = shooterUUID;
    }
}

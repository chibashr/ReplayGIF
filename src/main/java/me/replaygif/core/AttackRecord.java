package me.replaygif.core;

import java.util.UUID;

/**
 * Immutable record of one melee attack (EntityDamageByEntityEvent with player damager).
 * Created by CombatEventTracker, stored in WorldSnapshot.attacksThisFrame.
 */
public final class AttackRecord {

    public final UUID attackerUUID;
    public final UUID targetUUID;
    /** True if attacker was falling when attack landed (vanilla crit conditions). */
    public final boolean isCritical;
    /** True if this was a sweep (reduced damage to multiple targets). */
    public final boolean isSweep;
    /** Target's position relative to snapshot origin. */
    public final double targetRelX;
    public final double targetRelY;
    public final double targetRelZ;
    public final long timestamp;

    public AttackRecord(UUID attackerUUID, UUID targetUUID, boolean isCritical, boolean isSweep,
                        double targetRelX, double targetRelY, double targetRelZ, long timestamp) {
        this.attackerUUID = attackerUUID;
        this.targetUUID = targetUUID;
        this.isCritical = isCritical;
        this.isSweep = isSweep;
        this.targetRelX = targetRelX;
        this.targetRelY = targetRelY;
        this.targetRelZ = targetRelZ;
        this.timestamp = timestamp;
    }
}

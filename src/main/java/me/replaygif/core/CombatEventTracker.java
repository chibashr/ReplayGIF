package me.replaygif.core;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks melee attacks (EntityDamageByEntityEvent with player damager) for WorldSnapshot.
 * Stores attacks in a per-tick queue; SnapshotScheduler drains and clears each tick.
 * Follows the same capture pattern as BlockBreakTracker.
 */
public class CombatEventTracker implements Listener {

    private final Queue<PendingAttack> queue = new ConcurrentLinkedQueue<>();

    /**
     * Drains all attacks from the queue, filters by attacker UUID, and returns AttackRecords
     * with target position relative to the given origin. Call once per capture tick from
     * SnapshotScheduler (main thread). The queue is cleared after this call.
     *
     * @param attackerUUID player who dealt the attacks (subject of the snapshot)
     * @param originX      snapshot origin block X
     * @param originY      snapshot origin block Y
     * @param originZ      snapshot origin block Z
     * @return list of attacks by this player this tick; never null, may be empty
     */
    public List<AttackRecord> drainAttacksForAttacker(UUID attackerUUID, int originX, int originY, int originZ) {
        List<PendingAttack> drained = new ArrayList<>();
        PendingAttack p;
        while ((p = queue.poll()) != null) {
            drained.add(p);
        }
        List<AttackRecord> result = new ArrayList<>();
        for (PendingAttack a : drained) {
            if (!a.attackerUUID.equals(attackerUUID)) {
                queue.offer(a);
                continue;
            }
            double relX = a.targetWorldX - originX;
            double relY = a.targetWorldY - originY;
            double relZ = a.targetWorldZ - originZ;
            result.add(new AttackRecord(a.attackerUUID, a.targetUUID, a.isCritical, a.isSweep,
                    relX, relY, relZ, a.timestamp));
        }
        return List.copyOf(result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) {
            return;
        }
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) {
            return;
        }
        Location tLoc = target.getLocation();
        boolean isCritical = isCriticalHit(player);
        boolean isSweep = isSweepAttack(player, event.getFinalDamage());
        long timestamp = System.currentTimeMillis();
        queue.offer(new PendingAttack(
                player.getUniqueId(),
                target.getUniqueId(),
                isCritical,
                isSweep,
                tLoc.getX(),
                tLoc.getY(),
                tLoc.getZ(),
                timestamp));
    }

    private static boolean isCriticalHit(Player player) {
        return player.getFallDistance() > 0
                && !player.isOnGround()
                && !player.isSprinting()
                && player.getVelocity().getY() < 0;
    }

    private static boolean isSweepAttack(Player player, double finalDamage) {
        var attr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr == null) {
            return false;
        }
        double baseDamage = attr.getValue();
        return finalDamage > 0 && finalDamage < baseDamage * 0.9;
    }

    private static final class PendingAttack {
        final UUID attackerUUID;
        final UUID targetUUID;
        final boolean isCritical;
        final boolean isSweep;
        final double targetWorldX;
        final double targetWorldY;
        final double targetWorldZ;
        final long timestamp;

        PendingAttack(UUID attackerUUID, UUID targetUUID, boolean isCritical, boolean isSweep,
                      double targetWorldX, double targetWorldY, double targetWorldZ, long timestamp) {
            this.attackerUUID = attackerUUID;
            this.targetUUID = targetUUID;
            this.isCritical = isCritical;
            this.isSweep = isSweep;
            this.targetWorldX = targetWorldX;
            this.targetWorldY = targetWorldY;
            this.targetWorldZ = targetWorldZ;
            this.timestamp = timestamp;
        }
    }
}

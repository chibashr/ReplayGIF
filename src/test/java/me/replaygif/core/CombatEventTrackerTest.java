package me.replaygif.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CombatEventTracker covering CP1, CP2 from .planning/testing.md.
 */
class CombatEventTrackerTest {

    private CombatEventTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CombatEventTracker();
    }

    /** CP1 — EntityDamageByEntityEvent with falling attacker: isCritical=true. */
    @Test
    void cp1_entityDamageByEntity_fallingAttacker_isCriticalTrue() {
        UUID attackerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(attackerId);
        when(player.getFallDistance()).thenReturn(1.5f);
        when(player.isOnGround()).thenReturn(false);
        when(player.isSprinting()).thenReturn(false);
        when(player.getVelocity()).thenReturn(new Vector(0, -0.5, 0));

        LivingEntity target = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(targetId);
        Location tLoc = mock(Location.class);
        when(tLoc.getX()).thenReturn(10.5);
        when(tLoc.getY()).thenReturn(64.0);
        when(tLoc.getZ()).thenReturn(20.5);
        when(target.getLocation()).thenReturn(tLoc);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(player);
        when(event.getEntity()).thenReturn(target);

        tracker.onEntityDamageByEntity(event);
        List<AttackRecord> attacks = tracker.drainAttacksForAttacker(attackerId, 10, 64, 20);
        assertEquals(1, attacks.size());
        assertTrue(attacks.get(0).isCritical);
    }

    /** CP2 — Sweep attack (reduced damage): isSweep=true. */
    @Test
    void cp2_sweepAttack_reducedDamage_isSweepTrue() {
        UUID attackerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(attackerId);
        when(player.getFallDistance()).thenReturn(0f);
        when(player.isOnGround()).thenReturn(true);
        AttributeInstance attr = mock(AttributeInstance.class);
        when(attr.getValue()).thenReturn(9.0);
        when(player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).thenReturn(attr);

        LivingEntity target = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(targetId);
        Location tLoc = mock(Location.class);
        when(tLoc.getX()).thenReturn(0.0);
        when(tLoc.getY()).thenReturn(64.0);
        when(tLoc.getZ()).thenReturn(0.0);
        when(target.getLocation()).thenReturn(tLoc);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(player);
        when(event.getEntity()).thenReturn(target);
        when(event.getFinalDamage()).thenReturn(2.0); // less than 9 * 0.9

        tracker.onEntityDamageByEntity(event);
        List<AttackRecord> attacks = tracker.drainAttacksForAttacker(attackerId, 0, 64, 0);
        assertEquals(1, attacks.size());
        assertTrue(attacks.get(0).isSweep);
    }

    /** CP3 — Neither critical nor sweep: no combat particles (tracker still records, but isCritical and isSweep false). */
    @Test
    void cp3_normalAttack_isCriticalAndSweepFalse() {
        UUID attackerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(attackerId);
        when(player.getFallDistance()).thenReturn(0f);
        when(player.isOnGround()).thenReturn(true);
        AttributeInstance attr = mock(AttributeInstance.class);
        when(attr.getValue()).thenReturn(9.0);
        when(player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).thenReturn(attr);

        LivingEntity target = mock(LivingEntity.class);
        when(target.getUniqueId()).thenReturn(targetId);
        Location tLoc = mock(Location.class);
        when(tLoc.getX()).thenReturn(5.0);
        when(tLoc.getY()).thenReturn(70.0);
        when(tLoc.getZ()).thenReturn(5.0);
        when(target.getLocation()).thenReturn(tLoc);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(player);
        when(event.getEntity()).thenReturn(target);
        when(event.getFinalDamage()).thenReturn(9.0);

        tracker.onEntityDamageByEntity(event);
        List<AttackRecord> attacks = tracker.drainAttacksForAttacker(attackerId, 5, 70, 5);
        assertEquals(1, attacks.size());
        assertFalse(attacks.get(0).isCritical);
        assertFalse(attacks.get(0).isSweep);
    }
}

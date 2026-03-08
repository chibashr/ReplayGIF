package me.replaygif.core;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockRegistry covering BR1–BR3 from .planning/testing.md.
 */
class BlockRegistryTest {

    private BlockRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BlockRegistry();
    }

    /** BR1 — All materials mapped: every block Material has an ordinal; no material returns null. */
    @Test
    void br1_allMaterialsMapped() {
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                short ordinal = registry.getOrdinal(m);
                assertTrue(ordinal >= 0, "ordinal for " + m + " must be non-negative");
                assertNotNull(registry.getMaterial(ordinal), "getMaterial(" + ordinal + ") must not be null");
            }
        }
    }

    /** BR2 — AIR is ordinal 0: getOrdinal(Material.AIR) == 0 always. */
    @Test
    void br2_airIsOrdinalZero() {
        assertEquals(0, registry.getOrdinal(Material.AIR));
        assertEquals(Material.AIR, registry.getMaterial((short) 0));
    }

    /** BR3 — Round-trip: getMaterial(getOrdinal(m)) == m for every block material. */
    @Test
    void br3_roundTrip() {
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                short ordinal = registry.getOrdinal(m);
                Material roundTrip = registry.getMaterial(ordinal);
                assertEquals(m, roundTrip, "round-trip for " + m);
            }
        }
    }
}

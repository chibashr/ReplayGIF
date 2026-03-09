package me.replaygif.core;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EG1 — ItemSerializer isEnchanted: ":enchanted" suffix detection.
 * EG2 — Serialize: enchanted item produces "DIAMOND_SWORD:enchanted"; non-enchanted no suffix.
 */
class ItemSerializerTest {

    @Test
    void eg1_enchantedSuffix_isEnchantedTrue() {
        assertTrue(ItemSerializer.isEnchanted("DIAMOND_SWORD:enchanted"));
        assertTrue(ItemSerializer.isEnchanted("IRON_CHESTPLATE:dye=FF0000:enchanted"));
    }

    @Test
    void eg1_noSuffix_isEnchantedFalse() {
        assertFalse(ItemSerializer.isEnchanted("DIAMOND_SWORD"));
        assertFalse(ItemSerializer.isEnchanted("IRON_CHESTPLATE:dye=FF0000"));
        assertFalse(ItemSerializer.isEnchanted(null));
        assertFalse(ItemSerializer.isEnchanted(""));
    }

    /** EG2 — Serialize mocked enchanted stack → "DIAMOND_SWORD:enchanted". */
    @Test
    void eg2_enchantedDiamondSword_serializesWithEnchantedSuffix() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(stack.getEnchantments()).thenReturn(Map.of(Enchantment.DURABILITY, 1));
        assertEquals("DIAMOND_SWORD:enchanted", ItemSerializer.serialize(stack));
    }

    /** EG2 — Non-enchanted serializes without suffix. */
    @Test
    void eg2_nonEnchantedDiamondSword_serializesWithoutSuffix() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(stack.getEnchantments()).thenReturn(Collections.emptyMap());
        assertEquals("DIAMOND_SWORD", ItemSerializer.serialize(stack));
    }
}

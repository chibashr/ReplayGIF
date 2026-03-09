package me.replaygif.core;

import org.bukkit.inventory.ItemStack;

/**
 * Utility for parsing and serializing item strings stored in WorldSnapshot and EntitySnapshot.
 * Format: "MATERIAL_NAME" or "MATERIAL_NAME:key=value" (e.g. LEATHER_CHESTPLATE:dye=FF0000).
 * Enchanted items append ":enchanted" (e.g. "DIAMOND_SWORD:enchanted", "IRON_CHESTPLATE:dye=FF0000:enchanted").
 */
public final class ItemSerializer {

    private static final String ENCHANTED_SUFFIX = ":enchanted";

    private ItemSerializer() {}

    /**
     * Serializes an ItemStack to the compact string format. Appends ":enchanted" when the item has enchantments.
     *
     * @param stack the item stack; null returns null
     * @return compact string, or null if stack is null or air
     */
    public static String serialize(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String base = stack.getType().name();
        return hasAnyEnchantments(stack) ? base + ENCHANTED_SUFFIX : base;
    }

    private static boolean hasAnyEnchantments(ItemStack stack) {
        if (stack.getEnchantments() != null && !stack.getEnchantments().isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the serialized string contains the ":enchanted" suffix.
     */
    public static boolean isEnchanted(String serialized) {
        return serialized != null && serialized.contains(ENCHANTED_SUFFIX);
    }

    /**
     * Extracts the material name from a compact item string.
     *
     * @param compact ItemSerializer format string, or null
     * @return the part before the first ':', or the whole string; null if compact is null
     */
    public static String getMaterialName(String compact) {
        if (compact == null || compact.isEmpty()) {
            return null;
        }
        int colon = compact.indexOf(':');
        return colon < 0 ? compact : compact.substring(0, colon);
    }
}

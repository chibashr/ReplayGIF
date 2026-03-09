package me.replaygif.renderer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heuristic mapping from Material names to hex colors for block_colors_defaults.json.
 * Used to generate a complete defaults file so no block falls back to #808080.
 */
final class BlockColorHeuristic {

    private static final Map<String, String> COLOR_BY_PREFIX = new LinkedHashMap<>();
    static {
        COLOR_BY_PREFIX.put("WHITE_", "#E8E8E8");
        COLOR_BY_PREFIX.put("BLACK_", "#1C1C1C");
        COLOR_BY_PREFIX.put("LIGHT_GRAY_", "#A0A0A0");
        COLOR_BY_PREFIX.put("GRAY_", "#6B6B6B");
        COLOR_BY_PREFIX.put("RED_", "#B22222");
        COLOR_BY_PREFIX.put("ORANGE_", "#E87622");
        COLOR_BY_PREFIX.put("YELLOW_", "#E8C234");
        COLOR_BY_PREFIX.put("LIME_", "#7CFC00");
        COLOR_BY_PREFIX.put("GREEN_", "#228B22");
        COLOR_BY_PREFIX.put("CYAN_", "#00CED1");
        COLOR_BY_PREFIX.put("LIGHT_BLUE_", "#87CEEB");
        COLOR_BY_PREFIX.put("BLUE_", "#4169E1");
        COLOR_BY_PREFIX.put("PURPLE_", "#8B008B");
        COLOR_BY_PREFIX.put("MAGENTA_", "#C71585");
        COLOR_BY_PREFIX.put("PINK_", "#FF69B4");
        COLOR_BY_PREFIX.put("BROWN_", "#8B4513");
    }

    /** Returns a hex color for the given material name (block). Never returns #808080. */
    static String hexFor(String materialName) {
        for (Map.Entry<String, String> e : COLOR_BY_PREFIX.entrySet()) {
            if (materialName.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        String u = materialName;
        if (u.equals("AIR") || u.equals("CAVE_AIR") || u.equals("VOID_AIR")) return "#F0F0F0";
        if (u.equals("WATER") || u.contains("WATER")) return "#3F76E4";
        if (u.equals("LAVA") || u.contains("LAVA")) return "#FF6B00";
        if (u.contains("LEAVES") || u.contains("SAPLING") || u.contains("AZALEA") || u.equals("GRASS") || u.contains("FERN") || u.contains("TULIP") || u.contains("ORCHID") || u.contains("ROSE") || u.contains("DANDELION") || u.contains("POPPY") || u.contains("CORNFLOWER") || u.contains("LILY") || u.contains("ALLIUM") || u.contains("BLUET") || u.contains("PEONY") || u.contains("SUNFLOWER") || u.contains("LILAC") || u.contains("SEAGRASS") || u.contains("KELP") || u.contains("VINES") || u.contains("ROOTS") || u.contains("MOSS") || u.contains("SPROUTS") || u.contains("WART") && !u.contains("NETHER_WART_BLOCK")) return "#2D5016";
        if (u.contains("LOG") || u.contains("WOOD") || u.contains("PLANKS") || u.contains("FENCE") && !u.contains("IRON") || u.contains("GATE") && !u.contains("IRON") || u.contains("TRAPDOOR") || u.contains("PRESSURE_PLATE") && !u.contains("LIGHT_") && !u.contains("HEAVY") || u.contains("BUTTON") || u.contains("SIGN") || u.contains("STAIRS") && (u.contains("OAK") || u.contains("BIRCH") || u.contains("SPRUCE") || u.contains("JUNGLE") || u.contains("ACACIA") || u.contains("DARK_OAK") || u.contains("CRIMSON") || u.contains("WARPED") || u.contains("BAMBOO") || u.contains("CHERRY")) || u.contains("SLAB") && (u.contains("OAK") || u.contains("BIRCH") || u.contains("PLANKS") || u.contains("CRIMSON") || u.contains("WARPED") || u.contains("BAMBOO") || u.contains("CHERRY"))) return "#8B6914";
        if (u.contains("CRIMSON") || u.contains("WARPED") && (u.contains("STEM") || u.contains("HYPHAE") || u.contains("NYLIUM") || u.contains("PLANKS"))) return "#6B2D5C";
        if (u.contains("STONE") || u.contains("COBBLE") || u.contains("ANDESITE") || u.contains("DIORITE") || u.contains("GRANITE") || u.contains("DEEPSLATE") || u.contains("BASALT") || u.contains("BLACKSTONE") || u.contains("TUFF") || u.contains("BRICKS") && u.contains("STONE") || u.contains("INFESTED")) return "#7A7A7A";
        if (u.contains("OBSIDIAN") || u.contains("CRYING_OBSIDIAN")) return "#1A0A2E";
        if (u.contains("NETHERRACK") || u.contains("NETHER_BRICK")) return "#4A2C2A";
        if (u.contains("SAND") && !u.contains("RED")) return "#E8D5A3";
        if (u.contains("RED_SAND") || u.equals("RED_SAND")) return "#C4A574";
        if (u.contains("DIRT") || u.contains("FARMLAND") || u.contains("GRASS_BLOCK") || u.contains("PODZOL") || u.contains("MYCELIUM") || u.equals("ROOTED_DIRT")) return "#8B6B3D";
        if (u.contains("GLASS") && !u.contains("STAINED")) return "#C8E0E8";
        if (u.contains("ICE") || u.contains("PACKED_ICE") || u.contains("BLUE_ICE") || u.contains("FROSTED")) return "#A8D8E8";
        if (u.contains("SNOW") || u.contains("POWDER_SNOW")) return "#F8F8FF";
        if (u.contains("CLAY") || u.contains("TERRACOTTA") && !u.contains("GLAZED")) return "#A08060";
        if (u.contains("CONCRETE_POWDER")) return "#C0B0A0";
        if (u.contains("COAL") || u.contains("BLACKSTONE")) return "#2C2C2C";
        if (u.contains("IRON") && (u.contains("BLOCK") || u.contains("ORE") || u.contains("BARS") || u.contains("DOOR") || u.contains("TRAPDOOR"))) return "#A0A0A0";
        if (u.contains("GOLD") && (u.contains("BLOCK") || u.contains("ORE"))) return "#F7C84A";
        if (u.contains("DIAMOND")) return "#5ED4F0";
        if (u.contains("EMERALD")) return "#50C878";
        if (u.contains("LAPIS")) return "#4169E1";
        if (u.contains("REDSTONE")) return "#C9302C";
        if (u.contains("COPPER") || u.contains("EXPOSED") || u.contains("WEATHERED") || u.contains("OXIDIZED") || u.contains("WAXED")) return "#B87333";
        if (u.contains("AMETHYST") || u.contains("BUDDING")) return "#9966CC";
        if (u.contains("CORAL") || u.contains("BRAIN") || u.contains("BUBBLE") || u.contains("FIRE_CORAL") || u.contains("HORN") || u.contains("TUBE")) return "#E87474";
        if (u.contains("PRISMARINE") || u.contains("SEA_LANTERN") || u.contains("CONDUIT")) return "#5D9B8E";
        if (u.contains("SPONGE")) return "#D4C84A";
        if (u.contains("MAGMA") || u.contains("NETHERITE")) return "#3D2B2B";
        if (u.contains("END") && !u.contains("ROD")) return "#E8E4B8";
        if (u.contains("PURPUR")) return "#A080A0";
        if (u.contains("QUARTZ")) return "#E8E0D8";
        if (u.contains("BONE")) return "#E8E0C8";
        if (u.contains("HAY")) return "#C4A030";
        if (u.contains("HONEY")) return "#E8A830";
        if (u.contains("SLIME")) return "#7CFC00";
        if (u.contains("SPAWNER")) return "#4A2C5C";
        if (u.contains("BEDROCK") || u.contains("BARRIER") || u.contains("STRUCTURE")) return "#2C2C2C";
        if (u.contains("ANVIL") || u.contains("CAULDRON") || u.contains("HOPPER") || u.contains("DROPPER") || u.contains("DISPENSER")) return "#5C5C5C";
        if (u.contains("LANTERN") || u.contains("TORCH") || u.contains("GLOWSTONE") || u.contains("SHROOMLIGHT") || u.contains("SEA_LANTERN") || u.contains("END_ROD") || u.contains("BEACON") || u.contains("REDSTONE_TORCH") || u.contains("SOUL_TORCH")) return "#FFE4A0";
        if (u.contains("FIRE") || u.contains("SOUL_FIRE") || u.contains("LAVA")) return "#FF6B00";
        if (u.contains("CACTUS") || u.contains("SUGAR_CANE") || u.contains("BAMBOO") && !u.contains("PLANKS") && !u.contains("MOSAIC")) return "#2D5016";
        if (u.contains("MELON") || u.contains("PUMPKIN") || u.contains("CARVED") || u.contains("JACK_O")) return "#E87622";
        if (u.contains("CAKE") || u.contains("CANDLE_CAKE")) return "#F0E0D0";
        if (u.contains("SKULL") || u.contains("HEAD")) return "#8B7355";
        if (u.contains("CONDUIT")) return "#5D9B8E";
        if (u.contains("SCULK")) return "#1A2E2E";
        if (u.contains("LIGHT")) return "#F5F5DC";
        return "#888888";
    }
}

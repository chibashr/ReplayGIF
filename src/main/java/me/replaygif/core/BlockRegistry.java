package me.replaygif.core;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable mapping from block materials to compact ordinals so snapshot storage uses shorts
 * instead of material names. Built once at startup from Material.values() (blocks only);
 * AIR is forced to ordinal 0 so the renderer can treat 0 as "empty" without special-casing.
 * Unknown material → 0 and unknown ordinal → AIR keep the pipeline safe against future
 * Minecraft materials or corrupted data.
 */
public class BlockRegistry {

    private final Material[] ordinalToMaterial;
    private final Map<Material, Short> materialToOrdinal;

    /** Builds the bidirectional map; call once during plugin enable. */
    public BlockRegistry() {
        List<Material> blocks = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                blocks.add(m);
            }
        }
        // AIR at ordinal 0 regardless of enum position
        List<Material> ordered = new ArrayList<>(blocks.size());
        ordered.add(Material.AIR);
        for (Material m : blocks) {
            if (m != Material.AIR) {
                ordered.add(m);
            }
        }
        this.ordinalToMaterial = ordered.toArray(new Material[0]);
        this.materialToOrdinal = new HashMap<>(ordinalToMaterial.length);
        for (int i = 0; i < ordinalToMaterial.length; i++) {
            this.materialToOrdinal.put(ordinalToMaterial[i], (short) i);
        }
    }

    /** Ordinal for storage; unknown materials map to 0 so decoders see AIR. */
    public short getOrdinal(Material material) {
        return materialToOrdinal.getOrDefault(material, (short) 0);
    }

    /** Material for rendering; out-of-range ordinals return AIR to avoid NPE or invalid lookups. */
    public Material getMaterial(short ordinal) {
        if (ordinal < 0 || ordinal >= ordinalToMaterial.length) {
            return Material.AIR;
        }
        return ordinalToMaterial[ordinal];
    }

    /** Size of the ordinal space; BlockColorMap and others use this to allocate arrays. */
    public int getOrdinalCount() {
        return ordinalToMaterial.length;
    }
}

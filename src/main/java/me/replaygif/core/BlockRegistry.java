package me.replaygif.core;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Material → short ordinal, built at startup. Iterates Material.values(),
 * filters to block materials only. AIR is always ordinal 0. Bidirectional
 * lookups; unknown materials return ordinal 0, unknown ordinals return AIR.
 */
public class BlockRegistry {

    private final Material[] ordinalToMaterial;
    private final Map<Material, Short> materialToOrdinal;

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

    /** Returns the ordinal for the given material. Unknown materials return 0 (AIR). */
    public short getOrdinal(Material material) {
        return materialToOrdinal.getOrDefault(material, (short) 0);
    }

    /** Returns the material for the given ordinal. Unknown ordinals return Material.AIR. */
    public Material getMaterial(short ordinal) {
        if (ordinal < 0 || ordinal >= ordinalToMaterial.length) {
            return Material.AIR;
        }
        return ordinalToMaterial[ordinal];
    }

    /** Returns the number of block materials (ordinals 0 to count-1). */
    public int getOrdinalCount() {
        return ordinalToMaterial.length;
    }
}

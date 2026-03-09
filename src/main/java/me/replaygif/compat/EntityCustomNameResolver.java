package me.replaygif.compat;

import org.bukkit.Nameable;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves an entity's custom name to plain text. Version-specific implementations
 * use either {@code getCustomName()} (legacy) or {@code customName()} + serializer (preferred).
 */
public interface EntityCustomNameResolver {

    /**
     * Returns the custom name of the nameable entity as plain text, or null if none.
     */
    @Nullable
    String getCustomName(Nameable nameable);
}

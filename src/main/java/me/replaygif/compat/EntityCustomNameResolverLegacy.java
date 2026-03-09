package me.replaygif.compat;

import org.bukkit.Nameable;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves custom name using {@link Nameable#getCustomName()}.
 * Used on servers &lt; 1.20. Strips legacy § codes so name tags and labels stay plain.
 */
public final class EntityCustomNameResolverLegacy implements EntityCustomNameResolver {

    @Override
    @Nullable
    @SuppressWarnings("deprecation")
    public String getCustomName(Nameable nameable) {
        if (nameable == null) {
            return null;
        }
        String raw = nameable.getCustomName();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String stripped = AdventureTextUtil.stripLegacyFormatting(raw);
        return stripped.isEmpty() ? null : stripped;
    }
}

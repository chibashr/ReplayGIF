package me.replaygif.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Nameable;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves custom name using {@link Nameable#customName()} and serializing to plain text.
 * Used on servers 1.20+ (preferred API).
 */
public final class EntityCustomNameResolverModern implements EntityCustomNameResolver {

    @Override
    @Nullable
    public String getCustomName(Nameable nameable) {
        if (nameable == null) {
            return null;
        }
        Component component = nameable.customName();
        if (component == null) {
            return null;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain == null || plain.isEmpty() ? null : plain;
    }
}

package me.replaygif.compat;

import java.util.regex.Pattern;

/**
 * Shared helpers for Adventure/legacy text handling. Used so death messages,
 * entity custom names, and name tag rendering never show legacy formatting codes.
 */
public final class AdventureTextUtil {

    /** Strips Minecraft legacy formatting codes (§ followed by one character). */
    private static final Pattern LEGACY_FORMAT = Pattern.compile("§.");

    private AdventureTextUtil() {}

    /**
     * Removes legacy ChatColor-style codes (§ + one char) from the string.
     * Use after PlainTextComponentSerializer when the source might still contain §,
     * or for entity custom names from legacy getCustomName().
     *
     * @param text nullable; returns "" if null
     * @return plain text without § codes
     */
    public static String stripLegacyFormatting(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY_FORMAT.matcher(text).replaceAll("");
    }
}

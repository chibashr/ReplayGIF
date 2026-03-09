package me.replaygif.compat;

import org.bukkit.Server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the running server's Minecraft version at startup so components can
 * choose the correct API path (legacy vs preferred) without reflection.
 * Initialized once from {@link #init(Server)} during plugin onEnable.
 */
public final class VersionHelper {

    /** Match "MC: 1.21.4" or "1.18.2" in getVersion() string. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?:MC:?\\s*)?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static int major = 1;
    private static int minor = 18;
    private static boolean initialized;

    private VersionHelper() {}

    /**
     * Call once during plugin onEnable. Parses the server version from
     * {@code server.getVersion()}. If parsing fails, defaults to 1.18 (legacy path).
     */
    public static void init(Server server) {
        if (initialized) {
            return;
        }
        String versionString = server != null ? server.getVersion() : "";
        Matcher m = VERSION_PATTERN.matcher(versionString);
        if (m.find()) {
            try {
                major = Integer.parseInt(m.group(1));
                minor = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {
                // keep defaults
            }
        }
        initialized = true;
    }

    /** True when version is at least the given major.minor (e.g. 1.20 → use preferred Component APIs). */
    public static boolean isAtLeast(int majorReq, int minorReq) {
        if (major != majorReq) {
            return major > majorReq;
        }
        return minor >= minorReq;
    }

    /** Use preferred Nameable.customName() and Component-based sendMessage (1.20+). */
    public static boolean useModernNameableApi() {
        return isAtLeast(1, 20);
    }

    /** Use Component-based sendMessage for command output (1.20+). */
    public static boolean useModernSendMessage() {
        return isAtLeast(1, 20);
    }
}

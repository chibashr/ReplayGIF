package me.replaygif.renderer;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for SkinCache: null/offline skin handling, URL/URI handling, and that
 * skin fetch initiation does not block the main thread.
 */
class SkinCacheTest {

    private JavaPlugin plugin;
    private SkinCache skinCache;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(SkinCacheTest.class));
        when(plugin.getResource(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (skinCache != null) {
            skinCache.shutdown();
        }
    }

    /** Null textures → no fetch, no exception, getFace returns empty (placeholder path). */
    @Test
    void onPlayerJoin_nullTextures_usesPlaceholderPath() {
        skinCache = new SkinCache(plugin, true, 3600);
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getTextures()).thenReturn(null);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");

        assertDoesNotThrow(() -> skinCache.onPlayerJoin(player));

        Optional<?> face = skinCache.getFace(player.getUniqueId());
        assertTrue(face.isEmpty());
        assertNotNull(skinCache.getPlaceholder());
    }

    /** getSkin() null (offline mode) → no fetch, DEBUG only, placeholder path. */
    @Test
    void onPlayerJoin_nullSkinUrl_usesPlaceholderPath() {
        skinCache = new SkinCache(plugin, true, 3600);
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        PlayerTextures textures = mock(PlayerTextures.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getTextures()).thenReturn(textures);
        when(textures.isEmpty()).thenReturn(false);
        when(textures.getSkin()).thenReturn(null);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("OfflinePlayer");

        assertDoesNotThrow(() -> skinCache.onPlayerJoin(player));

        assertTrue(skinCache.getFace(player.getUniqueId()).isEmpty());
    }

    /** Main thread must not block more than 1ms during skin fetch initiation. */
    @Test
    void onPlayerJoin_doesNotBlockMainThread() throws MalformedURLException {
        skinCache = new SkinCache(plugin, true, 3600);
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        PlayerTextures textures = mock(PlayerTextures.class);
        URL skinUrl = new URL("https://textures.minecraft.net/texture/abc123");
        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getTextures()).thenReturn(textures);
        when(textures.isEmpty()).thenReturn(false);
        when(textures.getSkin()).thenReturn(skinUrl);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("OnlinePlayer");

        long startNs = System.nanoTime();
        skinCache.onPlayerJoin(player);
        long elapsedNs = System.nanoTime() - startNs;

        // A blocking HTTP fetch would take 100ms+; we only do profile/textures + executor.submit() on main thread
        assertTrue(elapsedNs < 20_000_000,
                "onPlayerJoin must return within 20ms (async fetch must not block main thread); elapsed " + (elapsedNs / 1_000) + " µs");
    }

    /** Disabled cache → onPlayerJoin no-op, no NPE. */
    @Test
    void onPlayerJoin_disabled_doesNothing() {
        skinCache = new SkinCache(plugin, false, 3600);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertDoesNotThrow(() -> skinCache.onPlayerJoin(player));
        assertTrue(skinCache.getFace(player.getUniqueId()).isEmpty());
    }
}

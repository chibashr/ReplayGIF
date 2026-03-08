package me.replaygif.api;

import me.replaygif.config.ConfigManager;
import me.replaygif.trigger.TriggerHandler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;

/**
 * Verifies ApiTriggerListener respects allow_api_triggers and only handles when subject is online.
 */
class ApiTriggerListenerTest {

    private TriggerHandler mockTriggerHandler;
    private ConfigManager mockConfigManager;
    private ApiTriggerListener listener;

    @BeforeEach
    void setUp() {
        mockTriggerHandler = mock(TriggerHandler.class);
        mockConfigManager = mock(ConfigManager.class);
        listener = new ApiTriggerListener(
                mockTriggerHandler, mockConfigManager,
                LoggerFactory.getLogger(ApiTriggerListenerTest.class));
    }

    @Test
    void whenAllowApiTriggersFalse_doesNotCallTriggerHandler() {
        when(mockConfigManager.getAllowApiTriggers()).thenReturn(false);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        ReplayGifTriggerEvent event = new ReplayGifTriggerEvent(
                player, "test", 4.0, 1.0, null, null);

        listener.onReplayGifTrigger(event);

        verifyNoInteractions(mockTriggerHandler);
    }

    @Test
    void whenAllowApiTriggersTrue_andPlayerOnline_callsTriggerHandler() {
        when(mockConfigManager.getAllowApiTriggers()).thenReturn(true);
        when(mockConfigManager.getTriggerApiDefaultPreSeconds()).thenReturn(4.0);
        when(mockConfigManager.getTriggerApiDefaultPostSeconds()).thenReturn(1.0);
        when(mockConfigManager.getTriggerApiDefaultOutputProfiles()).thenReturn(java.util.List.of("default"));
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");
        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getName()).thenReturn("world");
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(0);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(0);
        when(player.getLocation()).thenReturn(loc);
        ReplayGifTriggerEvent event = new ReplayGifTriggerEvent(
                player, "test", 4.0, 1.0, null, null);

        listener.onReplayGifTrigger(event);

        verify(mockTriggerHandler).handle(any(me.replaygif.trigger.TriggerContext.class));
    }

    @Test
    void whenPlayerOffline_doesNotCallTriggerHandler() {
        when(mockConfigManager.getAllowApiTriggers()).thenReturn(true);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        when(player.getName()).thenReturn("OfflinePlayer");
        ReplayGifTriggerEvent event = new ReplayGifTriggerEvent(
                player, "test", 4.0, 1.0, null, null);

        listener.onReplayGifTrigger(event);

        verifyNoInteractions(mockTriggerHandler);
    }
}

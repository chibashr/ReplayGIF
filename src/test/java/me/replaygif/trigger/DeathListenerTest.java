package me.replaygif.trigger;

import me.replaygif.config.TriggerRule;
import me.replaygif.config.TriggerRuleRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests for DeathListener (section 1a resolution).
 */
class DeathListenerTest {

    private TriggerHandler mockTriggerHandler;
    private TriggerRuleRegistry mockRuleRegistry;
    private DeathListener listener;

    @BeforeEach
    void setUp() {
        mockTriggerHandler = mock(TriggerHandler.class);
        mockRuleRegistry = mock(TriggerRuleRegistry.class);
        listener = new DeathListener(mockTriggerHandler, mockRuleRegistry,
                LoggerFactory.getLogger(DeathListenerTest.class));
    }

    @Test
    void onPlayerDeath_handlesContextToTriggerHandler() {
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("TestPlayer");
        Location loc = mock(Location.class);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(loc);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(100);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(-200);
        when(world.getName()).thenReturn("world");

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.deathMessage()).thenReturn(null);

        TriggerRule rule = new TriggerRule(
                "org.bukkit.event.entity.PlayerDeathEvent",
                null,
                List.of("getEntity"),
                List.of("default"),
                4.0, 1.0,
                null, "{player}",
                List.of(),
                true);
        when(mockRuleRegistry.getPlayerDeathRule()).thenReturn(Optional.of(rule));

        listener.onPlayerDeath(event);

        verify(mockTriggerHandler).handle(argThat((TriggerContext ctx) ->
                ctx.subjectUUID.equals(playerUuid)
                        && "TestPlayer".equals(ctx.subjectName)
                        && ctx.eventLabel.contains("died")
                        && ctx.outputProfileNames.equals(List.of("default"))
                        && ctx.preSeconds == 4.0
                        && ctx.postSeconds == 1.0));
    }

    @Test
    void onPlayerDeath_noRule_doesNotCallHandler() {
        when(mockRuleRegistry.getPlayerDeathRule()).thenReturn(Optional.empty());
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(Player.class));

        listener.onPlayerDeath(event);

        verifyNoInteractions(mockTriggerHandler);
    }

    @Test
    void onPlayerDeath_ruleDisabled_doesNotCallHandler() {
        TriggerRule rule = new TriggerRule(
                "org.bukkit.event.entity.PlayerDeathEvent",
                null, List.of("getEntity"),
                List.of("default"), 4.0, 1.0,
                null, "{player}", List.of(),
                false);
        when(mockRuleRegistry.getPlayerDeathRule()).thenReturn(Optional.of(rule));
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(mock(Player.class));

        listener.onPlayerDeath(event);

        verifyNoInteractions(mockTriggerHandler);
    }
}

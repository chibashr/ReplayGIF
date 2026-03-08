package me.replaygif.trigger;

import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.config.TriggerRuleRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DynamicListenerRegistry covering DL1–DL4 from .planning/testing.md.
 */
class DynamicListenerRegistryTest {

    @TempDir
    Path tempDir;

    private TriggerHandler mockTriggerHandler;
    private JavaPlugin mockPlugin;
    private PluginManager mockPluginManager;
    private TriggerRuleRegistry ruleRegistry;

    @BeforeEach
    void setUp() throws Exception {
        mockTriggerHandler = mock(TriggerHandler.class);
        mockPlugin = mock(JavaPlugin.class);
        when(mockPlugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(DynamicListenerRegistryTest.class));
        mockPluginManager = mock(PluginManager.class);
    }

    private void createRegistryWithTriggers(String triggersYaml) throws Exception {
        var dataFolder = tempDir.toFile();
        Files.writeString(tempDir.resolve("config.yml"), "");
        Files.writeString(tempDir.resolve("renderer.yml"), "");
        Files.writeString(tempDir.resolve("outputs.yml"), """
                profiles:
                  default:
                    - type: filesystem
                      path_template: "out.gif"
                """);
        Files.writeString(tempDir.resolve("triggers.yml"), triggersYaml);
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        ConfigManager configManager = new ConfigManager(mockPlugin);
        configManager.load();
        OutputProfileRegistry outputRegistry = new OutputProfileRegistry(configManager, mockPlugin);
        ruleRegistry = new TriggerRuleRegistry(configManager, outputRegistry, mockPlugin.getSLF4JLogger());
    }

    /** DL1 — Valid event class registered: fire event, trigger fires and render job starts. */
    @Test
    void dl1_validEventClass_registeredAndTriggerFires() throws Exception {
        createRegistryWithTriggers("""
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                  advancement:
                    enabled: true
                    event_class: "org.bukkit.event.player.PlayerAdvancementDoneEvent"
                    resolver: getPlayer
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);

        var captor = org.mockito.ArgumentCaptor.forClass(EventExecutor.class);
        DynamicListenerRegistry registry = new DynamicListenerRegistry(
                mockTriggerHandler, ruleRegistry, mockPlugin, mockPluginManager,
                LoggerFactory.getLogger(DynamicListenerRegistryTest.class));
        registry.register();

        verify(mockPluginManager).registerEvent(
                eq(PlayerAdvancementDoneEvent.class),
                any(org.bukkit.event.Listener.class),
                eq(EventPriority.MONITOR),
                captor.capture(),
                eq(mockPlugin));

        PlayerAdvancementDoneEvent event = mock(PlayerAdvancementDoneEvent.class);
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        Location loc = mock(Location.class);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(loc);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(0);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(0);
        when(world.getName()).thenReturn("world");
        when(event.getPlayer()).thenReturn(player);

        captor.getValue().execute(null, event);

        verify(mockTriggerHandler).handle(argThat((TriggerContext ctx) ->
                ctx.subjectUUID.equals(playerUuid) && "TestPlayer".equals(ctx.subjectName)));
    }

    /** DL2 — Invalid class name: plugin starts without crash, WARN logged, no listener registered. */
    @Test
    void dl2_invalidClassName_noCrashNoRegistration() throws Exception {
        createRegistryWithTriggers("""
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                  fake_rule:
                    enabled: true
                    event_class: "com.fake.NonExistentEvent"
                    resolver: getPlayer
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);

        DynamicListenerRegistry registry = new DynamicListenerRegistry(
                mockTriggerHandler, ruleRegistry, mockPlugin, mockPluginManager,
                LoggerFactory.getLogger(DynamicListenerRegistryTest.class));
        registry.register();

        // Only player_death is in internal rules; fake_rule is skipped due to ClassNotFoundException.
        // So registerEvent should not be called for any dynamic rule (only player_death exists and is excluded).
        verify(mockPluginManager, never()).registerEvent(
                any(Class.class),
                any(org.bukkit.event.Listener.class),
                any(EventPriority.class),
                any(EventExecutor.class),
                any(JavaPlugin.class));
    }

    /** DL3 — Non-player entity: event fired for non-player entity is silently skipped, no render job. */
    @Test
    void dl3_nonPlayerEntity_skippedNoRenderJob() throws Exception {
        createRegistryWithTriggers("""
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                  entity_damage:
                    enabled: true
                    event_class: "org.bukkit.event.entity.EntityDamageEvent"
                    resolver: getEntity
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);

        var captor = org.mockito.ArgumentCaptor.forClass(EventExecutor.class);
        DynamicListenerRegistry registry = new DynamicListenerRegistry(
                mockTriggerHandler, ruleRegistry, mockPlugin, mockPluginManager,
                LoggerFactory.getLogger(DynamicListenerRegistryTest.class));
        registry.register();

        verify(mockPluginManager).registerEvent(
                eq(EntityDamageEvent.class),
                any(org.bukkit.event.Listener.class),
                eq(EventPriority.MONITOR),
                captor.capture(),
                eq(mockPlugin));

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        org.bukkit.entity.Zombie zombie = mock(org.bukkit.entity.Zombie.class);
        when(event.getEntity()).thenReturn(zombie);

        captor.getValue().execute(null, event);

        verifyNoInteractions(mockTriggerHandler);
    }

    /** DL4 — Condition filtering: FALL fires trigger, ENTITY_ATTACK does not. */
    @Test
    void dl4_conditionFiltering_fallFiresEntityAttackDoesNot() throws Exception {
        createRegistryWithTriggers("""
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                  entity_damage_fall:
                    enabled: true
                    event_class: "org.bukkit.event.entity.EntityDamageEvent"
                    resolver: getEntity
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                    conditions:
                      - method_chain: getCause.name
                        operator: EQUALS
                        expected_value: FALL
                inbound:
                  use_default_for_unmatched: false
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);

        var captor = org.mockito.ArgumentCaptor.forClass(EventExecutor.class);
        DynamicListenerRegistry registry = new DynamicListenerRegistry(
                mockTriggerHandler, ruleRegistry, mockPlugin, mockPluginManager,
                LoggerFactory.getLogger(DynamicListenerRegistryTest.class));
        registry.register();

        verify(mockPluginManager).registerEvent(
                eq(EntityDamageEvent.class),
                any(org.bukkit.event.Listener.class),
                eq(EventPriority.MONITOR),
                captor.capture(),
                eq(mockPlugin));

        EventExecutor executor = captor.getValue();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("P");
        when(player.isOnline()).thenReturn(true);
        Location loc = mock(Location.class);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(loc);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(0);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(0);
        when(world.getName()).thenReturn("world");

        EntityDamageEvent eventFall = mock(EntityDamageEvent.class);
        when(eventFall.getEntity()).thenReturn(player);
        when(eventFall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        executor.execute(null, eventFall);
        verify(mockTriggerHandler).handle(any(TriggerContext.class));

        EntityDamageEvent eventAttack = mock(EntityDamageEvent.class);
        when(eventAttack.getEntity()).thenReturn(player);
        when(eventAttack.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        executor.execute(null, eventAttack);
        verify(mockTriggerHandler, times(1)).handle(any(TriggerContext.class));
    }
}

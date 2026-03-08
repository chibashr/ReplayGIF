package me.replaygif.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CF1 — Missing output profile reference: triggers.yml references a profile that does not exist
 * in outputs.yml. On load, an ERROR is logged, the rule is skipped, other rules load normally.
 */
class TriggerRuleRegistryCF1Test {

    @TempDir
    Path tempDir;

    private File dataFolder;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() throws IOException {
        dataFolder = tempDir.toFile();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("TriggerRuleRegistryCF1Test"));
    }

    @Test
    void cf1_missingOutputProfileReference_ruleSkippedOtherRulesLoad() throws IOException {
        // outputs.yml: only "default" profile with one valid target. No "missing_profile".
        Files.writeString(dataFolder.toPath().resolve("outputs.yml"), """
                profiles:
                  default:
                    - type: filesystem
                      path_template: "out.gif"
                """);

        // triggers.yml: player_death uses "default" (exists); test_rule uses "missing_profile" (does not exist).
        Files.writeString(dataFolder.toPath().resolve("triggers.yml"), """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                  test_rule_with_missing_profile:
                    enabled: true
                    event_class: "org.bukkit.event.player.PlayerAdvancementDoneEvent"
                    resolver: getPlayer
                    output_profiles: ["missing_profile"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);

        // Empty config/renderer so load() does not fail
        Files.writeString(dataFolder.toPath().resolve("config.yml"), "");
        Files.writeString(dataFolder.toPath().resolve("renderer.yml"), "");

        ConfigManager configManager = new ConfigManager(plugin);
        configManager.load();
        OutputProfileRegistry outputRegistry = new OutputProfileRegistry(configManager, plugin);
        TriggerRuleRegistry triggerRegistry = new TriggerRuleRegistry(configManager, outputRegistry, plugin.getSLF4JLogger());

        // Rule referencing "missing_profile" must be skipped (not in registry).
        assertFalse(
                triggerRegistry.getInternalRules().stream()
                        .anyMatch(r -> r.outputProfileNames.contains("missing_profile")),
                "No rule with output_profiles containing 'missing_profile' should remain");

        // player_death rule (references "default") must still be present.
        assertTrue(
                triggerRegistry.getInternalRules().stream()
                        .anyMatch(r -> r.pattern.contains("PlayerDeathEvent")),
                "player_death rule should remain");
    }
}

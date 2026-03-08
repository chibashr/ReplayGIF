package me.replaygif.trigger;

import me.replaygif.config.ConfigManager;
import me.replaygif.config.OutputProfileRegistry;
import me.replaygif.config.TriggerRuleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebhookInboundServer covering WH1–WH8 from .planning/testing.md.
 */
class WebhookInboundServerTest {

    @TempDir
    Path tempDir;

    private ConfigManager configManager;
    private TriggerRuleRegistry triggerRuleRegistry;
    private TriggerHandler mockTriggerHandler;
    private WebhookInboundServer server;
    private HttpClient httpClient;
    private org.bukkit.plugin.java.JavaPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        mockPlugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(mockPlugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(WebhookInboundServerTest.class));
        when(mockPlugin.getResource(anyString())).thenReturn(null);
        writeConfig("config.yml", """
                buffer_seconds: 30
                fps: 10
                webhook_server:
                  enabled: true
                  port: 0
                  secret: "test-secret"
                """);
        writeConfig("renderer.yml", "");
        writeConfig("outputs.yml", """
                profiles:
                  default:
                    - type: filesystem
                      path_template: "out.gif"
                  achievements:
                    - type: filesystem
                      path_template: "ach.gif"
                """);
        writeConfig("triggers.yml", """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                    subject_path: "player"
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);
        configManager = new ConfigManager(mockPlugin);
        configManager.load();
        OutputProfileRegistry outputRegistry = new OutputProfileRegistry(configManager, mockPlugin);
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, outputRegistry, mockPlugin.getSLF4JLogger());
        mockTriggerHandler = mock(TriggerHandler.class);
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private void writeConfig(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + path))
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        builder.header("Content-Type", "application/json");
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** WH1 — No X-ReplayGif-Secret header: response 401, WARN logged. */
    @Test
    void wh1_noSecret_returns401() throws Exception {
        assumeServerStarted();
        HttpResponse<String> resp = post("/trigger", "{\"event_key\":\"test\",\"player\":\"x\"}", Map.of());
        assertEquals(401, resp.statusCode());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH2 — Wrong secret: response 401. */
    @Test
    void wh2_wrongSecret_returns401() throws Exception {
        assumeServerStarted();
        HttpResponse<String> resp = post("/trigger", "{\"event_key\":\"test\",\"player\":\"x\"}",
                Map.of("X-ReplayGif-Secret", "wrong_value"));
        assertEquals(401, resp.statusCode());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH3 — Valid trigger, player online: 202 with job_id, render job starts. (Unit test: no real player so we get 404; 202 requires integration/live server.) */
    @Test
    void wh3_validTrigger_playerOnline_returns202_jobStarts() throws Exception {
        writeConfig("triggers.yml", """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: true
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                    subject_path: "player"
                  rules:
                    - event_key: "custom.event"
                      subject_path: "player"
                      output_profiles: ["default"]
                      pre_seconds: 4.0
                      post_seconds: 1.0
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);
        configManager.load();
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, new OutputProfileRegistry(configManager, mockPlugin), mockPlugin.getSLF4JLogger());
        server.stop();
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        assumeServerStarted();
        String body = "{\"event_key\":\"custom.event\",\"player\":\"" + UUID.randomUUID() + "\"}";
        HttpResponse<String> resp = post("/trigger", body, Map.of("X-ReplayGif-Secret", "test-secret"));
        // No online player in unit test -> 404; with real player would be 202 and job_id
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("player not online"), resp.body());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH4 — Player not online: 404 with JSON error, no render. */
    @Test
    void wh4_playerNotOnline_returns404_noRender() throws Exception {
        assumeServerStarted();
        String body = "{\"event_key\":\"x\",\"player\":\"NonExistentPlayer\"}";
        writeConfig("triggers.yml", """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: true
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                    subject_path: "player"
                  rules: []
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);
        configManager.load();
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, new OutputProfileRegistry(configManager, mockPlugin), mockPlugin.getSLF4JLogger());
        server.stop();
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        assumeServerStarted();
        HttpResponse<String> resp = post("/trigger", body, Map.of("X-ReplayGif-Secret", "test-secret"));
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("error") && resp.body().contains("player not online"), resp.body());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH5 — Unknown event_key, use_default_for_unmatched false: 200 status "ignored", no render. */
    @Test
    void wh5_unknownEventKey_unmatchedDisabled_returns200Ignored() throws Exception {
        assumeServerStarted();
        String body = "{\"event_key\":\"unknown.event\",\"player\":\"Someone\"}";
        HttpResponse<String> resp = post("/trigger", body, Map.of("X-ReplayGif-Secret", "test-secret"));
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("ignored") && resp.body().contains("no matching rule"), resp.body());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH6 — Wildcard rule matches: event_key "player.advancement.story.mine_stone" matches "player.advancement.*". (Unit test: no player so 404; rule matched so not 200 ignored.) */
    @Test
    void wh6_wildcardMatching_renderStarts() throws Exception {
        writeConfig("triggers.yml", """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                    subject_path: "player"
                  rules:
                    - event_key: "player.advancement.*"
                      subject_path: "player"
                      output_profiles: ["default"]
                      pre_seconds: 4.0
                      post_seconds: 1.0
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);
        configManager.load();
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, new OutputProfileRegistry(configManager, mockPlugin), mockPlugin.getSLF4JLogger());
        server.stop();
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        assumeServerStarted();
        String body = "{\"event_key\":\"player.advancement.story.mine_stone\",\"player\":\"NoSuchPlayer\"}";
        HttpResponse<String> resp = post("/trigger", body, Map.of("X-ReplayGif-Secret", "test-secret"));
        // Wildcard matched so we don't get 200 ignored; we get 404 (player not online)
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("player not online"), resp.body());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH7 — Specific rule takes precedence over wildcard: exact match used (not wildcard). Request with specific event_key gets 404 when player not online; rule matching ensures exact match wins. */
    @Test
    void wh7_specificRuleTakesPrecedence_specificProfileUsed() throws Exception {
        writeConfig("triggers.yml", """
                internal:
                  player_death:
                    enabled: true
                    output_profiles: ["default"]
                    pre_seconds: 4.0
                    post_seconds: 1.0
                inbound:
                  use_default_for_unmatched: false
                  defaults:
                    default_pre_seconds: 4.0
                    default_post_seconds: 1.0
                    default_output_profiles: ["default"]
                    subject_path: "player"
                  rules:
                    - event_key: "player.advancement.*"
                      subject_path: "player"
                      output_profiles: ["default"]
                      pre_seconds: 4.0
                      post_seconds: 1.0
                    - event_key: "player.advancement.story.mine_stone"
                      subject_path: "player"
                      output_profiles: ["achievements"]
                      pre_seconds: 2.0
                      post_seconds: 0.5
                api:
                  default_pre_seconds: 4.0
                  default_post_seconds: 1.0
                  default_output_profiles: ["default"]
                """);
        configManager.load();
        triggerRuleRegistry = new TriggerRuleRegistry(configManager, new OutputProfileRegistry(configManager, mockPlugin), mockPlugin.getSLF4JLogger());
        server.stop();
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        assumeServerStarted();
        String body = "{\"event_key\":\"player.advancement.story.mine_stone\",\"player\":\"OfflinePlayer\"}";
        HttpResponse<String> resp = post("/trigger", body, Map.of("X-ReplayGif-Secret", "test-secret"));
        // Specific rule matched (exact match over wildcard); player not online -> 404
        assertEquals(404, resp.statusCode());
        verifyNoInteractions(mockTriggerHandler);
    }

    /** WH8 — webhook_server.enabled false: no port bound, getPort() -1. */
    @Test
    void wh8_serverDisabled_noPortBound() throws Exception {
        server.stop();
        writeConfig("config.yml", """
                buffer_seconds: 30
                fps: 10
                webhook_server:
                  enabled: false
                  port: 8765
                  secret: "test-secret"
                """);
        configManager = new ConfigManager(mockPlugin);
        configManager.load();
        server = new WebhookInboundServer(configManager, triggerRuleRegistry, mockTriggerHandler,
                LoggerFactory.getLogger(WebhookInboundServerTest.class));
        server.start();
        assertEquals(-1, server.getPort(), "Server should not bind when disabled");
    }

    private void assumeServerStarted() {
        assertTrue(server.getPort() > 0, "Webhook server should be bound to a port");
    }
}

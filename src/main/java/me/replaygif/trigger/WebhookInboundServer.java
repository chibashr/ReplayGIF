package me.replaygif.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.replaygif.config.ConfigManager;
import me.replaygif.config.TriggerRule;
import me.replaygif.config.TriggerRuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Embedded HttpServer for inbound POST /trigger. Binds only if webhook_server.enabled is true.
 * Validation and response codes per trigger-resolution.md section 2.
 */
public final class WebhookInboundServer {

    private static final String TRIGGER_PATH = "/trigger";
    private static final String HEADER_SECRET = "X-ReplayGif-Secret";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int BODY_LOG_LIMIT = 200;

    private final ConfigManager configManager;
    private final TriggerRuleRegistry triggerRuleRegistry;
    private final TriggerHandler triggerHandler;
    private final Logger logger;
    private HttpServer server;

    public WebhookInboundServer(ConfigManager configManager,
                                TriggerRuleRegistry triggerRuleRegistry,
                                TriggerHandler triggerHandler,
                                Logger logger) {
        this.configManager = configManager;
        this.triggerRuleRegistry = triggerRuleRegistry;
        this.triggerHandler = triggerHandler;
        this.logger = logger;
    }

    /**
     * Starts the HTTP server if webhook_server.enabled is true. No-op otherwise.
     * If port is in use, logs at ERROR and does not crash.
     */
    public void start() {
        if (!configManager.getWebhookServerEnabled()) {
            return;
        }
        int port = configManager.getWebhookServerPort();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(TRIGGER_PATH, this::handleTrigger);
            server.setExecutor(null);
            server.start();
            logger.info("Inbound webhook server listening on port {}", port);
        } catch (IOException e) {
            logger.error("Inbound webhook server could not bind to port {}: {}", port, e.getMessage());
        }
    }

    /** Returns the bound port, or -1 if not started. */
    public int getPort() {
        return server != null && server.getAddress() != null ? server.getAddress().getPort() : -1;
    }

    /**
     * Stops the server. No-op if not started or already stopped.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("Inbound webhook server stopped.");
        }
    }

    private void handleTrigger(HttpExchange exchange) {
        try {
            handleTriggerInternal(exchange);
        } catch (IOException e) {
            logger.warn("Inbound webhook I/O error: {}", e.getMessage());
            try {
                sendJson(exchange, 500, "{\"error\": \"internal error\"}");
            } catch (IOException ignored) {}
        } catch (Exception e) {
            logger.warn("Inbound webhook error: {}", e.getMessage(), e);
            try {
                sendJson(exchange, 500, "{\"error\": \"internal error\"}");
            } catch (IOException ignored) {}
        }
    }

    private void handleTriggerInternal(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        // 1. Method must be POST
        if (!"POST".equalsIgnoreCase(method)) {
            sendResponse(exchange, 405, null);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        // 2. Path must be exactly /trigger
        if (!TRIGGER_PATH.equals(path)) {
            sendResponse(exchange, 404, null);
            return;
        }
        String remoteAddress = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().toString() : "unknown";
        String secret = configManager.getWebhookServerSecret();
        String headerSecret = exchange.getRequestHeaders().getFirst(HEADER_SECRET);
        // 3. Header X-ReplayGif-Secret must exactly match
        if (headerSecret == null || !secret.equals(headerSecret)) {
            logger.warn("Inbound webhook: unauthorized request from {}", remoteAddress);
            sendJson(exchange, 401, "{}");
            return;
        }
        // 4. Content-Type must start with application/json
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.trim().toLowerCase().startsWith(CONTENT_TYPE_JSON)) {
            logger.warn("Inbound webhook: Content-Type not application/json");
            sendResponse(exchange, 415, null);
            return;
        }
        // 5. Read and parse JSON
        String body = readBody(exchange);
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new RuntimeException("Not a JSON object");
            }
            json = parsed.getAsJsonObject();
        } catch (Exception e) {
            logger.warn("Inbound webhook: invalid JSON. Body (first {} chars): {}", BODY_LOG_LIMIT,
                    body.length() > BODY_LOG_LIMIT ? body.substring(0, BODY_LOG_LIMIT) : body);
            sendJson(exchange, 400, "{\"error\": \"invalid JSON\"}");
            return;
        }
        // 6. event_key required
        String eventKey = getStringOrNull(json, "event_key");
        if (eventKey == null || eventKey.isBlank()) {
            logger.warn("Inbound webhook: missing event_key");
            sendJson(exchange, 400, "{\"error\": \"missing event_key\"}");
            return;
        }
        boolean useDefaultForUnmatched = configManager.getTriggerInboundUseDefaultForUnmatched();
        Optional<TriggerRule> ruleOpt = triggerRuleRegistry.matchInbound(eventKey);
        double preSeconds;
        double postSeconds;
        List<String> outputProfileNames;
        String subjectPath;
        TriggerRule matchedRule;
        if (ruleOpt.isPresent()) {
            matchedRule = ruleOpt.get();
            preSeconds = getDouble(json, "pre_seconds", matchedRule.preSeconds);
            postSeconds = getDouble(json, "post_seconds", matchedRule.postSeconds);
            outputProfileNames = getOutputProfileList(json, matchedRule.outputProfileNames);
            subjectPath = matchedRule.inboundSubjectPath;
        } else {
            if (!useDefaultForUnmatched) {
                logger.debug("Inbound webhook: no matching rule for event_key {}", eventKey);
                sendJson(exchange, 200, "{\"status\": \"ignored\", \"reason\": \"no matching rule\"}");
                return;
            }
            matchedRule = null;
            preSeconds = getDouble(json, "pre_seconds", configManager.getTriggerInboundDefaultPreSeconds());
            postSeconds = getDouble(json, "post_seconds", configManager.getTriggerInboundDefaultPostSeconds());
            outputProfileNames = getOutputProfileList(json, configManager.getTriggerInboundDefaultOutputProfiles());
            subjectPath = configManager.getTriggerInboundDefaultSubjectPath();
        }
        // 12. Resolve subject at path
        String subjectValue = resolvePath(json, subjectPath);
        if (subjectValue == null) {
            logger.warn("Inbound webhook: subject not resolvable at path: {}", subjectPath);
            sendJson(exchange, 400, "{\"error\": \"subject not resolvable at path: " + escapeJson(subjectPath) + "\"}");
            return;
        }
        // 13. Resolve player: UUID first, then name
        Player player = null;
        try {
            UUID uuid = UUID.fromString(subjectValue);
            player = Bukkit.getPlayer(uuid);
        } catch (IllegalArgumentException ignored) {
            // not a valid UUID
        }
        if (player == null) {
            player = Bukkit.getPlayerExact(subjectValue);
        }
        if (player == null || !player.isOnline()) {
            logger.warn("Inbound webhook: player not online: {}", subjectValue);
            sendJson(exchange, 404, "{\"error\": \"player not online: " + escapeJson(subjectValue) + "\"}");
            return;
        }
        String eventLabel = (matchedRule != null)
                ? resolveLabel(json, matchedRule.labelPath, matchedRule.labelFallback, player.getName())
                : eventKey;
        Map<String, String> metadata = flattenMetadata(json.get("metadata"));
        UUID jobId = UUID.randomUUID();
        TriggerContext context = new TriggerContext.Builder()
                .subjectUUID(player.getUniqueId())
                .subjectName(player.getName())
                .eventLabel(eventLabel != null ? eventLabel : eventKey)
                .preSeconds(preSeconds)
                .postSeconds(postSeconds)
                .outputProfileNames(outputProfileNames)
                .metadata(metadata)
                .triggerTimestamp(System.currentTimeMillis())
                .jobId(jobId)
                .triggerX(player.getLocation().getBlockX())
                .triggerY(player.getLocation().getBlockY())
                .triggerZ(player.getLocation().getBlockZ())
                .dimension(dimensionFromWorld(player.getLocation().getWorld()))
                .worldName(player.getLocation().getWorld() != null ? player.getLocation().getWorld().getName() : "world")
                .build();
        // 15. Respond 202 with job_id
        sendJson(exchange, 202, "{\"job_id\": \"" + jobId + "\"}");
        logger.info("[{}] Inbound trigger accepted: event={} player={}", jobId, eventKey, player.getName());
        // 16. Submit to TriggerHandler
        triggerHandler.handle(context);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        exchange.sendResponseHeaders(code, body != null ? body.getBytes(StandardCharsets.UTF_8).length : -1);
        if (body != null && body.length() > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        } else if (body == null && code >= 0) {
            exchange.getResponseBody().close();
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String getStringOrNull(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonElement e = json.get(key);
        return e == null || e.isJsonNull() ? null : (e.isJsonPrimitive() ? e.getAsString() : null);
    }

    private static Double getDouble(JsonObject json, String key, double defaultVal) {
        if (!json.has(key)) return defaultVal;
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive()) return defaultVal;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return defaultVal;
        }
    }

    private static List<String> getOutputProfileList(JsonObject json, List<String> ruleDefault) {
        if (!json.has("output_profile")) return ruleDefault;
        JsonElement e = json.get("output_profile");
        if (e == null || e.isJsonNull()) return ruleDefault;
        if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            return s != null && !s.isBlank() ? List.of(s) : ruleDefault;
        }
        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            List<String> list = new ArrayList<>();
            for (JsonElement item : arr) {
                if (item != null && item.isJsonPrimitive()) {
                    list.add(item.getAsString());
                }
            }
            return list.isEmpty() ? ruleDefault : list;
        }
        return ruleDefault;
    }

    private static Map<String, String> flattenMetadata(JsonElement meta) {
        if (meta == null || !meta.isJsonObject()) return Map.of();
        Map<String, String> out = new TreeMap<>();
        flattenInto(meta.getAsJsonObject(), "", out);
        return out;
    }

    private static void flattenInto(JsonObject obj, String prefix, Map<String, String> out) {
        for (String key : obj.keySet()) {
            JsonElement val = obj.get(key);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (val == null || val.isJsonNull()) {
                out.put(path, "");
            } else if (val.isJsonPrimitive()) {
                out.put(path, val.getAsString());
            } else if (val.isJsonObject()) {
                flattenInto(val.getAsJsonObject(), path, out);
            } else if (val.isJsonArray()) {
                out.put(path, val.toString());
            }
        }
    }

    /** Walk dot-notation path; return terminal string value or null if missing/not string. */
    private static String resolvePath(JsonObject root, String path) {
        if (path == null || path.isBlank()) return null;
        String[] segments = path.split("\\.");
        JsonElement current = root;
        for (String seg : segments) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(seg);
        }
        if (current == null || current.isJsonNull()) return null;
        if (!current.isJsonPrimitive()) return null;
        return current.getAsString();
    }

    private static String resolveLabel(JsonObject json, String labelPath, String labelFallback, String playerName) {
        if (labelPath != null && !labelPath.isBlank()) {
            String resolved = resolvePath(json, labelPath);
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        if (labelFallback != null && playerName != null) {
            return labelFallback.replace("{player}", playerName);
        }
        return labelFallback != null ? labelFallback : "";
    }

    private static String dimensionFromWorld(org.bukkit.World world) {
        if (world == null) return "world";
        if (world instanceof org.bukkit.Keyed k && k.getKey() != null) {
            return k.getKey().toString();
        }
        return world.getName();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

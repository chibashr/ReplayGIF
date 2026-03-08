package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the GIF to a Discord webhook URL with an embed (title, description, fields from
 * context and metadata). Uses multipart/form-data (payload_json + file) per Discord's
 * webhook API so the message shows both embed and inline GIF.
 */
public class DiscordWebhookOutput implements OutputTarget {

    private static final String BOUNDARY = "----ReplayGifBoundary" + UUID.randomUUID().toString().replace("-", "");
    private static final String CRLF = "\r\n";

    private final String url;
    private final Logger logger;

    /**
     * @param url    Discord webhook URL (e.g. https://discord.com/api/webhooks/...)
     * @param logger for non-2xx or I/O errors
     */
    public DiscordWebhookOutput(String url, Logger logger) {
        this.url = url;
        this.logger = logger;
    }

    @Override
    public void dispatch(TriggerContext context, byte[] gifBytes) {
        if (gifBytes == null) {
            gifBytes = new byte[0];
        }
        String filename = TemplateVariableResolver.resolve(context, "{player}_{event}.gif");
        filename = filename.replaceAll("[/\\\\]", "_");
        if (filename.isEmpty()) {
            filename = "replay.gif";
        }
        String payloadJson = buildEmbedJson(context);

        try {
            // Malformed URL (e.g. typo in config) throws MalformedURLException, which is an IOException — caught below so no exception reaches console
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            try (OutputStream out = conn.getOutputStream()) {
                writeMultipart(out, payloadJson, filename, gifBytes);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.error("[{}] DiscordWebhookOutput received non-2xx response: {}", context.jobId, code);
            }
        } catch (IOException e) {
            logger.error("[{}] DiscordWebhookOutput failed: {}", context.jobId, e.getMessage()); // includes malformed URL, connection refused, etc.
        }
    }

    private String buildEmbedJson(TriggerContext context) {
        String title = TemplateVariableResolver.resolve(context, "{player} - {event}");
        String desc = TemplateVariableResolver.resolve(context, "At {x}, {y}, {z} | {world} | {dimension}");
        String ts = TemplateVariableResolver.resolve(context, "{timestamp}");
        String date = TemplateVariableResolver.resolve(context, "{date}");
        String time = TemplateVariableResolver.resolve(context, "{time}");
        String jobId = context.jobId.toString();

        StringBuilder fields = new StringBuilder();
        appendField(fields, "Timestamp", ts);
        appendField(fields, "Date", date);
        appendField(fields, "Time", time);
        appendField(fields, "Job ID", jobId);
        if (context.metadata != null) {
            for (Map.Entry<String, String> e : context.metadata.entrySet()) {
                appendField(fields, e.getKey(), e.getValue() != null ? e.getValue() : "");
            }
        }

        return "{\"embeds\":[{\"title\":"
                + escapeJson(title)
                + ",\"description\":"
                + escapeJson(desc)
                + ",\"fields\":["
                + (fields.length() > 0 ? fields.substring(0, fields.length() - 1) : "")
                + "]}]}";
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        sb.append("{\"name\":").append(escapeJson(name)).append(",\"value\":").append(escapeJson(value)).append(",\"inline\":true},");
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private void writeMultipart(OutputStream out, String payloadJson, String filename, byte[] gifBytes) throws IOException {
        byte[] boundaryBytes = BOUNDARY.getBytes(StandardCharsets.UTF_8);
        byte[] crlf = CRLF.getBytes(StandardCharsets.UTF_8);

        out.write(("--").getBytes(StandardCharsets.UTF_8));
        out.write(boundaryBytes);
        out.write(crlf);
        out.write("Content-Disposition: form-data; name=\"payload_json\"".getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write("Content-Type: application/json".getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(crlf);
        out.write(payloadJson.getBytes(StandardCharsets.UTF_8));
        out.write(crlf);

        out.write(("--").getBytes(StandardCharsets.UTF_8));
        out.write(boundaryBytes);
        out.write(crlf);
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"").getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write("Content-Type: image/gif".getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
        out.write(crlf);
        out.write(gifBytes);
        out.write(crlf);

        out.write(("--").getBytes(StandardCharsets.UTF_8));
        out.write(boundaryBytes);
        out.write(("--").getBytes(StandardCharsets.UTF_8));
        out.write(crlf);
    }
}

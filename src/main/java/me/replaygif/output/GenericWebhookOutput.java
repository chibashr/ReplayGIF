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
 * POSTs the GIF to any URL as multipart/form-data (payload_json + file) so external systems
 * can receive replays without Discord-specific format. Optional custom headers allow auth or
 * custom endpoints. Metadata and context are included in payload_json for server-side use.
 */
public class GenericWebhookOutput implements OutputTarget {

    private static final String BOUNDARY = "----ReplayGifBoundary" + UUID.randomUUID().toString().replace("-", "");
    private static final String CRLF = "\r\n";

    private final String url;
    private final Map<String, String> headers;
    private final Logger logger;

    /**
     * @param url     POST endpoint (e.g. https://example.com/upload)
     * @param headers optional (e.g. Authorization); null treated as empty
     * @param logger  for non-2xx or I/O errors
     */
    public GenericWebhookOutput(String url, Map<String, String> headers, Logger logger) {
        this.url = url;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
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
        String payloadJson = buildMetadataJson(context);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            for (Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getKey() != null && !h.getKey().isBlank()) {
                    conn.setRequestProperty(h.getKey().trim(), h.getValue() != null ? h.getValue() : "");
                }
            }

            try (OutputStream out = conn.getOutputStream()) {
                writeMultipart(out, payloadJson, filename, gifBytes);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.error("[{}] GenericWebhookOutput received non-2xx response: {}", context.jobId, code);
            }
        } catch (IOException e) {
            logger.error("[{}] GenericWebhookOutput failed: {}", context.jobId, e.getMessage());
        }
    }

    private String buildMetadataJson(TriggerContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"player\":").append(escapeJson(context.subjectName));
        sb.append(",\"uuid\":").append(escapeJson(context.subjectUUID.toString()));
        sb.append(",\"event\":").append(escapeJson(context.eventLabel));
        sb.append(",\"x\":").append(context.triggerX);
        sb.append(",\"y\":").append(context.triggerY);
        sb.append(",\"z\":").append(context.triggerZ);
        sb.append(",\"dimension\":").append(escapeJson(context.dimension));
        sb.append(",\"world\":").append(escapeJson(context.worldName));
        sb.append(",\"timestamp\":").append(context.triggerTimestamp);
        sb.append(",\"date\":").append(escapeJson(TemplateVariableResolver.resolve(context, "{date}")));
        sb.append(",\"time\":").append(escapeJson(TemplateVariableResolver.resolve(context, "{time}")));
        sb.append(",\"job_id\":").append(escapeJson(context.jobId.toString()));
        if (context.metadata != null && !context.metadata.isEmpty()) {
            for (Map.Entry<String, String> e : context.metadata.entrySet()) {
                sb.append(",").append(escapeJson(e.getKey())).append(":").append(escapeJson(e.getValue() != null ? e.getValue() : ""));
            }
        }
        sb.append("}");
        return sb.toString();
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
        byte[] crlf = CRLF.getBytes(StandardCharsets.UTF_8);
        byte[] boundaryBytes = BOUNDARY.getBytes(StandardCharsets.UTF_8);

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

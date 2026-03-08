package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Single place for template substitution so embed text, filenames, and paths all use the
 * same variable set and formatting. resolve() is for display text; resolveForPath() strips
 * path-unsafe characters and ".." to avoid traversal when template comes from config or API.
 */
public final class TemplateVariableResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");

    private TemplateVariableResolver() {
    }

    /**
     * Replaces {player}, {uuid}, {event}, {x}, {y}, {z}, {dimension}, {world}, {timestamp},
     * {date}, {time}, {job_id}, and any {key} from context.metadata. Use for embeds and filenames.
     *
     * @param context  source of values; never null
     * @param template string with placeholders; null/empty returned as-is
     * @return substituted string
     */
    public static String resolve(TriggerContext context, String template) {
        if (template == null || template.isEmpty()) {
            return template == null ? null : "";
        }
        String date = formatDate(context.triggerTimestamp);
        String time = formatTime(context.triggerTimestamp);

        String out = template
                .replace("{player}", nullToEmpty(context.subjectName))
                .replace("{uuid}", context.subjectUUID.toString())
                .replace("{event}", nullToEmpty(context.eventLabel))
                .replace("{x}", String.valueOf(context.triggerX))
                .replace("{y}", String.valueOf(context.triggerY))
                .replace("{z}", String.valueOf(context.triggerZ))
                .replace("{dimension}", nullToEmpty(context.dimension))
                .replace("{world}", nullToEmpty(context.worldName))
                .replace("{timestamp}", String.valueOf(context.triggerTimestamp))
                .replace("{date}", date)
                .replace("{time}", time)
                .replace("{job_id}", context.jobId.toString());

        if (context.metadata != null) {
            for (Map.Entry<String, String> e : context.metadata.entrySet()) {
                String key = e.getKey();
                String value = e.getValue() != null ? e.getValue() : "";
                out = out.replace("{" + key + "}", value);
            }
        }
        return out;
    }

    /**
     * Same variables as resolve(), but substituted values are sanitized (no "..", path
     * separators replaced with _) so the result is safe to use as a path segment under the plugin dir.
     *
     * @param context  source of values; never null
     * @param template path template; null/empty returned as-is
     * @return path-safe string
     */
    public static String resolveForPath(TriggerContext context, String template) {
        if (template == null || template.isEmpty()) {
            return template == null ? null : "";
        }
        String date = formatDate(context.triggerTimestamp);
        String time = formatTime(context.triggerTimestamp);

        String out = template
                .replace("{player}", sanitizeForPath(context.subjectName))
                .replace("{uuid}", context.subjectUUID.toString())
                .replace("{event}", sanitizeForPath(context.eventLabel))
                .replace("{x}", String.valueOf(context.triggerX))
                .replace("{y}", String.valueOf(context.triggerY))
                .replace("{z}", String.valueOf(context.triggerZ))
                .replace("{dimension}", sanitizeForPath(context.dimension))
                .replace("{world}", sanitizeForPath(context.worldName))
                .replace("{timestamp}", String.valueOf(context.triggerTimestamp))
                .replace("{date}", date)
                .replace("{time}", time)
                .replace("{job_id}", context.jobId.toString());

        if (context.metadata != null) {
            for (Map.Entry<String, String> e : context.metadata.entrySet()) {
                String key = e.getKey();
                String value = sanitizeForPath(e.getValue());
                out = out.replace("{" + key + "}", value);
            }
        }
        return out;
    }

    private static String formatDate(long epochMs) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }

    private static String formatTime(long epochMs) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sanitizeForPath(String s) {
        if (s == null) return "";
        return s.replace("..", "").replaceAll("[/\\\\]", "_");
    }
}

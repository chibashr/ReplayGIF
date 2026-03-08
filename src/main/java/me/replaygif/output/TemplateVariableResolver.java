package me.replaygif.output;

import me.replaygif.trigger.TriggerContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Resolves template variables in output embed templates and filesystem path templates.
 * Supports all variables from the template variable reference table.
 */
public final class TemplateVariableResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");

    private TemplateVariableResolver() {
    }

    /**
     * Replaces all template variables in the given string using context.
     * Variables: {player}, {uuid}, {event}, {x}, {y}, {z}, {dimension}, {world},
     * {timestamp}, {date}, {time}, {job_id}, and {key} for any key in context.metadata.
     *
     * @param context  trigger context; never null
     * @param template template string; null/empty returns as-is
     * @return resolved string
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
     * Like {@link #resolve(TriggerContext, String)} but sanitizes substituted values
     * for use in filesystem paths: removes ".." and replaces path separators with underscore.
     *
     * @param context  trigger context; never null
     * @param template path template; null/empty returns as-is
     * @return resolved path segment string safe for filesystem use
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

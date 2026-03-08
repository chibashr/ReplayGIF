package me.replaygif.trigger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable. Created when a trigger fires, passed through the entire
 * pipeline from TriggerHandler to OutputTarget. Never modified after
 * construction.
 */
public final class TriggerContext {

    /** UUID of the player whose buffer is being rendered. Never null. */
    public final UUID subjectUUID;

    /** Display name of the player at trigger time. Never null. */
    public final String subjectName;

    /** Human-readable label for the event that triggered the render. Never null. */
    public final String eventLabel;

    /** Seconds of buffer before the trigger timestamp to include. Always >= 0.0. */
    public final double preSeconds;

    /** Seconds after the trigger timestamp to capture before rendering starts. Always >= 0.0. */
    public final double postSeconds;

    /** Names of output profiles to dispatch to. Never null. Never empty. */
    public final List<String> outputProfileNames;

    /** Freeform metadata for template variable resolution. May be empty. Never null. */
    public final Map<String, String> metadata;

    /** Milliseconds since epoch when the trigger fired. */
    public final long triggerTimestamp;

    /** Unique ID for this render job. Never null. */
    public final UUID jobId;

    /** Absolute world coordinates of the player at trigger time. */
    public final int triggerX;
    public final int triggerY;
    public final int triggerZ;

    /** Dimension key at trigger time. Never null. */
    public final String dimension;

    /** World name at trigger time. Never null. */
    public final String worldName;

    private TriggerContext(
            UUID subjectUUID,
            String subjectName,
            String eventLabel,
            double preSeconds,
            double postSeconds,
            List<String> outputProfileNames,
            Map<String, String> metadata,
            long triggerTimestamp,
            UUID jobId,
            int triggerX,
            int triggerY,
            int triggerZ,
            String dimension,
            String worldName) {
        this.subjectUUID = subjectUUID;
        this.subjectName = subjectName;
        this.eventLabel = eventLabel;
        this.preSeconds = preSeconds;
        this.postSeconds = postSeconds;
        this.outputProfileNames = outputProfileNames;
        this.metadata = metadata;
        this.triggerTimestamp = triggerTimestamp;
        this.jobId = jobId;
        this.triggerX = triggerX;
        this.triggerY = triggerY;
        this.triggerZ = triggerZ;
        this.dimension = dimension;
        this.worldName = worldName;
    }

    public static final class Builder {
        private UUID subjectUUID;
        private String subjectName;
        private String eventLabel;
        private double preSeconds;
        private double postSeconds;
        private List<String> outputProfileNames;
        private Map<String, String> metadata;
        private long triggerTimestamp;
        private UUID jobId;
        private int triggerX;
        private int triggerY;
        private int triggerZ;
        private String dimension;
        private String worldName;

        public Builder subjectUUID(UUID subjectUUID) {
            this.subjectUUID = subjectUUID;
            return this;
        }

        public Builder subjectName(String subjectName) {
            this.subjectName = subjectName;
            return this;
        }

        public Builder eventLabel(String eventLabel) {
            this.eventLabel = eventLabel;
            return this;
        }

        public Builder preSeconds(double preSeconds) {
            this.preSeconds = preSeconds;
            return this;
        }

        public Builder postSeconds(double postSeconds) {
            this.postSeconds = postSeconds;
            return this;
        }

        public Builder outputProfileNames(List<String> outputProfileNames) {
            this.outputProfileNames = outputProfileNames;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder triggerTimestamp(long triggerTimestamp) {
            this.triggerTimestamp = triggerTimestamp;
            return this;
        }

        public Builder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder triggerX(int triggerX) {
            this.triggerX = triggerX;
            return this;
        }

        public Builder triggerY(int triggerY) {
            this.triggerY = triggerY;
            return this;
        }

        public Builder triggerZ(int triggerZ) {
            this.triggerZ = triggerZ;
            return this;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder worldName(String worldName) {
            this.worldName = worldName;
            return this;
        }

        public TriggerContext build() {
            List<String> profiles = outputProfileNames;
            if (profiles == null || profiles.isEmpty()) {
                profiles = List.of("default");
            } else {
                profiles = List.copyOf(profiles);
            }
            Map<String, String> meta = metadata != null ? Map.copyOf(metadata) : Map.of();
            return new TriggerContext(
                    subjectUUID,
                    subjectName,
                    eventLabel,
                    preSeconds,
                    postSeconds,
                    profiles,
                    meta,
                    triggerTimestamp,
                    jobId,
                    triggerX,
                    triggerY,
                    triggerZ,
                    dimension,
                    worldName);
        }
    }
}

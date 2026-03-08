package me.replaygif.trigger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable bag of everything the pipeline needs from trigger time to output dispatch.
 * Built once per trigger (API, event, death, webhook, dynamic listener); passed through
 * slice → render → encode → dispatch so outputs can resolve templates and log by jobId
 * without touching the trigger source. Builder enforces non-null profiles and copied
 * collections so no downstream code can see a half-built or mutated context.
 */
public final class TriggerContext {

    /** Identifies which SnapshotBuffer to slice. */
    public final UUID subjectUUID;

    /** For templates ({player}) and logging. */
    public final String subjectName;

    /** For templates ({event}) and embed titles. */
    public final String eventLabel;

    /** Slice start = triggerTimestamp - (preSeconds * 1000). */
    public final double preSeconds;

    /** Wait this long after trigger before slicing, so post-moment is captured. */
    public final double postSeconds;

    /** Builder normalizes to at least "default" so dispatch always has a list. */
    public final List<String> outputProfileNames;

    /** Copied at build so callers cannot mutate after handoff. */
    public final Map<String, String> metadata;

    /** Used for slice window and for {timestamp} / {date} / {time} in templates. */
    public final long triggerTimestamp;

    /** Single id for this job; used in logs and optional API return. */
    public final UUID jobId;

    /** For template variables {x}, {y}, {z}. */
    public final int triggerX;
    public final int triggerY;
    public final int triggerZ;

    /** For {dimension} in templates. */
    public final String dimension;

    /** For {world} in templates. */
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

    /** Fluent builder; build() copies lists/maps so the context is immutable and safe to pass across threads. */
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

        /** Normalizes profiles (default if empty) and copies collections for immutability. */
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

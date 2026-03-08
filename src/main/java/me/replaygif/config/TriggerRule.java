package me.replaygif.config;

import java.util.List;

/**
 * Immutable. Built from triggers.yml at startup by TriggerRuleRegistry.
 * One instance per configured rule (internal or inbound).
 */
public final class TriggerRule {

    /** For inbound: event key pattern (supports trailing *). For internal: fully-qualified event class name. Never null. */
    public final String pattern;

    /** For inbound rules: dot-notation path into JSON payload to resolve subject. For internal: null. */
    public final String inboundSubjectPath;

    /** For internal rules: getter method names to reach a Player. For inbound: null. */
    public final List<String> internalGetterChain;

    /** Output profile names this rule dispatches to. Never null. Never empty. */
    public final List<String> outputProfileNames;

    public final double preSeconds;
    public final double postSeconds;

    /** For inbound: path into payload for event label. Nullable. */
    public final String labelPath;

    /** Fallback label if labelPath is null or missing. Supports {player}. Never null. */
    public final String labelFallback;

    /** Optional conditions for internal listeners. All must pass. May be empty. Never null. */
    public final List<TriggerCondition> conditions;

    public final boolean enabled;

    public TriggerRule(
            String pattern,
            String inboundSubjectPath,
            List<String> internalGetterChain,
            List<String> outputProfileNames,
            double preSeconds,
            double postSeconds,
            String labelPath,
            String labelFallback,
            List<TriggerCondition> conditions,
            boolean enabled) {
        this.pattern = pattern;
        this.inboundSubjectPath = inboundSubjectPath;
        this.internalGetterChain = internalGetterChain != null ? List.copyOf(internalGetterChain) : null;
        this.outputProfileNames = outputProfileNames != null ? List.copyOf(outputProfileNames) : List.of();
        this.preSeconds = preSeconds;
        this.postSeconds = postSeconds;
        this.labelPath = labelPath;
        this.labelFallback = labelFallback != null ? labelFallback : "";
        this.conditions = conditions != null ? List.copyOf(conditions) : List.of();
        this.enabled = enabled;
    }
}

package me.replaygif.config;

/**
 * Immutable. One condition for internal Bukkit trigger rules.
 * Method chain is called on the event; result is compared to expectedValue.
 */
public final class TriggerCondition {

    /** Method chain to call on the event to get the value to test. */
    public final String methodChain;

    /** The comparison operator. */
    public enum Operator { EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN }
    public final Operator operator;

    /** The expected value as a string. Compared after toString() on the result. */
    public final String expectedValue;

    public TriggerCondition(String methodChain, Operator operator, String expectedValue) {
        this.methodChain = methodChain;
        this.operator = operator;
        this.expectedValue = expectedValue;
    }
}

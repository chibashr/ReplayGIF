package com.replayplugin.job;

/**
 * Info about the job currently being rendered (oldest .json in queue by creation time).
 */
public final class CurrentJobInfo {

    private final String playerName;
    private final String eventType;
    private final long startTimeMillis;

    public CurrentJobInfo(String playerName, String eventType, long startTimeMillis) {
        this.playerName = playerName != null ? playerName : "unknown";
        this.eventType = eventType != null ? eventType : "";
        this.startTimeMillis = startTimeMillis;
    }

    public String getPlayerName() { return playerName; }
    public String getEventType() { return eventType; }
    public long getStartTimeMillis() { return startTimeMillis; }
}

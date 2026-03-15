package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Render job produced by the capture pipeline. Matches IPC-CONTRACT.md top-level.
 */
public final class RenderJob {

    @JsonProperty("player_uuid")
    private final UUID playerUuid;
    @JsonProperty("event_type")
    private final String eventType;
    private final String timestamp;
    @JsonProperty("render_config")
    private final RenderConfigDto renderConfig;
    @JsonProperty("pre_frames")
    private final List<FrameSnapshot> preFrames;
    @JsonProperty("post_frames")
    private final List<FrameSnapshot> postFrames;
    @JsonProperty("player_name")
    private final String playerName;

    @JsonCreator
    public RenderJob(
            @JsonProperty("player_uuid") UUID playerUuid,
            @JsonProperty("player_name") String playerName,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("render_config") RenderConfigDto renderConfig,
            @JsonProperty("pre_frames") List<FrameSnapshot> preFrames,
            @JsonProperty("post_frames") List<FrameSnapshot> postFrames) {
        this.playerUuid = playerUuid;
        this.playerName = playerName != null ? playerName : "unknown";
        this.eventType = eventType != null ? eventType : "";
        this.timestamp = timestamp != null ? timestamp : "";
        this.renderConfig = renderConfig;
        this.preFrames = preFrames != null ? List.copyOf(preFrames) : List.of();
        this.postFrames = postFrames != null ? List.copyOf(postFrames) : List.of();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getEventType() { return eventType; }
    public String getTimestamp() { return timestamp; }
    public RenderConfigDto getRenderConfig() { return renderConfig; }
    public List<FrameSnapshot> getPreFrames() { return preFrames; }
    public List<FrameSnapshot> getPostFrames() { return postFrames; }

    /**
     * Output GIF filename per IPC: &lt;playerName&gt;_&lt;eventType&gt;_&lt;timestamp&gt;.gif
     */
    public String getGifFilename() {
        return playerName + "_" + eventType + "_" + timestamp + ".gif";
    }
}

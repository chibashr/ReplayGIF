package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Single frame: entity state + chunks at one tick. Matches IPC-CONTRACT.md frame object.
 */
public final class FrameSnapshot {

    @JsonProperty("frame_index")
    private final int frameIndex;
    @JsonProperty("captured_at_tick")
    private final long capturedAtTick;
    @JsonProperty("entity_state")
    private final EntityState entityState;
    private final List<ChunkData> chunks;

    @JsonCreator
    public FrameSnapshot(
            @JsonProperty("frame_index") int frameIndex,
            @JsonProperty("captured_at_tick") long capturedAtTick,
            @JsonProperty("entity_state") EntityState entityState,
            @JsonProperty("chunks") List<ChunkData> chunks) {
        this.frameIndex = frameIndex;
        this.capturedAtTick = capturedAtTick;
        this.entityState = entityState;
        this.chunks = chunks != null ? List.copyOf(chunks) : List.of();
    }

    public int getFrameIndex() { return frameIndex; }
    public long getCapturedAtTick() { return capturedAtTick; }
    public EntityState getEntityState() { return entityState; }
    public List<ChunkData> getChunks() { return chunks; }
}

package com.replayplugin.sidecar.model;

import com.replayplugin.capture.ChunkData;
import com.replayplugin.capture.FrameSnapshot;

import java.util.List;

/**
 * Adapts FrameSnapshot to WorldSnapshot for the renderer.
 */
public final class WorldSnapshotAdapter implements WorldSnapshot {

    private final FrameSnapshot frame;

    public WorldSnapshotAdapter(FrameSnapshot frame) {
        this.frame = frame;
    }

    @Override
    public List<ChunkData> getChunks() {
        return frame.getChunks();
    }
}

package com.replayplugin.sidecar.model;

import com.replayplugin.capture.ChunkData;

import java.util.List;

/**
 * One frame's world data: chunks within capture radius. Entity state passed separately to renderer.
 */
public interface WorldSnapshot {

    List<ChunkData> getChunks();
}

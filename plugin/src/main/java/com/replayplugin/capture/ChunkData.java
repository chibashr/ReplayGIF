package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Chunk snapshot within capture radius. Matches IPC-CONTRACT.md chunk object.
 */
public final class ChunkData {

    @JsonProperty("chunk_x")
    private final int chunkX;
    @JsonProperty("chunk_z")
    private final int chunkZ;
    private final String biome;
    private final List<BlockEntry> blocks;

    @JsonCreator
    public ChunkData(
            @JsonProperty("chunk_x") int chunkX,
            @JsonProperty("chunk_z") int chunkZ,
            @JsonProperty("biome") String biome,
            @JsonProperty("blocks") List<BlockEntry> blocks) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.biome = biome != null ? biome : "minecraft:plains";
        this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public String getBiome() { return biome; }
    public List<BlockEntry> getBlocks() { return blocks; }
}

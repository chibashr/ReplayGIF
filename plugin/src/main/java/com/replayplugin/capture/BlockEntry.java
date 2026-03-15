package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Single block in a chunk. Matches IPC-CONTRACT.md block object.
 */
public final class BlockEntry {

    private final int x;
    private final int y;
    private final int z;
    @JsonProperty("block_id")
    private final String blockId;
    @JsonProperty("block_state_properties")
    private final Map<String, String> blockStateProperties;
    @JsonProperty("sky_light")
    private final int skyLight;
    @JsonProperty("block_light")
    private final int blockLight;

    @JsonCreator
    public BlockEntry(
            @JsonProperty("x") int x,
            @JsonProperty("y") int y,
            @JsonProperty("z") int z,
            @JsonProperty("block_id") String blockId,
            @JsonProperty("block_state_properties") Map<String, String> blockStateProperties,
            @JsonProperty("sky_light") int skyLight,
            @JsonProperty("block_light") int blockLight) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId != null ? blockId : "minecraft:air";
        this.blockStateProperties = blockStateProperties != null ? Map.copyOf(blockStateProperties) : Map.of();
        this.skyLight = Math.max(0, Math.min(15, skyLight));
        this.blockLight = Math.max(0, Math.min(15, blockLight));
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getBlockId() { return blockId; }
    public Map<String, String> getBlockStateProperties() { return blockStateProperties; }
    public int getSkyLight() { return skyLight; }
    public int getBlockLight() { return blockLight; }
}

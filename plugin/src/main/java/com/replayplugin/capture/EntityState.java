package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Player state at a single tick. Matches IPC-CONTRACT.md entity_state.
 */
public final class EntityState {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String pose;
    private final Map<String, String> equipment;
    @JsonProperty("skin_texture_url")
    private final String skinTextureUrl;

    @JsonCreator
    public EntityState(
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("z") double z,
            @JsonProperty("yaw") float yaw,
            @JsonProperty("pitch") float pitch,
            @JsonProperty("pose") String pose,
            @JsonProperty("equipment") Map<String, String> equipment,
            @JsonProperty("skin_texture_url") String skinTextureUrl) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.pose = pose;
        this.equipment = equipment != null ? Map.copyOf(equipment) : Map.of();
        this.skinTextureUrl = skinTextureUrl != null ? skinTextureUrl : "";
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getPose() { return pose; }
    public Map<String, String> getEquipment() { return equipment; }
    public String getSkinTextureUrl() { return skinTextureUrl; }
}

package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-job render settings. Matches IPC-CONTRACT.md render_config.
 */
public final class RenderConfigDto {

    private final int fps;
    @JsonProperty("pixels_per_block")
    private final int pixelsPerBlock;
    @JsonProperty("capture_radius_chunks")
    private final int captureRadiusChunks;
    @JsonProperty("camera_mode")
    private final String cameraMode;
    @JsonProperty("cutout_enabled")
    private final boolean cutoutEnabled;
    @JsonProperty("output_destinations")
    private final List<DestinationDto> outputDestinations;

    @JsonCreator
    public RenderConfigDto(
            @JsonProperty("fps") int fps,
            @JsonProperty("pixels_per_block") int pixelsPerBlock,
            @JsonProperty("capture_radius_chunks") int captureRadiusChunks,
            @JsonProperty("camera_mode") String cameraMode,
            @JsonProperty("cutout_enabled") boolean cutoutEnabled,
            @JsonProperty("output_destinations") List<DestinationDto> outputDestinations) {
        this.fps = fps;
        this.pixelsPerBlock = pixelsPerBlock;
        this.captureRadiusChunks = captureRadiusChunks;
        this.cameraMode = cameraMode != null ? cameraMode : "follow_player";
        this.cutoutEnabled = cutoutEnabled;
        this.outputDestinations = outputDestinations != null ? List.copyOf(outputDestinations) : List.of();
    }

    public int getFps() { return fps; }
    public int getPixelsPerBlock() { return pixelsPerBlock; }
    public int getCaptureRadiusChunks() { return captureRadiusChunks; }
    public String getCameraMode() { return cameraMode; }
    public boolean isCutoutEnabled() { return cutoutEnabled; }
    public List<DestinationDto> getOutputDestinations() { return outputDestinations; }
}

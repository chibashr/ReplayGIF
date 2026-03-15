package com.replayplugin.capture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output destination. Matches IPC-CONTRACT.md output_destinations entry.
 */
public final class DestinationDto {

    private final String type;
    private final String url;

    @JsonCreator
    public DestinationDto(@JsonProperty("type") String type, @JsonProperty("url") String url) {
        this.type = type != null ? type : "disk";
        this.url = url;
    }

    public String getType() { return type; }
    public String getUrl() { return url; }
}

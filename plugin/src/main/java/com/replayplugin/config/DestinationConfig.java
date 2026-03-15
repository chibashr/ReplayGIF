package com.replayplugin.config;

/**
 * Parsed destination entry from config: type (disk, discord_webhook, in_game) and optional url.
 */
public class DestinationConfig {

    private final String type;
    private final String url;

    public DestinationConfig(String type, String url) {
        this.type = type;
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }
}

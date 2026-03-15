package com.replayplugin.sidecar.output;

import com.replayplugin.capture.DestinationDto;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Dispatches completed GIF to configured destinations: disk (no-op), discord_webhook (with retry), in_game (no-op in sidecar).
 */
public final class OutputDispatcher {

    private static final Logger LOG = Logger.getLogger(OutputDispatcher.class.getName());
    private static final int RETRY_DELAY_MS = 5000;

    /**
     * Dispatch GIF to each destination. File is already on disk. Discord: retry once after 5s on failure.
     */
    public static void dispatch(Path gifPath, List<DestinationDto> destinations, String playerUuid, String playerName, String jobId) {
        if (destinations == null) return;
        for (DestinationDto d : destinations) {
            String type = d.getType();
            if (type == null) continue;
            switch (type) {
                case "disk":
                    break;
                case "discord_webhook": {
                    String url = d.getUrl();
                    if (url == null || url.isEmpty()) {
                        LOG.warning("discord_webhook destination missing url for job " + jobId);
                        break;
                    }
                    boolean ok = DiscordWebhookSender.send(url, gifPath);
                    if (!ok) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        ok = DiscordWebhookSender.send(url, gifPath);
                    }
                    if (!ok) {
                        LOG.severe("Discord webhook failed after retry for job " + jobId);
                    }
                    break;
                }
                case "in_game":
                    break;
                default:
                    break;
            }
        }
    }
}

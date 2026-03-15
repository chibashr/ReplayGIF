package com.replayplugin.sidecar.output;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DSC-001 through DSC-004: Discord Webhook Delivery.
 */
class DiscordWebhookSenderTest {

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tempDir;

    @Test
    void DSC001_postSucceedsFirstAttempt_noRetry() throws Exception {
        Path gif = tempDir.resolve("test.gif");
        Files.write(gif, new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 2, 3});
        String baseUrl = "http://localhost:" + wireMock.getPort();
        String webhookUrl = baseUrl + "/webhook";

        wireMock.stubFor(post(urlPathEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(204)));

        boolean ok = DiscordWebhookSender.send(webhookUrl, gif);
        assertTrue(ok);
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/webhook")));
    }

    @Test
    void DSC002_postFails_retryAfter5sSucceeds() throws Exception {
        Path gif = tempDir.resolve("test.gif");
        Files.write(gif, new byte[]{'G', 'I', 'F', '8', '9', 'a'});
        String baseUrl = "http://localhost:" + wireMock.getPort();
        String webhookUrl = baseUrl + "/webhook2";

        wireMock.stubFor(post(urlPathEqualTo("/webhook2"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("first"));
        wireMock.stubFor(post(urlPathEqualTo("/webhook2"))
                .inScenario("retry")
                .whenScenarioStateIs("first")
                .willReturn(aResponse().withStatus(204)));

        boolean ok = DiscordWebhookSender.send(webhookUrl, gif);
        assertFalse(ok);
        try { Thread.sleep(5100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        boolean okRetry = DiscordWebhookSender.send(webhookUrl, gif);
        assertTrue(okRetry);
    }

    @Test
    void DSC003_postFailsRetryFails_errorLogged() throws Exception {
        Path gif = tempDir.resolve("test.gif");
        Files.write(gif, new byte[]{'G', 'I', 'F'});
        String webhookUrl = "http://localhost:" + wireMock.getPort() + "/fail";

        wireMock.stubFor(post(urlPathEqualTo("/fail"))
                .willReturn(aResponse().withStatus(500)));

        boolean ok1 = DiscordWebhookSender.send(webhookUrl, gif);
        assertFalse(ok1);
        try { Thread.sleep(5100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        boolean ok2 = DiscordWebhookSender.send(webhookUrl, gif);
        assertFalse(ok2);
    }

    @Test
    void DSC004_destinationWithoutDiscordWebhook_noHttpRequest() {
        boolean ok = DiscordWebhookSender.send(null, tempDir.resolve("x.gif"));
        assertFalse(ok);
        ok = DiscordWebhookSender.send("", tempDir.resolve("x.gif"));
        assertFalse(ok);
    }
}

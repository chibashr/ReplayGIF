package com.replayplugin.sidecar.output;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Sends a GIF file to a Discord webhook via multipart/form-data POST. Returns true on 2xx.
 */
public final class DiscordWebhookSender {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * POST the GIF as multipart/form-data file field. Returns true on 2xx response.
     */
    public static boolean send(String webhookUrl, Path gifPath) {
        if (webhookUrl == null || webhookUrl.isEmpty() || gifPath == null || !Files.isRegularFile(gifPath)) {
            return false;
        }
        try {
            String boundary = "----ReplayGIF-" + System.nanoTime();
            String fileName = gifPath.getFileName().toString();
            byte[] body = buildMultipartBody(boundary, gifPath, fileName);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            return code >= 200 && code < 300;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] buildMultipartBody(String boundary, Path gifPath, String fileName) throws IOException {
        byte[] fileBytes = Files.readAllBytes(gifPath);
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: image/gif\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);
        return body;
    }
}

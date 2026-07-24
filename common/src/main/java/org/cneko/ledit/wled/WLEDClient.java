package org.cneko.ledit.wled;

import org.cneko.ledit.config.LedItConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends per-LED color data to a WLED device via HTTP JSON API.
 * Uses asynchronous HTTP to avoid blocking the render thread.
 */
public final class WLEDClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("ledit");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;

    public WLEDClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Send per-LED colors to the configured WLED device.
     * This method returns immediately — the HTTP request runs in the background.
     *
     * @param colors     2D array of [ledIndex][R,G,B], each 0-255
     * @param brightness global brightness 0-255
     */
    public void sendColors(int[][] colors, int brightness) {
        String json = buildPayload(colors, brightness);
        String url = "http://" + LedItConfig.wledAddress + ":" + LedItConfig.wledPort + "/json/state";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            LOGGER.warn("WLED returned status {} for {}", response.statusCode(), url);
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.debug("Failed to send LED data to WLED: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.debug("Failed to build WLED request: {}", e.getMessage());
        }
    }

    /**
     * Build the minimal JSON payload for per-LED color update.
     * Format: {"on":true,"bri":<b>,"seg":{"i":[[R,G,B],[R,G,B],...]}}
     */
    String buildPayload(int[][] colors, int brightness) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"on\":true,\"bri\":").append(brightness);
        sb.append(",\"seg\":{\"i\":[");

        for (int i = 0; i < colors.length; i++) {
            if (i > 0) sb.append(',');
            int[] rgb = colors[i];
            sb.append('[').append(rgb[0]).append(',').append(rgb[1]).append(',').append(rgb[2]).append(']');
        }

        sb.append("]}}");
        return sb.toString();
    }
}

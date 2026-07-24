package org.cneko.ledit.config;

import com.google.gson.*;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LedItConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ledit");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Platform.getConfigFolder().resolve("ledit.json");

    // ===== WLED Settings =====
    public static String wledAddress = "127.0.0.1";
    public static int wledPort = 80;
    public static int ledCount = 30;
    public static int targetFPS = 2;
    public static int brightness = 255;
    public static int transitionTicks = 60;

    // ===== E1.31 (sACN) Settings =====
    public static boolean useE131 = false;
    public static int e131Port = 5568;
    public static int e131Universe = 1;

    private LedItConfig() {
    }

    /**
     * Convert target FPS to tick interval.
     * Minecraft runs at 20 ticks per second.
     */
    public static int getTickInterval() {
        return Math.max(1, 20 / targetFPS);
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            wledAddress = getOrDefault(json, "wledAddress", wledAddress);
            wledPort = getOrDefault(json, "wledPort", wledPort);
            ledCount = getOrDefault(json, "ledCount", ledCount);
            brightness = getOrDefault(json, "brightness", brightness);

            // targetFPS — if missing, try to convert legacy updateInterval
            if (json.has("targetFPS")) {
                targetFPS = json.get("targetFPS").getAsInt();
            } else if (json.has("updateInterval")) {
                int interval = json.get("updateInterval").getAsInt();
                targetFPS = Math.max(1, 20 / Math.max(1, interval));
            }

            transitionTicks = getOrDefault(json, "transitionTicks", transitionTicks);
            useE131 = getOrDefault(json, "useE131", useE131);
            e131Port = getOrDefault(json, "e131Port", e131Port);
            e131Universe = getOrDefault(json, "e131Universe", e131Universe);
            LOGGER.info("LEDIt config loaded from {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.warn("Failed to load LEDIt config, using defaults", e);
        }
    }

    public static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("wledAddress", wledAddress);
        json.addProperty("wledPort", wledPort);
        json.addProperty("ledCount", ledCount);
        json.addProperty("targetFPS", targetFPS);
        json.addProperty("brightness", brightness);
        json.addProperty("transitionTicks", transitionTicks);
        json.addProperty("useE131", useE131);
        json.addProperty("e131Port", e131Port);
        json.addProperty("e131Universe", e131Universe);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
            LOGGER.info("LEDIt config saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("Failed to save LEDIt config", e);
        }
    }

    private static String getOrDefault(JsonObject json, String key, String def) {
        return json.has(key) ? json.get(key).getAsString() : def;
    }

    private static int getOrDefault(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static boolean getOrDefault(JsonObject json, String key, boolean def) {
        return json.has(key) ? json.get(key).getAsBoolean() : def;
    }
}

package com.atw.levelhead.data;

import com.atw.levelhead.ATWLevelHead;
import com.atw.levelhead.config.LevelHeadConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class NickDetector {
    private static final String API_KEY_ENV = "ATW_LEVELHEAD_HYPIXEL_API_KEY";
    private static final String API_KEY_PROPERTY = "atw.levelhead.hypixelApiKey";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final HttpJsonClient http = new HttpJsonClient("ATWLevelHead/0.1.0");
    private final JsonParser parser = new JsonParser();
    private final LevelHeadConfig config;
    private final ConcurrentHashMap<UUID, Assessment> uuidCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> missingMojangProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> missingHypixelPlayers = new ConcurrentHashMap<>();

    private volatile boolean hypixelLookupDisabled;
    private volatile String lastHypixelApiKey = "";

    public NickDetector(LevelHeadConfig config) {
        this.config = config;
    }

    public void resetHypixelLookup() {
        hypixelLookupDisabled = false;
        lastHypixelApiKey = "";
        missingHypixelPlayers.clear();
    }

    public Assessment assess(UUID uuid, String visibleName, boolean checkHypixel) {
        if (uuid == null) {
            return Assessment.notNicked();
        }

        Assessment cached = uuidCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        if (uuid.version() == 1) {
            return remember(uuid, Assessment.nicked("uuid-v1"));
        }

        String playerName = normalizeName(visibleName);
        if (playerName != null && isMissingMojangProfile(playerName)) {
            return remember(uuid, Assessment.nicked("mojang-missing"));
        }

        if (checkHypixel && isMissingHypixelPlayer(uuid)) {
            return remember(uuid, Assessment.nicked("hypixel-null"));
        }

        return Assessment.notNicked();
    }

    private Assessment remember(UUID uuid, Assessment assessment) {
        uuidCache.put(uuid, assessment);
        return assessment;
    }

    private boolean isMissingMojangProfile(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        Boolean cached = missingMojangProfiles.get(key);
        if (cached != null) {
            return cached;
        }

        boolean missing = false;
        try {
            String body = http.get("https://api.mojang.com/users/profiles/minecraft/" + HttpJsonClient.urlEncode(playerName));
            if (body == null || body.trim().isEmpty()) {
                missing = true;
            } else {
                JsonObject root = parser.parse(body).getAsJsonObject();
                missing = !root.has("id") || root.get("id").isJsonNull();
            }
        } catch (Exception exception) {
            ATWLevelHead.log("Mojang nick check failed for " + playerName + ": "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }

        missingMojangProfiles.put(key, missing);
        return missing;
    }

    private boolean isMissingHypixelPlayer(UUID uuid) {
        Boolean cached = missingHypixelPlayers.get(uuid);
        if (cached != null) {
            return cached;
        }

        String apiKey = loadApiKey();
        if (apiKey.isEmpty()) {
            return false;
        }
        if (!apiKey.equals(lastHypixelApiKey)) {
            lastHypixelApiKey = apiKey;
            hypixelLookupDisabled = false;
            missingHypixelPlayers.clear();
        }
        if (hypixelLookupDisabled) {
            return false;
        }

        boolean missing = false;
        try {
            String body = http.get(
                    "https://api.hypixel.net/v2/player?uuid=" + uuid.toString().replace("-", ""),
                    "API-Key",
                    apiKey
            );
            JsonObject root = parser.parse(body).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                hypixelLookupDisabled = true;
                ATWLevelHead.log("Hypixel nick check disabled: " + getString(root, "cause", "unknown"));
                return false;
            }
            missing = !root.has("player") || root.get("player").isJsonNull();
        } catch (Exception exception) {
            hypixelLookupDisabled = true;
            ATWLevelHead.log("Hypixel nick check failed: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }

        missingHypixelPlayers.put(uuid, missing);
        return missing;
    }

    private String loadApiKey() {
        String propertyValue = System.getProperty(API_KEY_PROPERTY);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(API_KEY_ENV);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        return config == null ? "" : config.getHypixelApiKey().trim();
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.replaceAll("\u00a7.", "").trim();
        return PLAYER_NAME.matcher(stripped).matches() && !ATWLevelHead.isHypixelNpcName(stripped)
                ? stripped
                : null;
    }

    private static String getString(JsonObject object, String field, String fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(field).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static class Assessment {
        private static final Assessment NOT_NICKED = new Assessment(false, "");

        private final boolean nicked;
        private final String reason;

        private Assessment(boolean nicked, String reason) {
            this.nicked = nicked;
            this.reason = reason;
        }

        public static Assessment notNicked() {
            return NOT_NICKED;
        }

        public static Assessment nicked(String reason) {
            return new Assessment(true, reason == null ? "unknown" : reason);
        }

        public boolean isNicked() {
            return nicked;
        }

        public String getReason() {
            return reason;
        }
    }
}

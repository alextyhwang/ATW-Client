package com.atw.levelhead.data;

import com.atw.levelhead.ATWLevelHead;
import com.atw.levelhead.config.LevelHeadConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HypixelBedwarsProvider implements LevelheadProvider {
    private static final String API_KEY_ENV = "ATW_LEVELHEAD_HYPIXEL_API_KEY";
    private static final String API_KEY_PROPERTY = "atw.levelhead.hypixelApiKey";
    private static final String LOCAL_API_KEY_RESOURCE = "/atw-levelhead-local.properties";
    private static final long THROTTLE_RETRY_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final DecimalFormat RATIO_FORMAT = new DecimalFormat("0.00");

    private final HttpJsonClient http = new HttpJsonClient("ATWLevelHead/0.1.0");
    private final JsonParser parser = new JsonParser();
    private final LevelHeadConfig config;
    private final String apiKey;

    private volatile String status = "ready";
    private volatile long throttledUntilMillis;

    public HypixelBedwarsProvider(LevelHeadConfig config) {
        this.config = config;
        apiKey = loadApiKey(config);
    }

    @Override
    public boolean isReady() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String getStatus() {
        return isReady() ? status : "missing-hypixel-api-key";
    }

    @Override
    public void reset() {
        status = "ready";
    }

    @Override
    public Map<UUID, LevelTag> fetch(List<UUID> uuids) throws Exception {
        Map<UUID, LevelTag> tags = new HashMap<>();
        if (!isReady()) {
            status = "missing-hypixel-api-key";
            return tags;
        }
        if (isThrottled()) {
            status = "hypixel-throttled-wait:" + Math.max(1L, (throttledUntilMillis - System.currentTimeMillis()) / 1000L) + "s";
            return tags;
        }

        boolean hadError = false;
        for (UUID uuid : uuids) {
            try {
                LevelTag tag = fetchOne(uuid);
                if (tag != null) {
                    tags.put(uuid, tag);
                }
            } catch (HypixelThrottleException exception) {
                hadError = true;
                throttledUntilMillis = System.currentTimeMillis() + THROTTLE_RETRY_DELAY_MILLIS;
                status = "hypixel-throttled:" + exception.getMessage();
                ATWLevelHead.log(status + "; skipping remaining BedWars API requests in this batch.");
                break;
            } catch (Exception exception) {
                hadError = true;
                status = "hypixel-error:" + exception.getClass().getSimpleName();
                ATWLevelHead.log("BedWars fetch failed for one player: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }

        if (!hadError) {
            status = "ok";
        }
        return tags;
    }

    private boolean isThrottled() {
        return throttledUntilMillis > System.currentTimeMillis();
    }

    private LevelTag fetchOne(UUID uuid) throws Exception {
        JsonObject player = fetchPlayer(uuid);
        if (player == null) {
            return LevelTag.nicked(uuid);
        }

        int star = getInt(path(player, "achievements"), "bedwars_level");
        JsonObject bedwars = path(player, "stats", "Bedwars");
        int finalKills = getInt(bedwars, "final_kills_bedwars");
        int finalDeaths = getInt(bedwars, "final_deaths_bedwars");
        String stars = BedwarsStatFormatter.formatStars(star);
        String lifetimeFkdr = BedwarsStatFormatter.formatFkdr(finalKills, finalDeaths);
        return new LevelTag(uuid, "", padVisibleRight(stars, 5)
                + "\u00a77| FKDR " + padVisibleLeft(lifetimeFkdr, 6));
    }

    public String fetchDetailedStats(UUID uuid, String fallbackName) {
        if (!isReady()) {
            return EnumChatFormatting.RED + "Hypixel API key is missing.";
        }

        try {
            JsonObject player = fetchPlayer(uuid);
            if (player == null) {
                return EnumChatFormatting.YELLOW + fallbackName + EnumChatFormatting.GRAY + " appears to be nicked or unavailable.";
            }

            String name = getString(player, "displayname", fallbackName == null ? uuid.toString() : fallbackName);
            JsonObject bedwars = path(player, "stats", "Bedwars");
            int star = getInt(path(player, "achievements"), "bedwars_level");
            double networkLevel = networkLevel(getDouble(player, "networkExp"));

            int finalKills = getInt(bedwars, "final_kills_bedwars");
            int finalDeaths = getInt(bedwars, "final_deaths_bedwars");
            int wins = getInt(bedwars, "wins_bedwars");
            int losses = getInt(bedwars, "losses_bedwars");
            int bedsBroken = getInt(bedwars, "beds_broken_bedwars");
            int bedsLost = getInt(bedwars, "beds_lost_bedwars");
            int winstreak = getInt(bedwars, "winstreak");

            return EnumChatFormatting.YELLOW + name
                    + EnumChatFormatting.GRAY + " | Level: " + EnumChatFormatting.AQUA + RATIO_FORMAT.format(networkLevel)
                    + EnumChatFormatting.GRAY + " | BW: " + BedwarsStatFormatter.formatStars(star)
                    + EnumChatFormatting.GRAY + " | FKDR: " + BedwarsStatFormatter.formatFkdr(finalKills, finalDeaths)
                    + EnumChatFormatting.GRAY + " (" + finalKills + "/" + finalDeaths + ")"
                    + EnumChatFormatting.GRAY + " | WLR: " + EnumChatFormatting.WHITE + ratio(wins, losses)
                    + EnumChatFormatting.GRAY + " (" + wins + "/" + losses + ")"
                    + EnumChatFormatting.GRAY + " | BBLR: " + EnumChatFormatting.WHITE + ratio(bedsBroken, bedsLost)
                    + EnumChatFormatting.GRAY + " (" + bedsBroken + "/" + bedsLost + ")"
                    + EnumChatFormatting.GRAY + " | WS: " + EnumChatFormatting.WHITE + winstreak;
        } catch (HypixelThrottleException exception) {
            throttledUntilMillis = System.currentTimeMillis() + THROTTLE_RETRY_DELAY_MILLIS;
            status = "hypixel-throttled:" + exception.getMessage();
            return EnumChatFormatting.RED + "Hypixel API throttled: " + exception.getMessage();
        } catch (Exception exception) {
            status = "hypixel-error:" + exception.getClass().getSimpleName();
            ATWLevelHead.log("Detailed BedWars stats fetch failed: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return EnumChatFormatting.RED + "Failed to fetch stats: " + exception.getClass().getSimpleName();
        }
    }

    private JsonObject fetchPlayer(UUID uuid) throws Exception {
        String body = http.get(
                "https://api.hypixel.net/v2/player?uuid=" + uuid.toString().replace("-", ""),
                "API-Key",
                apiKey
        );
        JsonObject root = parser.parse(body).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) {
            status = "hypixel-failed:" + root;
            ATWLevelHead.log(status);
            if (isThrottle(root)) {
                throw new HypixelThrottleException(getString(root, "cause", "throttle"));
            }
            return null;
        }
        if (!root.has("player") || root.get("player").isJsonNull()) {
            return null;
        }

        return root.getAsJsonObject("player");
    }

    private static JsonObject path(JsonObject object, String first, String second) {
        JsonObject child = path(object, first);
        if (child == null || !child.has(second) || !child.get(second).isJsonObject()) {
            return null;
        }
        return child.getAsJsonObject(second);
    }

    private static JsonObject path(JsonObject object, String first) {
        if (object == null || !object.has(first) || !object.get(first).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(first);
    }

    private static int getInt(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return 0;
        }
        try {
            return object.get(field).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double getDouble(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return 0.0D;
        }
        try {
            return object.get(field).getAsDouble();
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private static String ratio(int positive, int negative) {
        double value = negative <= 0 ? positive : positive / (double) negative;
        return RATIO_FORMAT.format(value);
    }

    private static double networkLevel(double experience) {
        if (experience <= 0.0D) {
            return 1.0D;
        }
        return 1.0D - 3.5D + Math.sqrt(12.25D + 0.0008D * experience) / 0.0008D;
    }

    private static String padVisibleRight(String value, int width) {
        StringBuilder builder = new StringBuilder(value == null ? "" : value);
        while (visibleLength(builder.toString()) < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static String padVisibleLeft(String value, int width) {
        StringBuilder builder = new StringBuilder(value == null ? "" : value);
        while (visibleLength(builder.toString()) < width) {
            builder.insert(0, ' ');
        }
        return builder.toString();
    }

    private static int visibleLength(String value) {
        return value == null ? 0 : value.replaceAll("\u00a7.", "").length();
    }

    private static boolean isThrottle(JsonObject root) {
        if (root.has("throttle") && !root.get("throttle").isJsonNull()) {
            try {
                if (root.get("throttle").getAsBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        String cause = getString(root, "cause", "").toLowerCase();
        return cause.contains("throttle") || cause.contains("rate") || cause.contains("429");
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

    private static String loadApiKey(LevelHeadConfig config) {
        String propertyValue = System.getProperty(API_KEY_PROPERTY);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(API_KEY_ENV);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        if (config != null && !config.getHypixelApiKey().trim().isEmpty()) {
            return config.getHypixelApiKey().trim();
        }

        return loadBundledApiKey();
    }

    private static String loadBundledApiKey() {
        try (InputStream inputStream = HypixelBedwarsProvider.class.getResourceAsStream(LOCAL_API_KEY_RESOURCE)) {
            if (inputStream == null) {
                return "";
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            String value = properties.getProperty("hypixelApiKey", "");
            return value == null ? "" : value.trim();
        } catch (Exception exception) {
            ATWLevelHead.log("Failed to load bundled Hypixel API key: " + exception.getMessage());
            return "";
        }
    }

    private static class HypixelThrottleException extends Exception {
        private HypixelThrottleException(String message) {
            super(message);
        }
    }
}

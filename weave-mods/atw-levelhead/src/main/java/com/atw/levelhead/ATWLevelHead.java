package com.atw.levelhead;

import com.atw.levelhead.command.LevelHeadCommand;
import com.atw.levelhead.config.LevelHeadConfig;
import com.atw.levelhead.data.HypixelBedwarsProvider;
import com.atw.levelhead.data.HypixelContextDetector;
import com.atw.levelhead.data.LevelTagDiskCache;
import com.atw.levelhead.data.LevelTag;
import com.atw.levelhead.data.LevelheadProvider;
import com.atw.levelhead.data.Sk1erLevelheadProvider;
import com.atw.levelhead.render.AboveHeadRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.ModInitializer;
import net.weavemc.loader.api.command.CommandBus;
import net.weavemc.loader.api.event.ChatReceivedEvent;
import net.weavemc.loader.api.event.ChatSentEvent;
import net.weavemc.loader.api.event.EntityListEvent;
import net.weavemc.loader.api.event.EventBus;
import net.weavemc.loader.api.event.PacketEvent;
import net.weavemc.loader.api.event.RenderLivingEvent;
import net.weavemc.loader.api.event.ServerConnectEvent;
import net.weavemc.loader.api.event.WorldEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ATWLevelHead implements ModInitializer {
    public static final String PREFIX = EnumChatFormatting.AQUA + "[ATW LevelHead] " + EnumChatFormatting.RESET;
    private static final Pattern HYPIXEL_NPC_NAME = Pattern.compile("[a-z][0-9]{2}n[0-9]{6}");
    private static final Pattern CHAT_SPEAKER = Pattern.compile("^.*?(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*(?::|»|>)\\s+.*$");
    private static final int LEVEL_FETCH_BATCH_SIZE = 80;
    private static final long LEVEL_REQUEST_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long LEVEL_DRAIN_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final long RECENT_CHAT_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private static ATWLevelHead instance;

    private final ConcurrentHashMap<String, LevelTag> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> queued = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastRequested = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RecentSpeaker> recentChatSpeakers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<UUID> pending = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ATW-LevelHead-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private final LevelHeadConfig config = LevelHeadConfig.load();
    private final LevelTagDiskCache diskCache = LevelTagDiskCache.load();
    private final Sk1erLevelheadProvider sk1erProvider = new Sk1erLevelheadProvider();
    private final HypixelBedwarsProvider bedwarsProvider = new HypixelBedwarsProvider(config);
    private final AboveHeadRenderer renderer = new AboveHeadRenderer(this);

    private volatile boolean hypixel;
    private volatile boolean authAttempted;
    private volatile long nextDrainAt;
    private volatile String networkFetchStatus = "idle";

    public static ATWLevelHead getInstance() {
        return instance;
    }

    @Override
    public void preInit() {
        instance = this;
        log("Loading ATW LevelHead for Weave.");
        CommandBus.register(new LevelHeadCommand(this));
        EventBus.subscribe(RenderLivingEvent.Post.class, renderer::render);
        EventBus.subscribe(WorldEvent.Load.class, this::onWorldLoad);
        EventBus.subscribe(WorldEvent.Unload.class, this::onWorldUnload);
        EventBus.subscribe(EntityListEvent.Add.class, this::onEntityAdd);
        EventBus.subscribe(ServerConnectEvent.class, this::onServerConnect);
        EventBus.subscribe(PacketEvent.Send.class, this::onPacketSend);
        EventBus.subscribe(ChatReceivedEvent.class, this::onChatReceived);
    }

    public LevelTag getTag(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return cache.get(cacheKey(cacheMode(), uuid));
    }

    public LevelTag getCachedTag(UUID uuid) {
        if (!shouldShowTabTags()) {
            return null;
        }

        String mode = cacheMode();
        LevelTag tag = uuid == null ? null : cache.get(cacheKey(mode, uuid));
        if (tag != null || uuid == null) {
            return tag;
        }

        tag = diskCache.getFresh(mode, uuid);
        if (tag == null && !activeProvider().isReady()) {
            tag = diskCache.getAny(mode, uuid);
        }
        if (tag != null) {
            cache.put(cacheKey(mode, uuid), tag);
        }
        return tag;
    }

    public boolean isHypixel() {
        return hypixel;
    }

    public int cacheSize() {
        return cache.size();
    }

    public int diskCacheSize() {
        return diskCache.size();
    }

    public int queueSize() {
        return pending.size();
    }

    public boolean isAuthenticated() {
        return activeProvider().isReady();
    }

    public String providerStatus() {
        return activeProvider().getStatus();
    }

    public String hypixelContext() {
        return HypixelContextDetector.detect(hypixel).name();
    }

    public String configuredMode() {
        return config.isBedwarsMode() ? "bedwars" : "level";
    }

    public boolean shouldShowTabTags() {
        return isBedwarsStatsActive();
    }

    public String networkFetchStatus() {
        return networkFetchStatus;
    }

    public String hypixelContextSummary() {
        return HypixelContextDetector.summary(hypixel);
    }

    public void logHypixelContextDebug() {
        HypixelContextDetector.logDebug(hypixel);
    }

    public LevelHeadConfig getConfig() {
        return config;
    }

    public String displayMode() {
        return isBedwarsStatsActive() ? "bedwars" : "level";
    }

    public void setDisplayMode(String mode) {
        String normalized = "bedwars".equalsIgnoreCase(mode) || "bw".equalsIgnoreCase(mode) ? "bedwars" : "level";
        config.setDisplayMode(normalized);
        config.save();
        reload();
    }

    public void reload() {
        clearSessionCache();
        activeProvider().reset();
        authAttempted = false;
        detectCurrentServer();
        authenticateIfNeeded();
        queueWorldPlayers();
        drainQueueSoon();
    }

    public void clearCache() {
        clearSessionCache();
        diskCache.clear();
    }

    public void clearSessionCache() {
        cache.clear();
        queued.clear();
        pending.clear();
        lastRequested.clear();
        recentChatSpeakers.clear();
        nextDrainAt = 0L;
    }

    public void queuePlayer(EntityPlayer player) {
        if (player == null || !hypixel || isHypixelNpc(player)) {
            return;
        }

        queueUuid(player.getUniqueID());
    }

    public void queuePlayer(UUID uuid) {
        queueUuid(uuid);
    }

    private void queueUuid(UUID uuid) {
        String mode = cacheMode();
        if (uuid == null || cache.containsKey(cacheKey(mode, uuid))) {
            return;
        }

        LevelTag cachedTag = getCachedDiskTag(mode, uuid, !activeProvider().isReady());
        if (cachedTag != null) {
            cache.put(cacheKey(mode, uuid), cachedTag);
            return;
        }

        Long lastAttempt = lastRequested.get(uuid);
        long now = System.currentTimeMillis();
        if (lastAttempt != null && now - lastAttempt < requestCooldownMillis()) {
            return;
        }

        if (queued.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }

        pending.add(uuid);
        drainQueueSoon();
    }

    public boolean isSelf(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && player.getUniqueID() != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID());
    }

    public boolean isHypixelNpc(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        return isHypixelNpcName(player.getName())
                || player.getDisplayName() != null && isHypixelNpcDisplayName(player.getDisplayName().getFormattedText());
    }

    public static boolean isHypixelNpcName(String name) {
        return name != null && HYPIXEL_NPC_NAME.matcher(name).matches();
    }

    public static boolean isHypixelNpcDisplayName(String displayName) {
        return displayName != null && displayName.replaceAll("\u00a7.", "").startsWith("[NPC]");
    }

    public void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + message));
        }
    }

    public void requestChatStats(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            sendChat(EnumChatFormatting.RED + "Usage: /atwlh stats <player>");
            return;
        }

        PlayerTarget target = resolvePlayer(playerName.trim(), false);
        if (target == null) {
            sendChat(EnumChatFormatting.RED + "Couldn't find a recent chat speaker or tab player matching '" + playerName.trim() + "'.");
            return;
        }

        sendChat(EnumChatFormatting.GRAY + "Loading stats for " + target.name + "...");
        executor.execute(() -> sendChatOnClientThread(formatStats(target)));
    }

    public void requestRecentChatStats() {
        List<PlayerTarget> targets = recentChatTargets();
        if (targets.isEmpty()) {
            sendChat(EnumChatFormatting.YELLOW + "No players have spoken in chat in the last 2 minutes.");
            return;
        }

        sendChat(EnumChatFormatting.GRAY + "Loading stats for " + targets.size() + " recent chat player" + (targets.size() == 1 ? "..." : "s..."));
        executor.execute(() -> {
            for (PlayerTarget target : targets) {
                sendChatOnClientThread(formatStats(target));
            }
        });
    }

    private void onServerConnect(ServerConnectEvent event) {
        hypixel = looksLikeHypixel(event.getIp());
        log("Server connect: " + event.getIp() + ":" + event.getPort() + ", hypixel=" + hypixel);
        if (hypixel) {
            authenticateIfNeeded();
        }
    }

    private void onWorldLoad(WorldEvent.Load event) {
        clearSessionCache();
        detectCurrentServer();
        if (hypixel) {
            authenticateIfNeeded();
            queueWorldPlayers();
        }
    }

    private void onWorldUnload(WorldEvent.Unload event) {
        clearSessionCache();
    }

    private void onEntityAdd(EntityListEvent.Add event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            queuePlayer((EntityPlayer) entity);
        }
    }

    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C01PacketChatMessage)) {
            return;
        }

        String message = ((C01PacketChatMessage) packet).getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        ChatSentEvent chatSentEvent = new ChatSentEvent(message);
        EventBus.callEvent(chatSentEvent);
        if (chatSentEvent.isCancelled()) {
            event.setCancelled(true);
            log("Cancelled outgoing command packet after Weave handled: " + commandName(message));
        }
    }

    private void onChatReceived(ChatReceivedEvent event) {
        if (!hypixel || event.getMessage() == null) {
            return;
        }

        String message = stripFormatting(event.getMessage().getFormattedText());
        java.util.regex.Matcher matcher = CHAT_SPEAKER.matcher(message);
        if (!matcher.matches()) {
            return;
        }

        PlayerTarget target = resolveExactPlayer(matcher.group(1));
        if (target != null) {
            recentChatSpeakers.put(target.name.toLowerCase(Locale.ROOT), new RecentSpeaker(target, System.currentTimeMillis()));
        }
    }

    private void detectCurrentServer() {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData data = mc.getCurrentServerData();
        hypixel = data != null && looksLikeHypixel(data.serverIP);
    }

    private boolean looksLikeHypixel(String ip) {
        if (ip == null) {
            return false;
        }
        String normalized = ip.toLowerCase(Locale.ROOT);
        return normalized.contains("hypixel.net") || normalized.contains("hypixel.io");
    }

    private String commandName(String message) {
        String withoutSlash = message.length() > 1 ? message.substring(1) : "";
        int space = withoutSlash.indexOf(' ');
        return space == -1 ? withoutSlash : withoutSlash.substring(0, space);
    }

    private void queueWorldPlayers() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }

        for (Object object : mc.theWorld.playerEntities) {
            if (object instanceof EntityPlayer) {
                queuePlayer((EntityPlayer) object);
            }
        }
    }

    private void authenticateIfNeeded() {
        if ("bedwars".equals(displayMode())) {
            return;
        }

        if (authAttempted || sk1erProvider.isAuthenticated()) {
            return;
        }

        authAttempted = true;
        executor.execute(() -> {
            try {
                sk1erProvider.authenticate();
            } catch (Exception exception) {
                sk1erProvider.fail("Auth exception: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                exception.printStackTrace();
            }
        });
    }

    private void drainQueueSoon() {
        long now = System.currentTimeMillis();
        if (now < nextDrainAt) {
            return;
        }

        nextDrainAt = now + TimeUnit.SECONDS.toMillis(1);
        executor.execute(this::drainQueue);
    }

    private void drainQueue() {
        if (!hypixel) {
            networkFetchStatus = "not-hypixel";
            return;
        }

        authenticateIfNeeded();
        String mode = cacheMode();
        LevelheadProvider provider = activeProvider();
        if (!provider.isReady()) {
            nextDrainAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2);
            networkFetchStatus = "provider-not-ready";
            return;
        }

        List<UUID> batch = new ArrayList<>();
        List<UUID> fetchBatch = new ArrayList<>();
        UUID uuid;
        while (batch.size() < fetchBatchSize() && (uuid = pending.poll()) != null) {
            batch.add(uuid);
            queued.remove(uuid);
            LevelTag cachedTag = getCachedDiskTag(mode, uuid, false);
            if (cachedTag != null) {
                cache.put(cacheKey(mode, uuid), cachedTag);
            } else {
                fetchBatch.add(uuid);
                lastRequested.put(uuid, System.currentTimeMillis());
            }
        }

        if (batch.isEmpty()) {
            networkFetchStatus = "idle";
            return;
        }

        if (fetchBatch.isEmpty()) {
            networkFetchStatus = "cache-only";
            log("Served " + batch.size() + " LevelHead entr" + (batch.size() == 1 ? "y" : "ies") + " from disk cache.");
            scheduleNextDrainIfNeeded();
            return;
        }

        try {
            networkFetchStatus = "fetching:" + fetchBatch.size();
            java.util.Map<UUID, LevelTag> fetched = provider.fetch(fetchBatch);
            for (java.util.Map.Entry<UUID, LevelTag> entry : fetched.entrySet()) {
                cache.put(cacheKey(mode, entry.getKey()), entry.getValue());
            }
            diskCache.putAll(mode, fetched);
            networkFetchStatus = "ok:" + fetched.size() + "/" + fetchBatch.size();
            log("Fetched " + fetched.size() + "/" + fetchBatch.size() + " LevelHead entr" + (fetchBatch.size() == 1 ? "y." : "ies."));
        } catch (Exception exception) {
            networkFetchStatus = "exception:" + exception.getClass().getSimpleName();
            log("Fetch exception: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            exception.printStackTrace();
        }

        scheduleNextDrainIfNeeded();
    }

    private void scheduleNextDrainIfNeeded() {
        if (pending.isEmpty()) {
            return;
        }

        long delay = drainIntervalMillis();
        nextDrainAt = System.currentTimeMillis() + delay;
        executor.execute(() -> {
            sleep(delay);
            drainQueue();
        });
    }

    private LevelTag getCachedDiskTag(UUID uuid, boolean allowStale) {
        return getCachedDiskTag(cacheMode(), uuid, allowStale);
    }

    private LevelTag getCachedDiskTag(String mode, UUID uuid, boolean allowStale) {
        LevelTag cachedTag = diskCache.getFresh(mode, uuid);
        if (cachedTag == null && allowStale) {
            cachedTag = diskCache.getAny(mode, uuid);
        }
        return cachedTag;
    }

    private String formatStats(PlayerTarget target) {
        return bedwarsProvider.fetchDetailedStats(target.uuid, target.name);
    }

    private LevelTag getOrFetchLevelTag(String mode, UUID uuid, LevelheadProvider provider) {
        String key = cacheKey(mode, uuid);
        LevelTag tag = cache.get(key);
        if (tag != null) {
            return tag;
        }

        tag = diskCache.getFresh(mode, uuid);
        if (tag != null) {
            cache.put(key, tag);
            return tag;
        }

        try {
            if ("level".equals(mode) && !sk1erProvider.isAuthenticated()) {
                sk1erProvider.authenticate();
            }
            if (!provider.isReady()) {
                return diskCache.getAny(mode, uuid);
            }

            java.util.Map<UUID, LevelTag> fetched = provider.fetch(java.util.Collections.singletonList(uuid));
            diskCache.putAll(mode, fetched);
            tag = fetched.get(uuid);
            if (tag != null) {
                cache.put(key, tag);
                return tag;
            }
        } catch (Exception exception) {
            log("Chat stats fetch failed for " + mode + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }

        return diskCache.getAny(mode, uuid);
    }

    private void sendChatOnClientThread(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        mc.addScheduledTask(() -> sendChat(message));
    }

    private PlayerTarget resolvePlayer(String name, boolean recentOnly) {
        purgeOldRecentChatSpeakers();
        List<PlayerTarget> matches = new ArrayList<>();
        String normalized = name.toLowerCase(Locale.ROOT);
        for (RecentSpeaker speaker : recentChatSpeakers.values()) {
            String speakerName = speaker.target.name.toLowerCase(Locale.ROOT);
            if (speakerName.equals(normalized) || speakerName.startsWith(normalized)) {
                matches.add(speaker.target);
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            sendChat(EnumChatFormatting.YELLOW + "Multiple recent matches: " + joinNames(matches));
            return null;
        }
        if (recentOnly) {
            return null;
        }

        PlayerTarget exact = resolveExactPlayer(name);
        if (exact != null) {
            return exact;
        }

        return resolveTabPlayer(name);
    }

    private PlayerTarget resolveExactPlayer(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return null;
        }

        net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        if (info == null || info.getGameProfile() == null || info.getGameProfile().getId() == null) {
            return null;
        }
        if (isHypixelNpcName(info.getGameProfile().getName())) {
            return null;
        }
        return new PlayerTarget(info.getGameProfile().getName(), info.getGameProfile().getId());
    }

    private PlayerTarget resolveTabPlayer(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null || name == null) {
            return null;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        List<PlayerTarget> matches = new ArrayList<>();
        for (Object object : mc.getNetHandler().getPlayerInfoMap()) {
            if (!(object instanceof net.minecraft.client.network.NetworkPlayerInfo)) {
                continue;
            }

            net.minecraft.client.network.NetworkPlayerInfo info = (net.minecraft.client.network.NetworkPlayerInfo) object;
            if (info.getGameProfile() == null || info.getGameProfile().getId() == null) {
                continue;
            }

            String playerName = info.getGameProfile().getName();
            if (playerName == null || isHypixelNpcName(playerName)) {
                continue;
            }

            String tabName = playerName.toLowerCase(Locale.ROOT);
            if (tabName.equals(normalized) || tabName.startsWith(normalized)) {
                matches.add(new PlayerTarget(playerName, info.getGameProfile().getId()));
            }
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            sendChat(EnumChatFormatting.YELLOW + "Multiple tab matches: " + joinNames(matches));
        }
        return null;
    }

    private List<PlayerTarget> recentChatTargets() {
        purgeOldRecentChatSpeakers();
        List<RecentSpeaker> speakers = new ArrayList<>(recentChatSpeakers.values());
        java.util.Collections.sort(speakers, (left, right) -> Long.compare(right.lastSeenMillis, left.lastSeenMillis));
        List<PlayerTarget> targets = new ArrayList<>();
        for (RecentSpeaker speaker : speakers) {
            targets.add(speaker.target);
        }
        return targets;
    }

    private void purgeOldRecentChatSpeakers() {
        long cutoff = System.currentTimeMillis() - RECENT_CHAT_WINDOW_MILLIS;
        for (Map.Entry<String, RecentSpeaker> entry : recentChatSpeakers.entrySet()) {
            if (entry.getValue().lastSeenMillis < cutoff) {
                recentChatSpeakers.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "");
    }

    private String joinNames(List<PlayerTarget> targets) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < targets.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(targets.get(index).name);
        }
        return builder.toString();
    }

    private boolean isBedwarsStatsActive() {
        return hypixel && config.isBedwarsMode()
                && HypixelContextDetector.detect(hypixel) == HypixelContextDetector.Context.BEDWARS_GAME;
    }

    private String cacheMode() {
        return "bedwars".equals(displayMode()) ? bedwarsCacheMode() : "level";
    }

    private String bedwarsCacheMode() {
        return "bedwars-v3";
    }

    private static String cacheKey(String mode, UUID uuid) {
        return mode + ":" + uuid.toString();
    }

    private int fetchBatchSize() {
        return LEVEL_FETCH_BATCH_SIZE;
    }

    private long requestCooldownMillis() {
        return LEVEL_REQUEST_COOLDOWN_MILLIS;
    }

    private long drainIntervalMillis() {
        return LEVEL_DRAIN_INTERVAL_MILLIS;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public static void log(String message) {
        System.out.println("[ATW LevelHead] " + message);
    }

    private LevelheadProvider activeProvider() {
        return providerForMode(displayMode());
    }

    private LevelheadProvider providerForMode(String mode) {
        return mode != null && mode.startsWith("bedwars") ? bedwarsProvider : sk1erProvider;
    }

    private static class PlayerTarget {
        private final String name;
        private final UUID uuid;

        private PlayerTarget(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    private static class RecentSpeaker {
        private final PlayerTarget target;
        private final long lastSeenMillis;

        private RecentSpeaker(PlayerTarget target, long lastSeenMillis) {
            this.target = target;
            this.lastSeenMillis = lastSeenMillis;
        }
    }
}

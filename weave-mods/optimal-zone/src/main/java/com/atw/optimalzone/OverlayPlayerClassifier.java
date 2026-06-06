package com.atw.optimalzone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;

public final class OverlayPlayerClassifier {
    private static final String LOCAL_PLAYER = "localPlayer";
    private static final String NETWORK_INFO_BY_UUID = "networkPlayerInfoByUuid";
    private static final String NETWORK_INFO_BY_NAME = "networkPlayerInfoByName";
    private static final String MISSING_NETWORK_INFO = "missingNetworkPlayerInfo";
    private static final String MISSING_MINECRAFT_FALLBACK = "missingMinecraftFallback";
    private static final String MISSING_NET_HANDLER_FALLBACK = "missingNetHandlerFallback";

    private OverlayPlayerClassifier() {
    }

    public static boolean shouldTreatAsRealPlayer(EntityPlayer player) {
        String reason = realPlayerReason(player);
        return LOCAL_PLAYER.equals(reason)
                || NETWORK_INFO_BY_UUID.equals(reason)
                || NETWORK_INFO_BY_NAME.equals(reason)
                || MISSING_MINECRAFT_FALLBACK.equals(reason)
                || MISSING_NET_HANDLER_FALLBACK.equals(reason);
    }

    public static String realPlayerReason(EntityPlayer player) {
        if (player == null) {
            return "nullPlayer";
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return MISSING_MINECRAFT_FALLBACK;
        }

        if (mc.thePlayer != null && player == mc.thePlayer) {
            return LOCAL_PLAYER;
        }

        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            return MISSING_NET_HANDLER_FALLBACK;
        }

        if (player.getUniqueID() != null && netHandler.getPlayerInfo(player.getUniqueID()) != null) {
            return NETWORK_INFO_BY_UUID;
        }

        String name = player.getName();
        if (name != null && netHandler.getPlayerInfo(name) != null) {
            return NETWORK_INFO_BY_NAME;
        }

        return MISSING_NETWORK_INFO;
    }
}

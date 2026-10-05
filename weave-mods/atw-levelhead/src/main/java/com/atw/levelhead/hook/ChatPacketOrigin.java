package com.atw.levelhead.hook;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.Packet;

/** Marks packets whose text already passed through Weave's built-in chat hook. */
public final class ChatPacketOrigin {
    private static final Map<Packet<?>, Boolean> API_HANDLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ChatPacketOrigin() {}

    public static void mark(Packet<?> packet) {
        API_HANDLED.put(packet, Boolean.TRUE);
    }

    public static boolean wasHandled(Packet<?> packet) {
        return API_HANDLED.containsKey(packet);
    }
}

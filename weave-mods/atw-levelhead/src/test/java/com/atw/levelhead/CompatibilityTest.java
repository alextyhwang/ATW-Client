package com.atw.levelhead;

import com.atw.levelhead.command.LevelHeadCommand;
import com.atw.levelhead.config.LevelHeadConfig;
import com.atw.levelhead.data.LevelTag;
import com.atw.levelhead.data.LevelTagDiskCache;
import com.atw.levelhead.hook.ChatCommandCompatibilityHook;
import com.atw.levelhead.hook.ChatPacketOrigin;
import com.atw.levelhead.hook.TabListLevelTagHook;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.weavemc.api.Hook;
import net.weavemc.api.command.Command;
import net.weavemc.api.command.CommandBus;
import net.weavemc.api.event.ChatEvent;
import net.weavemc.api.event.PacketEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityTest {
    @Test void commandNameIsRemovedBeforeExistingSubcommandHandling() {
        ATWLevelHead mod = mock(ATWLevelHead.class);
        LevelHeadConfig config = mock(LevelHeadConfig.class);
        when(mod.getConfig()).thenReturn(config);
        LevelHeadCommand command = new LevelHeadCommand(mod);
        command.execute(new String[]{"atwlh", "mode", "bw"});
        verify(mod).setDisplayMode("bedwars");
        command.execute(new String[]{"atwlevelhead", "bg", "off"});
        verify(config).setBackgroundEnabled(false);
        verify(config).save();
        command.execute(new String[]{"atwlh", "stats", "captainatw"});
        verify(mod).requestChatStats("captainatw");
        command.execute(new String[]{"atwlh", "reload"});
        verify(mod).reload();
        command.execute(new String[]{"atwlh", "clearcache"});
        verify(mod).clearCache();
    }

    @Test void packetBridgeRunsTheRealCommandBusAndCancelsOnlyHandledCommands() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CommandBus.register(new Command("atw_test_bridge", "atw_test_alias") {
            @Override public void execute(String[] args) {
                assertArrayEquals(new String[]{"atw_test_alias", "value"}, args);
                calls.incrementAndGet();
            }
        });
        ATWLevelHead mod = mock(ATWLevelHead.class);
        Method bridge = ATWLevelHead.class.getDeclaredMethod("onPacketSend", PacketEvent.Send.class);
        bridge.setAccessible(true);
        PacketEvent.Send handled = new PacketEvent.Send(new C01PacketChatMessage("/atw_test_alias value"));
        bridge.invoke(mod, handled);
        assertTrue(handled.isCancelled());
        assertEquals(1, calls.get());
        bridge.invoke(mod, handled);
        assertEquals(1, calls.get(), "cancelled packets must not execute twice");
        PacketEvent.Send manual = new PacketEvent.Send(new C01PacketChatMessage("/atw_test_alias value"));
        ChatPacketOrigin.mark(manual.getPacket());
        bridge.invoke(mod, manual);
        assertFalse(manual.isCancelled());
        assertEquals(1, calls.get(), "API-origin packets must not emit a second chat event");
        PacketEvent.Send serverCommand = new PacketEvent.Send(new C01PacketChatMessage("/atw_unregistered_server_command"));
        bridge.invoke(mod, serverCommand);
        assertFalse(serverCommand.isCancelled());
        PacketEvent.Send chat = new PacketEvent.Send(new C01PacketChatMessage("hello"));
        bridge.invoke(mod, chat);
        assertFalse(chat.isCancelled());
        assertEquals(1, calls.get());
    }

    @Test void cachePersistsSeparateLevelAndBedwarsValues() {
        UUID uuid = UUID.randomUUID();
        LevelTagDiskCache cache = LevelTagDiskCache.load();
        cache.clear();
        cache.putAll("level", Collections.singletonMap(uuid, new LevelTag(uuid, "&aLevel ", "120")));
        cache.putAll("bedwars", Collections.singletonMap(uuid, new LevelTag(uuid, "&eStars ", "300")));
        LevelTagDiskCache loaded = LevelTagDiskCache.load();
        assertEquals("\u00a7aLevel 120", loaded.getFresh("level", uuid).getText());
        assertEquals("\u00a7eStars 300", loaded.getFresh("bedwars", uuid).getText());
        assertNull(loaded.getFresh("bedwars-other", uuid));
        loaded.clear();
    }

    @Test void chatOriginHookCoexistsWithTheActualModernApiInEitherOrder() throws Exception {
        Class<?> apiClass = Class.forName("net.weavemc.api.hook.ChatEventSentHook");
        Hook api = (Hook) apiClass.getDeclaredConstructor().newInstance();
        for (boolean apiFirst : new boolean[]{true, false}) {
            ClassNode player = new ClassNode();
            player.name = "net/minecraft/client/entity/EntityPlayerSP";
            player.superName = "java/lang/Object";
            MethodNode send = new MethodNode(Opcodes.ACC_PUBLIC, "sendChatMessage",
                    "(Ljava/lang/String;)V", null, null);
            String packet = "net/minecraft/network/play/client/C01PacketChatMessage";
            send.instructions.add(new TypeInsnNode(Opcodes.NEW, packet));
            send.instructions.add(new InsnNode(Opcodes.DUP));
            send.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            send.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, packet, "<init>",
                    "(Ljava/lang/String;)V", false));
            send.instructions.add(new InsnNode(Opcodes.POP));
            send.instructions.add(new InsnNode(Opcodes.RETURN));
            send.maxLocals = 2; send.maxStack = 8;
            player.methods.add(send);
            ChatCommandCompatibilityHook origin = new ChatCommandCompatibilityHook();
            Hook.AssemblerConfig cfg = mock(Hook.AssemblerConfig.class);
            if (apiFirst) api.transform(player, cfg);
            origin.transform(player, cfg);
            if (!apiFirst) api.transform(player, cfg);
            int count = send.instructions.size();
            origin.transform(player, cfg);
            assertEquals(count, send.instructions.size());
            long dispatches = java.util.Arrays.stream(send.instructions.toArray()).filter(i ->
                    i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("postEvent")).count();
            assertEquals(1, dispatches, "the official API must be the only chat emitter");
            assertEquals(1, java.util.Arrays.stream(send.instructions.toArray()).filter(i ->
                    i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("mark")).count());
            new Analyzer<>(new BasicVerifier()).analyze(player.name, send);
            ClassNode gui = new ClassNode(); gui.name = "net/minecraft/client/gui/GuiScreen";
            MethodNode clipboard = method("setClipboardString", "(Ljava/lang/String;)V", Opcodes.RETURN);
            MethodNode text = method("setText", "(Ljava/lang/String;Z)V", Opcodes.RETURN);
            gui.methods.add(clipboard); gui.methods.add(text);
            origin.transform(gui, cfg);
            assertEquals(1, clipboard.instructions.size());
            assertEquals(1, text.instructions.size());
        }
    }

    @Test void constructorAndPreInitDoNotLoadMinecraftClasses() throws Exception {
        java.util.concurrent.atomic.AtomicInteger gameLoads = new java.util.concurrent.atomic.AtomicInteger();
        java.net.URL[] urls = {ATWLevelHead.class.getProtectionDomain().getCodeSource().getLocation()};
        try (java.net.URLClassLoader isolated = new java.net.URLClassLoader(urls, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.")) {
                    gameLoads.incrementAndGet();
                    throw new ClassNotFoundException("Minecraft is unavailable before init: " + name);
                }
                if (name.equals("com.atw.levelhead.LevelHeadInitializer")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> initializer = Class.forName("com.atw.levelhead.LevelHeadInitializer", true, isolated);
            net.weavemc.api.ModInitializer instance = (net.weavemc.api.ModInitializer) initializer.getConstructor().newInstance();
            instance.preInit(mock(java.lang.instrument.Instrumentation.class));
            assertEquals(0, gameLoads.get());
        }
    }

    @Test void tabHookKeepsEveryReturnAndDoesNotDoubleFormat() throws Exception {
        ClassNode node = new ClassNode();
        node.name = "net/minecraft/client/gui/GuiPlayerTabOverlay";
        node.superName = "java/lang/Object";
        MethodNode name = new MethodNode(Opcodes.ACC_PUBLIC, "getPlayerName",
                "(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", null, null);
        name.instructions.add(new LdcInsnNode("player"));
        name.instructions.add(new InsnNode(Opcodes.ARETURN));
        name.maxLocals = 2; name.maxStack = 4;
        node.methods.add(name);
        TabListLevelTagHook hook = new TabListLevelTagHook();
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        int count = name.instructions.size();
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        assertEquals(count, name.instructions.size());
        assertTrue(java.util.Arrays.stream(name.instructions.toArray()).anyMatch(i ->
                i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("appendLevelTag")));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, name);
    }

    private static MethodNode method(String name, String desc, int returnOpcode) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null);
        method.instructions.add(new InsnNode(returnOpcode));
        method.maxLocals = 3; method.maxStack = 8;
        return method;
    }
}

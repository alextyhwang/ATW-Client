package com.atw.rebrand;

import com.atw.rebrand.hook.LunarLogoResourceHook;
import com.atw.rebrand.hook.MenuLogoOverlayHook;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.weavemc.api.Hook;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RebrandTest {
    @Test void recoveredLogoMatchesRecordedOriginalPngSha256() throws Exception {
        // SHA-256 of atw-rebrand/logo.png recovered from the installed ATWRebrand 0.1.0 jar.
        // The canonical test needs only its packaged resource, never the recovery/staging jar.
        try (java.io.InputStream resource = getClass().getResourceAsStream("/atw-rebrand/logo.png")) {
            assertNotNull(resource);
            assertEquals("df5c280b7045f30a5aa4acddfebeb5a5210c5c1cc8cd81170f479fc6291c6b50",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.readAllBytes())));
        }
        assertEquals("ATW Client", ATWRebrand.NAME);
    }

    @Test void fallbackRenderHookRemainsIdempotentAndVerifiable() throws Exception {
        ClassNode node = new ClassNode();
        node.name = "net/minecraft/client/gui/GuiScreen";
        node.superName = "java/lang/Object";
        MethodNode draw = new MethodNode(Opcodes.ACC_PUBLIC, "drawScreen", "(IIF)V", null, null);
        draw.instructions.add(new InsnNode(Opcodes.RETURN));
        draw.maxLocals = 4; draw.maxStack = 2;
        node.methods.add(draw);
        MenuLogoOverlayHook hook = new MenuLogoOverlayHook();
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        int count = draw.instructions.size();
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        assertEquals(count, draw.instructions.size());
        assertTrue(java.util.Arrays.stream(draw.instructions.toArray()).anyMatch(i ->
                i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("render")
                && ((MethodInsnNode) i).owner.equals("com/atw/rebrand/render/MenuLogoOverlay")));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, draw);
    }

    @Test void recoveredInactiveLogoHookOnlyMatchesTheOriginalExactResource() {
        ClassNode lunar = new ClassNode(); lunar.name = "com/moonsworth/lunar/Test";
        MethodNode method = new MethodNode();
        LdcInsnNode target = new LdcInsnNode("logo/logo-128x117.png");
        LdcInsnNode other = new LdcInsnNode("logo/other.png");
        method.instructions.add(target); method.instructions.add(other); lunar.methods.add(method);
        new LunarLogoResourceHook().transform(lunar, mock(Hook.AssemblerConfig.class));
        assertEquals("atw-rebrand/logo.png", target.cst);
        assertEquals("logo/other.png", other.cst);
        ClassNode foreign = new ClassNode(); foreign.name = "other/Test";
        MethodNode unrelated = new MethodNode();
        LdcInsnNode foreignLogo = new LdcInsnNode("logo/logo-128x117.png");
        unrelated.instructions.add(foreignLogo); foreign.methods.add(unrelated);
        new LunarLogoResourceHook().transform(foreign, mock(Hook.AssemblerConfig.class));
        assertEquals("logo/logo-128x117.png", foreignLogo.cst);
    }
}

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
    @Test void brandingLayerWinsWithoutChangingOriginalListOrUnrelatedResources() throws Exception {
        net.minecraft.client.resources.IResourcePack user = mock(net.minecraft.client.resources.IResourcePack.class);
        java.util.List<net.minecraft.client.resources.IResourcePack> original = java.util.List.of(user);
        java.util.List<net.minecraft.client.resources.IResourcePack> layered = BrandingResources.append(original);
        assertEquals(1, original.size());
        assertSame(user, layered.get(0));
        assertTrue(layered.get(1) instanceof BrandingResources);
        assertEquals(2, BrandingResources.append(layered).size());
        BrandingResources brand = (BrandingResources) layered.get(1);
        assertTrue(brand.resourceExists(new net.minecraft.util.ResourceLocation("lunar", "logo/logo-branding-215x33.png")));
        assertFalse(brand.resourceExists(new net.minecraft.util.ResourceLocation("minecraft", "textures/blocks/stone.png")));
        assertFalse(brand.resourceExists(new net.minecraft.util.ResourceLocation("lunar", "backgrounds/splash.png")));
        try (java.io.InputStream stream = brand.getInputStream(new net.minecraft.util.ResourceLocation("lunar", "logo/logo-128x117.png"))) {
            assertEquals(128, javax.imageio.ImageIO.read(stream).getWidth());
        }
    }

    @Test void resourceReloadHookIsIdempotentAndVerifiable() throws Exception {
        ClassNode node = new ClassNode(); node.name = "net/minecraft/client/resources/SimpleReloadableResourceManager";
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "reloadResources", "(Ljava/util/List;)V", null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN)); method.maxLocals=2; method.maxStack=1; node.methods.add(method);
        com.atw.rebrand.hook.BrandingResourcesHook hook = new com.atw.rebrand.hook.BrandingResourcesHook();
        hook.transform(node, mock(Hook.AssemblerConfig.class)); int size = method.instructions.size();
        hook.transform(node, mock(Hook.AssemblerConfig.class)); assertEquals(size,method.instructions.size());
        new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
    }
    @Test void packagedLogoIsAnAtwBadge() throws Exception {
        try (java.io.InputStream resource = getClass().getResourceAsStream("/atw-rebrand/logo.png")) {
            assertNotNull(resource);
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(resource);
            assertNotNull(image);
            assertEquals(1024, image.getWidth());
            assertEquals(1024, image.getHeight());
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

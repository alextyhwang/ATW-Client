package com.atw.renderboost.hook;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** The two rejected methods are uninstrumented in the second actual startup capture. */
@Tag("private-name-restart")
class NameProbeRestartCaptureTest {
    static ClassNode renderer() throws Exception {
        Path root=Paths.get(System.getProperty("atwboost.nameRestartCaptureRoot"));
        try(java.util.stream.Stream<Path> paths=Files.list(root)) {
            Path path=paths.filter(p->p.getFileName().toString().contains("RendererLivingEntity") && p.toString().endsWith(".live.class")).findFirst().orElseThrow(()->new IllegalStateException("Missing renderer input"));
            ClassNode c=new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(c,0); return c;
        }
    }
    @Test void secondActualStartupRetainsBothExactBodiesAfterDetachedSessionAndWeaveNameComparison() throws Exception {
        ClassNode c=NameProbeHookStageCaptureTest.rename(renderer(),false);
        NameProbeHookStage stage=new NameProbeHookStage(c);
        int checked=0;
        for(MethodNode m:c.methods) {
            String name=NameProbeHookStageCaptureTest.plain(m.name);
            if (!(name.equals("impl$drawLabel") || name.equals("renderName") && m.desc.startsWith("(Lnet/minecraft/entity/EntityLivingBase;"))) continue;
            for(AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode)
                assertNotEquals(NameProbeHook.RUNTIME,((MethodInsnNode)i).owner,"second capture must retain uninstrumented target body");
            AbstractInsnNode[] before=m.instructions.toArray();
            MethodNode comparison=assertDoesNotThrow(()->stage.comparison(m));
            assertTrue(NameProbeHook.matches(c.name,comparison),name+" retains the original exact evidence");
            assertArrayEquals(before,m.instructions.toArray(),"comparison never mutates the actual method");
            checked++;
        }
        assertEquals(2,checked);
    }

    @Test void aDifferentSessionCannotAdmitUnchangedUniqueMethodNames() throws Exception {
        ClassNode c=NameProbeHookStageCaptureTest.rename(renderer(),false);
        for(MethodNode m:c.methods) if(m.visibleAnnotations!=null)
            for(AnnotationNode a:m.visibleAnnotations) if(NameProbeHookStage.MERGED.equals(a.desc))
                a.values.set(a.values.indexOf("sessionId")+1,"00000000-0000-0000-0000-000000000000");
        assertThrows(IllegalArgumentException.class,()->new NameProbeHookStage(c));
    }

    @Test void eachOfTheFourUniqueDeclarationsRejectsAnIncorrectSessionSuffix() throws Exception {
        ClassNode original=renderer(); int checked=0;
        for(int index=0;index<original.methods.size();index++) {
            MethodNode candidate=original.methods.get(index);
            if(!Arrays.asList("md1a3c1b$lambda$impl$drawLabel$3$1", "md1a3c1b$lambda$proxy$renderName$0$4",
                    "md1a3c1b$lambda$proxy$renderName$1$3", "md1a3c1b$lambda$proxy$renderName$2$2").contains(candidate.name)) continue;
            ClassNode c=NameProbeHookStageCaptureTest.rename(renderer(),false);
            MethodNode m=c.methods.get(index);
            m.name=m.name.replace("md1a3c1b$","mdffffff$");
            AbstractInsnNode[] before=m.instructions.toArray();
            assertThrows(IllegalArgumentException.class,()->new NameProbeHookStage(c));
            assertArrayEquals(before,m.instructions.toArray()); checked++;
        }
        assertEquals(4,checked);
    }

    @Test void sessionNormalizationDoesNotHideChangedBootstrapHandleOwners() throws Exception {
        ClassNode c=NameProbeHookStageCaptureTest.rename(renderer(),false);
        MethodNode m=c.methods.stream().filter(n->NameProbeHookStageCaptureTest.plain(n.name).equals("impl$drawLabel")).findFirst().get();
        boolean changed=false;
        for(AbstractInsnNode i:m.instructions) if(i instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode call=(InvokeDynamicInsnNode)i;
            for(int index=0;index<call.bsmArgs.length;index++) if(call.bsmArgs[index] instanceof org.objectweb.asm.Handle) {
                org.objectweb.asm.Handle h=(org.objectweb.asm.Handle)call.bsmArgs[index];
                if(h.getOwner().equals(c.name)) {
                    call.bsmArgs[index]=new org.objectweb.asm.Handle(h.getTag(),h.getOwner()+"$unknown",h.getName(),h.getDesc(),h.isInterface());
                    changed=true; break;
                }
            }
            if(changed) break;
        }
        assertTrue(changed);
        NameProbeHookStage stage=new NameProbeHookStage(c);
        assertThrows(IllegalArgumentException.class,()->stage.comparison(m));
    }
}

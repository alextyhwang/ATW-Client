package com.atw.renderboost.hook;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;

@Tag("private-name-codec-capture")
class NameEncodingCaptureTest implements Opcodes {
    static final class Relocated extends NameParseCaptureTest.Relocated {
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals(NameEncodingHook.class.getName()))return super.loadClass(name,resolve);
            Class<?> found=findLoadedClass(name);
            if(found==null)try(java.io.InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                ClassWriter writer=new ClassWriter(0);new ClassReader(in).accept(new org.objectweb.asm.commons.ClassRemapper(writer,new org.objectweb.asm.commons.Remapper(){
                    @Override public String map(String n){return n.startsWith("org/objectweb/asm/")?"net/weavemc/loader/impl/shaded/asm/"+n.substring("org/objectweb/asm/".length()):n;}
                }),0);byte[] bytes=writer.toByteArray();found=defineClass(name,bytes,0,bytes.length);
            }catch(java.io.IOException e){throw new ClassNotFoundException(name,e);}if(resolve)resolveClass(found);return found;
        }
    }
    @Test void composedAdmissionWorksWithActualRelocatedAsmOperandTags()throws Exception {
        Relocated loader=new Relocated();Class<?> node=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"),reader=loader.loadClass("net.weavemc.loader.impl.shaded.asm.ClassReader"),visitor=loader.loadClass("net.weavemc.loader.impl.shaded.asm.ClassVisitor");
        java.lang.reflect.Method matches=loader.loadClass(NameEncodingHook.class.getName()).getMethod("matches",node);
        for(ClassNode original:NameParseCaptureTest.captures()) {
            ClassNode c=composed(NameProbeHookStageCaptureTest.rename(original,false));ClassWriter writer=new ClassWriter(0);c.accept(writer);Object relocated=node.getConstructor().newInstance();
            reader.getMethod("accept",visitor,int.class).invoke(reader.getConstructor(byte[].class).newInstance((Object)writer.toByteArray()),relocated,0);assertEquals(true,matches.invoke(null,relocated),c.name);
        }
    }
    static ClassNode composed(ClassNode original){assertTrue(NameParseHook.matches(original));new NameParseHook().transform(original,()->{});assertTrue(NameEncodingHook.matches(original),original.name);return original;}
    static void verify(ClassNode c)throws Exception {
        Map<MethodNode,AbstractInsnNode[]> nodes=new IdentityHashMap<>();Map<MethodNode,String> hashes=new IdentityHashMap<>();Map<MethodNode,List<TryCatchBlockNode>> handlers=new IdentityHashMap<>();
        for(MethodNode m:c.methods){nodes.put(m,m.instructions.toArray());hashes.put(m,TerrainEvidence.fingerprint(m));handlers.put(m,new ArrayList<>(m.tryCatchBlocks));}
        new NameEncodingHook().transform(c,()->{});
        for(MethodNode m:c.methods){assertEquals(handlers.get(m),m.tryCatchBlocks);int last=-1;
            for(AbstractInsnNode n:nodes.get(m)){int pos=m.instructions.indexOf(n);assertTrue(pos>last);last=pos;}
            if(!c.name.equals(NameParseHook.CONVERTER)||!NameParseHookStage.logicalName(m.name).equals("serializeShowEntity"))assertEquals(hashes.get(m),TerrainEvidence.fingerprint(m));
            else new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
        }
        c.accept(new CheckClassAdapter(new ClassWriter(0),false));assertFalse(NameEncodingHook.matches(c),"repeat transform rejects");
        int count=c.methods.stream().mapToInt(m->m.instructions.size()).sum();new NameEncodingHook().transform(c,()->fail());assertEquals(count,c.methods.stream().mapToInt(m->m.instructions.size()).sum());
    }
    @Test void allTenCapturedAndPinnedConflictInputsComposePreservingNameDecodeAndAllOtherBodies()throws Exception {
        for(ClassNode original:NameParseCaptureTest.captures()){ClassNode conflict=NameProbeHookStageCaptureTest.rename(original,false);verify(composed(original));verify(composed(conflict));}
    }
    @Test @Tag("private-name-codec-startup") void observedStartupShapesComposeAfterUnchangedNames()throws Exception {
        for(ClassNode original:NameParseCaptureTest.captures())verify(composed(NameParseStartupProfileTest.startup(original)));
    }
    @Test void capturedReverseMethodExecutesPublicDifferentialContract()throws Exception {
        ClassNode c=NameParseCaptureTest.captures().stream().filter(n->n.name.equals(NameParseHook.CONVERTER)).findFirst().get();
        MethodNode m=c.methods.stream().filter(n->n.name.equals("serializeShowEntity")&&n.desc.equals(NameEncodingHook.DESC)).findFirst().get();NameEncodingHookTest.parity(m);
    }
    @Test void capturedReverseMethodExecutesProductionOutwardFreshnessContract()throws Exception {
        ClassNode c=NameParseCaptureTest.captures().stream().filter(n->n.name.equals(NameParseHook.CONVERTER)).findFirst().get();
        MethodNode m=c.methods.stream().filter(n->n.name.equals("serializeShowEntity")&&n.desc.equals(NameEncodingHook.DESC)).findFirst().get();NameEncodingProductionExecutionTest.parity(m);
    }
    @Test void everyComposedMethodOperandAccessShapeMarkerAndProvenanceMutationRejects()throws Exception {
        for(ClassNode original:NameParseCaptureTest.captures()) {
            ClassNode baseline=composed(NameProbeHookStageCaptureTest.rename(original,false));
            for(int index=0;index<baseline.methods.size();index++)for(int change=0;change<2;change++) {
                ClassNode c=new ClassNode();baseline.accept(c);MethodNode m=c.methods.get(index);if(change==0)m.instructions.insert(new InsnNode(NOP));else m.access^=ACC_FINAL;assertFalse(NameEncodingHook.matches(c));
            }
            for(int change=0;change<5;change++){ClassNode c=new ClassNode();baseline.accept(c);
                if(change==0)c.fields.add(new FieldNode(ACC_PRIVATE,"unknown","I",null,null));
                else if(change==1)c.fields.stream().filter(f->f.name.equals(NameParseHook.MARKER)).findFirst().get().value=0;
                else if(change==2)c.methods.add(c.methods.get(0));
                else if(change==3)Collections.reverse(c.methods);
                else c.methods.get(0).invisibleAnnotations=new ArrayList<>(Collections.singletonList(new AnnotationNode(NameParseHookStage.MERGED)));
                assertFalse(NameEncodingHook.matches(c),c.name+" mutation "+change);
            }
            for(MethodNode m:baseline.methods)if(m.visibleAnnotations!=null)for(AnnotationNode annotation:m.visibleAnnotations)if(annotation.desc.equals(NameParseHookStage.MERGED)) {
                ClassNode c=new ClassNode();baseline.accept(c);AnnotationNode a=c.methods.get(baseline.methods.indexOf(m)).visibleAnnotations.stream().filter(n->n.desc.equals(NameParseHookStage.MERGED)).findFirst().get();a.values.set(a.values.indexOf("mixin")+1,"unknown.Mixin");assertFalse(NameEncodingHook.matches(c));
            }
        }
    }
}

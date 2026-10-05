package com.atw.renderboost.hook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** All actual inputs are read in place. No private class bytes enter resources/archives. */
@Tag("private-name-codec-capture")
class NameParseCaptureTest implements Opcodes {
    static List<ClassNode> captures()throws Exception {
        List<ClassNode> result=new ArrayList<>();
        try(java.util.stream.Stream<Path> files=Files.list(Paths.get(System.getProperty("atwboost.nameCodecCaptureRoot")))) {
            for(Path p:(Iterable<Path>)files.filter(p->p.toString().endsWith(".live.class"))::iterator) {
                ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(c,0);
                if(Arrays.asList(NameParseHook.TARGETS).contains(c.name))result.add(c);
            }
        }
        assertEquals(10,result.size());return result;
    }
    @Test void tenActualClassesAndDetachedHookStagePassWithoutOriginalRewrites()throws Exception {
        for(ClassNode c:captures()) {
            assertTrue(NameParseHook.matches(c),c.name);ClassNode stage=NameProbeHookStageCaptureTest.rename(c,false);
            assertTrue(NameParseHook.matches(stage),c.name+" hook stage");
            Map<MethodNode,AbstractInsnNode[]> originals=new IdentityHashMap<>();Map<MethodNode,List<TryCatchBlockNode>> handlers=new IdentityHashMap<>();
            for(MethodNode m:stage.methods){originals.put(m,m.instructions.toArray());handlers.put(m,new ArrayList<>(m.tryCatchBlocks));}
            new NameParseHook().transform(stage,()->{});
            for(MethodNode m:stage.methods) {
                assertEquals(handlers.get(m),m.tryCatchBlocks,"handler priorities unchanged");int last=-1;
                for(AbstractInsnNode n:originals.get(m)){int position=m.instructions.indexOf(n);assertTrue(position>last,"all original instruction nodes stay in order");last=position;}
                if(!NameParseHookStage.logicalName(m.name).equals("deserializeShowEntity") || !c.name.equals(NameParseHook.CONVERTER))assertArrayEquals(originals.get(m),m.instructions.toArray());
                else new Analyzer<>(new BasicVerifier()).analyze(stage.name,m);
            }
        }
    }
    @Test void metadataReportsSeparateOrderedShapeFieldsAndExactMethodHashesWithoutMutation()throws Exception {
        for(ClassNode c:captures()) {
            Properties exact=NameParseHook.mismatchReport(c);assertEquals("true",exact.getProperty("shapeMatch"));assertEquals("true",exact.getProperty("basicShapeMatch"));assertEquals("0",exact.getProperty("changedMethods"));
            ClassNode reordered=new ClassNode();c.accept(reordered);Collections.reverse(reordered.methods);
            Properties report=NameParseHook.mismatchReport(reordered);assertEquals("0",report.getProperty("changedMethods"));
            if(c.methods.size()>1){assertEquals("false",report.getProperty("basicShapeMatch"));assertFalse(NameParseHook.matches(reordered));}
            ClassNode fieldChanged=new ClassNode();c.accept(fieldChanged);
            if(!fieldChanged.fields.isEmpty()) {
                fieldChanged.fields.get(0).signature="Ljava/lang/Object;";
                Properties fields=NameParseHook.mismatchReport(fieldChanged);assertEquals("true",fields.getProperty("basicShapeMatch"));assertEquals("false",fields.getProperty("shapeMatch"));assertEquals("0",fields.getProperty("changedMethods"));
            }
        }
    }
    @Test void everyDependencyMethodBodyAndAccessMutationFailsClosed()throws Exception {
        int tested=0;
        for(ClassNode c:captures())for(int index=0;index<c.methods.size();index++)for(int change=0;change<2;change++) {
            ClassNode altered=new ClassNode();c.accept(altered);MethodNode m=altered.methods.get(index);
            if(change==0)m.instructions.insert(new InsnNode(NOP));else m.access^=ACC_FINAL;
            assertFalse(NameParseHook.matches(altered),c.name+"."+m.name);tested++;
        }
        assertEquals(246,tested);
    }
    @Test void unknownMetadataLayoutForeignReferencesAndDuplicateDeclarationsReject()throws Exception {
        for(ClassNode c:captures()) {
            ClassNode changed=new ClassNode();c.accept(changed);changed.fields.add(new FieldNode(ACC_PUBLIC,"unproved","I",null,null));assertFalse(NameParseHook.matches(changed));
            changed=new ClassNode();c.accept(changed);changed.methods.add(changed.methods.get(0));assertFalse(NameParseHook.matches(changed));
            for(MethodNode original:c.methods)if(original.visibleAnnotations!=null)
                for(AnnotationNode a:original.visibleAnnotations)if(a.desc.equals(NameParseHookStage.MERGED)) {
                    ClassNode stage=NameProbeHookStageCaptureTest.rename(c,false);
                    MethodNode m=stage.methods.get(c.methods.indexOf(original));AnnotationNode merged=m.visibleAnnotations.stream().filter(n->n.desc.equals(NameParseHookStage.MERGED)).findFirst().get();
                    merged.values.set(merged.values.indexOf("mixin")+1,"unknown.Mixin");assertFalse(NameParseHook.matches(stage));
                }
        }
    }
    @Test void actualCapturedConverterExecutesOriginalCallAndExceptionOrderWithFreshHitResults()throws Exception {
        ClassNode c=captures().stream().filter(n->n.name.equals(NameParseHook.CONVERTER)).findFirst().get();
        MethodNode original=c.methods.stream().filter(m->m.name.equals("deserializeShowEntity")&&m.desc.equals(NameParseHook.DESC)).findFirst().get();
        NameParseHookTest.parity(original);
    }
    @Test void actualNativeCompoundAndStringConstructorGetterBodiesProduceFreshTagsOnHits()throws Exception {
        Map<String,byte[]> bytes=new HashMap<>();
        for(ClassNode captured:captures())if(captured.name.equals("net/minecraft/nbt/NBTBase") || captured.name.equals("net/minecraft/nbt/NBTTagCompound") || captured.name.equals("net/minecraft/nbt/NBTTagString")) {
            ClassNode c=new ClassNode();c.version=captured.version;c.access=captured.access;c.name=captured.name;c.superName=captured.superName;c.fields=captured.fields;
            for(MethodNode m:captured.methods) {
                boolean include=m.name.equals("<init>") || m.name.equals("getString") || m.name.equals("getId");
                if(c.name.endsWith("NBTTagCompound"))include|=m.name.equals("setString") || m.name.equals("setTag") || m.name.equals("hasKey") || m.name.equals("getTagId");
                if(include)c.methods.add(m);
            }
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);bytes.put(c.name.replace('/','.'),w.toByteArray());
        }
        // The captured constructor calls Guava's ordinary HashMap factory.
        ClassNode maps=NameProbeCaptureTest.empty("com/google/common/collect/Maps",false);
        MethodNode factory=new MethodNode(ACC_PUBLIC|ACC_STATIC,"newHashMap","()Ljava/util/HashMap;",null,null);
        factory.instructions.add(new TypeInsnNode(NEW,"java/util/HashMap"));factory.instructions.add(new InsnNode(DUP));factory.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/util/HashMap","<init>","()V",false));factory.instructions.add(new InsnNode(ARETURN));maps.methods.add(factory);
        ClassWriter mapBytes=new ClassWriter(ClassWriter.COMPUTE_MAXS);maps.accept(mapBytes);bytes.put(maps.name.replace('/','.'),mapBytes.toByteArray());
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!bytes.containsKey(name))return super.loadClass(name,resolve);Class<?> c=findLoadedClass(name);
                if(c==null){byte[] b=bytes.get(name);c=defineClass(name,b,0,b.length);}if(resolve)resolveClass(c);return c;
            }
        };
        Class<?> compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");java.lang.reflect.Method set=compound.getMethod("setString",String.class,String.class),get=compound.getMethod("getString",String.class);
        Object original=compound.getConstructor().newInstance();set.invoke(original,"name","§aNative");set.invoke(original,"id","uuid");
        com.atw.renderboost.cache.NameParseCache.Access access=new com.atw.renderboost.cache.NameParseCache.Access() {
            public String read(Object c,String key){try{return (String)get.invoke(c,key);}catch(Exception e){throw new AssertionError(e);}}
            public Object fresh(int bits,String name,String id,String type){try{Object c=compound.getConstructor().newInstance();set.invoke(c,"name",name);set.invoke(c,"id",id);if((bits&4)!=0)set.invoke(c,"type",type);return c;}catch(Exception e){throw new AssertionError(e);}}
        };
        com.atw.renderboost.cache.NameParseCache cache=new com.atw.renderboost.cache.NameParseCache(256,4096,524288);String key="{name:\"§aNative\",id:\"uuid\"}";cache.record(key,original,access);
        Object first=cache.lookup(key,access),second=cache.lookup(key,access);assertNotSame(original,first);assertNotSame(first,second);assertEquals(get.invoke(original,"name"),get.invoke(first,"name"));
        java.lang.reflect.Field map=compound.getField("tagMap");Map<?,?> a=(Map<?,?>)map.get(original),b=(Map<?,?>)map.get(first),d=(Map<?,?>)map.get(second);
        assertNotSame(a,b);assertNotSame(b,d);for(Object field:a.keySet()){assertNotSame(a.get(field),b.get(field));assertNotSame(b.get(field),d.get(field));}
        assertFalse(b.containsKey("type"));set.invoke(first,"name","mutation");assertEquals("§aNative",get.invoke(second,"name"));
    }
    static class Relocated extends ClassLoader {
        Relocated(){super(NameParseCaptureTest.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            String internal=name.replace('.','/');boolean asm=internal.startsWith("net/weavemc/loader/impl/shaded/asm/");
            if(!asm && !name.equals(NameParseHook.class.getName()) && !name.equals(NameParseHookStage.class.getName()) && !name.startsWith(NameParseHookStage.class.getName()+"$") && !name.equals(TerrainEvidence.class.getName()))return super.loadClass(name,resolve);
            Class<?> found=findLoadedClass(name);
            if(found==null)try(InputStream in=getParent().getResourceAsStream((asm?"org/objectweb/asm/"+internal.substring("net/weavemc/loader/impl/shaded/asm/".length()):internal)+".class")) {
                ClassWriter w=new ClassWriter(0);new ClassReader(in).accept(new ClassRemapper(w,new Remapper(){
                    @Override public String map(String n){return n.startsWith("org/objectweb/asm/")?"net/weavemc/loader/impl/shaded/asm/"+n.substring("org/objectweb/asm/".length()):n;}
                }),0);byte[] data=w.toByteArray();found=defineClass(name,data,0,data.length);
            }catch(IOException e){throw new ClassNotFoundException(name,e);}
            if(resolve)resolveClass(found);return found;
        }
    }
    @Test void tenExactGatesWorkUnderActualRelocatedAsmTags()throws Exception {
        Relocated l=new Relocated();Class<?> node=l.loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"),reader=l.loadClass("net.weavemc.loader.impl.shaded.asm.ClassReader"),visitor=l.loadClass("net.weavemc.loader.impl.shaded.asm.ClassVisitor");
        java.lang.reflect.Method match=l.loadClass(NameParseHook.class.getName()).getMethod("matches",node);
        for(ClassNode c:captures()) {
            ClassWriter w=new ClassWriter(0);c.accept(w);Object n=node.getConstructor().newInstance();reader.getMethod("accept",visitor,int.class).invoke(reader.getConstructor(byte[].class).newInstance((Object)w.toByteArray()),n,0);
            assertEquals(true,match.invoke(null,n),c.name);
        }
    }
}

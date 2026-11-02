package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameEncodingCache;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual captured compound/tag stringifiers execute in private, with the pinned disk factory.
 * Disk factory use proves offline parity only and never updates runtime admission. */
@Tag("private-name-codec-capture")
class NameEncodingNativeCaptureTest implements Opcodes {
    @Test void exactCapturedNbtStringificationEscapingOrderingAndFreshOwnershipMatchStoredStrings()throws Exception {
        Map<String,byte[]> bytes=new HashMap<>();
        for(ClassNode captured:NameParseCaptureTest.captures())if(captured.name.equals("net/minecraft/nbt/NBTBase")||captured.name.equals("net/minecraft/nbt/NBTTagCompound")||captured.name.equals("net/minecraft/nbt/NBTTagString")) {
            ClassNode c=new ClassNode();c.version=captured.version;c.access=captured.access;c.name=captured.name;c.superName=captured.superName;c.fields=captured.fields;
            for(MethodNode m:captured.methods)if(m.name.equals("<init>")||m.name.equals("toString")||m.name.equals("quoteAndEscape")||m.name.equals("setString")||m.name.equals("getId"))c.methods.add(m);
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);bytes.put(c.name.replace('/','.'),w.toByteArray());
        }
        Path jar=Paths.get("../../build/dependencies/minecraft/lunarclient/libraries/com/google/guava/guava/17.0/guava-17.0.jar");
        ClassNode maps=new ClassNode();try(JarFile input=new JarFile(jar.toFile())){new ClassReader(input.getInputStream(input.getJarEntry(NameEncodingHook.MAPS+".class"))).accept(maps,0);}
        assertTrue(NameEncodingHook.factorySemantics(maps));
        Properties diskEvidence=NameEncodingHook.profile(maps);diskEvidence.setProperty("captureKind","disk-only");diskEvidence.setProperty("parentApproved","true");
        assertFalse(NameEncodingHook.factoryMatches(maps,diskEvidence),"disk-only evidence cannot authorize factory admission");
        ClassNode minimal=NameProbeCaptureTest.empty(NameEncodingHook.MAPS,false);minimal.methods.add(maps.methods.stream().filter(m->m.name.equals("newHashMap")&&m.desc.equals("()Ljava/util/HashMap;")).findFirst().get());
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);minimal.accept(w);bytes.put(minimal.name.replace('/','.'),w.toByteArray());
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {if(!bytes.containsKey(name))return super.loadClass(name,resolve);Class<?> c=findLoadedClass(name);if(c==null){byte[] data=bytes.get(name);c=defineClass(name,data,0,data.length);}if(resolve)resolveClass(c);return c;}
        };
        Class<?> compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");java.lang.reflect.Method set=compound.getMethod("setString",String.class,String.class);
        NameEncodingCache cache=new NameEncodingCache(256,4096,8192,524288);
        for(String name:new String[]{null,"","§a","\"\\","\u0000\n\t","\ud83d\ude00","\ud800","\udc00","Aa","BB"}) {
            Object first=compound.getConstructor().newInstance(),second=compound.getConstructor().newInstance();
            for(Object target:new Object[]{first,second}){set.invoke(target,"id","uuid");set.invoke(target,"type","minecraft:player");if(name!=null)set.invoke(target,"name",name);}
            NameEncodingCache.Key key=new NameEncodingCache.Key("uuid","minecraft:player",name!=null,name);String original=first.toString(),again=first.toString(),other=second.toString();assertEquals(original,again);assertEquals(original,other);assertNotSame(original,again);assertNotSame(original,other);cache.record(key,original);assertEquals(second.toString(),cache.lookup(key));
            java.lang.reflect.Field field=compound.getField("tagMap");Map<?,?> a=(Map<?,?>)field.get(first),b=(Map<?,?>)field.get(second);assertNotSame(a,b);for(Object k:a.keySet())assertNotSame(a.get(k),b.get(k));
            set.invoke(first,"name","mutated");assertEquals(original,cache.lookup(key));assertNotEquals(first.toString(),cache.lookup(key));
        }
    }
}

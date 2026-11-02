package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class NamePrepRelocationTest {
    static class Relocated extends ClassLoader {
        Relocated() { super(NamePrepRelocationTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String internal = name.replace('.', '/'), prefix = "net/weavemc/loader/impl/shaded/asm/";
            boolean asm = internal.startsWith(prefix);
            if (!asm && !name.equals(NamePrepHook.class.getName()) && !name.equals(TerrainEvidence.class.getName())) return super.loadClass(name, resolve);
            Class<?> found = findLoadedClass(name);
            if (found == null) try (InputStream in = getParent().getResourceAsStream((asm ? "org/objectweb/asm/" + internal.substring(prefix.length()) : internal) + ".class")) {
                ClassWriter w = new ClassWriter(0);
                new ClassReader(in).accept(new ClassRemapper(w, new Remapper() {
                    @Override public String map(String n) { return n.startsWith("org/objectweb/asm/") ? prefix + n.substring("org/objectweb/asm/".length()) : n; }
                }), 0); byte[] b = w.toByteArray(); found = defineClass(name, b, 0, b.length);
            } catch (IOException e) { throw new ClassNotFoundException(name, e); }
            if (resolve) resolveClass(found); return found;
        }
        Object node(ClassNode c) throws Exception {
            Class<?> type = loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"), reader = loadClass("net.weavemc.loader.impl.shaded.asm.ClassReader");
            ClassWriter w = new ClassWriter(0); c.accept(w); Object out = type.getConstructor().newInstance();
            reader.getMethod("accept", loadClass("net.weavemc.loader.impl.shaded.asm.ClassVisitor"), int.class)
                    .invoke(reader.getConstructor(byte[].class).newInstance((Object)w.toByteArray()), out, 0);
            return out;
        }
    }
    @Test void exactGuardAdmitsPinnedOperandsThroughWeavesRelocatedAsmTypesAndRejectsChangedGetter() throws Exception {
        Relocated loader = new Relocated(); Class<?> node = loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode");
        Method matches = loader.loadClass(NamePrepHook.class.getName()).getMethod("matches", node);
        ClassNode c = NamePrepHookTest.mapped(); assertEquals(true, matches.invoke(null, loader.node(c)));
        NamePrepHookTest.method(c, "getColorPrefix").instructions.insert(new InsnNode(Opcodes.NOP));
        assertEquals(false, matches.invoke(null, loader.node(c)));
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the artifact Hook against the pinned loader's actual shaded Hook/ASM ABI. */
final class ShadedHookContractTest {
    static final String HOOK = "com.atw.dispatch.hook.DispatchHook";
    static final String SHADED = "net.weavemc.loader.impl.shaded.asm.";
    static final Path MODULE = DispatchDeliveryTest.REPOSITORY.resolve(
        "weave-mods/atw-event-dispatch/build/libs/ATWDispatchExperiment-0.1.0.jar");

    static final class ContractLoader extends URLClassLoader {
        final boolean transform;
        boolean busDefined;
        boolean framesRequested;
        Object hook;
        final Properties relocation = new Properties();
        ContractLoader(boolean transform) throws Exception {
            // Platform parent prevents accidentally satisfying the ABI with the published API or unshaded ASM.
            super(new URL[]{DispatchDeliveryTest.LOADER.toUri().toURL(), MODULE.toUri().toURL(),
                DispatchDeliveryTest.class.getProtectionDomain().getCodeSource().getLocation()},
                ClassLoader.getPlatformClassLoader());
            this.transform = transform;
            relocation.load(new ByteArrayInputStream(DispatchDeliveryTest.read(
                DispatchDeliveryTest.LOADER, "weave-relocation-data.properties")));
            hook = loadClass(HOOK).getConstructor().newInstance();
            assertFalse(busDefined, "Hook initialization resolved EventBus before registration");
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                if (name.equals(HOOK)) {
                    byte[] original = DispatchDeliveryTest.read(MODULE, name.replace('.', '/') + ".class");
                    ClassWriter writer = new ClassWriter(0);
                    // Apply only the pinned loader's relocation table; no Minecraft name remapping is needed here.
                    String target = relocation.getProperty("target").replace('.', '/');
                    String[] packages = relocation.getProperty("packages").replace('.', '/').split(";");
                    Remapper remapper = new Remapper(Opcodes.ASM9) {
                        @Override public String map(String internal) {
                            for (String prefix : packages) if (internal.startsWith(prefix + "/"))
                                return target + "/" + prefix.substring(prefix.lastIndexOf('/') + 1) + internal.substring(prefix.length());
                            return internal;
                        }
                    };
                    new ClassReader(original).accept(new ClassRemapper(writer, remapper), 0);
                    byte[] relocated = writer.toByteArray();
                    return defineClass(name, relocated, 0, relocated.length);
                }
                if (name.equals(DispatchDeliveryTest.BUS)) {
                    byte[] bytes = DispatchDeliveryTest.read(DispatchDeliveryTest.LOADER, name.replace('.', '/') + ".class");
                    if (transform) bytes = transform(bytes);
                    busDefined = true;
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            } catch (Exception e) { throw new ClassNotFoundException(name, e); }
        }
        byte[] transform(byte[] bytes) throws Exception {
            Class<?> readerType = loadClass(SHADED + "ClassReader"), nodeType = loadClass(SHADED + "tree.ClassNode");
            Class<?> visitorType = loadClass(SHADED + "ClassVisitor");
            Object node = nodeType.getConstructor().newInstance();
            Object reader = readerType.getConstructor(byte[].class).newInstance((Object) bytes);
            readerType.getMethod("accept", visitorType, int.class).invoke(reader, node, 0);
            assertEquals(true, hook.getClass().getMethod("matches", nodeType).invoke(null, node));
            Class<?> configType = loadClass("net.weavemc.api.Hook$AssemblerConfig");
            Object config = java.lang.reflect.Proxy.newProxyInstance(this, new Class<?>[]{configType},
                (proxy, method, args) -> { if (method.getName().equals("computeFrames")) framesRequested = true; return null; });
            hook.getClass().getMethod("transform", nodeType, configType).invoke(hook, node, config);
            Class<?> writerType = loadClass(SHADED + "ClassWriter");
            Object writer = writerType.getConstructor(int.class).newInstance(ClassWriter.COMPUTE_FRAMES);
            nodeType.getMethod("accept", visitorType).invoke(node, writer);
            return (byte[]) writerType.getMethod("toByteArray").invoke(writer);
        }
        @SuppressWarnings("unchecked") List<String> suite() throws Exception {
            return (List<String>) loadClass(DispatchDeliveryTest.HARNESS).getMethod("run").invoke(null);
        }
        String status() throws Exception {
            return (String) loadClass(DispatchDeliveryTest.HELPER).getMethod("status").invoke(null);
        }
    }

    @Test void artifactRelocationAndActualShadedHookFirstDefinitionParity() throws Exception {
        try (ContractLoader baseline = new ContractLoader(false); ContractLoader candidate = new ContractLoader(true)) {
            assertTrue(candidate.loadClass("net.weavemc.api.Hook").isInstance(candidate.hook));
            assertEquals(baseline.suite(), candidate.suite());
            assertTrue(candidate.framesRequested);
            assertTrue(candidate.status().contains("hookTransforms=1"), candidate.status());
            assertTrue(candidate.status().contains("hookTransformed=1"), candidate.status());
            assertTrue(candidate.status().contains("active=true"), candidate.status());
            System.out.println("Actual shaded Hook/ASM ABI: 45 parity cases; " + candidate.status());
        }
    }
}

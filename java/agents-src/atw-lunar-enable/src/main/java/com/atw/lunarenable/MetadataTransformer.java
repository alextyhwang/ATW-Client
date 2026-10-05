package com.atw.lunarenable;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Prevents Lunar's server metadata from applying mod restrictions while
 * preserving IP, brand, client settings, and authentication metadata.
 */
final class MetadataTransformer implements ClassFileTransformer {
    private static final String MOD_SETTINGS_KEY = "modSettings";
    private static final String IGNORED_MOD_SETTINGS_KEY = "atwIgnoredModSettings";

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (!TransformUtils.isLunarClass(className) || classfileBuffer == null) {
            return null;
        }

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            if (!TransformUtils.isLunarClass(reader.getSuperName())) {
                return null;
            }

            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            for (MethodNode method : classNode.methods) {
                if (!"(Lcom/google/gson/JsonElement;)V".equals(method.desc)
                        || !TransformUtils.containsAllStrings(method, "ip", "brand", MOD_SETTINGS_KEY)) {
                    continue;
                }

                boolean changed = false;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof LdcInsnNode
                            && MOD_SETTINGS_KEY.equals(((LdcInsnNode) instruction).cst)) {
                        ((LdcInsnNode) instruction).cst = IGNORED_MOD_SETTINGS_KEY;
                        changed = true;
                    }
                }

                if (changed) {
                    ClassWriter writer = new ClassWriter(reader, 0);
                    classNode.accept(writer);
                    System.out.println("[ATW LunarEnable] Preserved connection metadata and ignored server mod settings.");
                    return writer.toByteArray();
                }
            }
        } catch (Throwable error) {
            System.err.println("[ATW LunarEnable] Metadata transform skipped: " + error);
        }

        return null;
    }
}

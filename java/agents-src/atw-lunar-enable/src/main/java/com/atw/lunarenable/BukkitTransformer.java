package com.atw.lunarenable;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Prevents Bukkit integration packets from changing the local Lunar mod state. */
final class BukkitTransformer implements ClassFileTransformer {
    private static final String CLIENT_HANDLER_INTERFACE =
            "com/lunarclient/bukkitapi/nethandler/client/LCNetHandlerClient";
    private static final String MOD_SETTINGS_PACKET =
            "(Lcom/lunarclient/bukkitapi/nethandler/client/LCPacketModSettings;";

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
            if (!Arrays.asList(reader.getInterfaces()).contains(CLIENT_HANDLER_INTERFACE)) {
                return null;
            }

            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            boolean changed = false;

            for (MethodNode method : classNode.methods) {
                if (!method.desc.startsWith(MOD_SETTINGS_PACKET)
                        || Type.getReturnType(method.desc).getSort() != Type.VOID) {
                    continue;
                }

                method.instructions.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                method.tryCatchBlocks.clear();
                method.exceptions.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.maxStack = 0;
                changed = true;
            }

            if (changed) {
                ClassWriter writer = new ClassWriter(reader, 0);
                classNode.accept(writer);
                System.out.println("[ATW LunarEnable] Ignored Bukkit mod-settings packet handler.");
                return writer.toByteArray();
            }
        } catch (Throwable error) {
            System.err.println("[ATW LunarEnable] Bukkit transform skipped: " + error);
        }

        return null;
    }
}

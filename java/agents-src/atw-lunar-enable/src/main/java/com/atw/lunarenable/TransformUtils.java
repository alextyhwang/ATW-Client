package com.atw.lunarenable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class TransformUtils {
    private static final String LUNAR_CLASS_PREFIX = "com/moonsworth/lunar/";

    private TransformUtils() {
    }

    static boolean isLunarClass(String className) {
        return className != null && className.startsWith(LUNAR_CLASS_PREFIX);
    }

    static boolean containsAllStrings(MethodNode method, String... strings) {
        Set<String> remaining = new HashSet<>(Arrays.asList(strings));
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode) {
                Object value = ((LdcInsnNode) instruction).cst;
                if (value instanceof String) {
                    remaining.remove(value);
                    if (remaining.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

package io.github.nilsen84.nohitdelay;

import net.weavemc.api.Hook;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinecraftHookTest {
    @Test void replacesOnlyTenTickConstantsInsideClickMouse() {
        ClassNode node = new ClassNode();
        node.name = "net/minecraft/client/Minecraft";
        MethodNode click = new MethodNode(Opcodes.ACC_PUBLIC, "clickMouse", "()V", null, null);
        IntInsnNode delay = new IntInsnNode(Opcodes.BIPUSH, 10);
        IntInsnNode unrelatedConstant = new IntInsnNode(Opcodes.BIPUSH, 6);
        click.instructions.add(delay); click.instructions.add(unrelatedConstant);
        MethodNode other = new MethodNode(Opcodes.ACC_PUBLIC, "other", "()V", null, null);
        IntInsnNode otherDelay = new IntInsnNode(Opcodes.BIPUSH, 10);
        other.instructions.add(otherDelay);
        node.methods.add(click); node.methods.add(other);
        MinecraftHook hook = new MinecraftHook();
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        assertEquals(0, delay.operand);
        assertEquals(6, unrelatedConstant.operand);
        assertEquals(10, otherDelay.operand);
        hook.transform(node, mock(Hook.AssemblerConfig.class));
        assertEquals(0, delay.operand);
    }
}

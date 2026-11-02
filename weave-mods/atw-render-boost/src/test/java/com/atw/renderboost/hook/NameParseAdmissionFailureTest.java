package com.atw.renderboost.hook;

import java.util.ArrayList;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class NameParseAdmissionFailureTest {
    private static ClassNode failingComparison(final Error error) {
        ClassNode node = new ClassNode();
        node.name = "net/minecraft/nbt/NBTTagCompound";
        node.methods = new ArrayList<MethodNode>() {
            @Override public Iterator<MethodNode> iterator() { throw error; }
        };
        return node;
    }

    @Test void nestedComparisonPropagatesTheOriginalFatalError() {
        for (Error error : new Error[]{new ThreadDeath(), new StackOverflowError(),
                new InternalError(), new UnknownError()}) {
            assertSame(error, assertThrows(error.getClass(),
                () -> NameParseHook.matches(failingComparison(error))));
        }
    }

    @Test void optionalComparisonFailuresStillRejectAdmission() {
        assertFalse(NameParseHook.matches(failingComparison(new OutOfMemoryError())));
        assertFalse(NameParseHook.matches(failingComparison(new AssertionError())));
    }
}

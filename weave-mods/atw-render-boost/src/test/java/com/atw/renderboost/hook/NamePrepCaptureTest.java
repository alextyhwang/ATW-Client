package com.atw.renderboost.hook;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** Existing private disk captures read in place. No live admission or startup-composition claim. */
@Tag("private-name-capture")
class NamePrepCaptureTest {
    @Test void twoRetainedActualLunarBodiesMatchMappedMotifAndPreserveOriginalNodes() throws Exception {
        Path root = Paths.get(System.getProperty("atwboost.nameCaptureRoot"));
        Path restart = Paths.get(new String(Files.readAllBytes(root.getParent().resolve("restart-capture-path.txt")), java.nio.charset.StandardCharsets.UTF_8).trim());
        assertTrue(restart.toAbsolutePath().normalize().startsWith(root.getParent().toAbsolutePath().normalize()));
        for (Path directory : Arrays.asList(root, restart)) {
            Path file;
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                file = files.filter(p -> p.getFileName().toString().startsWith("net_minecraft_scoreboard_ScorePlayerTeam.") && p.toString().endsWith(".live.class")).findFirst().get();
            }
            ClassNode c = new ClassNode(); new ClassReader(Files.readAllBytes(file)).accept(c, 0); assertTrue(NamePrepHook.matches(c));
            MethodNode method = NamePrepHookTest.method(c, "formatString"); List<AbstractInsnNode> original = Arrays.asList(method.instructions.toArray());
            new NamePrepHook().transform(c, () -> {}); int last = -1;
            List<AbstractInsnNode> after = Arrays.asList(method.instructions.toArray());
            for (AbstractInsnNode n : original) { assertTrue(after.indexOf(n) > last); last = after.indexOf(n); }
            new Analyzer<>(new BasicVerifier()).analyze(c.name, method);
        }
    }
}

package com.atw.renderboost.hook;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainHookEvidenceOutputTest implements Opcodes {
    @TempDir Path temp;

    static ClassNode input(String name) {
        ClassNode c = new ClassNode(); c.version = V1_8; c.access = ACC_PUBLIC;
        c.name = name; c.superName = "java/lang/Object";
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "unrecognized", "()V", null, null);
        method.instructions.add(new InsnNode(RETURN)); c.methods.add(method);
        return c;
    }

    @Test void disabledPropertyDoesNotSerializeOrReadFilesystemOrChangeNode() {
        String previous = System.getProperty(TerrainHookEvidenceOutput.PROPERTY);
        try {
            System.clearProperty(TerrainHookEvidenceOutput.PROPERTY);
            ClassNode c = input(TerrainHook.LIST);
            c.methods.get(0).instructions.add(new LdcInsnNode(new Object())); // cannot serialize
            AbstractInsnNode[] original = c.methods.get(0).instructions.toArray();
            assertDoesNotThrow(() -> TerrainHookEvidenceOutput.record(c));
            assertArrayEquals(original, c.methods.get(0).instructions.toArray());
            assertEquals(0, countFiles(temp));
        } finally { restoreProperty(previous); }
    }

    @Test void captureSerializesOnlyInputAndReportsMissingShapeAndMethodsWithoutMutation() throws Exception {
        ClassNode c = input(TerrainHook.LIST);
        String shape = TerrainEvidence.classShape(c), fingerprint = TerrainEvidence.fingerprint(c.methods.get(0));
        AbstractInsnNode[] original = c.methods.get(0).instructions.toArray();
        Properties report = TerrainHookEvidenceOutput.capture(c, temp, temp.toString());
        assertEquals("false", report.getProperty("shapeMatch"));
        assertTrue(Integer.parseInt(report.getProperty("missingMethods")) > 0);
        assertEquals(shape, report.getProperty("actualShape"));
        byte[] bytes = Files.readAllBytes(temp.resolve("VboRenderList.hook-input.class"));
        assertEquals(TerrainHookEvidenceOutput.sha256(bytes), report.getProperty("serializedInputSha256"));
        ClassNode saved = new ClassNode(); new ClassReader(bytes).accept(saved, 0);
        assertEquals(shape, TerrainEvidence.classShape(saved));
        assertEquals(fingerprint, TerrainEvidence.fingerprint(saved.methods.get(0)));
        assertEquals(shape, TerrainEvidence.classShape(c));
        assertArrayEquals(original, c.methods.get(0).instructions.toArray());
        assertEquals(fingerprint, TerrainEvidence.fingerprint(c.methods.get(0)));
        assertFalse(TerrainEvidence.capturedClassMatches(c));
        assertEquals(2, countFiles(temp));
    }

    @Test void rejectsMissingRelativeOutsideAndSymlinkDirectoriesWithoutWrites() throws Exception {
        ClassNode c = input(TerrainHook.LIST);
        Path missing = temp.resolve("missing"), outside = Files.createDirectory(temp.resolve("outside"));
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(c, missing, missing.toString()));
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(c, temp, "relative"));
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(c, temp, outside.toString()));
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
            assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(c, link, link.toString()));
        } catch (java.io.IOException | UnsupportedOperationException unavailable) {
            // Windows developer-mode/admin symlink support is optional; the other guards always run.
        }
        assertFalse(Files.exists(missing)); assertEquals(0, countFiles(outside));
    }

    @Test void allowlistIsExactlyFifteenUniqueTargetsAndRejectsUnrelatedClasses() throws Exception {
        assertEquals(15, TerrainHook.TARGETS.length);
        assertEquals(15, new HashSet<>(Arrays.asList(TerrainHook.TARGETS)).size());
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(input("unrelated/Game"), temp, temp.toString()));
        assertEquals(0, countFiles(temp));
    }

    @Test void existingEvidenceCannotBeOverwritten() throws Exception {
        ClassNode c = input(TerrainHook.LIST);
        TerrainHookEvidenceOutput.capture(c, temp, temp.toString());
        byte[] original = Files.readAllBytes(temp.resolve("VboRenderList.hook-input.class"));
        c.methods.get(0).instructions.insert(new InsnNode(NOP));
        assertThrows(FileAlreadyExistsException.class, () -> TerrainHookEvidenceOutput.capture(c, temp, temp.toString()));
        assertArrayEquals(original, Files.readAllBytes(temp.resolve("VboRenderList.hook-input.class")));
        assertEquals(2, countFiles(temp));
    }

    @Test void diagnosticFailureDoesNotBypassRejectionOrMutateUnknownNode() {
        String previous = System.getProperty(TerrainHookEvidenceOutput.PROPERTY);
        try {
            System.setProperty(TerrainHookEvidenceOutput.PROPERTY, temp.resolve("absent").toString());
            ClassNode c = input(TerrainHook.BUFFER);
            String shape = TerrainEvidence.classShape(c);
            AbstractInsnNode[] original = c.methods.get(0).instructions.toArray();
            new TerrainHook().transform(c, () -> fail("Unknown input must not compute frames"));
            assertEquals(shape, TerrainEvidence.classShape(c));
            assertArrayEquals(original, c.methods.get(0).instructions.toArray());
            assertFalse(TerrainEvidence.capturedClassMatches(c));
            assertEquals(0, countFiles(temp));
        } finally { restoreProperty(previous); }
    }

    @Test void reportsTemporaryConflictNames() {
        ClassNode c = input(TerrainHook.LIST);
        c.methods.get(0).name = "$weave_potentialConflict$unrecognized";
        assertEquals("1", TerrainEvidence.mismatchReport(c).getProperty("weaveConflictNames"));
    }

    @Test void nullCodeSourceUsesLauncherPrivateHomeAndCapturesAtExactExistingDirectory() throws Exception {
        Path checkout = temp.resolve("ATW-Client-Performance");
        Path home = checkout.resolve("build/data/home");
        Path permitted = Files.createDirectories(checkout.resolve("upgrade-work/environment-20261005/terrain-hookstage"));
        ProtectionDomain weaveDomain = new ProtectionDomain(null, null);
        assertNull(weaveDomain.getCodeSource());
        Path discovered = TerrainHookEvidenceOutput.privateDirectory(weaveDomain, home.toString());
        assertEquals(permitted, discovered);
        Properties report = TerrainHookEvidenceOutput.capture(input(TerrainHook.LIST), discovered, permitted.toString());
        assertEquals("false", report.getProperty("shapeMatch"));
        assertTrue(Files.isRegularFile(permitted.resolve("VboRenderList.hook-input.class")));
        assertTrue(Files.isRegularFile(permitted.resolve("VboRenderList.mismatch.properties")));
        assertEquals(2, countFiles(permitted));
        assertFalse(Files.exists(home)); // discovery never creates the launcher home
    }

    @Test void launcherHomeTakesPrecedenceOverUnrelatedCodeSource() throws Exception {
        Path checkout = temp.resolve("ATW-Client-Performance");
        ProtectionDomain domain = new ProtectionDomain(new CodeSource(temp.resolve("external/mod.jar").toUri().toURL(),
                (java.security.cert.Certificate[]) null), null);
        assertEquals(checkout.resolve("upgrade-work/environment-20261005/terrain-hookstage"),
                TerrainHookEvidenceOutput.privateDirectory(domain, checkout.resolve("build/data/home").toString()));
        assertEquals(0, countFiles(temp));
    }

    @Test void codeSourceRemainsFallbackWhenHomeIsMissingOrOutsideCheckout() throws Exception {
        Path checkout = temp.resolve("ATW-Client-Performance");
        ProtectionDomain domain = new ProtectionDomain(new CodeSource(checkout.resolve("weave-mods/runtime/mod.jar").toUri().toURL(),
                (java.security.cert.Certificate[]) null), null);
        Path permitted = checkout.resolve("upgrade-work/environment-20261005/terrain-hookstage");
        assertEquals(permitted, TerrainHookEvidenceOutput.privateDirectory(domain, null));
        assertEquals(permitted, TerrainHookEvidenceOutput.privateDirectory(domain, temp.getRoot().resolve("unrelated-home").toString()));
        assertEquals(0, countFiles(temp));
    }

    @Test void nullCodeSourceAndUnrelatedOrRelativeHomeFailClosedWithoutWrites() {
        ProtectionDomain weaveDomain = new ProtectionDomain(null, null);
        // Gradle's TempDir itself lives beneath the real Performance checkout.
        // Use root-relative lexical paths so these homes have no matching ancestor.
        for (String home : Arrays.asList(null, "", temp.getRoot().resolve("unrelated-home").toString(), "ATW-Client-Performance/build/data/home",
                temp.getRoot().resolve("ATW-Client-Performance-other/build/data/home").toString())) {
            assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.privateDirectory(weaveDomain, home));
        }
        assertEquals(0, countFiles(temp));
    }

    @Test void discoveredDirectoryStillRejectsMissingAndOtherOutputLocationsWithoutWrites() throws Exception {
        Path checkout = temp.resolve("ATW-Client-Performance");
        Path permitted = TerrainHookEvidenceOutput.privateDirectory(new ProtectionDomain(null, null),
                checkout.resolve("build/data/home").toString());
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(input(TerrainHook.LIST), permitted, permitted.toString()));
        Files.createDirectories(permitted);
        assertThrows(IllegalArgumentException.class, () -> TerrainHookEvidenceOutput.capture(input(TerrainHook.LIST), permitted, temp.toString()));
        assertEquals(0, countFiles(permitted));
    }

    @Test @Tag("private-terrain-capture") void reproduceLoaderConflictRenamingAgainstAllFifteenOriginalCaptures() throws Exception {
        Path captures = Paths.get(getClass().getResource("/terrain/").toURI());
        int total = 0, renamedClasses = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(captures, "*.live.class.dat")) {
            for (Path file : files) {
                ClassNode original = new ClassNode(); new ClassReader(Files.readAllBytes(file)).accept(original, 0);
                assertTrue(TerrainEvidence.capturedClassMatches(original));
                MethodNode checked = original.methods.stream().filter(m -> TerrainEvidence.capturedMethodMatches(original.name, m)).findFirst().get();
                int access = checked.access;
                checked.access ^= ACC_FINAL;
                assertTrue(Integer.parseInt(TerrainEvidence.mismatchReport(original).getProperty("changedFingerprints")) > 0);
                checked.access = access;
                original.methods.add(checked);
                assertTrue(Integer.parseInt(TerrainEvidence.mismatchReport(original).getProperty("duplicateMethods")) > 0);
                original.methods.remove(original.methods.size() - 1);
                assertTrue(TerrainEvidence.capturedClassMatches(original));
                Map<String, String> renames = new HashMap<>();
                for (MethodNode m : original.methods) {
                    List<AnnotationNode> annotations = new ArrayList<>();
                    if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
                    if (m.invisibleAnnotations != null) annotations.addAll(m.invisibleAnnotations);
                    if (annotations.stream().anyMatch(a -> a.desc.endsWith("/MixinMerged;")))
                        renames.put(original.name + "." + m.name + m.desc, "$weave_potentialConflict$" + m.name);
                }
                ClassNode stage = new ClassNode(); original.accept(new ClassRemapper(stage, new SimpleRemapper(renames)));
                Properties report = TerrainEvidence.mismatchReport(stage);
                System.out.println("PRIVATE offline loader conflict simulation: " + original.name + " renamed=" + renames.size()
                        + " shapeMatch=" + report.getProperty("shapeMatch") + " missing=" + report.getProperty("missingMethods")
                        + " changed=" + report.getProperty("changedFingerprints"));
                if (!renames.isEmpty()) { renamedClasses++; assertFalse(TerrainEvidence.capturedClassMatches(stage)); }
                else assertTrue(TerrainEvidence.capturedClassMatches(stage));
                total++;
            }
        }
        assertEquals(15, total);
        System.out.println("PRIVATE offline conflict simulation classes=" + renamedClasses + "/15; not live-stage acceptance");
    }

    private static long countFiles(Path dir) {
        try (java.util.stream.Stream<Path> files = Files.list(dir)) { return files.count(); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static void restoreProperty(String previous) {
        if (previous == null) System.clearProperty(TerrainHookEvidenceOutput.PROPERTY);
        else System.setProperty(TerrainHookEvidenceOutput.PROPERTY, previous);
    }
}

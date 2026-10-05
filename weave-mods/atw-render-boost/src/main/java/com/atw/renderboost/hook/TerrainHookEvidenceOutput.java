package com.atw.renderboost.hook;

import java.io.OutputStream;
import java.nio.file.*;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Temporary, opt-in evidence at the hook boundary; never broad game capture. */
final class TerrainHookEvidenceOutput {
    static final String PROPERTY = "atwboost.terrainEvidenceOutput";
    private static final Set<String> ATTEMPTED = new HashSet<>();
    private TerrainHookEvidenceOutput() {}

    static void record(ClassNode node) {
        String output = System.getProperty(PROPERTY);
        // Default path performs no filesystem work, serialization, hashing, or reporting.
        if (output == null || output.isEmpty() || !Arrays.asList(TerrainHook.TARGETS).contains(node.name)) return;
        synchronized (ATTEMPTED) { if (!ATTEMPTED.add(node.name)) return; }
        try {
            Path permitted = privateDirectory(TerrainHookEvidenceOutput.class.getProtectionDomain(),
                    System.getProperty("user.home"));
            Properties report = capture(node, permitted, output);
            System.out.println("[ATW Render Boost] Terrain hook evidence: " + node.name
                    + " sha256=" + report.getProperty("serializedInputSha256")
                    + " shapeMatch=" + report.getProperty("shapeMatch")
                    + " missingMethods=" + report.getProperty("missingMethods")
                    + " changedFingerprints=" + report.getProperty("changedFingerprints")
                    + " weaveConflictNames=" + report.getProperty("weaveConflictNames"));
        } catch (Exception failure) {
            // No exception message/stack/path: diagnostics cannot expose arbitrary input or break loading.
            System.out.println("[ATW Render Boost] Terrain hook evidence skipped: " + node.name
                    + " reason=" + failure.getClass().getSimpleName());
        }
    }

    static Path privateDirectory(ProtectionDomain domain, String userHome) throws Exception {
        // Weave-defined mod classes may have no CodeSource. The launcher already
        // supplies its private data/home as user.home; never treat the output
        // property itself as a discovery root or create a directory here.
        Path checkout = null;
        if (userHome != null && !userHome.isEmpty()) {
            try { checkout = checkoutAncestor(Paths.get(userHome)); }
            catch (InvalidPathException invalidHome) { /* Try the code source below. */ }
        }
        CodeSource source = domain == null ? null : domain.getCodeSource();
        if (checkout == null && source != null && source.getLocation() != null)
            checkout = checkoutAncestor(Paths.get(source.getLocation().toURI()));
        if (checkout == null) throw new IllegalArgumentException("Private checkout unavailable");
        return checkout.resolve("upgrade-work/environment-20261005/terrain-hookstage");
    }

    private static Path checkoutAncestor(Path location) {
        if (!location.isAbsolute()) return null;
        Path checkout = location.normalize();
        while (checkout != null && !"ATW-Client-Performance".equals(String.valueOf(checkout.getFileName())))
            checkout = checkout.getParent();
        return checkout;
    }

    static Properties capture(ClassNode node, Path permitted, String output) throws Exception {
        if (!Arrays.asList(TerrainHook.TARGETS).contains(node.name))
            throw new IllegalArgumentException("Not a terrain hook target");
        Path requested = Paths.get(output);
        Path root = permitted.toAbsolutePath().normalize();
        if (!requested.isAbsolute() || !requested.normalize().equals(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !root.toRealPath().equals(root))
            throw new IllegalArgumentException("Existing private directory required");
        // No frame/max computation and no class loading. These are serialized ClassNode
        // input bytes in the hook namespace, not the raw original JVM classfile bytes.
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] bytes = writer.toByteArray();
        Properties report = TerrainEvidence.mismatchReport(node);
        report.setProperty("stage", "TerrainHook.transform input before any ATW terrain mutation");
        report.setProperty("serializedInputSha256", sha256(bytes));
        String stem = node.name.substring(node.name.lastIndexOf('/') + 1);
        // Create only these two flat allowlisted files; never create directories or overwrite evidence.
        Files.write(root.resolve(stem + ".hook-input.class"), bytes, StandardOpenOption.CREATE_NEW);
        try (OutputStream out = Files.newOutputStream(root.resolve(stem + ".mismatch.properties"), StandardOpenOption.CREATE_NEW)) {
            report.store(out, "PRIVATE hook-stage terrain evidence; never publish or bundle");
        }
        return report;
    }

    static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }
}

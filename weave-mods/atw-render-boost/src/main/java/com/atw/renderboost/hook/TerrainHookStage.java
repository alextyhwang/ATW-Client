package com.atw.renderboost.hook;

import java.io.InputStream;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/**
 * Two exact observed layouts, with all 809 original executable bodies proved offline.
 * This is admission evidence, not live visual/performance validation. Only a detached
 * comparison tree is remapped; Weave owns renaming/serialization of the actual tree.
 */
final class TerrainHookStage {
    static final String PREFIX = "$weave_potentialConflict$";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String ORIGINAL_SESSION_SUFFIX = "d6e203";
    private static final String[] UNIQUE_METHOD_TAILS = {"lambda$bridge$reloadChunks$0$0", "lambda$lunar$renderBeam$2$1",
            "lambda$lunar$renderBeam$1$2", "lambda$lunar$drawSelectionBoundingBox$0$3"};
    private static final Properties PROVEN = new Properties();
    static {
        try (InputStream in = TerrainHookStage.class.getResourceAsStream("/terrain-hookstage-evidence.properties")) {
            if (in == null) throw new IllegalStateException("Missing hook-stage proof");
            PROVEN.load(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private TerrainHookStage() {}

    static boolean matches(ClassNode actual) {
        if (!PROVEN.containsKey(actual.name + ".@hookShape")) return false;
        try {
            ClassNode copy = comparisonCopy(actual);
            String shape = TerrainEvidence.classShape(copy);
            boolean post = shape.equals(PROVEN.getProperty(actual.name + ".@postShape"));
            boolean hook = shape.equals(PROVEN.getProperty(actual.name + ".@hookShape"));
            if (!post && !hook) return false;
            // Some non-Mixin classes have identical layouts in both stages. For Mixin
            // classes the observed stage must have ALL, rather than some, merged names prefixed.
            for (MethodNode m : actual.methods) if (merged(m) != null) {
                boolean conflict = m.name.startsWith(PREFIX);
                post &= !conflict;
                hook &= conflict;
            }
            if (!post && !hook) return false;
            for (FieldNode f : copy.fields) if (!fieldFingerprint(f).equals(
                    PROVEN.getProperty(copy.name + ".@field." + f.name + f.desc))) return false;
            for (MethodNode m : copy.methods) {
                // ONLY the exact observed hook layout permits PUBLIC|STATIC <clinit>.
                // JVMS 4.6 ignores PUBLIC here. No other access bits are normalized.
                if (hook && m.name.equals("<clinit>") && m.desc.equals("()V") && m.access == 9) m.access = 8;
                if (!TerrainEvidence.fingerprint(m).equals(PROVEN.getProperty(copy.name + "." + m.name + m.desc))) return false;
            }
            return !copy.methods.isEmpty();
        } catch (IllegalArgumentException e) { return false; }
    }

    static ClassNode comparisonCopy(ClassNode actual) {
        String session = null;
        Set<String> declarations = new HashSet<>();
        for (MethodNode m : actual.methods) {
            if (!declarations.add(m.name + m.desc)) throw new IllegalArgumentException("Duplicate method");
            AnnotationNode a = merged(m);
            if (a != null) {
                String id = session(a);
                if (session != null && !session.equals(id)) throw new IllegalArgumentException("Mixed Mixin sessions");
                session = id;
            }
        }
        Map<String, String> methods = new HashMap<>(), fields = new HashMap<>();
        Set<String> logical = new HashSet<>();
        for (MethodNode m : actual.methods) {
            String name = m.name.startsWith(PREFIX) ? m.name.substring(PREFIX.length()) : m.name;
            AnnotationNode a = merged(m);
            if (!name.equals(m.name) && a == null) throw new IllegalArgumentException("Unmarked conflict name");
            name = sessionMethod(actual.name, name, session);
            if (!logical.add(name + m.desc)) throw new IllegalArgumentException("Logical duplicate method");
            String expected = PROVEN.getProperty(actual.name + ".@merged." + name + m.desc);
            if (!Objects.equals(expected, a == null ? null : metadata(m))) throw new IllegalArgumentException("Unknown merged metadata");
            if (!name.equals(m.name)) methods.put(m.name + m.desc, name);
        }
        Set<String> fieldNames = new HashSet<>(), actualFields = new HashSet<>();
        for (FieldNode f : actual.fields) {
            if (!actualFields.add(f.name + f.desc)) throw new IllegalArgumentException("Duplicate field");
            String name = f.name;
            if (TerrainHook.GLOBAL.equals(actual.name) && session != null
                    && name.equals("fd" + ORIGINAL_SESSION_SUFFIX + "$lunar$chunksToWait$0")
                    && !name.equals("fd" + session.substring(30) + "$lunar$chunksToWait$0"))
                throw new IllegalArgumentException("Mismatched unique field session");
            if (TerrainHook.GLOBAL.equals(actual.name) && session != null
                    && name.equals("fd" + session.substring(30) + "$lunar$chunksToWait$0"))
                name = "fd" + ORIGINAL_SESSION_SUFFIX + "$lunar$chunksToWait$0";
            if (!fieldNames.add(name + f.desc)) throw new IllegalArgumentException("Duplicate field");
            if (!name.equals(f.name)) fields.put(f.name + f.desc, name);
        }
        // Do not turn a dangling old/unprefixed call into a valid one merely because
        // it hashes like the post-load body. Real references must resolve to the real
        // declarations BEFORE the detached comparison tree is normalized.
        for (MethodNode m : actual.methods) for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)n;
                checkReference(actual.name, call.owner, call.name, call.desc, declarations, logical, true, session);
            } else if (n instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode)n;
                checkReference(actual.name, field.owner, field.name, field.desc, actualFields, fieldNames, false, session);
            } else if (n instanceof LdcInsnNode) {
                checkConstant(((LdcInsnNode)n).cst, actual.name, declarations, logical, actualFields, fieldNames, session);
            } else if (n instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode)n;
                checkConstant(dynamic.bsm, actual.name, declarations, logical, actualFields, fieldNames, session);
                for (Object arg : dynamic.bsmArgs) checkConstant(arg, actual.name, declarations, logical, actualFields, fieldNames, session);
            }
        }
        ClassNode copy = new ClassNode();
        actual.accept(new ClassRemapper(copy, new SimpleRemapper(Collections.<String, String>emptyMap()) {
            @Override public String mapMethodName(String owner, String name, String desc) {
                return actual.name.equals(owner) ? methods.getOrDefault(name + desc, name) : name;
            }
            @Override public String mapFieldName(String owner, String name, String desc) {
                return actual.name.equals(owner) ? fields.getOrDefault(name + desc, name) : name;
            }
        }));
        return copy;
    }

    private static void checkReference(String owner, String targetOwner, String name, String desc,
            Set<String> actual, Set<String> logical, boolean method, String session) {
        if (!owner.equals(targetOwner)) return;
        String normal = name;
        if (method) {
            if (normal.startsWith(PREFIX)) normal = normal.substring(PREFIX.length());
            normal = sessionMethod(owner, normal, session);
        } else if (TerrainHook.GLOBAL.equals(owner) && session != null
                && normal.equals("fd" + session.substring(30) + "$lunar$chunksToWait$0"))
            normal = "fd" + ORIGINAL_SESSION_SUFFIX + "$lunar$chunksToWait$0";
        if (logical.contains(normal + desc) && !actual.contains(name + desc))
            throw new IllegalArgumentException("Unresolved lifecycle reference");
    }
    private static void checkConstant(Object value, String owner, Set<String> actualMethods, Set<String> logicalMethods,
            Set<String> actualFields, Set<String> logicalFields, String session) {
        if (value instanceof Handle) {
            Handle h = (Handle)value;
            boolean method = h.getTag() > 4;
            checkReference(owner, h.getOwner(), h.getName(), h.getDesc(), method ? actualMethods : actualFields,
                    method ? logicalMethods : logicalFields, method, session);
        } else if (value instanceof ConstantDynamic) {
            ConstantDynamic c = (ConstantDynamic)value;
            checkConstant(c.getBootstrapMethod(), owner, actualMethods, logicalMethods, actualFields, logicalFields, session);
            for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++)
                checkConstant(c.getBootstrapMethodArgument(i), owner, actualMethods, logicalMethods, actualFields, logicalFields, session);
        }
    }

    private static String sessionMethod(String owner, String name, String session) {
        if (!TerrainHook.GLOBAL.equals(owner) || session == null) return name;
        String prefix = "md" + session.substring(30) + "$";
        for (String tail : UNIQUE_METHOD_TAILS)
            if (name.equals("md" + ORIGINAL_SESSION_SUFFIX + "$" + tail) && !name.equals(prefix + tail))
                throw new IllegalArgumentException("Mismatched unique method session");
        if (!name.startsWith(prefix)) return name;
        String tail = name.substring(prefix.length());
        // Exact four observed @Unique lambdas; never a regex for arbitrary added members.
        for (String supported : UNIQUE_METHOD_TAILS) if (tail.equals(supported))
            return "md" + ORIGINAL_SESSION_SUFFIX + "$" + supported;
        return name;
    }

    /** Resolve one proved merged declaration without changing its real temporary name. */
    static MethodNode mergedMethod(ClassNode actual, String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : actual.methods) if (m.desc.equals(desc)
                && (m.name.equals(name) || m.name.equals(PREFIX + name))) {
            String expected = PROVEN.getProperty(actual.name + ".@merged." + name + desc);
            if (expected == null || !expected.equals(metadata(m))) throw new IllegalArgumentException("Unproved merged declaration");
            session(merged(m));
            if (found != null) throw new IllegalArgumentException("Ambiguous merged declaration");
            found = m;
        }
        if (found == null) throw new IllegalArgumentException("Missing merged declaration");
        return found;
    }

    static AnnotationNode merged(MethodNode m) {
        AnnotationNode found = null;
        List<AnnotationNode> annotations = new ArrayList<>();
        if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
        // InjectionHandler's hasMixinAnnotation examines visible annotations only.
        // An invisible lookalike is not proof that its conflict name is loader-owned.
        if (m.invisibleAnnotations != null) for (AnnotationNode a : m.invisibleAnnotations)
            if (MERGED.equals(a.desc)) throw new IllegalArgumentException("Invisible merged metadata");
        for (AnnotationNode a : annotations) if (MERGED.equals(a.desc)) {
            if (found != null) throw new IllegalArgumentException("Duplicate merged metadata");
            found = a;
        }
        return found;
    }
    private static Map<String, Object> values(AnnotationNode a) {
        if (a == null || a.values == null || a.values.size() != 6) throw new IllegalArgumentException("Invalid merged metadata");
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < a.values.size(); i += 2) {
            if (!(a.values.get(i) instanceof String)) throw new IllegalArgumentException("Invalid merged key");
            if (values.put((String)a.values.get(i), a.values.get(i + 1)) != null) throw new IllegalArgumentException("Duplicate merged value");
        }
        if (!(values.get("mixin") instanceof String) || !(values.get("priority") instanceof Integer)
                || !(values.get("sessionId") instanceof String)) throw new IllegalArgumentException("Invalid merged values");
        return values;
    }
    private static String session(AnnotationNode a) {
        String id = (String)values(a).get("sessionId");
        if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid Mixin session");
        return id;
    }
    static String metadata(MethodNode m) {
        Map<String, Object> values = values(merged(m));
        return values.get("mixin") + ":" + values.get("priority");
    }
    static String fieldFingerprint(FieldNode f) {
        // Reuse operand hashing with a detached pseudo-method to include typed ConstantValue.
        MethodNode proof = new MethodNode(f.access, f.name, f.desc, f.signature, null);
        if (f.value != null) proof.instructions.add(new LdcInsnNode(f.value));
        return TerrainEvidence.fingerprint(proof);
    }
}

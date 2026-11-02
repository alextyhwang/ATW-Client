package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NamePrepRuntime;
import java.util.*;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.Handle;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.tree.*;

/** Version 1: only the exact pinned final StringBuilder.toString seam, with original nodes retained. */
public final class NamePrepHook extends Hook implements Opcodes {
    public static final String OWNER = NamePrepRuntime.OWNER;
    private static final String RUNTIME = "com/atw/renderboost/cache/NamePrepRuntime";
    private static final String STR = "Ljava/lang/String;";
    private static final String ARGS = "Ljava/lang/Class;Ljava/lang/Object;" + STR + STR + STR + "J";
    private static final Map<String, String> EXPECTED;
    // Incoming profile 1: actual Weave 1.4.1 Hook-entry capture, corroborated by
    // both prior Lunar captures through the shipped conflict stage. Never remap here.
    public static final int INCOMING_PROFILE_VERSION = 1;
    private static final String CONFLICT = "weave_potentialConflict";
    private static final String PREFIX = "$" + CONFLICT + "$";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String MIXIN = "com.moonsworth.lunar.OCCOIRIHCIHOCIOCICROCRHRHRIHCO.CHIRCOROIOHCCIIRRCCHOIHHHIOIOH.RRCOIIORCHOIRRORHROICHCROIHHRR.CIRIIHOCHOHHOCRRRHORICIIOHOIHC";
    private static final Map<String, String> BRIDGES;
    static {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("formatString(" + STR + ")" + STR, "b79adfac80aaa82cc21411a8f2c07c22e78696de5fbcb2bf14764c9e206fd543");
        hashes.put("formatPlayerName(Lnet/minecraft/scoreboard/Team;" + STR + ")" + STR, "0e1dc57d49b496324d388c25aa9684a32959f90496ae620acef4542426a55747");
        hashes.put("getColorPrefix()" + STR, "6a0c6072f4a82c0cdc8f5542f285aad1559d48418df98cec5a1505133ebdd1d8");
        hashes.put("getColorSuffix()" + STR, "c375c131e88f6fb38f4efd5f8434fce487ff20023d52ba27dcf5cae697479462");
        EXPECTED = Collections.unmodifiableMap(hashes);
        Map<String, String> bridges = new LinkedHashMap<>();
        bridges.put(PREFIX + "bridge$getChatFormat()Lcom/moonsworth/lunar/CIHOOIHOIROIIRCORCCHIOIIOCICCH/RCRCIOCOHIICRIHHICCCRIOOHCRCRO/OHHCORHIHICROOCCROICROCHORRIHO;", "32a2f558f5db35fde8d975c88031c994e32175c1627b3d67b27301d4f135e880");
        bridges.put(PREFIX + "bridge$formatString(Lnet/kyori/adventure/text/Component;)Lnet/kyori/adventure/text/Component;", "32bc4e6e167f6bae5db7629857c51fa76c9c10cb3bc60d80ec5036dd9c0f35e0");
        bridges.put(PREFIX + "bridge$getPrefixAndSuffix()Lnet/kyori/adventure/text/TextComponent;", "d96ab816f3fc0d57569caa0624e09df6b626f31ddb513f83c97fe4a130d2cc3f");
        BRIDGES = Collections.unmodifiableMap(bridges);
    }
    public NamePrepHook() { super(OWNER); }
    public static boolean matches(ClassNode node) {
        if (!OWNER.equals(node.name) || !"net/minecraft/scoreboard/Team".equals(node.superName)
                || node.access != (ACC_PUBLIC | ACC_SUPER) || (node.version != V1_6 && node.version != V17)) return false;
        for (FieldNode f : node.fields) if (f.name.startsWith(NamePrepRuntime.MARKER)) return false;
        if (!incomingProfile(node)) return false;
        for (Map.Entry<String, String> entry : EXPECTED.entrySet()) {
            MethodNode found = null;
            for (MethodNode m : node.methods) if ((m.name + m.desc).equals(entry.getKey())) {
                if (found != null) return false; found = m;
            }
            if (found == null || !entry.getValue().equals(TerrainEvidence.fingerprint(found))
                    || found.signature != null || !found.exceptions.isEmpty()
                    || (found.visibleAnnotations != null && !found.visibleAnnotations.isEmpty())
                    || (found.invisibleAnnotations != null && !found.invisibleAnnotations.isEmpty())) return false;
        }
        for (String name : Arrays.asList("namePrefixSPT", "colorSuffix")) {
            FieldNode found = null;
            for (FieldNode f : node.fields) if (f.name.equals(name)) { if (found != null) return false; found = f; }
            // Vanilla mapped/private and corroborated Lunar/public declarations only.
            if (found == null || !STR.equals(found.desc) || (found.access != ACC_PUBLIC && found.access != ACC_PRIVATE)
                    || found.signature != null || found.value != null) return false;
        }
        return true;
    }
    private static boolean incomingProfile(ClassNode node) {
        Set<String> declarations = new HashSet<>(), aliases = new HashSet<>();
        String session = null;
        for (FieldNode f : node.fields) if (f.name.contains(CONFLICT)) return false;
        for (MethodNode m : node.methods) {
            String key = m.name + m.desc;
            if (!declarations.add(key)) return false;
            if (!m.name.contains(CONFLICT)) continue;
            if (!BRIDGES.containsKey(key) || !aliases.add(key)
                    || !BRIDGES.get(key).equals(TerrainEvidence.fingerprint(m))
                    || m.signature != null || !m.exceptions.isEmpty()
                    || nonempty(m.invisibleAnnotations) || nonempty(m.visibleTypeAnnotations)
                    || nonempty(m.invisibleTypeAnnotations) || m.annotationDefault != null
                    || nonempty(m.visibleLocalVariableAnnotations) || nonempty(m.invisibleLocalVariableAnnotations)
                    || m.visibleParameterAnnotations != null || m.invisibleParameterAnnotations != null
                    || m.visibleAnnotableParameterCount != 0 || m.invisibleAnnotableParameterCount != 0
                    || nonempty(m.attrs) || m.visibleAnnotations == null || m.visibleAnnotations.size() != 1) return false;
            for (AbstractInsnNode instruction : m.instructions)
                if (nonempty(instruction.visibleTypeAnnotations) || nonempty(instruction.invisibleTypeAnnotations)) return false;
            AnnotationNode a = m.visibleAnnotations.get(0);
            if (!MERGED.equals(a.desc) || a.values == null || a.values.size() != 6) return false;
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < 6; i += 2) {
                Object name = a.values.get(i);
                if (!(name instanceof String) || values.containsKey(name)) return false;
                values.put((String)name, a.values.get(i + 1));
            }
            if (!MIXIN.equals(values.get("mixin")) || !Integer.valueOf(200).equals(values.get("priority"))
                    || !(values.get("sessionId") instanceof String)) return false;
            String id = (String)values.get("sessionId");
            try { if (!UUID.fromString(id).toString().equals(id)) return false; }
            catch (IllegalArgumentException malformed) { return false; }
            if (session != null && !session.equals(id)) return false;
            session = id;
        }
        if (!aliases.isEmpty()) {
            if (node.version != V17 || !aliases.equals(BRIDGES.keySet())) return false;
            for (String key : aliases) if (declarations.contains(key.substring(PREFIX.length()))) return false;
            for (FieldNode f : node.fields) if ((f.name.equals("namePrefixSPT") || f.name.equals("colorSuffix")) && f.access != ACC_PUBLIC) return false;
            for (MethodNode m : node.methods) if (!aliases.contains(m.name + m.desc)) {
                if (m.name.startsWith("bridge$") || mergedAnnotation(m.visibleAnnotations) || mergedAnnotation(m.invisibleAnnotations)) return false;
            }
        }
        // Inspect operands everywhere, including handles/condy. Do not strip or
        // resolve arbitrary names; only exact local incoming declarations qualify.
        for (MethodNode m : node.methods) for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode) {
                MethodInsnNode c = (MethodInsnNode)n;
                if (!reference(c.owner, c.name, c.desc, c.getOpcode() == INVOKEVIRTUAL && !c.itf, aliases)) return false;
            } else if (n instanceof FieldInsnNode) {
                if (((FieldInsnNode)n).name.contains(CONFLICT)) return false;
            } else if (n instanceof LdcInsnNode) {
                if (!constant(((LdcInsnNode)n).cst, aliases)) return false;
            } else if (n instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode c = (InvokeDynamicInsnNode)n;
                if (c.name.contains(CONFLICT) || !constant(c.bsm, aliases)) return false;
                for (Object arg : c.bsmArgs) if (!constant(arg, aliases)) return false;
            }
        }
        return true;
    }
    private static boolean nonempty(List<?> list) { return list != null && !list.isEmpty(); }
    private static boolean mergedAnnotation(List<AnnotationNode> annotations) {
        if (annotations != null) for (AnnotationNode a : annotations) if (a.desc.endsWith("MixinMerged;")) return true;
        return false;
    }
    private static boolean reference(String owner, String name, String desc, boolean virtual, Set<String> aliases) {
        if (!name.contains(CONFLICT)) {
            // A same-owner plain bridge reference in an incoming alias stage is unresolved.
            return !OWNER.equals(owner) || aliases.isEmpty() || !name.startsWith("bridge$");
        }
        return virtual && OWNER.equals(owner) && aliases.contains(name + desc);
    }
    private static boolean constant(Object value, Set<String> aliases) {
        if (value instanceof Handle) {
            Handle h = (Handle)value;
            return reference(h.getOwner(), h.getName(), h.getDesc(), h.getTag() == H_INVOKEVIRTUAL && !h.isInterface(), aliases);
        }
        if (value instanceof ConstantDynamic) {
            ConstantDynamic c = (ConstantDynamic)value;
            if (c.getName().contains(CONFLICT) || !constant(c.getBootstrapMethod(), aliases)) return false;
            for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++) if (!constant(c.getBootstrapMethodArgument(i), aliases)) return false;
        }
        return true;
    }
    @Override public void transform(ClassNode node, AssemblerConfig cfg) {
        if (!matches(node)) {
            NamePrepRuntime.evidence(false, null);
            System.out.println("[ATW nameprep] UNAVAILABLE: unknown composition; original formatter retained"); return;
        }
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("formatString") && m.desc.equals("(" + STR + ")" + STR)).findFirst().get();
        // Validate a detached injection before mutating the supplied method.
        MethodNode staged = new MethodNode(method.access, method.name, method.desc, null, null); method.accept(staged); inject(staged);
        inject(method);
        String marker = NamePrepRuntime.MARKER + "$" + UUID.randomUUID().toString().replace("-", "");
        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, marker, "I", null, NamePrepRuntime.MARKER_VALUE));
        cfg.computeFrames(); NamePrepRuntime.evidence(true, marker);
        System.out.println("[ATW nameprep] exact v1 formatter/getter composition installed; requested OFF by default");
    }
    private static MethodInsnNode call(String name, String desc) { return new MethodInsnNode(INVOKESTATIC, RUNTIME, name, desc, false); }
    private static void values(InsnList out, int prefix, int suffix, int epoch) {
        out.add(new LdcInsnNode(Type.getObjectType(OWNER))); out.add(new VarInsnNode(ALOAD, 0));
        out.add(new VarInsnNode(ALOAD, prefix)); out.add(new VarInsnNode(ALOAD, 1));
        out.add(new VarInsnNode(ALOAD, suffix)); out.add(new VarInsnNode(LLOAD, epoch));
    }
    static void inject(MethodNode m) {
        MethodInsnNode prefixCall = null, suffixCall = null, stringify = null;
        int appendCount = 0;
        for (AbstractInsnNode n : m.instructions) if (n instanceof MethodInsnNode) {
            MethodInsnNode c = (MethodInsnNode)n;
            if (c.owner.equals(OWNER) && c.name.equals("getColorPrefix")) { if (prefixCall != null) throw new IllegalArgumentException(); prefixCall = c; }
            if (c.owner.equals(OWNER) && c.name.equals("getColorSuffix")) { if (suffixCall != null) throw new IllegalArgumentException(); suffixCall = c; }
            if (c.owner.equals("java/lang/StringBuilder") && c.name.equals("append")) appendCount++;
            if (c.owner.equals("java/lang/StringBuilder") && c.name.equals("toString")) { if (stringify != null) throw new IllegalArgumentException(); stringify = c; }
        }
        if (prefixCall == null || suffixCall == null || stringify == null || appendCount != 3 || !m.tryCatchBlocks.isEmpty()) throw new IllegalArgumentException("Unsupported assembly seam");
        // Exact admitted body uses only receiver 0 and argument 1; no cross-experiment dependency.
        int p = Math.max(2, m.maxLocals), s = p + 1, epoch = s + 1;
        m.maxLocals = epoch + 2; m.maxStack += 10;
        for (int i = 0; i < 2; i++) {
            InsnList capture = new InsnList(); capture.add(new InsnNode(DUP)); capture.add(new VarInsnNode(ASTORE, i == 0 ? p : s));
            m.instructions.insert(i == 0 ? prefixCall : suffixCall, capture);
        }
        LabelNode original = new LabelNode(), miss = new LabelNode(), end = new LabelNode(), noRecord = new LabelNode();
        InsnList lookup = new InsnList();
        lookup.add(new InsnNode(LCONST_0)); lookup.add(new VarInsnNode(LSTORE, epoch));
        lookup.add(call("requested", "()Z")); lookup.add(new JumpInsnNode(IFEQ, original));
        lookup.add(call("epoch", "()J")); lookup.add(new VarInsnNode(LSTORE, epoch));
        values(lookup, p, s, epoch); lookup.add(call("lookup", "(" + ARGS + ")" + STR));
        lookup.add(new InsnNode(DUP)); lookup.add(new JumpInsnNode(IFNULL, miss));
        lookup.add(new InsnNode(SWAP)); lookup.add(new InsnNode(POP)); lookup.add(new JumpInsnNode(GOTO, end));
        lookup.add(miss); lookup.add(new InsnNode(POP)); lookup.add(original);
        m.instructions.insertBefore(stringify, lookup);
        InsnList record = new InsnList(); record.add(new VarInsnNode(LLOAD, epoch)); record.add(new InsnNode(LCONST_0));
        record.add(new InsnNode(LCMP)); record.add(new JumpInsnNode(IFEQ, noRecord));
        record.add(new InsnNode(DUP)); values(record, p, s, epoch); record.add(call("record", "(" + STR + ARGS + ")V"));
        record.add(noRecord); record.add(end); m.instructions.insert(stringify, record);
    }
}

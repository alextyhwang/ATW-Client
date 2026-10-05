package com.atw.renderboost.hook;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Replays the pinned InjectionHandler local conflict map on private inputs read in place. */
@Tag("private-name-capture")
class NameProbeHookStageCaptureTest implements Opcodes {
    static final String PREFIX = "$weave_potentialConflict$";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    static Map<String,String> conflictMap(ClassNode c, boolean inverse) {
        Map<String,String> map = new HashMap<>();
        for (MethodNode m : c.methods) if (m.visibleAnnotations != null && m.visibleAnnotations.stream()
                .anyMatch(a -> a.desc.endsWith("spongepowered/asm/mixin/transformer/meta/MixinMerged;"))) {
            String plain = inverse ? m.name.substring(PREFIX.length()) : m.name;
            map.put(c.name + "." + (inverse ? m.name : plain) + m.desc, inverse ? plain : PREFIX + plain);
        }
        return map;
    }
    static ClassNode rename(ClassNode c, boolean inverse) {
        Map<String,String> map = conflictMap(c,inverse);
        ClassNode out = new ClassNode();
        c.accept(new ClassRemapper(out,new SimpleRemapper(map) {
            @Override public String map(String key) {
                String result = super.map(key);
                if (result != null || inverse) return result;
                // InjectionHandler.transform$2$1: identity fallback, including member keys.
                int dot = key.indexOf('.'), paren = key.indexOf('(');
                return dot < 0 ? key : key.substring(dot+1,paren < 0 ? key.length() : paren);
            }
        }));
        return out;
    }
    static String plain(String name) { return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name; }
    static boolean target(String owner, MethodNode m) {
        MethodNode identity = new MethodNode(m.access,plain(m.name),m.desc,null,null);
        return NameProbeHook.metric(owner,identity) >= 0;
    }
    static int wrappers(ClassNode c) {
        int total=0;
        for (MethodNode m:c.methods) if (target(c.name,m))
            for (AbstractInsnNode i:m.instructions) if (i instanceof MethodInsnNode
                    && NameProbeHook.RUNTIME.equals(((MethodInsnNode)i).owner) && "enter".equals(((MethodInsnNode)i).name)) total++;
        return total;
    }
    @Test void pinnedLoaderStageReproducesThreeOfTwelveDirectMatchesThenInstallsAllTwelve() throws Exception {
        int legacy=0, installed=0, targets=0;
        for (ClassNode captured:NameProbeCaptureTest.captures()) {
            ClassNode stage=rename(captured,false);
            for (MethodNode m:stage.methods) if (target(stage.name,m)) {
                targets++;
                if (NameProbeHook.matches(stage.name,m)) legacy++;
                MethodNode comparison = assertDoesNotThrow(() -> new NameProbeHookStage(stage).comparison(m), stage.name + "." + m.name + m.desc);
                assertTrue(NameProbeHook.matches(stage.name, comparison), "normalized actual method must retain its exact fingerprint");
            }
            new NameProbeHook().transform(stage,()->{});
            installed+=wrappers(stage);
        }
        assertEquals(12,targets);
        assertEquals(3,legacy,"the original direct gate reproduces the observed live 3/12 failure");
        assertEquals(12,installed,"all twelve exact bodies must be admitted at Weave's real rename stage");
    }
    static MethodNode originalOnly(MethodNode m, List<AbstractInsnNode> instructions, List<TryCatchBlockNode> handlers) {
        MethodNode out=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(new String[0]));
        Map<LabelNode,LabelNode> labels=new IdentityHashMap<>();
        for (AbstractInsnNode i:instructions) if (i instanceof LabelNode) labels.put((LabelNode)i,new LabelNode());
        for (AbstractInsnNode i:instructions) out.instructions.add(i.clone(labels));
        for (TryCatchBlockNode h:handlers) out.tryCatchBlocks.add(new TryCatchBlockNode(labels.get(h.start),labels.get(h.end),labels.get(h.handler),h.type));
        return out;
    }
    @Test void stageWrappersPreserveOriginalNodesThenLoaderUnrenameRestoresEveryOriginalExecutableBody() throws Exception {
        int installed=0;
        for (ClassNode captured:NameProbeCaptureTest.captures()) {
            ClassNode stage=rename(captured,false), roundTrip=rename(stage,true);
            for (int index=0;index<captured.methods.size();index++)
                assertEquals(TerrainEvidence.fingerprint(captured.methods.get(index)),TerrainEvidence.fingerprint(roundTrip.methods.get(index)));
            Map<MethodNode,List<AbstractInsnNode>> originals=new IdentityHashMap<>();
            Map<MethodNode,List<TryCatchBlockNode>> handlers=new IdentityHashMap<>();
            Map<MethodNode,String> fingerprints=new IdentityHashMap<>();
            for (MethodNode m:stage.methods) {
                originals.put(m,Arrays.asList(m.instructions.toArray()));
                handlers.put(m,new ArrayList<>(m.tryCatchBlocks)); fingerprints.put(m,TerrainEvidence.fingerprint(m));
            }
            new NameProbeHook().transform(stage,()->{}); installed+=wrappers(stage);
            ClassNode restored=rename(stage,true);
            for (int index=0;index<stage.methods.size();index++) {
                MethodNode m=stage.methods.get(index), post=restored.methods.get(index);
                List<AbstractInsnNode> after=Arrays.asList(m.instructions.toArray());
                List<AbstractInsnNode> restoredOriginals=new ArrayList<>(); int last=-1;
                for (AbstractInsnNode original:originals.get(m)) {
                    int position=after.indexOf(original); assertTrue(position>last,"same original nodes remain in order"); last=position;
                    restoredOriginals.add(post.instructions.get(position));
                }
                assertEquals(fingerprints.get(m),TerrainEvidence.fingerprint(originalOnly(m,originals.get(m),handlers.get(m))));
                assertEquals(handlers.get(m),m.tryCatchBlocks.subList(0,handlers.get(m).size()));
                assertEquals(target(stage.name,m)?1:0,m.tryCatchBlocks.size()-handlers.get(m).size());
                MethodNode body=originalOnly(post,restoredOriginals,post.tryCatchBlocks.subList(0,handlers.get(m).size()));
                assertEquals(TerrainEvidence.fingerprint(captured.methods.get(index)),TerrainEvidence.fingerprint(body));
                if (target(stage.name,m)) new Analyzer<>(new BasicVerifier()).analyze(restored.name,post);
            }
            restored.accept(new CheckClassAdapter(new ClassWriter(0),false));
            int count=stage.methods.stream().mapToInt(m->m.instructions.size()).sum();
            new NameProbeHook().transform(stage,()->fail("idempotent stage wrapper"));
            assertEquals(count,stage.methods.stream().mapToInt(m->m.instructions.size()).sum());
        }
        assertEquals(12,installed);
    }
    @Test void allTwelveStageBodiesRejectExecutableAccessMemberDescriptorAndHandlerTampering() throws Exception {
        int tested=0;
        for (ClassNode captured:NameProbeCaptureTest.captures()) for (MethodNode target:captured.methods)
            if (NameProbeHook.metric(captured.name,target)>=0) for (int change=0;change<6;change++) {
                ClassNode stage=rename(captured,false);
                MethodNode m=stage.methods.stream().filter(n->plain(n.name).equals(target.name)&&n.desc.equals(target.desc)).findFirst().get();
                switch(change) {
                    case 0: m.instructions.insert(new InsnNode(NOP)); break;
                    case 1: m.access^=ACC_FINAL; break;
                    case 2: m.name+="$tampered"; break;
                    case 3: m.desc="()V"; break;
                    case 4:
                        MethodInsnNode call=null;
                        for (AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode) {call=(MethodInsnNode)i;break;}
                        assertNotNull(call); call.owner+="$tampered"; break;
                    default:
                        LabelNode first=new LabelNode(), last=new LabelNode(); m.instructions.insert(first); m.instructions.add(last);
                        m.tryCatchBlocks.add(new TryCatchBlockNode(first,last,first,"java/lang/Throwable")); break;
                }
                AbstractInsnNode[] before=m.instructions.toArray(); String name=m.name, desc=m.desc, body=TerrainEvidence.fingerprint(m);
                int access=m.access, locals=m.maxLocals, stack=m.maxStack;
                List<TryCatchBlockNode> handlers=new ArrayList<>(m.tryCatchBlocks);
                new NameProbeHook().transform(stage,()->{});
                assertArrayEquals(before,m.instructions.toArray()); assertEquals(body,TerrainEvidence.fingerprint(m));
                assertEquals(name,m.name); assertEquals(desc,m.desc); assertEquals(access,m.access);
                assertEquals(locals,m.maxLocals); assertEquals(stack,m.maxStack); assertEquals(handlers,m.tryCatchBlocks);
                tested++;
            }
        assertEquals(72,tested);
    }
    static ClassNode font(List<ClassNode> captures) { return captures.stream().filter(c->c.name.equals(NameProbeHook.TARGETS[4])).findFirst().get(); }
    static MethodNode width(ClassNode c) { return c.methods.stream().filter(m->target(c.name,m)).findFirst().get(); }
    static AnnotationNode merged(MethodNode m) { return m.visibleAnnotations.stream().filter(a->MERGED.equals(a.desc)).findFirst().get(); }
    static void rejectUntouched(ClassNode c) {
        Map<MethodNode,AbstractInsnNode[]> nodes=new IdentityHashMap<>(); Map<MethodNode,String> bodies=new IdentityHashMap<>();
        for (MethodNode m:c.methods) {nodes.put(m,m.instructions.toArray());bodies.put(m,TerrainEvidence.fingerprint(m));}
        new NameProbeHook().transform(c,()->fail("invalid rename proof must fail closed"));
        for (MethodNode m:c.methods) {assertArrayEquals(nodes.get(m),m.instructions.toArray());assertEquals(bodies.get(m),TerrainEvidence.fingerprint(m));}
    }
    @Test void renamedTargetsRejectMissingInvisibleUnknownDuplicateAndMalformedAnnotationAndPrefixProof() throws Exception {
        for (int change=0;change<13;change++) {
            ClassNode stage=rename(font(NameProbeCaptureTest.captures()),false); MethodNode m=width(stage); AnnotationNode a=merged(m);
            switch(change) {
                case 0: m.visibleAnnotations.remove(a); break;
                case 1: m.visibleAnnotations.remove(a); m.invisibleAnnotations=new ArrayList<>(Collections.singletonList(a)); break;
                case 2: m.visibleAnnotations.add(a); break;
                case 3: a.desc="Lunknown/spongepowered/asm/mixin/transformer/meta/MixinMerged;"; break;
                case 4: a.values.set(a.values.indexOf("mixin")+1,"unknown.Mixin"); break;
                case 5: a.values.set(a.values.indexOf("priority")+1,999); break;
                case 6: a.values.set(a.values.indexOf("sessionId")+1,"invalid"); break;
                case 7: a.values.add("mixin"); a.values.add("unknown"); break;
                case 8: m.name=PREFIX+m.name; break;
                case 9: m.name="$weave_potentialConflict"+plain(m.name); break;
                case 10: stage.methods.add(new MethodNode(m.access,m.name,m.desc,null,null)); break;
                case 11: stage.methods.add(new MethodNode(m.access,plain(m.name),m.desc,null,null)); break;
                default: m.name=plain(m.name); break; // mixed loader stage
            }
            rejectUntouched(stage);
        }
    }
    @Test void danglingUnprefixedLocalCallsAndCrossOwnerPrefixedReferencesRejectWithoutTreeChanges() throws Exception {
        for (int change=0;change<4;change++) {
            ClassNode stage=rename(font(NameProbeCaptureTest.captures()),false); MethodNode m=width(stage);
            MethodInsnNode local=null, external=null;
            for (AbstractInsnNode i:m.instructions) if (i instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)i;
                if (stage.name.equals(call.owner)) local=call; else external=call;
            }
            assertNotNull(local); assertNotNull(external); assertTrue(local.name.startsWith(PREFIX));
            switch(change) {
                case 0: local.name=plain(local.name); break;
                case 1: local.name=PREFIX+local.name; break;
                case 2: external.name=PREFIX+external.name; break;
                default: local.desc="()F"; break;
            }
            rejectUntouched(stage);
        }
    }
}

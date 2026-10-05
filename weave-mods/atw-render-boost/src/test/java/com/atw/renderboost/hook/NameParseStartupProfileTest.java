package com.atw.renderboost.hook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reconstructs actual reported startup member order from original captured bodies, in memory only. */
@Tag("private-name-codec-startup")
class NameParseStartupProfileTest implements Opcodes {
    static Properties report(String owner)throws Exception {
        Path root=Paths.get(System.getProperty("atwboost.nameCodecStartupRoot"));Properties p=new Properties();
        try(InputStream in=Files.newInputStream(root.resolve(owner.replace('/','_')+".mismatch.properties"))){p.load(in);}return p;
    }
    static ClassNode startup(ClassNode captured)throws Exception {
        Properties p=report(captured.name);String[] shape=p.getProperty("actualBasicShape").split("\\|",-1);
        ClassNode c=new ClassNode();captured.accept(c);Map<String,FieldNode> fields=new HashMap<>();Map<String,MethodNode> methods=new HashMap<>();
        for(FieldNode f:c.fields)fields.put(f.name+":"+f.desc,f);for(MethodNode m:c.methods)methods.put(m.name+":"+m.desc,m);
        List<FieldNode> orderedFields=new ArrayList<>();List<MethodNode> orderedMethods=new ArrayList<>();
        for(int i=1;i<shape.length;i++) {
            String[] member=shape[i].split(":",3);int access=Integer.parseInt(member[0]);String key=member[1]+":"+member[2];
            if(member[2].startsWith("(")) {
                MethodNode m=methods.remove(key);assertNotNull(m,key);
                if(m.access!=access){assertEquals("<clinit>",m.name);assertEquals(ACC_STATIC,m.access);assertEquals(ACC_STATIC|ACC_PUBLIC,access);m.access=access;}
                String actualHash=p.getProperty("method."+m.name+m.desc+".actual");
                if(actualHash!=null)assertEquals(actualHash,TerrainEvidence.fingerprint(m),"PUBLIC-only body proof");
                orderedMethods.add(m);
            }else{FieldNode f=fields.remove(key);assertNotNull(f,key);assertEquals(f.access,access);orderedFields.add(f);}
        }
        assertTrue(fields.isEmpty());assertTrue(methods.isEmpty());c.fields=orderedFields;c.methods=orderedMethods;
        assertEquals(p.getProperty("actualBasicShape"),TerrainEvidence.classShape(c));
        return NameProbeHookStageCaptureTest.rename(c,false);
    }
    @Test void allTenActualStartupReportsMustPassOriginalBodyAndMetadataGuard()throws Exception {
        int accepted=0;for(ClassNode captured:NameParseCaptureTest.captures())if(NameParseHook.matches(startup(captured)))accepted++;
        assertEquals(10,accepted,"exact actual startup order/access profiles must admit all ten");
    }
    @Test void startupProfileRejectsUnobservedOrderAccessBodyDuplicatesMetadataAndFieldChanges()throws Exception {
        for(ClassNode captured:NameParseCaptureTest.captures()) {
            ClassNode baseline=startup(captured);assertTrue(NameParseHook.matches(baseline));
            for(int change=0;change<6;change++) {
                ClassNode c=new ClassNode();baseline.accept(c);
                switch(change) {
                    case 0:Collections.reverse(c.methods);break;
                    case 1:c.methods.get(0).access^=ACC_FINAL;break;
                    case 2:c.methods.get(0).instructions.insert(new InsnNode(NOP));break;
                    case 3:c.methods.add(c.methods.get(0));break;
                    case 4:c.fields.add(new FieldNode(ACC_PRIVATE,"unprovedField","I",null,null));break;
                    default:c.methods.get(0).invisibleAnnotations=new ArrayList<>(Collections.singletonList(new AnnotationNode(NameParseHookStage.MERGED)));break;
                }
                assertFalse(NameParseHook.matches(c),c.name+" negative "+change);
            }
            for(MethodNode m:baseline.methods)if(NameParseHookStage.logicalName(m.name).equals("<clinit>")) {
                ClassNode c=new ClassNode();baseline.accept(c);MethodNode clinit=c.methods.stream().filter(n->NameParseHookStage.logicalName(n.name).equals("<clinit>")).findFirst().get();clinit.access|=ACC_PRIVATE;
                assertFalse(NameParseHook.matches(c),"no global access mask");
                if(m.access==(ACC_STATIC|ACC_PUBLIC)) {
                    c=new ClassNode();baseline.accept(c);clinit=c.methods.stream().filter(n->NameParseHookStage.logicalName(n.name).equals("<clinit>")).findFirst().get();clinit.access=ACC_STATIC;
                    assertFalse(NameParseHook.matches(c),"unobserved order/access combination must reject");
                }
            }
        }
    }
    @Test void startupNormalizationLeavesAllRunningNamesAccessInstructionsAndHandlersUnchanged()throws Exception {
        for(ClassNode captured:NameParseCaptureTest.captures()) {
            ClassNode c=startup(captured);Map<MethodNode,AbstractInsnNode[]> nodes=new IdentityHashMap<>();Map<MethodNode,Integer> access=new IdentityHashMap<>();Map<MethodNode,String> names=new IdentityHashMap<>();Map<MethodNode,List<TryCatchBlockNode>> handlers=new IdentityHashMap<>();
            for(MethodNode m:c.methods){nodes.put(m,m.instructions.toArray());access.put(m,m.access);names.put(m,m.name);handlers.put(m,new ArrayList<>(m.tryCatchBlocks));}
            assertTrue(NameParseHook.matches(c));new NameParseHook().transform(c,()->{});
            for(MethodNode m:c.methods) {
                assertEquals(access.get(m),m.access);assertEquals(names.get(m),m.name);assertEquals(handlers.get(m),m.tryCatchBlocks);int last=-1;
                for(AbstractInsnNode n:nodes.get(m)){int position=m.instructions.indexOf(n);assertTrue(position>last);last=position;}
            }
        }
    }
}

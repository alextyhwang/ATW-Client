package com.atw.renderboost.hook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.AbstractList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class NameEncodingEvidenceOutputTest implements Opcodes {
    @TempDir Path temporary;
    static Properties missingFactoryEvidence()throws Exception {
        Properties p=new Properties();
        try(java.io.InputStream in=NameEncodingEvidenceOutputTest.class.getResourceAsStream("/name-encoding/factory-missing.properties")) {
            assertNotNull(in);p.load(in);
        }
        assertEquals("missing",p.getProperty("captureKind"));assertEquals("false",p.getProperty("parentApproved"));return p;
    }
    static ClassNode factory() {
        ClassNode c=NameProbeCaptureTest.empty(NameEncodingHook.MAPS,false);MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC,"newHashMap","()Ljava/util/HashMap;",null,null);
        m.instructions.add(new TypeInsnNode(NEW,"java/util/HashMap"));m.instructions.add(new InsnNode(DUP));m.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/util/HashMap","<init>","()V",false));m.instructions.add(new InsnNode(ARETURN));c.methods.add(m);return c;
    }
    @Test void exactFactorySemanticsAndMetadataAreInsufficientForLiveAdmission()throws Exception {
        ClassNode c=factory();assertTrue(NameEncodingHook.factorySemantics(c));assertFalse(NameEncodingHook.matches(c));
        assertFalse(NameEncodingHook.factoryMatches(c,missingFactoryEvidence()));
        AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();Properties p=NameEncodingEvidenceOutput.capture(c,temporary,temporary.toString());
        assertEquals("hook-input-only",p.getProperty("captureKind"));assertEquals("false",p.getProperty("parentApproved"));assertEquals("true",p.getProperty("factorySemanticsMatch"));assertEquals("false",p.getProperty("admitted"));assertArrayEquals(before,c.methods.get(0).instructions.toArray());
        new NameEncodingHook().transform(c,()->fail());assertTrue(c.fields.isEmpty());
        c.methods.get(0).instructions.insert(new InsnNode(NOP));assertFalse(NameEncodingHook.factorySemantics(c));
    }
    @Test void metadataFlagsCannotPromoteAnUnobservedFactoryProfile() {
        ClassNode c=factory();Properties p=new Properties();p.setProperty("@shape",NameParseHook.fields(c));
        p.setProperty("newHashMap()Ljava/util/HashMap;",TerrainEvidence.fingerprint(c.methods.get(0)));p.setProperty("captureKind","disk-only");p.setProperty("parentApproved","true");assertFalse(NameEncodingHook.factoryMatches(c,p));
        p.setProperty("captureKind","actual-loaded");assertFalse(NameEncodingHook.factoryMatches(c,p));assertFalse(NameEncodingHook.matches(c),"fixture evidence is never production admission");
        p.setProperty("captureKind",NameEncodingHook.FACTORY_STARTUP_KIND);p.setProperty("@actualLoadedShape",p.getProperty("@shape"));assertFalse(NameEncodingHook.factoryMatches(c,p),"both observed shapes must be pinned");
        c.fields.add(new FieldNode(ACC_PUBLIC,"unknown","I",null,null));assertFalse(NameEncodingHook.factoryMatches(c,p));p.setProperty("@shape",NameParseHook.fields(c));assertFalse(NameEncodingHook.factoryMatches(c,p));
        c.methods.get(0).instructions.insert(new InsnNode(NOP));p.setProperty("newHashMap()Ljava/util/HashMap;",TerrainEvidence.fingerprint(c.methods.get(0)));assertFalse(NameEncodingHook.factoryMatches(c,p),"even approved different factory semantics reject");
    }
    @Test void reportsNeverPublishConstantsPayloadsOrBytecodeAndCannotOverwriteOrEscape()throws Exception {
        ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);c.fields.add(new FieldNode(ACC_PRIVATE,"secret","Ljava/lang/String;",null,"PRIVATE_SENTINEL"));c.methods.add(NameEncodingHookTest.body());
        NameEncodingEvidenceOutput.capture(c,temporary,temporary.toString());Path output=temporary.resolve(c.name.replace('/','_')+".properties");assertFalse(new String(Files.readAllBytes(output),StandardCharsets.ISO_8859_1).contains("PRIVATE_SENTINEL"));
        assertThrows(FileAlreadyExistsException.class,()->NameEncodingEvidenceOutput.capture(c,temporary,temporary.toString()));
        for(String outside:new String[]{"relative",temporary.resolve("other").toString(),temporary.getParent().toString()})assertThrows(IllegalArgumentException.class,()->NameEncodingEvidenceOutput.capture(c,temporary,outside));
        c.name="unknown/Owner";assertThrows(IllegalArgumentException.class,()->NameEncodingEvidenceOutput.capture(c,temporary,temporary.toString()));
    }
    @Test void defaultOffDiagnosticsDoNotTouchOrResolveTheNode() {
        String old=System.getProperty(NameEncodingEvidenceOutput.PROPERTY);try{System.clearProperty(NameEncodingEvidenceOutput.PROPERTY);ClassNode c=factory();AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();NameEncodingEvidenceOutput.record(c);assertArrayEquals(before,c.methods.get(0).instructions.toArray());}
        finally{if(old==null)System.clearProperty(NameEncodingEvidenceOutput.PROPERTY);else System.setProperty(NameEncodingEvidenceOutput.PROPERTY,old);}
    }
    public static final class Boundary {
        static Error failure;
        public static String getProperty(String ignored){throw failure;}
    }
    @Test void admissionAndMetadataCatchesPreserveTerminationAndFatalVmErrorIdentity()throws Exception {
        ClassLoader isolated=new ClassLoader(getClass().getClassLoader()) {
            Class<?> define()throws Exception {
                org.objectweb.asm.ClassReader reader;
                try(java.io.InputStream in=getParent().getResourceAsStream(NameEncodingEvidenceOutput.class.getName().replace('.','/')+".class")){reader=new org.objectweb.asm.ClassReader(in);}
                ClassNode c=new ClassNode();reader.accept(c,0);
                for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)n;if(call.owner.equals("java/lang/System")&&call.name.equals("getProperty"))call.owner=org.objectweb.asm.Type.getInternalName(Boundary.class);
                }
                org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);c.accept(writer);byte[] bytes=writer.toByteArray();return defineClass(c.name.replace('/','.'),bytes,0,bytes.length);
            }
        };
        java.lang.reflect.Method define=isolated.getClass().getDeclaredMethod("define");define.setAccessible(true);
        java.lang.reflect.Method record=((Class<?>)define.invoke(isolated)).getDeclaredMethod("record",ClassNode.class);record.setAccessible(true);
        try {
            for(Error fatal:new Error[]{new ThreadDeath(),new StackOverflowError("controlled"),new InternalError("controlled"),new UnknownError("controlled")}) {
                ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);
                c.fields=new AbstractList<FieldNode>(){public int size(){return 1;}public FieldNode get(int index){throw fatal;}};
                assertSame(fatal,assertThrows(fatal.getClass(),()->NameEncodingHook.matches(c)));
                Boundary.failure=fatal;assertSame(fatal,assertThrows(java.lang.reflect.InvocationTargetException.class,()->record.invoke(null,factory())).getCause());
            }
            ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);
            c.fields=new AbstractList<FieldNode>(){public int size(){return 1;}public FieldNode get(int index){throw new OutOfMemoryError("controlled admission allocation");}};
            assertFalse(NameEncodingHook.matches(c));Boundary.failure=new OutOfMemoryError("controlled metadata allocation");assertDoesNotThrow(()->record.invoke(null,factory()));
        }finally{Boundary.failure=null;}
    }
}

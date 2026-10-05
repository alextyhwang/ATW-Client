package com.atw.renderboost.hook;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class NameParseHookEvidenceOutputTest implements Opcodes {
    @TempDir Path temporary;
    @Test void disabledDiagnosticsDoNothingToTheSuppliedNode() {
        String old=System.getProperty(NameParseHookEvidenceOutput.PROPERTY);
        try {
            System.clearProperty(NameParseHookEvidenceOutput.PROPERTY);
            ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);c.methods.add(NameParseHookTest.body());
            AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();
            NameParseHookEvidenceOutput.record(c);assertArrayEquals(before,c.methods.get(0).instructions.toArray());
        }finally{if(old==null)System.clearProperty(NameParseHookEvidenceOutput.PROPERTY);else System.setProperty(NameParseHookEvidenceOutput.PROPERTY,old);}
    }
    @Test void explicitMetadataOutputWritesOnePropertiesFileWithNoBytesOrFieldPayload()throws Exception {
        ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);
        c.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,"test","Ljava/lang/String;",null,"PRIVATE_PAYLOAD_SENTINEL"));
        c.methods.add(NameParseHookTest.body());AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();
        Properties p=NameParseHookEvidenceOutput.capture(c,temporary,temporary.toString());
        assertNotNull(p.getProperty("actualBasicShape"));assertArrayEquals(before,c.methods.get(0).instructions.toArray());
        try(java.util.stream.Stream<Path> files=Files.list(temporary)) {
            List<Path> paths=new ArrayList<>();files.forEach(paths::add);assertEquals(1,paths.size());assertTrue(paths.get(0).toString().endsWith(".mismatch.properties"));
            String text=new String(Files.readAllBytes(paths.get(0)),java.nio.charset.StandardCharsets.ISO_8859_1);assertFalse(text.contains("PRIVATE_PAYLOAD_SENTINEL"));
        }
        assertThrows(FileAlreadyExistsException.class,()->NameParseHookEvidenceOutput.capture(c,temporary,temporary.toString()));
    }
    @Test void outsideRelativeMissingAndUnknownTargetDestinationsReject()throws Exception {
        ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);
        for(String output:new String[]{"relative",temporary.resolve("outside").toString(),temporary.getParent().toString()})
            assertThrows(IllegalArgumentException.class,()->NameParseHookEvidenceOutput.capture(c,temporary,output));
        c.name="unknown/Owner";assertThrows(IllegalArgumentException.class,()->NameParseHookEvidenceOutput.capture(c,temporary,temporary.toString()));
    }
}

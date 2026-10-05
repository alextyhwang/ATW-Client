package com.atw.renderboost.hook;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;

/** Opt-in metadata-only ten-target diagnostics; never writes class bytes. */
final class NameParseHookEvidenceOutput {
    static final String PROPERTY="atwboost.nameParseEvidenceOutput";
    private static final Set<String> ATTEMPTED=new HashSet<>();
    private NameParseHookEvidenceOutput(){}
    static String digest(String value) {
        if(value==null)return "missing";
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
        }catch(Exception e){throw new IllegalStateException("Digest unavailable");}
    }
    static void record(ClassNode node) {
        String output=System.getProperty(PROPERTY);
        if(output==null || output.isEmpty() || !Arrays.asList(NameParseHook.TARGETS).contains(node.name))return;
        synchronized(ATTEMPTED){if(!ATTEMPTED.add(node.name))return;}
        try {
            Path terrain=TerrainHookEvidenceOutput.privateDirectory(NameParseHookEvidenceOutput.class.getProtectionDomain(),System.getProperty("user.home"));
            Path permitted=terrain.getParent().resolve("name-parse-cache/hookstage");
            Properties p=capture(node,permitted,output);
            System.out.println("[ATW name parse metadata] "+node.name+": provenance="+p.getProperty("provenance")+", shapeMatch="+p.getProperty("shapeMatch")+", basicShapeMatch="+p.getProperty("basicShapeMatch")+", changedMethods="+p.getProperty("changedMethods")+", missingMethods="+p.getProperty("missingMethods"));
        }catch(Throwable e){System.out.println("[ATW name parse metadata] "+node.name+": diagnostic skipped; reason="+e.getClass().getSimpleName());}
    }
    static Properties capture(ClassNode node,Path permitted,String output)throws Exception {
        if(!Arrays.asList(NameParseHook.TARGETS).contains(node.name))throw new IllegalArgumentException("Not an exact name parse target");
        Path requested=Paths.get(output),root=permitted.toAbsolutePath().normalize();
        if(!requested.isAbsolute() || !requested.normalize().equals(root) || !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))throw new IllegalArgumentException("Existing private directory required");
        Properties p=NameParseHook.mismatchReport(node);
        String stem=node.name.replace('/','_');
        try(OutputStream out=Files.newOutputStream(root.resolve(stem+".mismatch.properties"),StandardOpenOption.CREATE_NEW)) {
            p.store(out,"PRIVATE metadata-only name parse hook-input mismatch; no captured bytecode or payloads");
        }
        return p;
    }
}

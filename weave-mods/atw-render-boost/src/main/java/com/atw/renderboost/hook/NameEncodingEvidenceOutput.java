package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameEncodingRuntime;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.objectweb.asm.tree.*;

/** Opt-in hook-stage metadata only. This is explicitly NOT actual-loaded admission. */
final class NameEncodingEvidenceOutput {
    static final String PROPERTY="atwboost.nameEncodingEvidenceOutput";
    private static final Set<String> ATTEMPTED=new HashSet<>();
    private NameEncodingEvidenceOutput(){}
    static void record(ClassNode node) {
        if(!node.name.equals(NameEncodingHook.MAPS)&&!node.name.equals(NameParseHook.CONVERTER))return;
        try {
            String output=System.getProperty(PROPERTY);if(output==null||output.isEmpty())return;
            synchronized(ATTEMPTED){if(!ATTEMPTED.add(node.name))return;}
            Path terrain=TerrainHookEvidenceOutput.privateDirectory(NameEncodingEvidenceOutput.class.getProtectionDomain(),System.getProperty("user.home"));
            capture(node,terrain.getParent().resolve("name-encoding/hookstage"),output);
        }catch(Throwable e){NameEncodingRuntime.propagateFatal(e);System.out.println("[ATW name encoding metadata] skipped: "+e.getClass().getSimpleName());}
    }
    static Properties capture(ClassNode node,Path permitted,String output)throws Exception {
        if(!node.name.equals(NameEncodingHook.MAPS)&&!node.name.equals(NameParseHook.CONVERTER))throw new IllegalArgumentException("Not an encoding target");
        Path requested=Paths.get(output),root=permitted.toAbsolutePath().normalize();
        if(!requested.isAbsolute()||!requested.normalize().equals(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toRealPath().equals(root))throw new IllegalArgumentException("Existing private directory required");
        Properties p=new Properties();p.setProperty("captureKind","hook-input-only");p.setProperty("parentApproved","false");
        p.setProperty("owner",node.name);p.setProperty("admitted",String.valueOf(NameEncodingHook.matches(node)));
        if(node.name.equals(NameEncodingHook.MAPS))p.setProperty("factorySemanticsMatch",String.valueOf(NameEncodingHook.factorySemantics(node)));
        p.setProperty("shapeSha256",NameParseHookEvidenceOutput.digest(NameParseHook.fields(node)));
        for(MethodNode m:node.methods)p.setProperty("method."+m.name+m.desc,TerrainEvidence.fingerprint(m));
        try(OutputStream out=Files.newOutputStream(root.resolve(node.name.replace('/','_')+".properties"),StandardOpenOption.CREATE_NEW)){p.store(out,"PRIVATE hook-stage metadata; not actual-loaded evidence; no bytecode or payloads");}
        return p;
    }
}

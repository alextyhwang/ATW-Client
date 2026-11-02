package com.atw.renderboost.hook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Join prior actual-loaded executables with the exact observed startup declaration order.
 * Disk supplies order only. No attachment, class loading or runtime-marker qualification. */
@Tag("private-name-codec-capture")
class NameEncodingFactoryCaptureTest implements Opcodes {
    static final Path ROOT=Paths.get("../../upgrade-work/environment-20261005");
    static final Path CAPTURE=ROOT.resolve("name-render-probe/maps-capture-20261005/com_google_common_collect_Maps.loader1.live.class");
    static final Path HOOK=ROOT.resolve("name-encoding/hookstage/com_google_common_collect_Maps.properties");
    static final Path RELATION=Paths.get("../../upgrade-work/render-next-20261005/maps-loader-relationship.properties");
    static final String CAPTURE_SHA="ba2833b29c7b961343bbab979223fac6bac54ab1ce074decc26ddd428f7c869c";
    static final String SHAPE_SHA="1bd01e1dc828c8ffed8c2bb34e670ee91ba0fae02312493a1d7ba18e219b4e1e";
    static final String HOOK_SHAPE_SHA="7108c01762dd4572f88db7c695dcc4a868ba7f735073932217c42228d03c2e59";
    static Properties read(Path path)throws Exception {
        Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}return p;
    }
    static String sha(Path path)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));StringBuilder s=new StringBuilder();
        for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static ClassNode captured()throws Exception {
        assertEquals(CAPTURE_SHA,sha(CAPTURE));ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(CAPTURE)).accept(c,0);return c;
    }
    static ClassNode packaged()throws Exception {
        Path jar=Paths.get("../../build/dependencies/minecraft/lunarclient/libraries/com/google/guava/guava/17.0/guava-17.0.jar");
        assertEquals("8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc",sha(jar));
        ClassNode c=new ClassNode();try(JarFile input=new JarFile(jar.toFile())){new ClassReader(input.getInputStream(input.getJarEntry(NameEncodingHook.MAPS+".class"))).accept(c,0);}return c;
    }
    static Properties executable(ClassNode c) {
        Properties p=new Properties();p.setProperty("@shape",NameParseHook.fields(c));
        for(MethodNode m:c.methods)assertNull(p.setProperty(m.name+m.desc,TerrainEvidence.fingerprint(m)),"duplicate method");
        assertEquals(c.methods.size()+1,p.size());return p;
    }
    static void compareHook(ClassNode c,Properties hook) {
        assertEquals(NameEncodingHook.MAPS,c.name);assertTrue(NameEncodingHook.factorySemantics(c));
        Properties expected=new Properties();expected.setProperty("captureKind","hook-input-only");expected.setProperty("parentApproved","false");
        expected.setProperty("owner",c.name);expected.setProperty("admitted","false");expected.setProperty("factorySemanticsMatch","true");
        expected.setProperty("shapeSha256",NameParseHookEvidenceOutput.digest(NameParseHook.fields(c)));
        for(MethodNode m:c.methods)assertNull(expected.setProperty("method."+m.name+m.desc,TerrainEvidence.fingerprint(m)),"duplicate method");
        assertEquals(expected,hook,"complete hook-stage shape/method set must match; no missing/extra metadata");
    }
    static Properties verified()throws Exception {
        assertEquals("5600d7bace95ac09e114e5d2a865fad6c82105c677cce20f8701bf47cddee10e",sha(HOOK));
        assertEquals("16bcffca293b3a07c9bbe73c8476403f01be83767177b24aebd31d191427f0a0",sha(RELATION));
        Path report=CAPTURE.resolveSibling("capture-report.txt");
        assertEquals("7eee876a8ce308cbbc18bb6d0326c3b75a3e5a92c5abc5930cd55759b7c5103d",sha(report));
        Properties relation=read(RELATION);
        for(String key:new String[]{"mapsDefinitions","compoundDefinitions","converterDefinitions"})assertEquals("1",relation.getProperty(key),key);
        for(String key:new String[]{"compoundResolvesActualMaps","converterResolvesActualMaps","compoundConverterSameLoader"})assertEquals("true",relation.getProperty(key),key);
        String loader=relation.getProperty("mapsDefiningLoader");assertNotNull(loader);assertTrue(loader.startsWith("com.moonsworth.lunar.genesis."));
        String provenance=new String(Files.readAllBytes(report),StandardCharsets.UTF_8);
        assertTrue(provenance.contains("com.google.common.collect.Maps loader="+loader));
        assertTrue(provenance.contains("sha256="+CAPTURE_SHA));assertTrue(provenance.contains("TRANSFORMER_REMOVED true"));
        ClassNode c=captured(),disk=packaged();Properties hook=read(HOOK);
        assertEquals(97,c.methods.size());assertEquals(97,disk.methods.size());compareHook(disk,hook);
        assertEquals(HOOK_SHAPE_SHA,hook.getProperty("shapeSha256"));
        assertEquals(SHAPE_SHA,NameParseHookEvidenceOutput.digest(NameParseHook.fields(c)));
        Properties p=executable(c),diskProfile=executable(disk);
        Properties methods=new Properties();methods.putAll(p);methods.remove("@shape");diskProfile.remove("@shape");
        Properties hookMethods=new Properties();for(String key:hook.stringPropertyNames())if(key.startsWith("method."))hookMethods.setProperty(key.substring(7),hook.getProperty(key));
        assertEquals(methods,hookMethods,"97/97 actual-loaded method hashes match the fresh hook report directly; no missing/extra methods");
        assertEquals(methods,diskProfile,"packaged bytes diagnose order only; they do not supply live admission");
        ClassNode header=new ClassNode(),diskHeader=new ClassNode();c.accept(header);disk.accept(diskHeader);header.methods.clear();diskHeader.methods.clear();
        assertEquals(NameParseHook.fields(header),NameParseHook.fields(diskHeader),"header, interfaces and ordered fields match; only method order differs");
        assertNotEquals(NameParseHook.fields(c),NameParseHook.fields(disk));assertThrows(AssertionError.class,()->compareHook(c,hook));
        assertEquals(p,NameEncodingHook.profile(c),"no conflict/provenance normalization is needed for Maps");
        // Bodies/header/fields come from the actual-loaded capture; disk supplies ONLY method order.
        ClassNode startup=new ClassNode();c.accept(startup);startup.methods.clear();
        Map<String,MethodNode> loadedMethods=new HashMap<>();for(MethodNode m:c.methods)assertNull(loadedMethods.put(m.name+m.desc,m));
        for(MethodNode m:disk.methods)startup.methods.add(loadedMethods.remove(m.name+m.desc));assertTrue(loadedMethods.isEmpty());
        compareHook(startup,hook);assertEquals(HOOK_SHAPE_SHA,NameParseHookEvidenceOutput.digest(NameParseHook.fields(startup)));
        p.setProperty("@actualLoadedShape",p.getProperty("@shape"));p.setProperty("@shape",NameParseHook.fields(startup));
        p.setProperty("captureKind",NameEncodingHook.FACTORY_STARTUP_KIND);p.setProperty("parentApproved","true");
        assertTrue(NameEncodingHook.factoryMatches(startup,p));assertFalse(NameEncodingHook.factoryMatches(c,p));return p;
    }
    @Test void exactObservedStartupIsApprovedWhileHistoricalLoadedOrderRemainsProvenanceOnly()throws Exception {
        Properties p=verified();assertTrue(NameEncodingHook.factorySemantics(captured()));
        assertTrue(NameEncodingHook.factoryMatches(packaged(),p));
        Properties shipped=new Properties();try(InputStream in=getClass().getResourceAsStream("/name-encoding-factory-live.properties")){assertNotNull(in);shipped.load(in);}
        assertEquals(p,shipped,"production profile must be exactly the verified two-stage join");assertTrue(NameEncodingHook.factoryApproved());
        assertFalse(NameEncodingHook.matches(captured()));assertTrue(NameEncodingHook.matches(packaged()));
        ClassNode transformed=packaged();new NameEncodingHook().transform(transformed,()->fail("factory must not inject a callsite"));
        FieldNode marker=transformed.fields.stream().filter(f->f.name.startsWith(NameEncodingHook.MARKER+"$")).findFirst().get();
        assertEquals(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker.access);assertFalse(NameEncodingHook.matches(transformed));
        ClassNode another=packaged();new NameEncodingHook().transform(another,()->fail());
        assertTrue(another.fields.stream().anyMatch(f->f.name.startsWith(NameEncodingHook.MARKER+"$")&&!f.name.equals(marker.name)));
        List<String> actual=new ArrayList<>(),startup=new ArrayList<>();
        for(MethodNode m:captured().methods)actual.add(m.name+m.desc);for(MethodNode m:packaged().methods)startup.add(m.name+m.desc);
        int moved=0;for(int i=0;i<actual.size();i++)if(!actual.get(i).equals(startup.get(i)))moved++;
        assertEquals(97,moved);
        System.out.println("Exact startup factory approved: 97/97 actual-loaded fingerprints; "+moved+" method positions differ. Loaded provenance="+SHAPE_SHA+"; admitted startup="+HOOK_SHAPE_SHA);
    }
    @Test void changedMissingExtraMethodsShapeAndApprovalRejectDespitePinnedFactoryBody()throws Exception {
        ClassNode baseline=packaged();Properties p=verified();
        for(int i=0;i<baseline.methods.size();i++)for(boolean access:new boolean[]{false,true}) {
            ClassNode c=new ClassNode();baseline.accept(c);MethodNode m=c.methods.get(i);
            if(access)m.access^=ACC_FINAL;else m.instructions.insert(new InsnNode(NOP));
            assertFalse(NameEncodingHook.factoryMatches(c,p),m.name+m.desc);
        }
        for(int change=0;change<14;change++) {
            ClassNode c=new ClassNode();baseline.accept(c);
            if(change==0)c.methods.remove(c.methods.size()-1);
            else if(change==1)c.methods.add(new MethodNode(ACC_STATIC,"extra","()V",null,null));
            else if(change==2)Collections.reverse(c.methods);
            else if(change==3)c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,NameEncodingHook.MARKER+"$stale","I",null,NameEncodingHook.MARKER_VALUE));
            else if(change==4)Collections.swap(c.methods,0,1);
            else if(change==5)c.access^=ACC_FINAL;
            else if(change==6)c.fields.get(0).access^=ACC_PUBLIC;
            else if(change==7)c.fields.get(0).name="changed";
            else if(change==8)c.interfaces.add("unknown/Interface");
            else if(change==9)c.fields.get(0).signature="changed";
            else if(change==10)c.fields.get(0).value="changed";
            else if(change==11)c.fields.clear();
            else if(change==12)c.superName="unknown/Super";
            else c.version++;
            assertFalse(NameEncodingHook.factoryMatches(c,p));
        }
        for(int i=0;i<baseline.methods.size()-1;i++) {
            ClassNode c=new ClassNode();baseline.accept(c);Collections.swap(c.methods,i,i+1);
            assertFalse(NameEncodingHook.matches(c),"unobserved adjacent reorder "+i);
        }
        for(String kind:new String[]{"missing","disk-only","hook-input-only","actual-loaded"}) {
            Properties rejected=new Properties();rejected.putAll(p);rejected.setProperty("captureKind",kind);assertFalse(NameEncodingHook.factoryMatches(baseline,rejected));
        }
        Properties rejected=new Properties();rejected.putAll(p);rejected.setProperty("parentApproved","false");assertFalse(NameEncodingHook.factoryMatches(baseline,rejected));
        rejected.putAll(p);rejected.setProperty("extra()V",p.getProperty("newHashMap()Ljava/util/HashMap;"));assertFalse(NameEncodingHook.factoryMatches(baseline,rejected));
        assertFalse(NameEncodingHook.factoryMatches(baseline,NameEncodingEvidenceOutputTest.missingFactoryEvidence()));
        for(String key:new String[]{"@shape","@actualLoadedShape"}) {
            rejected=new Properties();rejected.putAll(p);rejected.setProperty(key,"different");assertFalse(NameEncodingHook.factoryMatches(baseline,rejected));
            rejected.remove(key);assertFalse(NameEncodingHook.factoryMatches(baseline,rejected));
        }
    }
    @Test void generateParentApprovedExactStartupProfileOnlyWhenExplicitlyRequested()throws Exception {
        String output=System.getProperty("atwboost.encodingFactoryEvidenceOutput");if(output==null)return;
        Path allowed=Paths.get("src/main/resources/name-encoding-factory-live.properties").toAbsolutePath().normalize();
        assertEquals(allowed,Paths.get(output).toAbsolutePath().normalize());Properties p=verified();
        try(OutputStream out=Files.newOutputStream(allowed)) {
            p.store(out,"Parent-approved exact observed startup: actual-loaded executable/header/fields + pinned packaged order corroborated by complete hook report. @actualLoadedShape is provenance only; no arbitrary permutation.");
        }
    }
    @Test void offlineComparisonRejectsIncompleteChangedHookMethodsAndShapeDigest()throws Exception {
        ClassNode c=packaged();Properties hook=read(HOOK);compareHook(c,hook);
        for(String key:new String[]{"shapeSha256","method.newHashMap()Ljava/util/HashMap;","method.<init>()V"}) {
            Properties changed=new Properties();changed.putAll(hook);changed.setProperty(key,"different");assertThrows(AssertionError.class,()->compareHook(c,changed));
            changed.remove(key);assertThrows(AssertionError.class,()->compareHook(c,changed));
        }
        Properties extra=new Properties();extra.putAll(hook);extra.setProperty("method.extra()V","different");assertThrows(AssertionError.class,()->compareHook(c,extra));
    }
}

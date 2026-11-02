package com.atw.renderboost.hook;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit offline generation of post-name hashes only; never generates factory approval. */
@Tag("private-name-codec-capture")
class NameEncodingEvidenceGenerationTest {
    @Test @Tag("private-name-codec-startup") void generateObservedStartupCompositionWhenExplicitlyRequested()throws Exception {
        String output=System.getProperty("atwboost.encodingEvidenceOutput");if(output==null)return;
        Path allowed=Paths.get("src/main/resources/name-encoding-composed.properties").toAbsolutePath().normalize();
        assertEquals(allowed,Paths.get(output).toAbsolutePath().normalize());
        ClassNode captured=NameParseCaptureTest.captures().stream().filter(c->c.name.equals(NameParseHook.CONVERTER)).findFirst().get();
        ClassNode startup=NameParseStartupProfileTest.startup(captured);assertTrue(NameParseHook.matches(startup));new NameParseHook().transform(startup,()->{});
        try(OutputStream out=Files.newOutputStream(allowed.resolveSibling("name-encoding-startup.properties"))){NameEncodingHook.profile(startup).store(out,"Offline observed startup converter AFTER unchanged NameParseHook; no bytecode or payloads");}
    }
    @Test void generateExactComposedEvidenceOnlyWhenExplicitlyRequested()throws Exception {
        String output=System.getProperty("atwboost.encodingEvidenceOutput");if(output==null)return;
        Path allowed=Paths.get("src/main/resources/name-encoding-composed.properties").toAbsolutePath().normalize();
        assertEquals(allowed,Paths.get(output).toAbsolutePath().normalize());
        ClassNode captured=NameParseCaptureTest.captures().stream().filter(c->c.name.equals(NameParseHook.CONVERTER)).findFirst().get();
        assertTrue(NameParseHook.matches(captured));new NameParseHook().transform(captured,()->{});
        Properties exact=NameEncodingHook.profile(captured);
        try(OutputStream out=Files.newOutputStream(allowed)){exact.store(out,"Offline exact captured converter AFTER unchanged NameParseHook; no bytecode or private payloads");}
    }
}

package com.atw.renderboost.hook;

import java.io.InputStream;
import java.util.*;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Pinned Weave 1.4.1 conflict-name proof; only detached diagnostic methods are normalized. */
final class NameParseHookStage {
    static final String PREFIX = "$weave_potentialConflict$";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String MARKER = "weave_potentialConflict";
    private static final Properties PROVEN = new Properties();
    static {
        try (InputStream in = NameParseHookStage.class.getResourceAsStream("/name-parse-hookstage.properties")) {
            if (in == null) throw new IllegalArgumentException("Missing conflict-name proof");
            PROVEN.load(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private final String owner;
    private final Map<String,MethodNode> actual = new HashMap<>(), logical = new HashMap<>();

    NameParseHookStage(ClassNode node) {
        owner=node.name;
        Boolean stage=null;
        String observedSession=null;
        for (MethodNode m:node.methods) {
            String name=logicalName(m.name);
            if (actual.put(m.name+m.desc,m)!=null)
                throw new IllegalArgumentException("Ambiguous conflict declaration");
            AnnotationNode a=merged(m);
            if (a==null && !name.equals(m.name)) throw new IllegalArgumentException("Unmarked conflict declaration");
            if (a!=null) {
                Map<String,Object> values=values(a);
                String id=(String)values.get("sessionId");
                if (!UUID.fromString(id).toString().equals(id) || (observedSession!=null && !observedSession.equals(id)))
                    throw new IllegalArgumentException("Invalid/mixed Mixin session");
                observedSession=id;
                boolean prefixed=!name.equals(m.name);
                if (stage!=null && stage!=prefixed) throw new IllegalArgumentException("Mixed conflict stage");
                stage=prefixed;
            }
        }
        for (MethodNode m:node.methods) {
            if (logical.put(canonicalName(m.name)+m.desc,m)!=null)
                throw new IllegalArgumentException("Ambiguous logical declaration");
        }
    }
    private String canonicalName(String name) {
        // No session-derived symbols are admitted in this separate codec evidence.
        return logicalName(name);
    }
    static String logicalName(String name) {
        if (!name.contains(MARKER)) return name;
        if (!name.startsWith(PREFIX)) throw new IllegalArgumentException("Malformed conflict prefix");
        String plain=name.substring(PREFIX.length());
        if (plain.isEmpty() || plain.contains(MARKER)) throw new IllegalArgumentException("Malformed conflict prefix");
        return plain;
    }
    private static AnnotationNode merged(MethodNode m) {
        AnnotationNode found=null;
        if (m.invisibleAnnotations!=null) for (AnnotationNode a:m.invisibleAnnotations)
            if (a.desc.endsWith("spongepowered/asm/mixin/transformer/meta/MixinMerged;"))
                throw new IllegalArgumentException("Invisible conflict annotation");
        if (m.visibleAnnotations!=null) for (AnnotationNode a:m.visibleAnnotations)
            if (a.desc.endsWith("spongepowered/asm/mixin/transformer/meta/MixinMerged;")) {
                // The pinned loader uses endsWith; only its exact observed descriptor is proved here.
                if (!MERGED.equals(a.desc) || found!=null) throw new IllegalArgumentException("Unknown/duplicate conflict annotation");
                found=a;
            }
        return found;
    }
    private static Map<String,Object> values(AnnotationNode a) {
        if (a.values==null || a.values.size()!=6) throw new IllegalArgumentException("Malformed conflict annotation");
        Map<String,Object> out=new HashMap<>();
        for (int i=0;i<a.values.size();i+=2) {
            if (!(a.values.get(i) instanceof String) || out.containsKey(a.values.get(i)))
                throw new IllegalArgumentException("Malformed conflict annotation");
            out.put((String)a.values.get(i),a.values.get(i+1));
        }
        if (!(out.get("mixin") instanceof String) || !(out.get("priority") instanceof Integer)
                || !(out.get("sessionId") instanceof String)) throw new IllegalArgumentException("Unknown conflict annotation values");
        return out;
    }
    private void prove(MethodNode m) {
        String expected=PROVEN.getProperty(owner+"."+canonicalName(m.name)+m.desc);
        AnnotationNode a=merged(m);
        String metadata=null;
        if (a!=null) {
            Map<String,Object> v=values(a);
            metadata=v.get("mixin")+":"+v.get("priority");
        }
        if (!Objects.equals(expected,metadata)) throw new IllegalArgumentException("Unproved conflict member metadata: " + owner + "." + logicalName(m.name) + m.desc);
    }
    private String reference(String targetOwner, String name, String desc) {
        String normal=logicalName(name);
        // InjectionHandler's SimpleRemapper map contains ONLY this class's declarations.
        // Other classes' member names (including handles) must retain their exact operands.
        if (!owner.equals(targetOwner)) {
            if (!normal.equals(name)) throw new IllegalArgumentException("Foreign conflict reference");
            return name;
        }
        normal=canonicalName(name);
        MethodNode declaration=actual.get(name+desc), normalized=logical.get(normal+desc);
        if (declaration==null) {
            if (normalized!=null || !normal.equals(name)) throw new IllegalArgumentException("Unresolved conflict reference");
            return name; // inherited/unknown unprefixed operands still face the full executable hash
        }
        prove(declaration);
        return normal;
    }
    MethodNode comparison(MethodNode m) {
        if (actual.get(m.name+m.desc)!=m) throw new IllegalArgumentException("Detached/unknown declaration");
        prove(m);
        MethodNode copy=new MethodNode(m.access,canonicalName(m.name),m.desc,m.signature,m.exceptions.toArray(new String[0]));
        m.accept(new MethodRemapper(copy,new Remapper() {
            @Override public String mapMethodName(String targetOwner,String name,String desc) {
                return reference(targetOwner,name,desc);
            }
            @Override public String mapFieldName(String targetOwner,String name,String desc) {
                if (name.contains(MARKER)) throw new IllegalArgumentException("Field conflict prefix is not loader-owned");
                return name;
            }
            @Override public String mapInvokeDynamicMethodName(String name,String desc) {
                if (name.contains(MARKER)) throw new IllegalArgumentException("Dynamic conflict name is not loader-owned");
                return name;
            }
        }));
        return copy;
    }
}

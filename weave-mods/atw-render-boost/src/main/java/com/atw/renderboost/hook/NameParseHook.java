package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameParseRuntime;
import java.io.InputStream;
import java.util.Properties;
import java.util.TreeSet;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Exact independent codec dependency admission and one parse-only callsite. */
public final class NameParseHook extends Hook implements Opcodes {
    public static final String CONVERTER="com/moonsworth/lunar/OCCOIRIHCIHOCIOCICROCRHRHRIHCO/HHCCHRHORCRIHRIIHRIICIHHCOOCHR/CIHOOIHOIROIIRCORCCHIOIIOCICCH/CCIRCHROCOHOCHICRORRCIRCRRIOHR";
    public static final String CODEC_FIELD="CCHCOOOICRICHCOHOHOCHCRIHCHIRC";
    public static final String MARKER="$atwNameParseBodyProof1";
    public static final int MARKER_VALUE=0x41545731;
    public static final String[] TARGETS={CONVERTER,"net/kyori/adventure/util/Codec","net/kyori/adventure/util/Codec$1",
            "net/minecraft/nbt/JsonToNBT","net/minecraft/nbt/JsonToNBT$Any","net/minecraft/nbt/JsonToNBT$Compound",
            "net/minecraft/nbt/JsonToNBT$Primitive","net/minecraft/nbt/NBTBase","net/minecraft/nbt/NBTTagCompound","net/minecraft/nbt/NBTTagString"};
    static final String DESC="(Lnet/kyori/adventure/text/Component;Lnet/kyori/adventure/util/Codec$Decoder;)Lnet/kyori/adventure/text/event/HoverEvent$ShowEntity;";
    static final String RUNTIME="com/atw/renderboost/cache/NameParseRuntime";
    private static final Properties EVIDENCE=new Properties();
    private static final Properties STARTUP_ORDER=new Properties();
    static {
        try(InputStream in=NameParseHook.class.getResourceAsStream("/name-parse-evidence.properties")) {
            if(in!=null)EVIDENCE.load(in);
        }catch(Exception ignored){/* No evidence means no admission. */}
        try(InputStream in=NameParseHook.class.getResourceAsStream("/name-parse-startup-order.properties")) {
            if(in!=null)STARTUP_ORDER.load(in);
        }catch(Exception ignored){/* No startup profile means captured profile only. */}
    }
    public NameParseHook(){super(TARGETS);}
    /** Metadata-only diagnostic. Never changes comparisons, evidence or the supplied node. */
    static Properties mismatchReport(ClassNode node) {
        Properties p=new Properties();p.setProperty("owner",node.name);
        p.setProperty("stage","NameParseHook input before candidate mutation");
        String expected=EVIDENCE.getProperty(node.name+".@shape");
        p.setProperty("expectedShapeSha256",NameParseHookEvidenceOutput.digest(expected));
        p.setProperty("actualFieldCount",String.valueOf(node.fields.size()));
        for(int i=0;i<node.fields.size();i++) {
            FieldNode f=node.fields.get(i);String prefix="field."+i+".";
            p.setProperty(prefix+"name",f.name);p.setProperty(prefix+"descriptor",f.desc);
            p.setProperty(prefix+"access",String.valueOf(f.access));
            p.setProperty(prefix+"signature",String.valueOf(f.signature));
            p.setProperty(prefix+"constantType",f.value==null?"null":f.value.getClass().getName());
            p.setProperty(prefix+"constantSha256",NameParseHookEvidenceOutput.digest(String.valueOf(f.value)));
        }
        try {
            NameParseHookStage stage=new NameParseHookStage(node);
            ClassNode comparison=new ClassNode();comparison.version=node.version;comparison.access=node.access;
            comparison.name=node.name;comparison.superName=node.superName;comparison.interfaces=node.interfaces;comparison.fields=node.fields;
            for(MethodNode m:node.methods)comparison.methods.add(stage.comparison(m));
            String shape=fields(comparison);
            p.setProperty("provenance","proved");p.setProperty("shapeMatch",String.valueOf(shape.equals(expected)));
            p.setProperty("actualShapeSha256",NameParseHookEvidenceOutput.digest(shape));
            p.setProperty("actualBasicShape",TerrainEvidence.classShape(comparison));
            if(expected!=null) {
                String[] parts=expected.split("\\|",-1);int end=parts.length-node.fields.size();
                if(end>0) {
                    StringBuilder base=new StringBuilder(parts[0]);for(int i=1;i<end;i++)base.append('|').append(parts[i]);
                    p.setProperty("expectedBasicShape",base.toString());
                    p.setProperty("basicShapeMatch",String.valueOf(base.toString().equals(TerrainEvidence.classShape(comparison))));
                    for(int i=0;i<node.fields.size();i++) {
                        String metadata=parts[end+i];FieldNode f=node.fields.get(i);
                        p.setProperty("field."+i+".metadataMatch",String.valueOf(metadata.equals(f.signature+":"+f.value)));
                        int colon=metadata.indexOf(':');
                        if(colon>=0) {
                            p.setProperty("field."+i+".expectedSignature",metadata.substring(0,colon));
                            p.setProperty("field."+i+".expectedConstantSha256",NameParseHookEvidenceOutput.digest(metadata.substring(colon+1)));
                        }
                    }
                }
            }
            int changed=0,missing=0;
            for(MethodNode m:comparison.methods) {
                String signature=m.name+m.desc,key=node.name+"."+signature;
                String wanted=EVIDENCE.getProperty(key),actual=TerrainEvidence.fingerprint(m);
                if(!actual.equals(wanted)) {
                    changed++;p.setProperty("method."+signature+".expected",wanted==null?"missing":wanted);
                    p.setProperty("method."+signature+".actual",actual);
                }
            }
            for(String key:new TreeSet<>(EVIDENCE.stringPropertyNames()))if(key.startsWith(node.name+".") && !key.endsWith(".@shape")) {
                String signature=key.substring(node.name.length()+1);
                if(comparison.methods.stream().noneMatch(m->signature.equals(m.name+m.desc)))missing++;
            }
            p.setProperty("changedMethods",String.valueOf(changed));p.setProperty("missingMethods",String.valueOf(missing));
        }catch(Throwable e) {
            p.setProperty("provenance","rejected");p.setProperty("proofFailureType",e.getClass().getSimpleName());
            if(e instanceof IllegalArgumentException)p.setProperty("proofFailureMetadata",String.valueOf(e.getMessage()));
        }
        return p;
    }
    static String fields(ClassNode c) {
        StringBuilder s=new StringBuilder(TerrainEvidence.classShape(c));
        for(FieldNode f:c.fields)s.append('|').append(f.signature).append(':').append(f.value);
        return s.toString();
    }
    public static boolean matches(ClassNode node) {
        try {
            NameParseHookStage stage=new NameParseHookStage(node);
            ClassNode comparison=new ClassNode(); comparison.version=node.version;comparison.access=node.access;
            comparison.name=node.name;comparison.superName=node.superName;comparison.interfaces=node.interfaces;comparison.fields=node.fields;
            for(MethodNode m:node.methods)comparison.methods.add(stage.comparison(m));
            String shape=fields(comparison);
            boolean captured=shape.equals(EVIDENCE.getProperty(node.name+".@shape"));
            boolean startup=shape.equals(STARTUP_ORDER.getProperty(node.name+".@shape"));
            if(!captured && !startup)return false;
            for(MethodNode m:comparison.methods) {
                // Actual startup has only this ignored PUBLIC bit on these three
                // initializers. Exact startup order/access/fields must match first;
                // comparison copies alone then face the unchanged executable hashes.
                if(!captured && startup && startupPublicInitializer(node.name,m))m.access=ACC_STATIC;
                if(!TerrainEvidence.fingerprint(m).equals(EVIDENCE.getProperty(node.name+"."+m.name+m.desc)))return false;
            }
            return !comparison.methods.isEmpty();
        }catch(Throwable ignored){return false;}
    }
    private static boolean startupPublicInitializer(String owner,MethodNode m) {
        return (owner.equals("net/minecraft/nbt/NBTBase") || owner.equals("net/minecraft/nbt/JsonToNBT") || owner.equals("net/minecraft/nbt/JsonToNBT$Primitive"))
                && m.name.equals("<clinit>") && m.desc.equals("()V") && m.access==(ACC_STATIC|ACC_PUBLIC);
    }
    @Override public void transform(ClassNode node,AssemblerConfig cfg) {
        NameParseHookEvidenceOutput.record(node);
        boolean accepted=matches(node),installed=false;
        if(accepted && CONVERTER.equals(node.name)) {
            for(MethodNode m:node.methods)
                if(NameParseHookStage.logicalName(m.name).equals("deserializeShowEntity") && m.desc.equals(DESC)) {
                    inject(m);cfg.computeFrames();installed=true;
                }
        }
        if(accepted)node.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,MARKER,"I",null,MARKER_VALUE));
        NameParseRuntime.evidence(node.name,accepted,installed);
        System.out.println("[ATW name parse] "+node.name+": "+(accepted?"proved" : "UNAVAILABLE; original path retained")+(installed?"; callsite installed":""));
    }
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(INVOKESTATIC,RUNTIME,name,desc,false);}
    /** All original nodes, including decode/checkcast/store and existing handler order, remain. */
    static void inject(MethodNode m) {
        MethodInsnNode decode=null; AbstractInsnNode store=null;
        for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode) {
            MethodInsnNode c=(MethodInsnNode)i;
            if(c.getOpcode()==INVOKEINTERFACE && c.owner.equals("net/kyori/adventure/util/Codec") && c.name.equals("decode") && c.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")) {
                if(decode!=null)throw new IllegalArgumentException("Ambiguous codec seam"); decode=c;
            }
        }
        if(decode==null)throw new IllegalArgumentException("Missing codec seam");
        for(AbstractInsnNode i=decode.getNext();i!=null;i=i.getNext())if(i.getOpcode()>=0) {
            if(i instanceof VarInsnNode && i.getOpcode()==ASTORE && ((VarInsnNode)i).var==4){store=i;break;}
            if(i.getOpcode()!=CHECKCAST)throw new IllegalArgumentException("Changed original compound store");
        }
        if(store==null)throw new IllegalArgumentException("Missing compound store");
        int codec=Math.max(5,m.maxLocals),hit=codec+1;
        m.maxLocals=hit+1;m.maxStack+=4;
        LabelNode original=new LabelNode(),miss=new LabelNode(),after=new LabelNode(),skip=new LabelNode();
        InsnList before=new InsnList();
        before.add(new InsnNode(ACONST_NULL));before.add(new VarInsnNode(ASTORE,codec));
        before.add(new FieldInsnNode(GETSTATIC,RUNTIME,"requested","Z"));before.add(new JumpInsnNode(IFEQ,original));
        before.add(new InsnNode(DUP2));before.add(new InsnNode(POP));before.add(new VarInsnNode(ASTORE,codec));
        before.add(new VarInsnNode(ALOAD,codec));before.add(new VarInsnNode(ALOAD,3));
        before.add(call("lookup","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"));
        before.add(new InsnNode(DUP));before.add(new JumpInsnNode(IFNULL,miss));
        before.add(new VarInsnNode(ASTORE,hit));before.add(new InsnNode(POP2));before.add(new VarInsnNode(ALOAD,hit));
        // No record on hit; leave the original cast/store/consumer sequence untouched.
        before.add(new InsnNode(ACONST_NULL));before.add(new VarInsnNode(ASTORE,codec));before.add(new JumpInsnNode(GOTO,after));
        before.add(miss);before.add(new InsnNode(POP));before.add(original);
        m.instructions.insertBefore(decode,before);m.instructions.insert(decode,after);
        InsnList record=new InsnList();record.add(new VarInsnNode(ALOAD,codec));record.add(new JumpInsnNode(IFNULL,skip));
        record.add(new VarInsnNode(ALOAD,codec));record.add(new VarInsnNode(ALOAD,3));record.add(new VarInsnNode(ALOAD,4));
        record.add(call("record","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V"));record.add(skip);
        m.instructions.insert(store,record);
    }
}

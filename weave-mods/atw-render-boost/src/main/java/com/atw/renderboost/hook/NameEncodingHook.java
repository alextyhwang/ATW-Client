package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameEncodingRuntime;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Runs AFTER unchanged NameParseHook. Exact composed converter, independent factory gate. */
public final class NameEncodingHook extends Hook implements Opcodes {
    public static final String MAPS="com/google/common/collect/Maps";
    public static final String MARKER="$atwNameEncodingBodyProof1";
    public static final int MARKER_VALUE=0x41545745;
    public static final String[] TARGETS=Arrays.copyOf(NameParseHook.TARGETS,11);
    static {TARGETS[10]=MAPS;}
    static final String DESC="(Lnet/kyori/adventure/text/event/HoverEvent$ShowEntity;Lnet/kyori/adventure/util/Codec$Encoder;)Lnet/kyori/adventure/text/Component;";
    static final String RUNTIME="com/atw/renderboost/cache/NameEncodingRuntime",TICKET="L"+RUNTIME+"$Ticket;";
    private static final Properties COMPOSED=load("/name-encoding-composed.properties");
    private static final Properties STARTUP=load("/name-encoding-startup.properties");
    private static final Properties FACTORY=load("/name-encoding-factory-live.properties");
    private static Properties load(String resource){Properties p=new Properties();try(InputStream in=NameEncodingHook.class.getResourceAsStream(resource)){if(in!=null)p.load(in);}catch(Exception ignored){}return p;}
    public NameEncodingHook(){super(TARGETS);}
    static Properties profile(ClassNode node) {
        NameParseHookStage stage=new NameParseHookStage(node);ClassNode c=new ClassNode();node.accept(c);c.methods.clear();
        for(MethodNode m:node.methods)c.methods.add(stage.comparison(m));
        Properties result=new Properties();result.setProperty("@shape",NameParseHook.fields(c));
        for(MethodNode m:c.methods)result.setProperty(m.name+m.desc,TerrainEvidence.fingerprint(m));return result;
    }
    static boolean exact(ClassNode node,Properties evidence) {
        if(!NameParseHook.fields(node).equals(evidence.getProperty("@shape")))return false;
        for(MethodNode m:node.methods)if(!TerrainEvidence.fingerprint(m).equals(evidence.getProperty(m.name+m.desc)))return false;
        return !node.methods.isEmpty()&&evidence.size()==node.methods.size()+1;
    }
    static final String FACTORY_STARTUP_KIND="actual-loaded-exact-startup";
    static final String FACTORY_LOADED_SHAPE_SHA="1bd01e1dc828c8ffed8c2bb34e670ee91ba0fae02312493a1d7ba18e219b4e1e";
    static final String FACTORY_STARTUP_SHAPE_SHA="7108c01762dd4572f88db7c695dcc4a868ba7f735073932217c42228d03c2e59";
    private static boolean factoryEvidenceApproved(Properties evidence) {
        if(!"true".equals(evidence.getProperty("parentApproved")))return false;
        return FACTORY_STARTUP_KIND.equals(evidence.getProperty("captureKind"))
                &&FACTORY_LOADED_SHAPE_SHA.equals(NameParseHookEvidenceOutput.digest(evidence.getProperty("@actualLoadedShape")))
                &&FACTORY_STARTUP_SHAPE_SHA.equals(NameParseHookEvidenceOutput.digest(evidence.getProperty("@shape")));
    }
    public static boolean factoryApproved(){return factoryEvidenceApproved(FACTORY);}
    static boolean factoryMatches(ClassNode node,Properties live) {
        if(!factoryEvidenceApproved(live)||!factorySemantics(node))return false;
        // Only this pinned startup layout is executable admission. The other full shape is provenance.
        Properties executable=new Properties();for(String key:live.stringPropertyNames())if(!key.equals("captureKind")&&!key.equals("parentApproved")
                &&!(FACTORY_STARTUP_KIND.equals(live.getProperty("captureKind"))&&key.equals("@actualLoadedShape")))executable.setProperty(key,live.getProperty(key));
        return exact(node,executable);
    }
    /** Structural factory semantics are mandatory even after parent approval of live bytes. */
    static boolean factorySemantics(ClassNode node) {
        if(!MAPS.equals(node.name))return false;
        MethodNode expected=new MethodNode(ACC_PUBLIC|ACC_STATIC,"newHashMap","()Ljava/util/HashMap;",null,null);
        expected.instructions.add(new TypeInsnNode(NEW,"java/util/HashMap"));expected.instructions.add(new InsnNode(DUP));
        expected.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/util/HashMap","<init>","()V",false));expected.instructions.add(new InsnNode(ARETURN));
        String hash=TerrainEvidence.fingerprint(expected);int count=0;
        for(MethodNode method:node.methods)if(method.name.equals(expected.name)&&method.desc.equals(expected.desc)) {
            if(!hash.equals(TerrainEvidence.fingerprint(method)))return false;count++;
        }return count==1;
    }
    public static boolean matches(ClassNode node) {
        try {
            if(MAPS.equals(node.name)) {
                return factoryMatches(node,FACTORY);
            }
            if(!Arrays.asList(NameParseHook.TARGETS).contains(node.name))return false;
            if(node.fields.stream().anyMatch(f->f.name.startsWith(MARKER)))return false;
            FieldNode marker=null;
            for(FieldNode f:node.fields)if(f.name.equals(NameParseHook.MARKER)){if(marker!=null)return false;marker=f;}
            if(marker==null||marker.access!=(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC)||!marker.desc.equals("I")
                    ||marker.signature!=null||!Integer.valueOf(NameParseHook.MARKER_VALUE).equals(marker.value))return false;
            if(NameParseHook.CONVERTER.equals(node.name)){Properties actual=profile(node);return actual.equals(COMPOSED)||actual.equals(STARTUP);}
            // Only the exact unchanged name marker is removed on a detached dependency copy.
            ClassNode copy=new ClassNode();node.accept(copy);copy.fields.removeIf(f->f.name.equals(NameParseHook.MARKER));
            return NameParseHook.matches(copy);
        }catch(Throwable error){NameEncodingRuntime.propagateFatal(error);return false;}
    }
    @Override public void transform(ClassNode node,AssemblerConfig cfg) {
        NameEncodingEvidenceOutput.record(node);
        boolean accepted=matches(node),installed=false;
        if(accepted&&NameParseHook.CONVERTER.equals(node.name)) {
            MethodNode method=node.methods.stream().filter(m->NameParseHookStage.logicalName(m.name).equals("serializeShowEntity")&&m.desc.equals(DESC)).findFirst().get();
            inject(method);cfg.computeFrames();installed=true;
        }
        String marker=accepted?MARKER+"$"+java.util.UUID.randomUUID().toString().replace("-",""):null;
        if(accepted)node.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,MARKER_VALUE));
        NameEncodingRuntime.evidence(node.name,accepted,installed,marker);
        System.out.println("[ATW name encoding] "+node.name+": "+(accepted?"proved":"UNAVAILABLE; original encode retained")+(installed?"; callsite installed":""));
    }
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(INVOKESTATIC,RUNTIME,name,desc,false);}
    /** Preserve every original node, cast, callback and normal return. No new handlers. */
    static void inject(MethodNode m) {
        MethodInsnNode encode=null;java.util.List<MethodInsnNode> writes=new java.util.ArrayList<>();AbstractInsnNode cast=null;
        for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;
            if(c.owner.equals("net/minecraft/nbt/NBTTagCompound")&&c.name.equals("setString")&&c.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V"))writes.add(c);
            if(c.getOpcode()==INVOKEINTERFACE&&c.owner.equals("net/kyori/adventure/util/Codec")&&c.name.equals("encode")&&c.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")){if(encode!=null)throw new IllegalArgumentException("Ambiguous encode");encode=c;}}
        if(encode==null||writes.size()!=3||!m.tryCatchBlocks.isEmpty())throw new IllegalArgumentException("Changed encode seam");
        for(AbstractInsnNode n=encode.getNext();n!=null;n=n.getNext())if(n.getOpcode()>=0){cast=n;break;}
        if(!(cast instanceof TypeInsnNode)||cast.getOpcode()!=CHECKCAST||!((TypeInsnNode)cast).desc.equals("java/lang/String"))throw new IllegalArgumentException("Changed original cast");
        int id=Math.max(4,m.maxLocals),type=id+1,name=id+2,present=id+3,codec=id+4,compound=id+5,ticket=id+6,output=id+7;
        m.maxLocals=output+1;m.maxStack+=8;
        InsnList init=new InsnList();init.add(new InsnNode(ACONST_NULL));init.add(new VarInsnNode(ASTORE,name));init.add(new InsnNode(ICONST_0));init.add(new VarInsnNode(ISTORE,present));m.instructions.insert(init);
        int[] locals={id,type,name};for(int i=0;i<3;i++){InsnList capture=new InsnList();capture.add(new InsnNode(DUP));capture.add(new VarInsnNode(ASTORE,locals[i]));
            if(i==2){capture.add(new InsnNode(ICONST_1));capture.add(new VarInsnNode(ISTORE,present));}m.instructions.insertBefore(writes.get(i),capture);}
        LabelNode original=new LabelNode(),miss=new LabelNode(),after=new LabelNode();InsnList before=new InsnList();
        before.add(new InsnNode(ACONST_NULL));before.add(new VarInsnNode(ASTORE,ticket));
        before.add(new FieldInsnNode(GETSTATIC,RUNTIME,"requested","Z"));before.add(new JumpInsnNode(IFEQ,original));
        before.add(new VarInsnNode(ASTORE,compound));before.add(new VarInsnNode(ASTORE,codec));
        before.add(new VarInsnNode(ALOAD,codec));before.add(new VarInsnNode(ALOAD,id));before.add(new VarInsnNode(ALOAD,type));before.add(new VarInsnNode(ILOAD,present));before.add(new VarInsnNode(ALOAD,name));
        before.add(call("begin","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)"+TICKET));before.add(new VarInsnNode(ASTORE,ticket));
        before.add(new VarInsnNode(ALOAD,ticket));before.add(call("hit","("+TICKET+")Ljava/lang/String;"));before.add(new InsnNode(DUP));before.add(new JumpInsnNode(IFNULL,miss));before.add(new JumpInsnNode(GOTO,after));
        before.add(miss);before.add(new InsnNode(POP));before.add(new VarInsnNode(ALOAD,codec));before.add(new VarInsnNode(ALOAD,compound));before.add(original);
        m.instructions.insertBefore(encode,before);m.instructions.insert(encode,after);
        InsnList staged=new InsnList();staged.add(new InsnNode(DUP));staged.add(new VarInsnNode(ASTORE,output));m.instructions.insert(cast,staged);
        for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==ARETURN){InsnList commit=new InsnList();commit.add(new VarInsnNode(ALOAD,ticket));commit.add(new VarInsnNode(ALOAD,output));commit.add(call("commit","("+TICKET+"Ljava/lang/String;)V"));m.instructions.insertBefore(n,commit);}
    }
}

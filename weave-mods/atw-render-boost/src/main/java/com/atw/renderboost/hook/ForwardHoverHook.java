package com.atw.renderboost.hook;

import com.atw.renderboost.cache.ForwardHoverRuntime;
import com.atw.renderboost.cache.NameEncodingRuntime;
import java.util.*;
import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Dependencies observe the unchanged names/reverse pipeline. Entity is owned ONLY by NameProbeHook. */
public final class ForwardHoverHook extends Hook implements Opcodes {
    public static final String ENTITY="net/minecraft/entity/Entity",MAPS=NameEncodingHook.MAPS;
    public static final String[] TARGETS={ENTITY,"net/minecraft/nbt/NBTBase","net/minecraft/nbt/NBTTagCompound","net/minecraft/nbt/NBTTagString",MAPS};
    public static final String MARKER="$atwForwardHoverProof1";
    static final String RUNTIME="com/atw/renderboost/cache/ForwardHoverRuntime",TICKET="L"+RUNTIME+"$Ticket;";
    public ForwardHoverHook(){super(Arrays.copyOfRange(TARGETS,1,TARGETS.length));}
    static String dependencyMarker(ClassNode node){
        FieldNode found=null;
        for(FieldNode f:node.fields)if(f.name.startsWith(NameEncodingHook.MARKER)){
            if(found!=null||!f.name.matches(java.util.regex.Pattern.quote(NameEncodingHook.MARKER)+"\\$[0-9a-f]{32}")
                ||f.access!=(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC)||!f.desc.equals("I")||f.signature!=null
                ||!Integer.valueOf(NameEncodingHook.MARKER_VALUE).equals(f.value))return null;found=f;
        }
        if(found==null)return null;
        ClassNode copy=new ClassNode();node.accept(copy);String name=found.name;copy.fields.removeIf(f->f.name.equals(name));
        // Reuse the COMPLETE corroborated exact startup factory and NBT profiles; no jar-only gate.
        return NameEncodingHook.matches(copy)?name:null;
    }
    @Override public void transform(ClassNode node,AssemblerConfig cfg){
        String marker=null;
        try{marker=dependencyMarker(node);}catch(Throwable error){NameEncodingRuntime.propagateFatal(error);}
        ForwardHoverRuntime.evidence(node.name,marker!=null,marker);
        System.out.println("[ATW forward hover] "+node.name+": "+(marker==null?"UNAVAILABLE; original stringify retained":"exact dependency observed"));
    }
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(INVOKESTATIC,RUNTIME,name,desc,false);}
    static int nextLocal(MethodNode m){
        int result=(m.access&ACC_STATIC)==0?1:0;for(Type t:Type.getArgumentTypes(m.desc))result+=t.getSize();result=Math.max(result,m.maxLocals);
        for(AbstractInsnNode n:m.instructions){if(n instanceof VarInsnNode){int op=n.getOpcode();result=Math.max(result,((VarInsnNode)n).var+((op==LLOAD||op==DLOAD||op==LSTORE||op==DSTORE)?2:1));}
            else if(n instanceof IincInsnNode)result=Math.max(result,((IincInsnNode)n).var+1);}
        if(m.localVariables!=null)for(LocalVariableNode v:m.localVariables)result=Math.max(result,v.index+Type.getType(v.desc).getSize());
        return result;
    }
    /** Called after pristine comparison admission, before wrapping. Preparation validates a detached copy. */
    static boolean compose(ClassNode owner,MethodNode original,MethodNode comparison){
        if(!ENTITY.equals(owner.name)||!NameProbeHook.matches(owner.name,comparison)||!comparison.name.equals("getHoverEvent")
            ||owner.fields.stream().anyMatch(f->f.name.startsWith(MARKER)))return false;
        try{
            MethodNode staged=new MethodNode(original.access,original.name,original.desc,original.signature,original.exceptions.toArray(new String[0]));original.accept(staged);
            int[] plan=inject(staged);NameProbeHook.wrap(staged,3);finish(staged,plan);
        }catch(Throwable error){NameEncodingRuntime.propagateFatal(error);ForwardHoverRuntime.evidence(ENTITY,false,null);return false;}
        // A mutation failure must propagate to the transformer, never fall into a second wrapper.
        int[] plan=inject(original);NameProbeHook.wrap(original,3);finish(original,plan);
        String marker=MARKER+"$"+UUID.randomUUID().toString().replace("-","");
        owner.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,0x41545746));
        ForwardHoverRuntime.evidence(ENTITY,true,marker);return true;
    }
    /** Complete body admission precedes this structural seam validation. No new catch blocks. */
    static int[] inject(MethodNode m){
        List<MethodInsnNode> writes=new ArrayList<>();MethodInsnNode stringify=null;AbstractInsnNode ret=null;
        for(AbstractInsnNode n:m.instructions){if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;
            if(c.owner.equals(TARGETS[2])&&c.name.equals("setString")&&c.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V"))writes.add(c);
            if(c.owner.equals(TARGETS[2])&&c.name.equals("toString")&&c.desc.equals("()Ljava/lang/String;")){if(stringify!=null)throw new IllegalArgumentException("Duplicate stringify");stringify=c;}}
            if(n.getOpcode()==ARETURN){if(ret!=null)throw new IllegalArgumentException("Duplicate return");ret=n;}}
        if(writes.size()!=3||stringify==null||ret==null||!m.tryCatchBlocks.isEmpty())throw new IllegalArgumentException("Unsupported forward seam");
        int id=nextLocal(m),name=id+1,ticket=id+2,prepared=id+3,output=id+4; m.maxLocals=output+1;m.maxStack+=8;
        int[] captured={id,-1,name};for(int i=0;i<3;i++)if(captured[i]>=0){InsnList capture=new InsnList();capture.add(new InsnNode(DUP));capture.add(new VarInsnNode(ASTORE,captured[i]));m.instructions.insertBefore(writes.get(i),capture);}
        InsnList prepare=new InsnList();prepare.add(new InsnNode(ACONST_NULL));prepare.add(new VarInsnNode(ASTORE,ticket));
        prepare.add(new InsnNode(ACONST_NULL));prepare.add(new VarInsnNode(ASTORE,prepared));LabelNode skip=new LabelNode();
        prepare.add(new FieldInsnNode(GETSTATIC,RUNTIME,"requested","Z"));prepare.add(new JumpInsnNode(IFEQ,skip));
        prepare.add(new LdcInsnNode(Type.getObjectType(ENTITY)));prepare.add(new VarInsnNode(ALOAD,1));prepare.add(new VarInsnNode(ALOAD,id));
        prepare.add(new VarInsnNode(ALOAD,2));prepare.add(new VarInsnNode(ALOAD,name));
        prepare.add(call("begin","(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)"+TICKET));prepare.add(new VarInsnNode(ASTORE,ticket));
        prepare.add(new VarInsnNode(ALOAD,ticket));prepare.add(call("hit","("+TICKET+")Ljava/lang/String;"));prepare.add(new VarInsnNode(ASTORE,prepared));prepare.add(skip);
        m.instructions.insert(writes.get(2),prepare);
        LabelNode miss=new LabelNode(),original=new LabelNode(),after=new LabelNode();InsnList seam=new InsnList();
        seam.add(new FieldInsnNode(GETSTATIC,RUNTIME,"requested","Z"));seam.add(new JumpInsnNode(IFEQ,original));
        seam.add(new VarInsnNode(ALOAD,ticket));seam.add(new VarInsnNode(ALOAD,prepared));seam.add(call("accept","("+TICKET+"Ljava/lang/String;)Ljava/lang/String;"));
        seam.add(new InsnNode(DUP));seam.add(new JumpInsnNode(IFNULL,miss));seam.add(new InsnNode(SWAP));seam.add(new InsnNode(POP));seam.add(new JumpInsnNode(GOTO,after));
        seam.add(miss);seam.add(new InsnNode(POP));seam.add(original);m.instructions.insertBefore(stringify,seam);m.instructions.insert(stringify,after);
        InsnList record=new InsnList();record.add(new InsnNode(DUP));record.add(new VarInsnNode(ASTORE,output));m.instructions.insert(after,record);
        return new int[]{ticket,output};
    }
    /** The exact single-return producer lets the probe region end after normal cleanup.
     * Commit remains outside that region; every original node/label stays in order. */
    static void finish(MethodNode m,int[] locals){
        AbstractInsnNode ret=null;for(AbstractInsnNode n:m.instructions)if(n.getOpcode()==ARETURN){if(ret!=null)throw new IllegalArgumentException("Duplicate return");ret=n;}
        if(ret==null||m.tryCatchBlocks.size()!=1)throw new IllegalArgumentException("Unsupported probe composition");
        LabelNode end=new LabelNode(),skip=new LabelNode();InsnList commit=new InsnList();commit.add(end);
        commit.add(new VarInsnNode(ALOAD,locals[0]));commit.add(new JumpInsnNode(IFNULL,skip));
        commit.add(new VarInsnNode(ALOAD,locals[0]));commit.add(new VarInsnNode(ALOAD,locals[1]));commit.add(call("commit","("+TICKET+"Ljava/lang/String;)V"));
        commit.add(skip);
        m.instructions.insertBefore(ret,commit);m.tryCatchBlocks.get(0).end=end;
    }
}

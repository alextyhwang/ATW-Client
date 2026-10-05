package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameParseCache;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

class NameParseHookTest implements Opcodes {
    public interface Component {
        static Component text(String s){Effects.constructions++;if(Effects.constructFailure!=null)throw Effects.constructFailure;return new Text(s);}
    }
    public static final class Text implements Component {public final String value;public Text(String value){this.value=value;}}
    public interface Serializer {
        static Serializer plainText(){return c->{Effects.serializations++;return ((Text)c).value;};}
        String serialize(Component component);
    }
    public interface Decoder {}
    public interface Codec {Object decode(Object key)throws NbtException;}
    public interface Key {
        static Key key(String value){Effects.keys++;if(value.equals("bad key"))throw new IllegalArgumentException("invalid key");return new TestKey(value);}
    }
    public static final class TestKey implements Key {final String value;TestKey(String value){this.value=value;}}
    public static final class Result {
        final Key key;final UUID id;final Component name;
        Result(Key k,UUID i,Component n){key=k;id=i;name=n;}
        public static Result of(Key key,UUID id,Component name){Effects.results++;return new Result(key,id,name);}
    }
    public static final class NbtException extends Exception {public NbtException(String s){super(s);}}
    public static final class Tag {final String value;Tag(String s){value=s;}}
    public static class Compound {
        final Map<String,Tag> values=new HashMap<>();
        public void setString(String k,String v){values.put(k,new Tag(v));}
        public String getString(String key){if(Effects.readFailure!=null)throw Effects.readFailure;Tag t=values.get(key);return t==null?"":t.value;}
    }
    public static final class Effects {
        static int serializations,decodes,keys,constructions,results;static Throwable decodeFailure;static RuntimeException constructFailure,readFailure;
        static Compound original;static Object passed;
        static void reset(){serializations=decodes=keys=constructions=results=0;decodeFailure=null;constructFailure=readFailure=null;passed=null;}
        public static Object decode(Object value)throws NbtException {
            decodes++;passed=value;
            if(decodeFailure instanceof NbtException)throw (NbtException)decodeFailure;
            if(decodeFailure instanceof RuntimeException)throw (RuntimeException)decodeFailure;
            return original;
        }
    }
    public static final class Bridge {
        public static boolean requested;static Codec expected;static NameParseCache cache;
        static Compound fresh;
        static final NameParseCache.Access access=new NameParseCache.Access() {
            public String read(Object c,String key){return ((Compound)c).getString(key);}
            public Object fresh(int bits,String name,String id,String type){
                Compound c=new Compound();c.setString("name",name);c.setString("id",id);if((bits&4)!=0)c.setString("type",type);fresh=c;return c;
            }
        };
        public static Object lookup(Object codec,String s){return codec==expected?cache.lookup(s,access):null;}
        public static void record(Object codec,String s,Object value){if(codec==expected)cache.record(s,value,access);}
    }
    static Map<String,String> mapping(String owner) {
        Map<String,String> map=new HashMap<>();map.put(NameParseHook.CONVERTER,owner);
        map.put("net/kyori/adventure/text/Component",Type.getInternalName(Component.class));
        map.put("net/kyori/adventure/text/TextComponent",Type.getInternalName(Component.class));
        map.put("net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer",Type.getInternalName(Serializer.class));
        map.put("net/kyori/adventure/util/Codec",Type.getInternalName(Codec.class));
        map.put("net/kyori/adventure/util/Codec$Decoder",Type.getInternalName(Decoder.class));
        map.put("net/kyori/adventure/key/Key",Type.getInternalName(Key.class));
        map.put("net/kyori/adventure/text/event/HoverEvent$ShowEntity",Type.getInternalName(Result.class));
        map.put("net/minecraft/nbt/NBTTagCompound",Type.getInternalName(Compound.class));
        map.put("net/minecraft/nbt/NBTException",Type.getInternalName(NbtException.class));
        map.put(NameParseHook.RUNTIME,Type.getInternalName(Bridge.class));return map;
    }
    static MethodNode body() {
        MethodNode m=new MethodNode(ACC_PUBLIC,"deserializeShowEntity",NameParseHook.DESC,null,null);
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,"net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer","plainText","()Lnet/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer;",true));
        m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new MethodInsnNode(INVOKEINTERFACE,"net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer","serialize","(Lnet/kyori/adventure/text/Component;)Ljava/lang/String;",true));m.instructions.add(new VarInsnNode(ASTORE,3));
        m.instructions.add(start);m.instructions.add(new FieldInsnNode(GETSTATIC,NameParseHook.CONVERTER,NameParseHook.CODEC_FIELD,"Lnet/kyori/adventure/util/Codec;"));m.instructions.add(new VarInsnNode(ALOAD,3));
        m.instructions.add(new MethodInsnNode(INVOKEINTERFACE,"net/kyori/adventure/util/Codec","decode","(Ljava/lang/Object;)Ljava/lang/Object;",true));
        m.instructions.add(new TypeInsnNode(CHECKCAST,"net/minecraft/nbt/NBTTagCompound"));m.instructions.add(new VarInsnNode(ASTORE,4));
        for(String key:new String[]{"type","id","name"}) {
            m.instructions.add(new VarInsnNode(ALOAD,4));m.instructions.add(new LdcInsnNode(key));m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","getString","(Ljava/lang/String;)Ljava/lang/String;",false));
            String owner=key.equals("type")?"net/kyori/adventure/key/Key":key.equals("id")?"java/util/UUID":"net/kyori/adventure/text/Component";
            String name=key.equals("type")?"key":key.equals("id")?"fromString":"text";
            String ret=key.equals("name")?"net/kyori/adventure/text/TextComponent":owner;
            m.instructions.add(new MethodInsnNode(INVOKESTATIC,owner,name,"(Ljava/lang/String;)L"+ret+";",!key.equals("id")));
        }
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,"net/kyori/adventure/text/event/HoverEvent$ShowEntity","of","(Lnet/kyori/adventure/key/Key;Ljava/util/UUID;Lnet/kyori/adventure/text/Component;)Lnet/kyori/adventure/text/event/HoverEvent$ShowEntity;",false));
        m.instructions.add(end);m.instructions.add(new InsnNode(ARETURN));m.instructions.add(handler);m.instructions.add(new VarInsnNode(ASTORE,4));
        m.instructions.add(new TypeInsnNode(NEW,"java/io/IOException"));m.instructions.add(new InsnNode(DUP));m.instructions.add(new VarInsnNode(ALOAD,4));m.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/io/IOException","<init>","(Ljava/lang/Throwable;)V",false));m.instructions.add(new InsnNode(ATHROW));
        m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"net/minecraft/nbt/NBTException"));m.maxLocals=5;m.maxStack=4;return m;
    }
    static final class Execution {
        final Object receiver;final Method baseline,patched;final Field codec;
        Execution(MethodNode original)throws Exception {
            String owner="fixture/NameCodecExecution";
            ClassNode c=NameProbeCaptureTest.empty(owner,false);NameProbeCaptureTest.constructor(c);
            c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,NameParseHook.CODEC_FIELD,Type.getDescriptor(Codec.class),null,null));
            MethodNode baseline=new MethodNode(original.access,original.name,original.desc,original.signature,null);original.accept(baseline);baseline.name="baseline";
            MethodNode patched=new MethodNode(original.access,original.name,original.desc,original.signature,null);original.accept(patched);NameParseHook.inject(patched);
            Remapper remap=new SimpleRemapper(mapping(owner));
            for(MethodNode m:new MethodNode[]{baseline,patched}){
                MethodNode n=new MethodNode(m.access,m.name,remap.mapMethodDesc(m.desc),null,null);m.accept(new MethodRemapper(n,remap));c.methods.add(n);
                new Analyzer<>(new BasicVerifier()).analyze(c.name,n);
            }
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] bytes=w.toByteArray();
            Class<?> type=new ClassLoader(NameParseHookTest.class.getClassLoader()){Class<?> define(){return defineClass(owner.replace('/','.'),bytes,0,bytes.length);}}.define();
            receiver=type.getConstructor().newInstance();this.baseline=type.getMethod("baseline",Component.class,Decoder.class);this.patched=type.getMethod("deserializeShowEntity",Component.class,Decoder.class);codec=type.getField(NameParseHook.CODEC_FIELD);
        }
        Object invoke(boolean patched,String input)throws Exception{return (patched?this.patched:baseline).invoke(receiver,new Text(input),null);}
    }
    static void parity(MethodNode original)throws Exception {
        Execution e=new Execution(original);Effects.reset();Bridge.requested=false;Bridge.cache=new NameParseCache(256,4096,524288);Bridge.expected=Effects::decode;e.codec.set(null,Bridge.expected);
        String id="00000000-0000-0000-0000-000000000001",input="{name:\"§aName\",id:\""+id+"\",type:\"minecraft:player\"}";
        Effects.original=new Compound();Effects.original.setString("name","§aName");Effects.original.setString("id",id);Effects.original.setString("type","minecraft:player");
        Result baseline=(Result)e.invoke(false,input);Effects.reset();
        Result off=(Result)e.invoke(true,input);assertSame(input,Effects.passed);assertEquals(1,Effects.decodes);assertEquals(0,Bridge.cache.size());
        assertEquals(baseline.id,off.id);assertEquals(((TestKey)baseline.key).value,((TestKey)off.key).value);assertEquals(((Text)baseline.name).value,((Text)off.name).value);
        Bridge.requested=true;Result miss=(Result)e.invoke(true,input);Result hit=(Result)e.invoke(true,input);
        assertEquals(2,Effects.decodes);assertEquals(3,Effects.serializations);assertEquals(3,Effects.constructions);assertEquals(3,Effects.results);
        assertNotSame(miss,hit);assertNotSame(miss.name,hit.name);assertEquals(off.id,hit.id);assertEquals(((Text)off.name).value,((Text)hit.name).value);
        assertNotSame(Effects.original,Bridge.fresh);for(String k:Effects.original.values.keySet())assertNotSame(Effects.original.values.get(k),Bridge.fresh.values.get(k));
        Bridge.fresh.setString("name","mutated");Result next=(Result)e.invoke(true,input);assertEquals("§aName",((Text)next.name).value);
        RuntimeException construction=new IllegalStateException("fresh constructor warning/failure");Effects.constructFailure=construction;
        assertSame(construction,assertThrows(InvocationTargetException.class,()->e.invoke(true,input)).getCause());Effects.constructFailure=null;
        for(Throwable failure:new Throwable[]{new NbtException("original parser failure"),new IllegalStateException("original runtime failure")}) {
            Effects.decodeFailure=failure;String unknown="{name:[1,\"x\"],id:\""+id+"\"}";
            Throwable actual=assertThrows(InvocationTargetException.class,()->e.invoke(true,unknown)).getCause();
            if(failure instanceof NbtException){assertInstanceOf(java.io.IOException.class,actual);assertSame(failure,actual.getCause());}else assertSame(failure,actual);
        }
        Effects.decodeFailure=null;e.codec.set(null,(Codec)Effects::decode);int before=Effects.decodes;e.invoke(true,input);assertEquals(before+1,Effects.decodes);
        e.codec.set(null,Bridge.expected);
        for(String bad:new String[]{"key","uuid"}) {
            Bridge.cache.clear();Effects.reset();Effects.original=new Compound();Effects.original.setString("name","current name");Effects.original.setString("type",bad.equals("key")?"bad key":"minecraft:player");Effects.original.setString("id",bad.equals("uuid")?"bad uuid":id);
            String malformed="{name:\"current name\",id:\""+Effects.original.getString("id")+"\",type:\""+Effects.original.getString("type")+"\"}";
            Throwable first=assertThrows(InvocationTargetException.class,()->e.invoke(true,malformed)).getCause();
            Throwable cached=assertThrows(InvocationTargetException.class,()->e.invoke(true,malformed)).getCause();
            assertEquals(first.getClass(),cached.getClass());assertEquals(first.getMessage(),cached.getMessage());assertEquals(1,Effects.decodes);assertEquals(2,Effects.keys);assertEquals(0,Effects.constructions);
        }
        Bridge.requested=false;Bridge.cache.clear();Effects.reset();
    }
    @Test void productionBranchPreservesOriginalCodecReturnFreshConsumersAndExactExceptions()throws Exception {parity(body());}
    @Test void unknownClassAndMethodCannotActivateOrMutate() {
        ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);c.methods.add(body());
        AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();new NameParseHook().transform(c,()->fail());assertArrayEquals(before,c.methods.get(0).instructions.toArray());
    }
}

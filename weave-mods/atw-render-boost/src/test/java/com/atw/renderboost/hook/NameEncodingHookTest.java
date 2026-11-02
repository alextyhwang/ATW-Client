package com.atw.renderboost.hook;

import com.atw.renderboost.cache.NameEncodingCache;
import java.io.InputStream;
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

/** Executable original/injected differential fixture. Admission is tested separately. */
class NameEncodingHookTest implements Opcodes {
    static final List<String> events=new ArrayList<>();
    static RuntimeException failure;static String failAt;
    static void event(String s){events.add(s);if(s.equals(failAt))throw failure;}
    public interface Component {
        static Component text(String s){event("text");if(s.contains("§"))event("warning");return new Text(s);}
    }
    public static final class Text implements Component {final String value;Text(String value){this.value=value;}public String content(){return value;}}
    public interface Key {String asString();}
    public interface Encoder {Object encode(Object value);}
    public interface Codec {Object encode(Object value);}
    public interface Serializer {
        static Serializer plainText(){event("plain");return c->{event("serialize");return ((Text)c).value;};}
        String serialize(Component c);
    }
    public static final class ShowEntity {
        UUID id;Key type;Component name,secondName;
        public UUID id(){event("id");return id;}
        public Key type(){event("type");return type;}
        public Component name(){event("name");return Collections.frequency(events,"name")==1?name:secondName;}
    }
    public static class Compound {
        final Map<String,String> tags=new HashMap<>();
        public Compound(){event("compound");}
        public void setString(String k,String v){event("write:"+k);tags.put(k,Objects.requireNonNull(v));}
        public String toString(){StringBuilder s=new StringBuilder("{");for(Map.Entry<String,String> e:tags.entrySet()){
            if(s.length()>1)s.append(',');s.append(e.getKey()).append(':').append('"');
            for(char c:e.getValue().toCharArray()){if(c=='\\'||c=='"')s.append('\\');s.append(c);}s.append('"');}return s.append('}').toString();}
    }
    /** Java compiler supplies a real method with the same original ordering. */
    public static final class Template {
        public static Codec codec;
        public Component serializeShowEntity(ShowEntity input,Encoder unused) {
            Compound tag=new Compound();tag.setString("id",input.id().toString());tag.setString("type",input.type().asString());
            if(input.name()!=null)tag.setString("name",Serializer.plainText().serialize(input.name()));
            return Component.text((String)codec.encode(tag));
        }
    }
    public static final class Ticket {final NameEncodingCache.Key key;final String hit;Ticket(NameEncodingCache.Key key,String hit){this.key=key;this.hit=hit;}}
    public static final class Bridge {
        public static boolean requested;static NameEncodingCache cache;static Object expected;
        public static Ticket begin(Object codec,String id,String type,boolean present,String name){if(codec!=expected)return null;
            NameEncodingCache.Key key=new NameEncodingCache.Key(id,type,present,name);return cache.supported(key)?new Ticket(key,cache.lookup(key)):null;}
        public static String hit(Ticket t){return t==null||t.hit==null?null:new String(t.hit);}
        public static void commit(Ticket t,String s){if(t!=null&&t.hit==null)cache.record(t.key,s);}
    }
    static Map<String,String> mapping(String owner) {
        Map<String,String> map=new HashMap<>();map.put(NameParseHook.CONVERTER,owner);
        map.put("net/kyori/adventure/text/Component",Type.getInternalName(Component.class));map.put("net/kyori/adventure/text/TextComponent",Type.getInternalName(Component.class));
        map.put("net/kyori/adventure/text/event/HoverEvent$ShowEntity",Type.getInternalName(ShowEntity.class));
        map.put("net/kyori/adventure/key/Key",Type.getInternalName(Key.class));map.put("net/kyori/adventure/util/Codec",Type.getInternalName(Codec.class));
        map.put("net/kyori/adventure/util/Codec$Encoder",Type.getInternalName(Encoder.class));
        map.put("net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer",Type.getInternalName(Serializer.class));
        map.put("net/minecraft/nbt/NBTTagCompound",Type.getInternalName(Compound.class));
        map.put(NameEncodingHook.RUNTIME,Type.getInternalName(Bridge.class));map.put(NameEncodingHook.RUNTIME+"$Ticket",Type.getInternalName(Ticket.class));return map;
    }
    static MethodNode body()throws Exception {
        ClassNode c=new ClassNode();try(InputStream in=Template.class.getResourceAsStream("/"+Type.getInternalName(Template.class)+".class")){new ClassReader(in).accept(c,0);}
        MethodNode original=c.methods.stream().filter(m->m.name.equals("serializeShowEntity")).findFirst().get();
        Map<String,String> forward=new HashMap<>();for(Map.Entry<String,String> e:mapping(Type.getInternalName(Template.class)).entrySet())forward.put(e.getValue(),e.getKey());
        forward.put(Type.getInternalName(Template.class)+".codec",NameParseHook.CODEC_FIELD);
        Remapper remap=new SimpleRemapper(forward);MethodNode out=new MethodNode(original.access,original.name,remap.mapMethodDesc(original.desc),null,null);
        original.accept(new MethodRemapper(out,remap));return out;
    }
    static final class Execution {
        final Object receiver;final Method baseline,patched;final Field codec;
        Execution(MethodNode original)throws Exception {
            String owner="fixture/EncodingExecution";ClassNode c=NameProbeCaptureTest.empty(owner,false);NameProbeCaptureTest.constructor(c);
            c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,NameParseHook.CODEC_FIELD,Type.getDescriptor(Codec.class),null,null));
            MethodNode baseline=new MethodNode(original.access,original.name,original.desc,null,null);original.accept(baseline);baseline.name="baseline";
            MethodNode patched=new MethodNode(original.access,original.name,original.desc,null,null);original.accept(patched);NameEncodingHook.inject(patched);
            Remapper remap=new SimpleRemapper(mapping(owner));
            for(MethodNode m:new MethodNode[]{baseline,patched}){MethodNode n=new MethodNode(m.access,m.name,remap.mapMethodDesc(m.desc),null,null);m.accept(new MethodRemapper(n,remap));c.methods.add(n);new Analyzer<>(new BasicVerifier()).analyze(c.name,n);}
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] bytes=w.toByteArray();
            Class<?> type=new ClassLoader(NameEncodingHookTest.class.getClassLoader()){Class<?> define(){return defineClass(owner.replace('/','.'),bytes,0,bytes.length);}}.define();
            receiver=type.getConstructor().newInstance();this.baseline=type.getMethod("baseline",ShowEntity.class,Encoder.class);this.patched=type.getMethod("serializeShowEntity",ShowEntity.class,Encoder.class);codec=type.getField(NameParseHook.CODEC_FIELD);
            Bridge.cache=new NameEncodingCache(256,4096,8192,524288);Bridge.expected=(Codec)value->{event("encode");return value.toString();};codec.set(null,Bridge.expected);
        }
        Text invoke(boolean injected,ShowEntity input)throws Exception{return (Text)(injected?patched:baseline).invoke(receiver,input,(Encoder)value->{throw new AssertionError("unused caller Encoder invoked");});}
    }
    static ShowEntity input(String type,String name){ShowEntity i=new ShowEntity();i.id=UUID.fromString("00000000-0000-0000-0000-000000000001");i.type=()->{event("asString");return type;};i.name=i.secondName=name==null?null:new Text(name);return i;}
    static void reset(){events.clear();failure=null;failAt=null;}
    static void parity(MethodNode original)throws Exception {
        Execution e=new Execution(original);
        for(String name:new String[]{null,"","§a","\"\\","\u0000\n\t","\ud83d\ude00","\ud800","\udc00","Aa","BB"}) {
            Bridge.cache.clear();ShowEntity input=input("minecraft:player",name);Bridge.requested=false;reset();Text baseline=e.invoke(false,input);List<String> expected=new ArrayList<>(events);
            reset();Text baselineAgain=e.invoke(false,input);assertEquals(expected,events);assertEquals(baseline.value,baselineAgain.value);assertNotSame(baseline.value,baselineAgain.value);
            reset();Text off=e.invoke(true,input);assertEquals(baseline.value,off.value);assertEquals(expected,events);assertEquals(0,Bridge.cache.size());
            Bridge.requested=true;reset();Text miss=e.invoke(true,input);assertEquals(expected,events);assertEquals(baseline.value,miss.value);
            reset();Text hit=e.invoke(true,input);List<String> hitExpected=new ArrayList<>(expected);hitExpected.remove("encode");assertEquals(hitExpected,events);assertEquals(baseline.value,hit.value);assertNotSame(miss,hit);
            assertNotSame(miss.value,hit.value);reset();Text secondHit=e.invoke(true,input);assertEquals(hitExpected,events);assertEquals(hit.value,secondHit.value);assertNotSame(hit,secondHit);assertNotSame(hit.value,secondHit.value);assertNotSame(miss.value,secondHit.value);
            assertEquals(name==null?1:2,Collections.frequency(events,"name"));assertEquals(name!=null&&name.contains("§")?1:0,Collections.frequency(events,"warning"));
            // Each mutable input field is observed anew; equal text stays outwardly fresh.
            input.id=UUID.fromString("00000000-0000-0000-0000-000000000002");reset();Text changed=e.invoke(true,input);assertNotEquals(hit.value,changed.value);assertTrue(events.contains("encode"));
            input.type=()->{event("asString");return "minecraft:changed";};reset();assertNotEquals(changed.value,e.invoke(true,input).value);assertTrue(events.contains("encode"));
            input.name=new Text("first read");input.secondName=new Text("second read");reset();assertTrue(e.invoke(true,input).value.contains("second read"));
        }
        for(String point:new String[]{"compound","id","type","asString","name","plain","serialize","write:id","write:type","write:name","encode","text","warning"}) {
            Bridge.cache.clear();ShowEntity input=input("minecraft:player","§aFailure");RuntimeException thrown=new IllegalStateException(point);
            reset();failure=thrown;failAt=point;Throwable baseline=assertThrows(InvocationTargetException.class,()->e.invoke(false,input)).getCause();List<String> expected=new ArrayList<>(events);
            reset();failure=thrown;failAt=point;assertSame(baseline,assertThrows(InvocationTargetException.class,()->e.invoke(true,input)).getCause());assertEquals(expected,events);assertEquals(0,Bridge.cache.size());
        }
        for(int nullCase=0;nullCase<4;nullCase++){Bridge.cache.clear();ShowEntity input=input("minecraft:player","name");if(nullCase==0)input=null;else if(nullCase==1)input.id=null;else if(nullCase==2)input.type=null;else input.type=()->null;
            final ShowEntity bad=input;reset();Throwable baseline=assertThrows(InvocationTargetException.class,()->e.invoke(false,bad)).getCause();List<String> expected=new ArrayList<>(events);reset();Throwable injected=assertThrows(InvocationTargetException.class,()->e.invoke(true,bad)).getCause();assertEquals(baseline.getClass(),injected.getClass());assertEquals(expected,events);assertEquals(0,Bridge.cache.size());}
        reset();ShowEntity input=input("t","§aHit");e.invoke(true,input);int size=Bridge.cache.size();reset();failure=new IllegalArgumentException();failAt="text";assertSame(failure,assertThrows(InvocationTargetException.class,()->e.invoke(true,input)).getCause());assertFalse(events.contains("encode"));assertEquals(size,Bridge.cache.size());
        reset();e.codec.set(null,(Codec)value->{event("encode");return value.toString();});e.invoke(true,input);assertTrue(events.contains("encode"));reset();Bridge.requested=false;
    }
    @Test void executableOffMissHitExceptionsWarningsAndCallbackOrderMatch()throws Exception {parity(body());}
    @Test void unsupportedInputCannotMutateOrRequestFrameComputation()throws Exception {
        ClassNode c=NameProbeCaptureTest.empty(NameParseHook.CONVERTER,false);c.methods.add(body());AbstractInsnNode[] before=c.methods.get(0).instructions.toArray();
        new NameEncodingHook().transform(c,()->fail());assertArrayEquals(before,c.methods.get(0).instructions.toArray());
        assertFalse(NameEncodingHook.factoryMatches(NameEncodingEvidenceOutputTest.factory(),NameEncodingEvidenceOutputTest.missingFactoryEvidence()));
    }
}

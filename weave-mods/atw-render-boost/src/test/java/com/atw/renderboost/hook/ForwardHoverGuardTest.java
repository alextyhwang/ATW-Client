package com.atw.renderboost.hook;

import com.atw.renderboost.cache.ForwardHoverRuntime;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class ForwardHoverGuardTest implements Opcodes {
    static int inspect(ForwardHoverExecutionTest.Fixture f,Map<String,Object> entries,String id,String type,String name)throws Exception{
        Object s=f.compound.getConstructor().newInstance();f.compound.getField("tagMap").set(s,entries);
        return (Integer)f.call("inspect",new Class<?>[]{Object.class,String.class,String.class,String.class},s,id,type,name);
    }
    static Map<String,Object> map(ForwardHoverExecutionTest.Fixture f,int capacity,String id,String type,String name)throws Exception{Map<String,Object> m=new HashMap<>(capacity);
        m.put("id",f.tag.getConstructor(String.class).newInstance(id));if(type!=null)m.put("type",f.tag.getConstructor(String.class).newInstance(type));m.put("name",f.tag.getConstructor(String.class).newInstance(name));return m;}
    @Test void inspectorRecordsActualTraversalAndRejectsUnknownMapsTagsKeysAndChangedData()throws Exception{
        ForwardHoverExecutionTest.Fixture f=new ForwardHoverExecutionTest.Fixture(true,true);f.request(true);f.invoke(true);
        String id=new String("id"),name=new String("name"),type=new String("");Map<String,Object> ordinary=map(f,16,id,null,name);
        int token=inspect(f,ordinary,id,null,name);assertTrue(token>0);assertNotEquals(token,inspect(f,map(f,128,id,null,name),id,null,name),"actual bootstrap HashMap traversals differ; never sort or assume insertion order");
        assertNotEquals(token,inspect(f,map(f,16,id,type,name),id,type,name));assertEquals(0,inspect(f,map(f,16,id,null,name),id,"",name));
        for(Map<String,Object> unsupported:Arrays.asList(new LinkedHashMap<>(ordinary),new HashMap<String,Object>(ordinary){},new TreeMap<>(ordinary)))assertEquals(0,inspect(f,unsupported,id,null,name));
        for(Object wrong:new Object[]{null,f.tag.getConstructor(String.class).newInstance(new String(id)),new Object(),new ForwardHoverExecutionTest.Tag(id)}){Map<String,Object> m=map(f,16,id,null,name);m.put("id",wrong);assertEquals(0,inspect(f,m,id,null,name));}
        Map<String,Object> extra=map(f,16,id,null,name);extra.put("other",new Object());assertEquals(0,inspect(f,extra,id,null,name));extra.remove("name");assertEquals(0,inspect(f,extra,id,null,name));
        assertEquals(0,inspect(f,map(f,16,id,null,name),null,null,name));assertEquals(0,inspect(f,map(f,16,id,null,name),id,null,null));
    }
    @Test void unsupportedActualInvocationRejoinsOriginalAndDoesNotRetainNativeState()throws Exception{
        ForwardHoverExecutionTest.Fixture f=new ForwardHoverExecutionTest.Fixture(true,true);f.request(true);
        Object c=f.compound.getConstructor().newInstance();String id=new String("id"),name=new String("name");
        f.compound.getMethod("setString",String.class,String.class).invoke(c,"id",id);f.compound.getMethod("setString",String.class,String.class).invoke(c,"name",name);
        Object ticket=f.begin(c,id,null,name);assertNotNull(ticket);f.commit(ticket,"original");assertEquals("original",f.hit(f.begin(c,id,null,name)));
        Field field=f.compound.getField("tagMap");Map<?,?> original=(Map<?,?>)field.get(c);field.set(c,new LinkedHashMap<>(original));assertNull(f.begin(c,id,null,name));field.set(c,original);
        assertNull(f.begin(c,new String(id),null,name));assertNull(f.begin(c,id,"",name));
        ForwardHoverExecutionTest.Fixture other=new ForwardHoverExecutionTest.Fixture(true,true);other.request(true);assertNull(other.begin(c,id,null,name));assertEquals("false",other.metadata().getProperty("forwardActive"));
        for(Field retained:ticket.getClass().getDeclaredFields())assertFalse(retained.getType()==Object.class||retained.getType()==f.compound||Map.class.isAssignableFrom(retained.getType()));
        Object stale=f.begin(c,id,null,"different");assertNull(stale);ticket=f.begin(c,id,null,name);f.clear();f.commit(ticket,"stale");assertEquals("0",f.metadata().getProperty("forwardEntries"));
    }
    @Test void helperLocalsAvoidWideSlotsDebugLocalsAndAmbiguousSeams()throws Exception{
        MethodNode m=ForwardHoverExecutionTest.body();m.maxLocals=3;m.instructions.insert(new VarInsnNode(DSTORE,20));
        // This intentionally unverifiable body tests allocation boundaries only, never admission.
        m.localVariables.add(new LocalVariableNode("wide","J",null,new LabelNode(),new LabelNode(),30));assertEquals(32,ForwardHoverHook.nextLocal(m));
        int[] plan=ForwardHoverHook.inject(m);assertTrue(plan[0]>=34);
        MethodNode duplicate=ForwardHoverExecutionTest.body();duplicate.instructions.insert(new MethodInsnNode(INVOKEVIRTUAL,ForwardHoverHook.TARGETS[2],"toString","()Ljava/lang/String;",false));
        AbstractInsnNode[] before=duplicate.instructions.toArray();assertThrows(IllegalArgumentException.class,()->ForwardHoverHook.inject(duplicate));assertArrayEquals(before,duplicate.instructions.toArray());
    }
}

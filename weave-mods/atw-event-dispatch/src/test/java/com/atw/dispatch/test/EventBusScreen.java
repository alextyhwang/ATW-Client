// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch.test;
import com.atw.dispatch.DispatchRuntime;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.weavemc.api.event.CancellableEvent;
import net.weavemc.api.event.Event;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.SubscribeEvent;

/** ATW private parity cases promoted to canonical tests; original API owns all registration and posts. */
public final class EventBusScreen {
    static final long DEADLINE = System.nanoTime() + TimeUnit.SECONDS.toNanos(100);
    static final List<String> rows = new ArrayList<>();
    static final List<String> warmupRows = new ArrayList<>();
    static final List<String> checks = new ArrayList<>();
    static volatile long sink;
    static int passCount;

    interface Tagged {}
    public static class ParentEvent extends Event {}
    public static class ChildEvent extends ParentEvent implements Tagged { final int id; ChildEvent(int id) { this.id = id; } }
    public static class CancelEvent extends CancellableEvent {}
    static final class Marker extends RuntimeException { Marker() { super("expected-marker"); } }

    static final class Fixture {
        final MapFixture candidate;
        final boolean baseline;
        final Thread caller = Thread.currentThread();
        final List<String> trace = new ArrayList<>();
        Fixture(boolean baseline) throws Exception { this.baseline = baseline; candidate = new MapFixture(); candidate.reset(); DispatchRuntime.request(!baseline); }
        void post(Event event) { EventBus.postEvent(event); }
        void log(String value) { require(Thread.currentThread() == caller, "callback escaped caller thread"); trace.add(value); }
        <T extends Event> void on(Class<T> type, Consumer<T> callback) { EventBus.subscribe(type, callback); }
        void remove(Consumer<?> callback) { removeConsumer(callback); }
        List<String> finish() {
            for (var entry : candidate.state().entrySet()) {
                require(entry.getValue() instanceof java.util.concurrent.CopyOnWriteArrayList<?>, "non-COW list");
            }
            candidate.reset();
            return List.copyOf(trace);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void removeConsumer(Consumer<?> callback) { EventBus.unsubscribe((Consumer) callback); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void rawSubscribe(Class<?> type, Consumer<?> callback) { EventBus.subscribe((Class) type, (Consumer) callback); }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void bounded() { require(System.nanoTime() < DEADLINE, "100s harness deadline exceeded"); }
    static void await(CountDownLatch latch) {
        try { require(latch.await(2, TimeUnit.SECONDS), "barrier timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    static void join(Thread worker) {
        try { worker.join(2500); require(!worker.isAlive(), "worker timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    interface Scenario { List<String> run(boolean baseline) throws Exception; }
    static void parity(String name, Scenario scenario, String... expected) throws Exception {
        bounded();
        List<String> baseline = scenario.run(true);
        List<String> candidate = scenario.run(false);
        require(baseline.equals(candidate), name + " parity: " + baseline + " != " + candidate);
        if (expected.length != 0) require(baseline.equals(Arrays.asList(expected)), name + " expected: " + baseline);
        checks.add(name + "\tPASS\t" + String.join("|", baseline));
        passCount++;
    }
    static String failure(Throwable throwable) {
        String result = throwable.getClass().getName() + ":" + throwable.getMessage();
        if (throwable.getCause() != null) result += "/cause=" + failure(throwable.getCause());
        return result;
    }
    static Throwable caught(Runnable work) {
        try { work.run(); throw new AssertionError("expected failure"); }
        catch (AssertionError e) { throw e; }
        catch (Throwable e) { return e; }
    }

    static void paritySuite() throws Exception {
        for (int count : new int[]{0, 1, 8, 32}) {
            parity("listener-count-" + count, baseline -> {
                Fixture f = new Fixture(baseline);
                ChildEvent event = new ChildEvent(7);
                for (int i = 0; i < count; i++) { final int n = i; f.on(ChildEvent.class, e -> {
                    require(e == event, "event identity"); f.log("L" + n);
                }); }
                f.post(event);
                require(f.trace.size() == count, "count");
                for (int i = 0; i < count; i++) require(f.trace.get(i).equals("L" + i), "registration order");
                require(f.candidate.state().keySet().equals(java.util.Set.of(ChildEvent.class, ParentEvent.class, Event.class)), "empty hierarchy map side effects");
                return f.finish();
            });
        }
        parity("hierarchy-and-order-object-interface-excluded", baseline -> {
            Fixture f = new Fixture(baseline);
            f.on(Event.class, e -> f.log("root"));
            f.on(ParentEvent.class, e -> f.log("parent-1"));
            f.on(ChildEvent.class, e -> f.log("child-1"));
            f.on(ParentEvent.class, e -> f.log("parent-2"));
            f.on(ChildEvent.class, e -> f.log("child-2"));
            rawSubscribe(Object.class, e -> f.log("object-bad"));
            rawSubscribe(Tagged.class, e -> f.log("interface-bad"));
            f.post(new ChildEvent(0)); return f.finish();
        }, "child-1", "child-2", "parent-1", "parent-2", "root");
        parity("same-level-subscribe", baseline -> {
            Fixture f = new Fixture(baseline);
            Consumer<ChildEvent> added = e -> f.log("added-" + e.id);
            f.on(ChildEvent.class, e -> { f.log("first-" + e.id); if (e.id == 1) f.on(ChildEvent.class, added); });
            f.on(ChildEvent.class, e -> f.log("last-" + e.id));
            f.post(new ChildEvent(1)); f.post(new ChildEvent(2)); return f.finish();
        }, "first-1", "last-1", "first-2", "last-2", "added-2");
        parity("same-level-unsubscribe", baseline -> {
            Fixture f = new Fixture(baseline);
            Consumer<ChildEvent> later = e -> f.log("later-" + e.id);
            f.on(ChildEvent.class, e -> { f.log("first-" + e.id); f.remove(later); });
            f.on(ChildEvent.class, later);
            f.post(new ChildEvent(1)); f.post(new ChildEvent(2)); return f.finish();
        }, "first-1", "later-1", "first-2");
        parity("next-level-subscribe", baseline -> {
            Fixture f = new Fixture(baseline);
            f.on(ParentEvent.class, e -> f.log("parent-old"));
            f.on(ChildEvent.class, e -> { f.log("child"); f.on(ParentEvent.class, p -> f.log("parent-new")); });
            f.post(new ChildEvent(0)); return f.finish();
        }, "child", "parent-old", "parent-new");
        parity("next-level-unsubscribe", baseline -> {
            Fixture f = new Fixture(baseline);
            Consumer<ParentEvent> parent = e -> f.log("parent-bad");
            f.on(ParentEvent.class, parent);
            f.on(ChildEvent.class, e -> { f.log("child"); f.remove(parent); });
            f.on(Event.class, e -> f.log("root"));
            f.post(new ChildEvent(0)); return f.finish();
        }, "child", "root");
        parity("nested-dispatch", baseline -> {
            Fixture f = new Fixture(baseline);
            f.on(ChildEvent.class, e -> { f.log("first-" + e.id); if (e.id == 1) {
                f.on(ChildEvent.class, n -> f.log("added-" + n.id)); f.post(new ChildEvent(2));
                f.log("resumed");
            } });
            f.on(ChildEvent.class, e -> f.log("last-" + e.id));
            f.on(ParentEvent.class, e -> f.log("parent-" + ((ChildEvent)e).id));
            f.post(new ChildEvent(1)); return f.finish();
        }, "first-1", "first-2", "last-2", "added-2", "parent-2", "resumed", "last-1", "parent-1");
        parity("nested-remove-and-next-level", baseline -> {
            Fixture f = new Fixture(baseline);
            Consumer<ChildEvent> last = e -> f.log("last-" + e.id);
            f.on(ChildEvent.class, e -> { f.log("first-" + e.id); if (e.id == 1) {
                f.remove(last); f.post(new ChildEvent(2));
                f.on(ParentEvent.class, p -> f.log("late-parent-" + ((ChildEvent)p).id));
            } });
            f.on(ChildEvent.class, last);
            f.post(new ChildEvent(1)); return f.finish();
        }, "first-1", "first-2", "last-1", "late-parent-1");
        parity("self-remove", baseline -> {
            Fixture f = new Fixture(baseline);
            AtomicReference<Consumer<ChildEvent>> self = new AtomicReference<>();
            self.set(e -> { f.log("self"); f.remove(self.get()); });
            f.on(ChildEvent.class, self.get()); f.post(new ChildEvent(1)); f.post(new ChildEvent(2));
            return f.finish();
        }, "self");
        parity("duplicate-consumer-remove-all-levels", baseline -> {
            Fixture f = new Fixture(baseline);
            Consumer<Event> same = e -> f.log("same");
            rawSubscribe(ChildEvent.class, same); rawSubscribe(ChildEvent.class, same); f.on(Event.class, same);
            f.post(new ChildEvent(1)); f.remove(same); f.post(new ChildEvent(2)); return f.finish();
        }, "same", "same", "same");
        parity("unsubscribe-identity-not-equals", baseline -> {
            Fixture f = new Fixture(baseline);
            class EqualConsumer implements Consumer<ChildEvent> {
                final String label; EqualConsumer(String label) { this.label = label; }
                public void accept(ChildEvent e) { f.log(label); }
                public boolean equals(Object o) { return o instanceof EqualConsumer; }
                public int hashCode() { return 1; }
            }
            EqualConsumer first = new EqualConsumer("first"), second = new EqualConsumer("second");
            f.on(ChildEvent.class, first); f.on(ChildEvent.class, second); f.remove(first);
            f.post(new ChildEvent(0)); return f.finish();
        }, "second");
        parity("reflect-private-inherited-arity-and-object-unsubscribe", baseline -> {
            Fixture f = new Fixture(baseline);
            ReflectChild object = new ReflectChild(f);
            EventBus.subscribe((Object)object);
            f.post(new ChildEvent(1)); EventBus.unsubscribe((Object)object); f.post(new ChildEvent(2));
            return f.finish();
        }, "reflect-child", "reflect-parent");
        parity("reflect-declared-method-order", baseline -> {
            Fixture f = new Fixture(baseline); ReflectOrder object = new ReflectOrder(f);
            List<String> expected = new ArrayList<>();
            for (Method method : ReflectOrder.class.getDeclaredMethods()) {
                if (method.isAnnotationPresent(SubscribeEvent.class) && method.getParameterCount() == 1) expected.add(method.getName());
            }
            EventBus.subscribe((Object)object); f.post(new ChildEvent(0));
            require(f.trace.equals(expected), "actual getDeclaredMethods order"); return f.finish();
        });
        parity("reflect-duplicate-object", baseline -> {
            Fixture f = new Fixture(baseline); ReflectChild object = new ReflectChild(f);
            EventBus.subscribe((Object)object); EventBus.subscribe((Object)object); f.post(new ChildEvent(1));
            EventBus.unsubscribe((Object)object); f.post(new ChildEvent(2)); return f.finish();
        }, "reflect-child", "reflect-child", "reflect-parent", "reflect-parent");
        parity("reflect-object-unsubscribe-during-callback", baseline -> {
            Fixture f = new Fixture(baseline); ReflectChild object = new ReflectChild(f);
            f.on(ChildEvent.class, e -> { f.log("remover"); EventBus.unsubscribe((Object)object); });
            EventBus.subscribe((Object)object); f.post(new ChildEvent(1)); f.post(new ChildEvent(2)); return f.finish();
        }, "remover", "reflect-child", "remover");
        parity("direct-exception-shortcircuit-and-identity", baseline -> {
            Fixture f = new Fixture(baseline); Marker marker = new Marker();
            f.on(ChildEvent.class, e -> f.log("first"));
            f.on(ChildEvent.class, e -> { f.log("throw"); throw marker; });
            f.on(ChildEvent.class, e -> f.log("later-bad")); f.on(ParentEvent.class, e -> f.log("parent-bad"));
            Throwable result = caught(() -> f.post(new ChildEvent(0)));
            require(result == marker, "direct exception identity"); f.log(failure(result)); return f.finish();
        }, "first", "throw", "com.atw.dispatch.test.EventBusScreen$Marker:expected-marker");
        parity("reflect-exception-shortcircuit", baseline -> {
            Fixture f = new Fixture(baseline); Marker marker = new Marker();
            ReflectThrow object = new ReflectThrow(f, marker); EventBus.subscribe((Object)object);
            f.on(ChildEvent.class, e -> f.log("later-bad")); f.on(ParentEvent.class, e -> f.log("parent-bad"));
            Throwable result = caught(() -> f.post(new ChildEvent(0)));
            require(result instanceof InvocationTargetException && result.getCause() == marker, "reflection wrapper/cause identity");
            f.log(failure(result)); return f.finish();
        }, "reflect-throw", "java.lang.reflect.InvocationTargetException:null/cause=com.atw.dispatch.test.EventBusScreen$Marker:expected-marker");
        parity("nested-exception-shortcircuit", baseline -> {
            Fixture f = new Fixture(baseline); Marker marker = new Marker();
            f.on(ChildEvent.class, e -> { f.log("first-" + e.id); if (e.id == 1) f.post(new ChildEvent(2)); else throw marker; });
            f.on(ChildEvent.class, e -> f.log("later-bad")); f.on(Event.class, e -> f.log("root-bad"));
            require(caught(() -> f.post(new ChildEvent(1))) == marker, "nested throw identity"); return f.finish();
        }, "first-1", "first-2");
        parity("cancellation-not-bus-filter", baseline -> {
            Fixture f = new Fixture(baseline); CancelEvent event = new CancelEvent(); event.setCancelled(true);
            f.on(CancelEvent.class, e -> { f.log("first-" + e.isCancelled()); e.setCancelled(false); });
            f.on(CancelEvent.class, e -> { f.log("second-" + e.isCancelled()); e.setCancelled(true); });
            f.on(CancellableEvent.class, e -> f.log("parent-" + e.isCancelled()));
            f.on(Event.class, e -> f.log("root-" + ((CancelEvent)e).isCancelled()));
            f.post(event); require(event.isCancelled(), "final cancellation"); return f.finish();
        }, "first-true", "second-false", "parent-true", "root-true");
        parity("null-public-contract", baseline -> {
            Fixture f = new Fixture(baseline);
            f.log(failure(caught(() -> f.post(null))));
            f.log(failure(caught(() -> EventBus.subscribe(ChildEvent.class, null))));
            f.log(failure(caught(() -> EventBus.subscribe((Class<ChildEvent>)null, e -> {}))));
            f.log(failure(caught(() -> EventBus.subscribe((Object)null))));
            f.log(failure(caught(() -> EventBus.unsubscribe((Object)null))));
            require(f.candidate.state().isEmpty(), "null rejection must not create listener map entries"); return f.finish();
        });
        parity("raw-wrong-consumer-accepted-then-cast-fails", baseline -> {
            Fixture f = new Fixture(baseline); Consumer<String> wrong = s -> f.log("wrong-bad");
            rawSubscribe(ChildEvent.class, wrong); f.on(ChildEvent.class, e -> f.log("later-bad"));
            Throwable result = caught(() -> f.post(new ChildEvent(0)));
            require(result instanceof ClassCastException, "erased consumer cast");
            // Java CCE text includes loader details; compare throwable class, not exact message.
            f.log(result.getClass().getName()); return f.finish();
        }, "java.lang.ClassCastException");
        parity("raw-non-consumer-rejected-by-public-descriptor", baseline -> {
            Fixture f = new Fixture(baseline);
            Method subscribe = EventBus.class.getMethod("subscribe", Class.class, Consumer.class);
            Throwable result;
            try { subscribe.invoke(null, ChildEvent.class, new Object()); throw new AssertionError("accepted non-consumer"); }
            catch (IllegalArgumentException e) { result = e; }
            require(f.candidate.state().isEmpty(), "reflection argument rejection");
            f.log(result.getClass().getName()); return f.finish();
        }, "java.lang.IllegalArgumentException");
        for (boolean add : new boolean[]{true, false}) {
            for (boolean nextLevel : new boolean[]{false, true}) {
                final String name = "concurrent-" + (add ? "subscribe" : "unsubscribe") + (nextLevel ? "-next-level" : "-same-level");
                parity(name, baseline -> concurrentScenario(baseline, add, nextLevel));
            }
        }
        // Repeat bounded deterministic schedules to catch accidental visibility assumptions.
        for (int round = 0; round < 16; round++) {
            final int n = round;
            parity("concurrent-repeat-" + n, baseline -> concurrentScenario(baseline, (n & 1) == 0, (n & 2) == 0));
        }
        new MapFixture().reset();
    }

    public static class ReflectParent {
        final Fixture fixture; ReflectParent(Fixture fixture) { this.fixture = fixture; }
        @SubscribeEvent private void parent(ParentEvent event) { fixture.log("reflect-parent"); }
    }
    public static class ReflectChild extends ReflectParent {
        ReflectChild(Fixture fixture) { super(fixture); }
        @SubscribeEvent private void child(ChildEvent event) { fixture.log("reflect-child"); }
        @SubscribeEvent private void zero() { fixture.log("zero-bad"); }
        @SubscribeEvent private void two(ChildEvent a, ChildEvent b) { fixture.log("two-bad"); }
        @SubscribeEvent private void unrelated(String value) { fixture.log("string-bad"); }
    }
    public static class ReflectOrder {
        final Fixture fixture; ReflectOrder(Fixture fixture) { this.fixture = fixture; }
        @SubscribeEvent private void z(ChildEvent event) { fixture.log("z"); }
        @SubscribeEvent private void a(ChildEvent event) { fixture.log("a"); }
        @SubscribeEvent private void m(ChildEvent event) { fixture.log("m"); }
    }
    public static class ReflectThrow {
        final Fixture fixture; final Marker marker;
        ReflectThrow(Fixture fixture, Marker marker) { this.fixture = fixture; this.marker = marker; }
        @SubscribeEvent private void throwing(ChildEvent event) { fixture.log("reflect-throw"); throw marker; }
    }

    static List<String> concurrentScenario(boolean baseline, boolean add, boolean nextLevel) throws Exception {
        Fixture f = new Fixture(baseline);
        CountDownLatch entered = new CountDownLatch(1), mutated = new CountDownLatch(1);
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Consumer<Event> target = e -> f.log("target-" + ((ChildEvent)e).id);
        Class<?> type = nextLevel ? ParentEvent.class : ChildEvent.class;
        f.on(ChildEvent.class, e -> { f.log("first-" + e.id); if (e.id == 1) { entered.countDown(); await(mutated); } });
        if (!add) rawSubscribe(type, target);
        Thread worker = new Thread(() -> {
            try { await(entered); if (add) rawSubscribe(type, target); else f.remove(target); }
            catch (Throwable e) { workerFailure.set(e); }
            finally { mutated.countDown(); }
        }, "private-registration-worker");
        worker.setDaemon(true); worker.start();
        try { f.post(new ChildEvent(1)); }
        finally { entered.countDown(); join(worker); }
        require(workerFailure.get() == null, "worker failure " + workerFailure.get());
        f.post(new ChildEvent(2));
        List<String> expected = new ArrayList<>(); expected.add("first-1");
        if (nextLevel ? add : !add) expected.add("target-1");
        expected.add("first-2"); if (add) expected.add("target-2");
        require(f.trace.equals(expected), "concurrent visibility: " + f.trace + " expected " + expected);
        return f.finish();
    }

    public static java.util.List<String> run() throws Exception { paritySuite(); return java.util.List.copyOf(checks); }
}

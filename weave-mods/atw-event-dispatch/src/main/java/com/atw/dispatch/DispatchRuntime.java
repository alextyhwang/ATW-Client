// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** No EventBus linkage: the injected caller supplies its own original registration map. */
public final class DispatchRuntime {
    private DispatchRuntime() {}
    private static volatile boolean requested; // Always false on JVM/class initialization; never persisted.
    private static volatile boolean available;
    private static volatile String failure = "first-definition Hook delivery not observed";
    private static volatile String definition = "unobserved";
    private static final AtomicLong hookTransforms = new AtomicLong();
    private static final AtomicLong hookRejected = new AtomicLong();
    private static volatile Class<?> observedBus;
    private static final AtomicLong seamPosts = new AtomicLong();
    private static final AtomicLong effectivePosts = new AtomicLong();
    private static final AtomicLong completedPosts = new AtomicLong();
    private static final AtomicLong fallbackPosts = new AtomicLong();
    private static final AtomicLong callbackFailures = new AtomicLong();
    private static volatile long activationStart;
    private static volatile String dispatchFailure = "none";

    public static synchronized void request(boolean on) {
        if (on && !requested) activationStart = effectivePosts.get();
        requested = on;
    }

    /** Hook construction does not reference or initialize this class's target. */
    public static void hookResult(boolean accepted, String reason) {
        if (accepted) hookTransforms.incrementAndGet();
        else { hookRejected.incrementAndGet(); available = false; failure = reason; }
    }

    /** Runs before any callback. False means the complete original method will run. */
    public static boolean prepare(Object event, Map<?, ?> map, Class<?> bus) {
        seamPosts.incrementAndGet();
        try {
            ClassLoader loader = bus.getClassLoader();
            if (loader == null || Class.forName(DispatchRuntime.class.getName(), false, loader) != DispatchRuntime.class)
                return reject("helper identity differs through EventBus defining loader");
            if (Class.forName(bus.getName(), false, DispatchRuntime.class.getClassLoader()) != bus)
                return reject("control helper resolves a different EventBus definition");
            if (observedBus == null) {
                if (!"atw-dispatch-strict-v1".equals(bus.getField("atwDispatchMarker").get(null)))
                    return reject("missing strict Hook marker");
                observedBus = bus;
            }
            if (observedBus != bus) return reject("multiple EventBus definitions share this helper; original retained");
            if (!requested) { fallbackPosts.incrementAndGet(); return false; }
            if (event == null) { fallbackPosts.incrementAndGet(); return false; } // Original Kotlin null check.
            if (map == null || map.getClass() != ConcurrentHashMap.class)
                return reject("original map is not exact ConcurrentHashMap");
            for (Class<?> level = event.getClass(); level != Object.class; level = level.getSuperclass()) {
                Object list = map.get(level);
                if (list != null && list.getClass() != CopyOnWriteArrayList.class)
                    return reject("original level list is not exact CopyOnWriteArrayList: " + level.getName());
            }
            // Record actual caller definition, never a looked-up singleton or reflected replacement map.
            if (!available) {
                definition = bus.getName() + " loader=" + loader + " helperLoader="
                    + DispatchRuntime.class.getClassLoader() + " source="
                    + (bus.getProtectionDomain().getCodeSource() == null ? "unknown"
                        : bus.getProtectionDomain().getCodeSource().getLocation());
            }
            available = true;
            failure = "none";
            return true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException error) {
            return reject("helper admission failed: " + error.getClass().getName());
        }
    }

    private static boolean reject(String reason) {
        available = false;
        failure = reason;
        fallbackPosts.incrementAndGet();
        return false;
    }

    /** Never returns to baseline, even on the first callback's exception. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void dispatch(Object event, Map map) {
        effectivePosts.incrementAndGet();
        try {
            for (Class<?> level = event.getClass(); level != Object.class; level = level.getSuperclass()) {
                Object value = map.computeIfAbsent(level, ignored -> new CopyOnWriteArrayList<>());
                if (value.getClass() != CopyOnWriteArrayList.class) {
                    available = false;
                    failure = "level changed after preflight; stopped without baseline replay";
                    throw new IllegalStateException(failure);
                }
                // Acquire one snapshot here, after callbacks from the previous hierarchy level.
                for (Object listener : (CopyOnWriteArrayList<?>) value) {
                    if (listener instanceof Consumer<?>) {
                        ((Consumer<Object>) listener).accept(event);
                    }
                }
            }
            completedPosts.incrementAndGet();
        } catch (Throwable error) {
            callbackFailures.incrementAndGet();
            dispatchFailure = error.getClass().getName();
            // ReflectEventConsumer can throw checked InvocationTargetException through Consumer.accept.
            DispatchRuntime.<RuntimeException>rethrow(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable error) throws T { throw (T) error; }

    /** Counter reset does not erase Hook delivery evidence or enable the experiment. */
    public static synchronized void reset() {
        seamPosts.set(0); effectivePosts.set(0); completedPosts.set(0);
        fallbackPosts.set(0); callbackFailures.set(0); activationStart = 0;
        dispatchFailure = "none";
    }

    public static String status() {
        String seamFailure = "unobserved";
        try {
            // Only queried by local status/control, never Hook construction or preInit.
            Class<?> bus = Class.forName("net.weavemc.api.event.EventBus", false, DispatchRuntime.class.getClassLoader());
            Object result = bus.getField("atwDispatchSeamFailure").get(null);
            seamFailure = result == null ? "none" : result.toString();
            if (result != null) { available = false; failure = seamFailure; }
        } catch (ReflectiveOperationException | LinkageError | SecurityException error) {
            available = false;
            seamFailure = "control definition unavailable/untransformed: " + error.getClass().getName();
            failure = seamFailure;
        }
        boolean active = requested && available && effectivePosts.get() > activationStart;
        return "ATW dispatch requested=" + requested + " available=" + available + " active=" + active
            + " hookTransformed=" + (observedBus == null ? 0 : 1) + " hookTransforms=" + hookTransforms.get()
            + " hookRejected=" + hookRejected.get() + " seamPosts=" + seamPosts.get()
            + " effectivePosts=" + effectivePosts.get() + " completedPosts=" + completedPosts.get()
            + " baselineFallback=" + fallbackPosts.get()
            + " dispatchFailures=" + callbackFailures.get() + " lastDispatchFailure=" + dispatchFailure
            + " failure=" + failure + " seamFailure=" + seamFailure
            + " definition=" + definition;
    }
}

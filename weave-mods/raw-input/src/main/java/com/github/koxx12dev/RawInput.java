package com.github.koxx12dev;

import com.github.koxx12dev.command.OpenFileCommand;
import com.github.koxx12dev.command.RescanCommand;
import com.github.koxx12dev.listener.UnintendedUsageWarningListener;
import com.github.koxx12dev.util.ChatUtil;
import com.github.koxx12dev.util.MouseDeltaAccumulator;
import com.github.koxx12dev.util.RawMouseHelper;
import java.lang.reflect.Constructor;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.command.CommandBus;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.StartGameEvent;
import net.weavemc.api.event.TickEvent;

/** Private API port reconstructed from the installed koxx12dev RawInput 1.0.1 jar. */
public class RawInput implements ModInitializer {
    // Discovery publishes the device; /rescan clears it. Native polling stays
    // on inputThread, which takes one device snapshot for each iteration.
    public static volatile Mouse mouse;
    private static final MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
    private static final Object sourceLock = new Object();
    private static RawSource source;
    private static long rescanGeneration;
    private static boolean discoveryMessagePending;

    /** Identity token for a selected-device epoch; allocated only on selection. */
    public static final class RawSource {
        private final Mouse device;
        private boolean activationRequested;
        private boolean publicationReady;

        private RawSource(Mouse device) {
            this.device = device;
        }
    }

    // sourceLock protects epoch changes, activation, flush and publication. Never hold
    // it across native polling, axis reads, LWJGL, Minecraft or chat calls.
    private static void reconcileSource() {
        Mouse selected = mouse;
        if ((source == null ? null : source.device) != selected) {
            deltas.drain();
            source = selected == null ? null : new RawSource(selected);
        }
    }

    /** Game-thread helper boundary: request a native flush, discard overlap. */
    public static RawSource activateSource() {
        synchronized (sourceLock) {
            reconcileSource();
            if (source != null && !source.activationRequested) {
                deltas.drain();
                source.activationRequested = true;
            }
            return source;
        }
    }

    public static long consumeDeltas(RawSource expected) {
        synchronized (sourceLock) {
            reconcileSource();
            return source == expected && expected != null ? deltas.drain() : 0L;
        }
    }

    /** /rescan invalidates even a rediscovery of the same Mouse object. */
    public static void rescan() {
        synchronized (sourceLock) {
            mouse = null;
            source = null;
            rescanGeneration++;
            deltas.drain();
            discoveryMessagePending = false;
        }
    }

    private static void invalidate(RawSource failed) {
        synchronized (sourceLock) {
            if (source != failed || mouse != failed.device) return;
            mouse = null;
            source = null;
            deltas.drain();
            discoveryMessagePending = false;
        }
    }

    static void onClientTick(TickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) return;
        boolean notify;
        synchronized (sourceLock) {
            notify = discoveryMessagePending;
            discoveryMessagePending = false;
        }
        if (notify) ChatUtil.addMessage(EnumChatFormatting.GREEN, "[RawInput] Found mouse");
    }

    /** Called by the game-owned helper; does not poll devices or consult GUI state. */
    public static long consumeDeltas() {
        synchronized (sourceLock) {
            reconcileSource();
            return deltas.drain();
        }
    }

    private static ControllerEnvironment createDefaultEnvironment() throws ReflectiveOperationException {
        Constructor<?> constructor = Class.forName("net.java.games.input.DefaultControllerEnvironment")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (ControllerEnvironment) constructor.newInstance();
    }

    @Override
    public void init() {
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            EventBus.subscribe(new UnintendedUsageWarningListener());
            CommandBus.register(new OpenFileCommand());
            return;
        }
        CommandBus.register(new RescanCommand());
        EventBus.subscribe(TickEvent.Pre.class, RawInput::onClientTick);
        EventBus.subscribe(StartGameEvent.Post.class, this::startInput);
    }

    private void startInput(StartGameEvent.Post event) {
        Minecraft.getMinecraft().mouseHelper = new RawMouseHelper();
        Thread inputThread = new Thread(() -> {
            ControllerEnvironment environment = null;
            while (true) {
                if (environment == null) {
                    try {
                        environment = createDefaultEnvironment();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    pollInput(environment);
                }
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        inputThread.setName("inputThread");
        inputThread.start();
    }

    // A single polling iteration, separated so it can be verified without
    // native input hardware or starting the game's permanent input thread.
    static void pollInput(ControllerEnvironment environment) {
        Mouse selectedMouse;
        RawSource selectedSource;
        boolean activationRequested;
        boolean publicationReady;
        long discoveryGeneration;
        synchronized (sourceLock) {
            reconcileSource();
            selectedSource = source;
            selectedMouse = selectedSource == null ? null : selectedSource.device;
            activationRequested = selectedSource != null && selectedSource.activationRequested;
            publicationReady = selectedSource != null && selectedSource.publicationReady;
            discoveryGeneration = rescanGeneration;
        }
        if (selectedMouse == null) {
            try {
                for (Controller controller : environment.getControllers()) {
                    try {
                        if (controller.getType() != Controller.Type.MOUSE) continue;
                        if (!controller.poll()) continue;
                        float px = ((Mouse) controller).getX().getPollData();
                        float py = ((Mouse) controller).getY().getPollData();
                        float epsilon = 0.1f;
                        if (px < -epsilon || px > epsilon || py < -epsilon || py > epsilon) {
                            synchronized (sourceLock) {
                                if (rescanGeneration != discoveryGeneration) return;
                                mouse = (Mouse) controller;
                                reconcileSource();
                                discoveryMessagePending = true;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Capture activation/readiness before poll. Its native interval may
            // reach back before activation even if the call starts afterward.
            try {
                if (!selectedMouse.poll()) {
                    invalidate(selectedSource);
                    return;
                }
                if (activationRequested && !publicationReady) {
                    // Only a successful poll started after activation can flush
                    // the overlapping native interval. Do not publish this sample.
                    synchronized (sourceLock) {
                        if (source == selectedSource && mouse == selectedMouse) {
                            selectedSource.publicationReady = true;
                        }
                    }
                    return;
                }
                // Retain task-entry menu observation in the worker. Ownership of
                // this cross-thread GUI read remains unresolved; tick snapshots
                // miss menu entry/exit occurring after Tick.Pre.
                if (publicationReady && Minecraft.getMinecraft().currentScreen == null) {
                    int dx = (int) selectedMouse.getX().getPollData();
                    int dy = (int) selectedMouse.getY().getPollData();
                    synchronized (sourceLock) {
                        if (source == selectedSource && mouse == selectedMouse) {
                            deltas.add(dx, dy);
                        }
                    }
                }
            } catch (RuntimeException failure) {
                // Failed components may still contain a previous successful
                // sample. Drop this epoch, then let the next loop discover.
                invalidate(selectedSource);
            }
        }
    }
}

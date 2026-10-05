package com.github.koxx12dev;

import com.github.koxx12dev.command.OpenFileCommand;
import com.github.koxx12dev.command.RescanCommand;
import com.github.koxx12dev.listener.UnintendedUsageWarningListener;
import com.github.koxx12dev.util.ChatUtil;
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

/** Private API port reconstructed from the installed koxx12dev RawInput 1.0.1 jar. */
public class RawInput implements ModInitializer {
    public static Mouse mouse;
    public static int dx;
    public static int dy;

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

    // A single original polling iteration, separated so it can be verified without
    // native input hardware or starting the game's permanent input thread.
    static void pollInput(ControllerEnvironment environment) {
        if (mouse == null) {
            try {
                for (Controller controller : environment.getControllers()) {
                    try {
                        if (controller.getType() != Controller.Type.MOUSE) continue;
                        controller.poll();
                        float px = ((Mouse) controller).getX().getPollData();
                        float py = ((Mouse) controller).getY().getPollData();
                        float epsilon = 0.1f;
                        if (px < -epsilon || px > epsilon || py < -epsilon || py > epsilon) {
                            mouse = (Mouse) controller;
                            ChatUtil.addMessage(EnumChatFormatting.GREEN, "[RawInput] Found mouse");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mouse.poll();
            if (Minecraft.getMinecraft().currentScreen == null) {
                dx += (int) mouse.getX().getPollData();
                dy += (int) mouse.getY().getPollData();
            }
        }
    }
}

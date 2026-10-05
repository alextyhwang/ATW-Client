package com.atw.optimalzone.command;

import com.atw.optimalzone.OptimalZoneMod;
import net.weavemc.api.command.CommandBus;
import net.weavemc.api.event.ChatEvent;
import net.weavemc.api.event.EventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the published 1.4.1 CommandBus/EventBus, without starting Minecraft. */
class CommandCompatibilityTest {
    private static final RecordingMod MOD = new RecordingMod();

    @BeforeAll
    static void registerCommands() {
        CommandBus.register(
                new OverlayCommand(MOD, "atwoverlay", OverlayCommand.Action.STATUS),
                new OverlayCommand(MOD, "toggleoptimalzone", OverlayCommand.Action.TOGGLE_OPTIMAL_ZONE),
                new OverlayCommand(MOD, "togglechams", OverlayCommand.Action.TOGGLE_CHAMS),
                new OverlayCommand(MOD, "toggleminimap", OverlayCommand.Action.TOGGLE_MINIMAP),
                new OverlayCommand(MOD, "togglebigmap", OverlayCommand.Action.TOGGLE_BIG_MAP),
                new OverlayCommand(MOD, "toggleinvisoverlay", OverlayCommand.Action.TOGGLE_INVIS_OVERLAY));
    }

    @BeforeEach
    void clearActions() {
        MOD.actions.clear();
    }

    @Test
    void manualCommandsUseArgumentAfterCommandName() {
        String[][] groups = {
                {"toggle", "master"}, {"optimal", "optimalzone", "zone"},
                {"projectile", "projectiles", "trajectory", "trajectories"},
                {"chams", "esp", "players"}, {"minimap", "map", "radar"},
                {"bigmap", "expandedmap", "fullscreenmap"}, {"terrain", "mapterrain"},
                {"perf", "performance", "mapperf"}, {"perfreset", "resetperf", "mapperfreset"},
                {"invis", "invisoverlay", "invisible"},
                {"debugtarget", "debugentity", "targetdebug", "entitydebug"}, {"status"}
        };
        String[] expected = {"toggle", "optimalzone", "projectiles", "chams", "minimap",
                "bigmap", "terrain", "perf", "perfreset", "invis", "debugtarget", "status"};
        for (int i = 0; i < groups.length; i++) {
            for (String alias : groups[i]) {
                MOD.actions.clear();
                ChatEvent.Sent event = new ChatEvent.Sent("/atwoverlay " + alias);
                EventBus.postEvent(event);
                assertTrue(event.isCancelled(), alias);
                assertEquals(List.of(expected[i]), MOD.actions, alias);
            }
        }
    }

    @Test
    void bareCommandsExecuteExactlyOnce() {
        String[] names = {"atwoverlay", "toggleoptimalzone", "togglechams", "toggleminimap",
                "togglebigmap", "toggleinvisoverlay"};
        String[] expected = {"status", "optimalzone", "chams", "minimap", "bigmap", "invis"};
        for (int i = 0; i < names.length; i++) {
            MOD.actions.clear();
            ChatEvent.Sent manual = new ChatEvent.Sent("/" + names[i]);
            EventBus.postEvent(manual);
            assertTrue(manual.isCancelled());
            assertEquals(List.of(expected[i]), MOD.actions);

        }
    }

    @Test
    void unknownServerCommandAndNormalChatRemainUncancelled() {
        for (String text : new String[]{"hello", "/servercommand argument"}) {
            ChatEvent.Sent event = new ChatEvent.Sent(text);
            EventBus.postEvent(event);
            assertFalse(event.isCancelled(), text);
            assertEquals(text, event.getMessage());
        }
        assertTrue(MOD.actions.isEmpty());
    }

    @Test
    void invalidSubcommandShowsUsageLocally() {
        ChatEvent.Sent event = new ChatEvent.Sent("/atwoverlay unknown");
        EventBus.postEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(1, MOD.actions.size());
        assertTrue(MOD.actions.get(0).contains("Usage: /atwoverlay"));
    }

    private static final class RecordingMod extends OptimalZoneMod {
        final List<String> actions = new ArrayList<>();
        @Override public void toggle() { actions.add("toggle"); }
        @Override public void toggleOptimalZone() { actions.add("optimalzone"); }
        @Override public void toggleProjectiles() { actions.add("projectiles"); }
        @Override public void toggleChams() { actions.add("chams"); }
        @Override public void toggleMinimap() { actions.add("minimap"); }
        @Override public void toggleBigMap() { actions.add("bigmap"); }
        @Override public void toggleMinimapTerrain() { actions.add("terrain"); }
        @Override public void sendMinimapPerformance() { actions.add("perf"); }
        @Override public void resetMinimapPerformance() { actions.add("perfreset"); }
        @Override public void toggleInvisOverlay() { actions.add("invis"); }
        @Override public void debugTargetEntity() { actions.add("debugtarget"); }
        @Override public void sendStatus() { actions.add("status"); }
        @Override public void sendChat(String message) { actions.add(message); }
    }
}

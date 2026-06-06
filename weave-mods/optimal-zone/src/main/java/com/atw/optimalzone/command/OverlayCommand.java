package com.atw.optimalzone.command;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.command.Command;
import org.jetbrains.annotations.NotNull;

public class OverlayCommand extends Command {
    private final OptimalZoneMod mod;
    private final Action defaultAction;

    public OverlayCommand(OptimalZoneMod mod, String name, Action defaultAction) {
        super(name);
        this.mod = mod;
        this.defaultAction = defaultAction;
    }

    @Override
    public void handle(@NotNull String[] args) {
        if (args.length == 0) {
            run(defaultAction);
            return;
        }

        String action = args[0].toLowerCase();
        if ("toggle".equals(action) || "master".equals(action)) {
            mod.toggle();
        } else if ("optimal".equals(action) || "optimalzone".equals(action) || "zone".equals(action)) {
            mod.toggleOptimalZone();
        } else if ("projectile".equals(action) || "projectiles".equals(action) || "trajectory".equals(action) || "trajectories".equals(action)) {
            mod.toggleProjectiles();
        } else if ("chams".equals(action) || "esp".equals(action) || "players".equals(action)) {
            mod.toggleChams();
        } else if ("minimap".equals(action) || "map".equals(action) || "radar".equals(action)) {
            mod.toggleMinimap();
        } else if ("bigmap".equals(action) || "expandedmap".equals(action) || "fullscreenmap".equals(action)) {
            mod.toggleBigMap();
        } else if ("terrain".equals(action) || "mapterrain".equals(action)) {
            mod.toggleMinimapTerrain();
        } else if ("perf".equals(action) || "performance".equals(action) || "mapperf".equals(action)) {
            mod.sendMinimapPerformance();
        } else if ("invis".equals(action) || "invisoverlay".equals(action) || "invisible".equals(action) || "footsteps".equals(action)) {
            mod.toggleInvisOverlay();
        } else if ("debugtarget".equals(action) || "debugentity".equals(action) || "targetdebug".equals(action) || "entitydebug".equals(action)) {
            mod.debugTargetEntity();
        } else if ("status".equals(action)) {
            mod.sendStatus();
        } else {
            mod.sendChat(EnumChatFormatting.YELLOW + "Usage: /atwoverlay [toggle|optimalzone|projectiles|chams|minimap|bigmap|terrain|perf|invis|debugtarget|status]");
        }
    }

    private void run(Action action) {
        switch (action) {
            case TOGGLE_OPTIMAL_ZONE:
                mod.toggleOptimalZone();
                break;
            case TOGGLE_CHAMS:
                mod.toggleChams();
                break;
            case TOGGLE_MINIMAP:
                mod.toggleMinimap();
                break;
            case TOGGLE_BIG_MAP:
                mod.toggleBigMap();
                break;
            case TOGGLE_INVIS_OVERLAY:
                mod.toggleInvisOverlay();
                break;
            case STATUS:
            default:
                mod.sendStatus();
                break;
        }
    }

    public enum Action {
        STATUS,
        TOGGLE_OPTIMAL_ZONE,
        TOGGLE_CHAMS,
        TOGGLE_MINIMAP,
        TOGGLE_BIG_MAP,
        TOGGLE_INVIS_OVERLAY
    }
}

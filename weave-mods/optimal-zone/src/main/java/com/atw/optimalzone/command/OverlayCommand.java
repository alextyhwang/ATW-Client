package com.atw.optimalzone.command;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.BlockPos;
import net.weavemc.api.command.Command;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.Locale;

public class OverlayCommand extends Command {
    private final OptimalZoneMod mod;
    private final Action defaultAction;

    public OverlayCommand(OptimalZoneMod mod, String name, Action defaultAction) {
        super(name);
        this.mod = mod;
        this.defaultAction = defaultAction;
    }

    @Override
    public void execute(@NotNull String[] args) {
        // Weave 1.x includes the command name at index 0.
        if (args.length <= 1) {
            run(defaultAction);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("trajectoryprep".equals(action)) {
            if (args.length != 3 || !Arrays.asList("on", "off", "status", "clear").contains(args[2].toLowerCase(Locale.ROOT))) {
                mod.sendChat(EnumChatFormatting.YELLOW + "Usage: /atwoverlay trajectoryprep on|off|status|clear (source experiment; startup OFF)");
            } else {
                mod.trajectoryPreparationCommand(args[2].toLowerCase(Locale.ROOT));
            }
        } else if ("toggle".equals(action) || "master".equals(action)) {
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
        } else if ("perfreset".equals(action) || "resetperf".equals(action) || "mapperfreset".equals(action)) {
            mod.resetMinimapPerformance();
        } else if ("invis".equals(action) || "invisoverlay".equals(action) || "invisible".equals(action)) {
            mod.toggleInvisOverlay();
        } else if ("debugtarget".equals(action) || "debugentity".equals(action) || "targetdebug".equals(action) || "entitydebug".equals(action)) {
            mod.debugTargetEntity();
        } else if ("status".equals(action)) {
            mod.sendStatus();
        } else {
            mod.sendChat(EnumChatFormatting.YELLOW + "Usage: /atwoverlay [toggle|optimalzone|projectiles|trajectoryprep on|off|status|clear|chams|minimap|bigmap|terrain|perf|perfreset|invis|debugtarget|status]");
        }
    }

    @Override
    public String[] getSuggestions(String[] args, BlockPos target) {
        String[] choices;
        // Unlike execute(), Weave's completion callback removes the command name.
        if (args.length == 1) {
            choices = new String[]{"toggle", "optimalzone", "projectiles", "trajectoryprep", "chams", "minimap",
                    "bigmap", "terrain", "perf", "perfreset", "invis", "debugtarget", "status"};
        } else if (args.length == 2 && "trajectoryprep".equalsIgnoreCase(args[0])) {
            choices = new String[]{"on", "off", "status", "clear"};
        } else {
            return new String[0];
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return Arrays.stream(choices).filter(choice -> choice.startsWith(prefix)).toArray(String[]::new);
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

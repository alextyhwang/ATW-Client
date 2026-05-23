package com.atw.optimalzone.command;

import com.atw.optimalzone.OptimalZoneMod;
import net.weavemc.loader.api.command.Command;
import org.jetbrains.annotations.NotNull;

public class ToggleOptimalZoneCommand extends Command {
    private final OptimalZoneMod mod;

    public ToggleOptimalZoneCommand(OptimalZoneMod mod) {
        super("toggleoptimalzone");
        this.mod = mod;
    }

    @Override
    public void handle(@NotNull String[] args) {
        mod.toggle();
    }
}

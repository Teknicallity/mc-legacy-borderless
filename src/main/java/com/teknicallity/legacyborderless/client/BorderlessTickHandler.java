package com.teknicallity.legacyborderless.client;

import com.teknicallity.legacyborderless.engine.BorderlessEngine;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;

import java.util.EnumSet;

/**
 * Drives {@link BorderlessEngine#onClientTick()} once per client tick: it keeps the "restore" window rectangle
 * current and applies a launch-in-fullscreen request once the window actually exists.
 */
public class BorderlessTickHandler implements ITickHandler {

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        // nothing
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        BorderlessEngine.onClientTick();
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }

    @Override
    public String getLabel() {
        return "LegacyBorderless:tick";
    }
}

package io.github.teknicallity.legacyborderless.client;

import io.github.teknicallity.legacyborderless.engine.BorderlessEngine;
import io.github.teknicallity.legacyborderless.engine.Log;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.settings.KeyBinding;

import java.util.EnumSet;

/**
 * Fires {@link BorderlessEngine#toggle()} when the bound key is pressed.
 * <p>
 * FML's {@code KeyHandler} calls {@link #keyDown} on the fresh key-down transition in <em>both</em> the tick-start
 * and tick-end phases (its 4th argument is {@code true} on that transition, despite being named "repeat"). With
 * {@code repeatings=false} it never fires while the key is merely held. Acting only in the tick-end phase therefore
 * yields exactly one toggle per physical press.
 */
public class BorderlessKeyHandler extends KeyBindingRegistry.KeyHandler {

    public BorderlessKeyHandler(KeyBinding keyBinding) {
        super(new KeyBinding[]{keyBinding}, new boolean[]{false});
    }

    @Override
    public String getLabel() {
        return "LegacyBorderless:key";
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }

    @Override
    public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isFirstPress) {
        if (!tickEnd) {
            return;
        }
        Log.info("Toggle key pressed (keyCode=" + kb.keyCode + "); toggling borderless.");
        BorderlessEngine.toggle();
    }

    @Override
    public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {
        // nothing
    }
}

package com.teknicallity.legacyborderless.client;

import com.teknicallity.legacyborderless.CommonProxy;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Client-side wiring: registers the rebindable "toggle borderless" key and the per-tick handler that drives
 * {@link com.teknicallity.legacyborderless.engine.BorderlessEngine}.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        // Default to F11. Vanilla's hard-coded F11 fullscreen is disabled by the coremod, so this rebindable
        // key binding is the sole owner of F11 (and can be moved to any key in Controls).
        KeyBinding toggleKey = new KeyBinding("Toggle Borderless Fullscreen", Keyboard.KEY_F11);
        KeyBindingRegistry.registerKeyBinding(new BorderlessKeyHandler(toggleKey));

        TickRegistry.registerTickHandler(new BorderlessTickHandler(), Side.CLIENT);
    }
}

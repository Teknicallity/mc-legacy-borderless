package io.github.teknicallity.legacyborderless;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Server/common proxy. There is nothing to do off the client — the mod is purely about the game window.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // no-op on the dedicated server
    }

    public void init() {
        // no-op on the dedicated server
    }
}

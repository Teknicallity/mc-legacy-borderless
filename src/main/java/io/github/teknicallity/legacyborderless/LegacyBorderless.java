package io.github.teknicallity.legacyborderless;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Mod entry point. The borderless engine works with no mod at all (the coremod injects into Minecraft directly),
 * but this piece adds the rebindable key binding and the per-tick housekeeping the engine relies on.
 * <p>
 * All client-only wiring lives behind a {@link SidedProxy} so the mod is safe to load on a dedicated server.
 */
// name/version/description come from mcmod.info (see useMetadata); modid stays here as the mod's key.
@Mod(modid = LegacyBorderless.MODID, useMetadata = true)
public class LegacyBorderless {

    public static final String MODID = "legacyborderless";

    @SidedProxy(
            clientSide = "io.github.teknicallity.legacyborderless.client.ClientProxy",
            serverSide = "io.github.teknicallity.legacyborderless.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}

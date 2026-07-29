package io.github.teknicallity.legacyborderless.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * FML core-mod entry point. Its only job is to register {@link FullscreenRedirectTransformer}, which rewrites
 * Minecraft's exclusive-fullscreen calls into borderless ones.
 * <p>
 * {@code @TransformerExclusions} keeps our own coremod/engine packages out of the transformer pipeline so they
 * load cleanly this early. The sorting index runs us after FML's deobfuscation pass; that is not strictly
 * required (the transformer matches on the stable LWJGL call + class name), but it keeps the ordering obvious.
 */
@IFMLLoadingPlugin.MCVersion("1.6.4")
@IFMLLoadingPlugin.TransformerExclusions({
        "io.github.teknicallity.legacyborderless.coremod",
        "io.github.teknicallity.legacyborderless.engine"
})
@IFMLLoadingPlugin.SortingIndex(1001)
public class LegacyBorderlessCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "io.github.teknicallity.legacyborderless.coremod.FullscreenRedirectTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // nothing to inject
    }
}

package com.console.uky.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import io.github.tox1cozz.mixinbooterlegacy.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FML core plugin. Its only job is to declare our early mixin config so the
 * SpongePowered Mixin subsystem (provided by UniMixins) applies it before the
 * game classes are loaded — which is what lets us reskin the FML splash screen.
 *
 * <p>Only the config that targets FML itself belongs here. Anything aimed at another
 * mod has to be registered later, and registering it here does lasting damage rather
 * than simply not working; see {@link LateMixins}.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("UKY Core")
@IFMLLoadingPlugin.SortingIndex(1001)
public class UkyCore implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.uky.early.client.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    /**
     * Applies an Easy/Standard switch chosen in the menu last session. Here because
     * this runs after Forge has looked for coremods and closed every jar, and before
     * it looks for mods — the one moment a mod's jar can be renamed. See {@link PackMode}.
     */
    @Override
    public void injectData(Map<String, Object> data) {
        Object dir = data.get("mcLocation");
        if (dir instanceof java.io.File) {
            try {
                PackMode.applyPending((java.io.File) dir);
            } catch (Throwable t) {
                // A switch that fails must not be a launch that fails.
                System.out.println("[UKY] pack mode: switch failed: " + t);
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

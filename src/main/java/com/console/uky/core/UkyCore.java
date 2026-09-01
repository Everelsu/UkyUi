package com.console.uky.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FML core plugin. Its only job is to declare our early mixin config so the
 * SpongePowered Mixin subsystem (provided by MixinBooter) applies it before the
 * game classes are loaded — which is what lets us reskin the FML splash screen.
 *
 * <p>{@code IEarlyMixinLoader} is deprecated in favour of MixinBooter's late loader,
 * which needs no coremod at all — and cannot be used here. The class this mixin
 * patches, {@code SplashProgress}, is loaded and run before mod discovery has
 * happened, so a config queued at mod-load time arrives after the screen it is meant
 * to replace has already been drawn. Registering the config by hand with
 * {@code Mixins.addConfiguration} is not the answer either: from a coremod
 * constructor that runs before the environment has settled, it goes through Guava
 * across two class loaders and throws {@code IllegalAccessError}. Letting MixinBooter
 * queue it, which is what this interface is for, is what works.
 *
 * <p>Only the config that targets FML itself belongs here, for a second reason on top
 * of that one: anything aimed at another mod has to be registered after mod discovery,
 * and registering it this early does lasting damage rather than simply not working —
 * see {@link LateMixins}.
 */
@SuppressWarnings("deprecation")
@IFMLLoadingPlugin.MCVersion("1.12.2")
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

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

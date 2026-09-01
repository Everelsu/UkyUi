package com.console.uky.core;

import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import zone.rong.mixinbooter.Context;
import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Mixin configs that target other mods, handed over once those mods exist.
 *
 * <p><b>These cannot be declared the way {@link UkyCore} declares ours.</b> A config
 * named in the jar manifest, or returned from an early loader, is registered while FML
 * is still loading core mods, and Mixin resolves a config's target classes the moment
 * it registers one. Ordinary mods are not on the classpath yet, so the lookup fails —
 * and LaunchWrapper remembers a class it could not find, permanently. When the mod's
 * jar joins the classpath a minute later that class is already condemned: every load
 * returns nothing, surfacing as {@code NoClassDefFoundError} inside the other mod, on
 * its own code, with our name nowhere in the stack trace.
 *
 * <p>MixinBooter's late loader is the answer to that: a class annotated
 * {@link MixinLoader}, found through FML's annotation table, asked for its configs once
 * the mod list is known. It also asks the right question — {@link Context} can be asked
 * whether a mod is present, so the config is queued only when there is something for it
 * to attach to.
 *
 * <p>One config for all of them, unlike the 1.7.10 branch, where the loader interface
 * takes a mixin list and can name them one at a time. Here the granularity is the
 * config, so the mixins inside it carry their own guards instead: each is
 * {@code @Pseudo} or names its target by string, and each is written to apply to
 * nothing quietly when its mod is absent.
 */
// Deprecated in MixinBooter in favour of a config queued at mod-load time, which is
// no use to us for the same reason UkyCore's early loader is not deprecated away: a
// config that arrives that late has already missed the classes it targets. Suppressed
// rather than worked around, exactly as UkyCore does.
@SuppressWarnings("deprecation")
@MixinLoader
public class LateMixins implements ILateMixinLoader {

    /** Mods this config has something to say about. */
    private static final String[] TARGETS = {
        // Waila (HWYLA on this version) — the block tooltip's panel and its filter.
        "waila",
        // CodeChickenLib, which is where NEI routes every item tooltip through.
        "codechickenlib", "nei", "notenoughitems",
        // The quest book: its notices, and its own copy of the tooltip code.
        "betterquesting",
    };

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.uky.mods.client.json");
    }

    /**
     * Whether any of the mods this config is for is installed.
     *
     * Every screen and overlay it restyles is client-side, so a dedicated server is
     * told no outright.
     */
    @Override
    public boolean shouldMixinConfigQueue(Context context) {
        if (!FMLLaunchHandler.side().isClient()) {
            return false;
        }
        for (int i = 0; i < TARGETS.length; i++) {
            if (context.isModPresent(TARGETS[i])) {
                return true;
            }
        }
        return false;
    }
}

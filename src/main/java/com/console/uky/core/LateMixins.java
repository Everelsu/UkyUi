package com.console.uky.core;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mixin configs that target other mods, handed over once those mods exist.
 *
 * <p><b>These cannot be declared the way {@link UkyCore} declares ours</b>, and they
 * cannot be added by hand either. Both have been tried and both fail, in different and
 * equally quiet ways:
 *
 * <ul>
 * <li>A config named in the jar manifest, or returned from {@code getMixinConfigs()},
 *     is registered while FML is still loading core mods, and Mixin resolves a config's
 *     target classes the moment it registers one. Ordinary mods are not on the
 *     classpath yet, so the lookup fails — and LaunchWrapper remembers a class it could
 *     not find, in {@code negativeResourceCache}, permanently. When the mod's jar joins
 *     the classpath a minute later that class is already condemned: every load returns
 *     nothing, surfacing as {@code NoClassDefFoundError} inside the other mod, on its
 *     own code, with our name nowhere in the stack trace. Registering the Waila config
 *     early took Waila out of the game entirely.
 * <li>Calling {@code Mixins.addConfiguration} from {@code FMLConstructionEvent} avoids
 *     that, and does nothing. By then the transformer is long past the point where it
 *     goes looking for configs it has not seen, so the config sits in the pending list
 *     and is never selected. No error, no warning, no mixin.
 * </ul>
 *
 * <p>The mechanism that does work is this one, which is what every mod in a GTNH-shaped
 * pack that mixes into another mod uses: a class annotated {@link LateMixin}, found
 * through FML's annotation table, asked for its config once the mod list is known.
 * GTNHMixins adds the config and then forces the select-and-prepare pass by hand, which
 * is the step the two approaches above are each missing one half of.
 *
 * <p>It also asks the right question. {@link #getMixins} is handed the set of loaded
 * mods, so a mixin is offered only when the mod it aims at is actually installed —
 * rather than being registered and left to fail politely.
 */
@LateMixin
public class LateMixins implements ILateMixinLoader {

    /** The config itself declares no mixins; the list below is the whole of it. */
    @Override
    public String getMixinConfig() {
        return "mixins.uky.mods.client.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<String>();
        // Every screen and overlay this mod restyles is client-side; a dedicated
        // server has nothing here to apply.
        if (!FMLLaunchHandler.side().isClient()) {
            return mixins;
        }
        // "Waila" with the capital, which is what its mcmod.info declares — matched
        // loosely all the same, because a fork is free to disagree about the capital
        // and nothing else about this depends on getting it exactly right.
        if (isLoaded(loadedMods, "Waila")) {
            mixins.add("MixinWailaOverlay");
        }
        return mixins;
    }

    private static boolean isLoaded(Set<String> loadedMods, String modId) {
        for (String loaded : loadedMods) {
            if (modId.equalsIgnoreCase(loaded)) {
                return true;
            }
        }
        return false;
    }
}

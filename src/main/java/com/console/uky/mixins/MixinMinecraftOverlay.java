package com.console.uky.mixins;

import com.console.uky.client.splash.UkySplash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hands every loading overlay to this mod's loading screen as it is put up.
 *
 * An event cannot do this: the start-up overlay goes up in Minecraft's constructor and
 * covers mod loading, and NeoForge does not start its game bus until mod loading is over.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftOverlay {

    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    private Overlay uky$takeOverLoading(Overlay overlay) {
        return UkySplash.wrap(overlay);
    }
}

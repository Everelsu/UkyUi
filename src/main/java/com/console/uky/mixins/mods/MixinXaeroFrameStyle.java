package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds this mod's frame to Xaero's list of frame styles.
 *
 * <p>The list is the setting: how far their button can be clicked comes from its length,
 * the label on it comes from the name at the chosen index, and "off" is the last entry
 * rather than a number. A name put into it one place from the end is a style in their
 * menu — and their own three keep meaning exactly what they did.
 *
 * <p>Here rather than anywhere later because the setting is built from the list's length
 * the first time anything reads it, and this class holding it is what is read. The end of
 * its own initialiser is the last moment the list is only a list.
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.common.config.MinimapConfigConstants", remap = false)
public abstract class MixinXaeroFrameStyle {

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 0)
    private static void uky$addFrameStyle(CallbackInfo ci) {
        XaeroFrame.addFrameStyle();
    }
}

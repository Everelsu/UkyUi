package com.console.uky.mixins.mods;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Xaero's minimap and its other on-screen panels off the debug screen, and off
 * the screen while the player is dead.
 *
 * <p>This build of Xaero for 1.7.10 has no "hide under F3" of its own: its HUD pass
 * draws every interface whatever else is on screen, so the minimap sat on top of the
 * F3 text. Its later versions hide under F3 by default, and this does the same — while
 * F3 is open, none of its interfaces are drawn; close it and they are back as they were.
 *
 * <p>{@code @Pseudo} and a {@code targets} string because Xaero is not on the compile
 * classpath; the config is not required, and {@code require = 0} keeps a renamed
 * method from failing the launch — the map simply stays visible under F3, as before.
 */
@Pseudo
@Mixin(targets = "xaero.common.interfaces.render.InterfaceRenderer", remap = false)
public abstract class MixinXaeroInterfaces {

    @Inject(method = "renderInterfaces(Lxaero/common/XaeroMinimapSession;F)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void uky$hideUnderDebugScreen(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        // Under F3, and while dead: the death screen (ours, or ukycorpses' death camera)
        // is no place for a map of where you were.
        if (mc.gameSettings != null && mc.gameSettings.showDebugInfo
                || mc.thePlayer != null && mc.thePlayer.getHealth() <= 0.0F) {
            ci.cancel();
        }
    }
}

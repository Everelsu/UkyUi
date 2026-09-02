package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the minimap's render, which is what tells the frame when to look and when to
 * draw.
 *
 * <p>The start is what makes the map's quad identifiable: it is the first one of the
 * render, and without a mark for where a render begins there is no such thing as first.
 * The end is the one honest place to draw ours — the coordinates were read in whatever
 * space Xaero set up, the single push and pop of the matrix inside this method is
 * balanced before it returns, so the space here is the space they were measured in.
 *
 * <p>It cannot be drawn at the cancelled frame pieces themselves, tempting as that is.
 * Those run in the middle of a buffer Xaero has open and is still filling, and putting
 * our own geometry into it in immediate mode would corrupt the draw they are part of.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapRenderer", remap = false)
public abstract class MixinXaeroMinimap {

    @Inject(method = "renderMinimap", at = @At("HEAD"), remap = false, require = 0)
    private void uky$beginMinimap(CallbackInfo ci) {
        XaeroFrame.begin();
    }

    @Inject(method = "renderMinimap", at = @At("RETURN"), remap = false, require = 0)
    private void uky$drawOurFrame(CallbackInfo ci) {
        XaeroFrame.end();
    }
}

package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws our frame where theirs would have been, once the map is finished.
 *
 * <p>The pieces are collected as they are cancelled — see {@link MixinXaeroFrame} — and
 * this is where the box they add up to is drawn around. The end of the same method is
 * the only honest place for it: the rectangles are in whatever space Xaero has set up,
 * and the one push/pop of the matrix inside this method happens after the frame and is
 * balanced before it returns, so the space here is the space they were measured in.
 *
 * <p>It cannot be done at the cancelled call itself, tempting as that is. Those run in
 * the middle of a buffer Xaero has open and is still filling; drawing our own geometry
 * into the same buffer, in immediate mode, would corrupt the draw they are part-way
 * through.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapRenderer", remap = false)
public abstract class MixinXaeroMinimap {

    @Inject(method = "renderMinimap", at = @At("RETURN"), remap = false, require = 0)
    private void uky$drawOurFrame(CallbackInfo ci) {
        XaeroFrame.drawCollected();
    }
}

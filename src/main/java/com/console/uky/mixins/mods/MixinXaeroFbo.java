package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the pass that draws the map into Xaero's frame buffer.
 *
 * <p>With the frame buffer on — which it is by default — the map is drawn twice per
 * render: once into the buffer, at the buffer's own origin, and once onto the screen
 * where the player sees it. Both go through the same helper with the same kind of quad,
 * and the buffer's comes first, so without this the frame would be measured from a
 * rectangle that is not on the screen at all.
 *
 * <p>Bracketing the pass is enough: {@link MixinXaeroFrame} ignores quads drawn inside
 * it, and the first one after it is the map.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapFBORenderer", remap = false)
public abstract class MixinXaeroFbo {

    @Inject(method = "renderChunksToFBO", at = @At("HEAD"), remap = false, require = 0)
    private void uky$beginOffScreen(CallbackInfo ci) {
        XaeroFrame.beginOffScreen();
    }

    @Inject(method = "renderChunksToFBO", at = @At("RETURN"), remap = false, require = 0)
    private void uky$endOffScreen(CallbackInfo ci) {
        XaeroFrame.endOffScreen();
    }
}

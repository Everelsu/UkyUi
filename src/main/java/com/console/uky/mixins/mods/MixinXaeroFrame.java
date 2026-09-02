package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the minimap off the two draw calls that make it: the map, and its frame.
 *
 * <p>Both go through this one helper class, which is Xaero's own and which nothing else
 * calls, so this is the whole of what the frame needs to know.
 *
 * <p>{@code drawMyTexturedModalRect} draws the square map itself, once, before anything
 * else in a render — and its arguments are the map's x, y, width and height. That is
 * where our frame goes, taken rather than calculated. Three earlier versions of this
 * calculated it, from their settings and then from their frame's own pieces, and each
 * one was off by a different amount at a different map size.
 *
 * <p>{@code addTexturedRectToExistingBuffer} draws their frame, eight rectangles of it —
 * four corners and four edges. Cancelling those removes their frame precisely, which is
 * what makes ours a replacement for it rather than a second frame drawn over one.
 *
 * <p>{@code remap = false} throughout: Xaero is not on the compile classpath and its
 * names are its own. The map quad is named with its descriptor because the class has two
 * methods by that name and it is all primitives, so it is safe to write; the frame piece
 * is named without one, because its descriptor names a vanilla type and this project has
 * no refmap to translate it (see {@code MixinGuiScreenTooltip}) — and that name is unique
 * in the class anyway.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapRendererHelper", remap = false)
public abstract class MixinXaeroFrame {

    @Inject(method = "drawMyTexturedModalRect(FFIIFFF)V", at = @At("HEAD"),
            remap = false, require = 0)
    private void uky$takeMapQuad(float x, float y, int u, int v, float width, float height,
                                 float textureSize, CallbackInfo ci) {
        XaeroFrame.takeMapQuad(x, y, width, height);
    }

    @Inject(method = "addTexturedRectToExistingBuffer", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void uky$takeFramePiece(BufferBuilder buffer, float x, float y, int u, int v,
                                    int width, int height, CallbackInfo ci) {
        if (XaeroFrame.takeFramePiece(x, y, width, height)) {
            ci.cancel();
        }
    }
}

package com.console.uky.mixins.mods;

import com.console.uky.client.mods.XaeroFrame;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over the eight rectangles Xaero's minimap frame is made of.
 *
 * <p>This is what makes ours <em>a frame style of theirs</em> rather than a second
 * frame drawn on top of one. The map's own renderer builds its frame out of eight
 * textured rectangles — four corners and four edges — and every one of them goes
 * through this one helper method, which nothing else in the mod calls. Cancelling them
 * removes their frame exactly where their frame is drawn, and the rectangles say where
 * that is, to the pixel, in whatever coordinate space they happen to be drawing in.
 *
 * <p>That is worth more than it sounds. The first version of this drew our frame from
 * the overlay pass, working the map's box out from the sizes Xaero exposes, and it was
 * wrong twice — the box included a margin the map does not, and the corner belonged to
 * the interface rather than to the map. Reading the frame's own rectangles cannot be
 * wrong about where the frame goes: it is the same eight numbers their own frame was
 * about to be drawn with.
 *
 * <p>Whichever of their three frame styles is picked, ours replaces it; "off" draws
 * nothing at all, ours included, because then there are no rectangles to take. A map
 * set to round is left alone — that frame is an ellipse and comes from somewhere else.
 *
 * <p>{@code remap = false} and the method named without its descriptor: Xaero is not on
 * the compile classpath and its names are its own, so nothing here needs remapping —
 * and this project has no refmap, which a descriptor naming a vanilla type would need
 * (see {@code MixinGuiScreenTooltip}). The name is unique in the class.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapRendererHelper", remap = false)
public abstract class MixinXaeroFrame {

    @Inject(method = "addTexturedRectToExistingBuffer", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void uky$takeFramePiece(BufferBuilder buffer, float x, float y, int u, int v,
                                    int width, int height, CallbackInfo ci) {
        if (XaeroFrame.takeFramePiece(x, y, width, height)) {
            ci.cancel();
        }
    }
}

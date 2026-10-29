package com.console.uky.mixins.mods;

import com.console.uky.client.gui.UkyTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The tooltip box CodeChickenLib draws, drawn as one of our panels instead.
 *
 * <p><b>Why this exists at all.</b> {@link com.console.uky.mixins.MixinGuiScreenTooltip}
 * takes {@code GuiScreen.drawHoveringText}, which is every tooltip in the game right up
 * until NEI is installed. NEI transforms {@code GuiContainer} so that tooltips go to
 * {@code GuiContainerManager.renderToolTips} instead, and that draws the box through
 * {@code GuiDraw} — so on any pack with NEI in it, the vanilla method is never called
 * and every tooltip over an item comes out in vanilla's purple frame. It looked exactly
 * like the mixin had failed to apply, and it had not: the tooltip simply stopped going
 * past it. That is why this is only visible in a built pack and never in development,
 * where there is no NEI.
 *
 * <p><b>Only the box.</b> {@code drawTooltipBox} draws the background and the border
 * and nothing else — the caller writes the text itself afterwards. Taking it means the
 * contents are untouched down to the last detail: NEI's paging for a tooltip taller
 * than the screen, the {@code ITooltipLineHandler} lines that packs use to put fluid
 * bars and item grids inside a tooltip, and whatever colours a {@code RenderTooltipEvent}
 * handler asked for. Taking the whole of {@code drawMultilineTip} instead would have
 * meant re-implementing all three, and losing them silently wherever the guess was wrong.
 *
 * <p>Both overloads are hooked and both are optional. The four-argument one is what
 * CodeChickenLib has always had; the eight-argument one carries the colours and is
 * newer. The four delegates to the eight where both exist, and cancelling at the head
 * of the four means the eight is never reached — so a box is drawn once either way,
 * and a version carrying only one of them is still covered.
 *
 * <p>{@code targets} rather than a class literal, and {@code remap = false} throughout:
 * this mod does not compile against CodeChickenLib, and none of these names is
 * Minecraft's, so there is nothing to remap and nothing to resolve at build time.
 *
 * @see com.console.uky.core.LateMixins for why this is registered late rather than early
 */
@Mixin(targets = "codechicken.lib.gui.GuiDraw", remap = false)
public abstract class MixinCclTooltip {

    /** {@code drawTooltipBox(x, y, w, h)} — the palette's own colours. */
    @Inject(method = "drawTooltipBox(IIII)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void uky$box(int x, int y, int w, int h, CallbackInfo ci) {
        if (UkyTooltip.panel(x, y, w, h)) {
            ci.cancel();
        }
    }

    /**
     * {@code drawTooltipBox(x, y, w, h, bgStart, bgEnd, borderStart, borderEnd)}.
     *
     * The four colours are dropped on purpose. They are the vanilla gradient, or
     * whatever a {@code RenderTooltipEvent} handler put in its place, and this is
     * replacing that gradient rather than tinting with it.
     */
    @Inject(method = "drawTooltipBox(IIIIIIII)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void uky$colouredBox(int x, int y, int w, int h, int backgroundStart,
                                        int backgroundEnd, int borderStart, int borderEnd,
                                        CallbackInfo ci) {
        if (UkyTooltip.panel(x, y, w, h)) {
            ci.cancel();
        }
    }
}

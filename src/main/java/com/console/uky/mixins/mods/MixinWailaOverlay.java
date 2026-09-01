package com.console.uky.mixins.mods;

import com.console.uky.client.mods.WailaHidden;
import com.console.uky.client.mods.WailaPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands Waila's tooltip background over to {@link WailaPanel}.
 *
 * {@code drawTooltipBox} is the whole of Waila's box: its caller measures the tooltip,
 * calls this with the rectangle and three colours, and then draws the icon and the
 * text over the top. Cancelling it therefore replaces the background and only the
 * background — the contents are still Waila's, still drawn by Waila, still including
 * whatever every other mod in the pack has added to them.
 *
 * <p>{@code @Pseudo} with a {@code targets} string rather than a class literal because
 * Waila is not on the compile classpath and may not be in the pack at all. The config
 * this mixin belongs to is not {@code required}, so an absent Waila costs a line in
 * the log and nothing else. {@code remap = false} throughout: this is a mod class, and
 * its names are the same in a development workspace and in a production jar.
 *
 * <p>The signature is spelled out. Waila declares one method by this name, but a
 * descriptor that stops matching is a failure at start-up with the method named in it,
 * whereas a bare name that starts matching something else is a failure at the first
 * block anyone looks at.
 */
@Pseudo
@Mixin(targets = "mcp.mobius.waila.overlay.OverlayRenderer", remap = false)
public abstract class MixinWailaOverlay {

    // require = 0 like the two below it, and for a sharper reason on this version:
    // 1.12's Waila is HWYLA, a fork that has had years to move this method. A box we
    // cannot take is Waila drawing its own, which is what the config switch does
    // anyway; a hard failure here would take the tooltip out of the game entirely.
    @Inject(method = "drawTooltipBox(IIIIIII)V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void uky$drawOurPanel(int x, int y, int width, int height,
                                         int background, int gradient1, int gradient2,
                                         CallbackInfo ci) {
        if (WailaPanel.draw(x, y, width, height)) {
            ci.cancel();
        }
    }

    /**
     * Suppresses the tooltip outright for a block on the config's hidden list.
     *
     * <p>{@code isOverlayVisible} is already the question "should there be a tooltip
     * right now" — it is what Waila asks before building one, not merely before drawing
     * it — so answering it is both the cheapest and the most complete place to say no.
     * Nothing is measured, nothing is asked of forty providers, and nothing is drawn.
     *
     * <p>Two hooks for one job because the forks disagree about which method that is:
     * the GTNH fork this pack ships has {@code isOverlayVisible}, while Mobius's
     * original and the ports of it gate the same decision inside a no-argument
     * {@code renderOverlay}. Each carries {@code require = 0}, so whichever one this
     * pack's Waila does not have is quietly skipped rather than failing the mixin — and
     * a Waila that has neither keeps its tooltip, which is the same as the feature
     * being off.
     *
     * @see WailaHidden
     */
    @Inject(method = "isOverlayVisible()Z", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void uky$hideListedBlock(CallbackInfoReturnable<Boolean> cir) {
        if (WailaHidden.hideTarget()) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /** The same suppression, for a Waila whose decision lives here instead. */
    @Inject(method = "renderOverlay()V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void uky$hideListedBlockLegacy(CallbackInfo ci) {
        if (WailaHidden.hideTarget()) {
            ci.cancel();
        }
    }
}

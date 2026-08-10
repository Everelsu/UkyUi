package com.console.uky.mixins.mods;

import com.console.uky.client.mods.WailaPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "drawTooltipBox(IIIIIII)V", at = @At("HEAD"), cancellable = true,
            remap = false)
    private static void uky$drawOurPanel(int x, int y, int width, int height,
                                         int background, int gradient1, int gradient2,
                                         CallbackInfo ci) {
        if (WailaPanel.draw(x, y, width, height)) {
            ci.cancel();
        }
    }
}

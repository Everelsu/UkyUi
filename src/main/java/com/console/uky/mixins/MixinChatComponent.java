package com.console.uky.mixins;

import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The chat lines on this mod's panel: the backdrop colour instead of plain black, and
 * the accent rail down the left that every panel here carries.
 *
 * Only the fill behind each line. Everything else — which lines show, how they fade,
 * scrolling, the tag marks, every chat setting — stays vanilla's.
 */
@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void uky$lineBackground(GuiGraphics g, int x1, int y1, int x2, int y2, int colour) {
        if (!UiConfig.redesignChat) {
            g.fill(x1, y1, x2, y2, colour);
            return;
        }
        int alpha = colour >>> 24;
        g.fill(x1, y1, x2, y2, (alpha << 24) | (Theme.background & 0xFFFFFF));
        // The rail is as strong as the line is faded in, and a touch stronger than the
        // fill, so it reads as an edge rather than as more background.
        int rail = Math.min(255, alpha * 2);
        g.fill(x1, y1, x1 + 1, y2, (rail << 24) | (Theme.accent & 0xFFFFFF));
    }
}

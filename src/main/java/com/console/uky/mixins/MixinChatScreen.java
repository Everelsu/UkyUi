package com.console.uky.mixins;

import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The chat input as one of this mod's fields: a dark box with the accent line under it,
 * the way every text field in this interface is drawn. Typing, history and suggestions
 * are vanilla's untouched.
 */
@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void uky$inputBackground(GuiGraphics g, int x1, int y1, int x2, int y2, int colour) {
        if (!UiConfig.redesignChat) {
            g.fill(x1, y1, x2, y2, colour);
            return;
        }
        int alpha = Math.max(colour >>> 24, 0xB0);
        g.fill(x1, y1, x2, y2, (alpha << 24) | (Theme.background & 0xFFFFFF));
        g.fill(x1, y1, x1 + 2, y2, Theme.accent | 0xFF000000);
        g.fill(x1, y2 - 1, x2, y2, (0xC0 << 24) | (Theme.accent & 0xFFFFFF));
    }
}

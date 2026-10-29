package com.console.uky.mixins;

import com.console.uky.client.gui.UkyTooltip;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hands every tooltip in the game over to {@link UkyTooltip}.
 *
 * <p>One method, and it is the only one worth taking: {@code renderToolTip} builds an
 * item's lines and hands them here, {@code func_146283_a} passes a plain list of
 * strings here, and every mod screen that shows a box of text ends up here too. Taking
 * it covers the inventory, the creative tabs, chat hovers, and whatever a pack's
 * machines put in front of the player, without knowing about any of them.
 *
 * <p><b>{@code remap = false} is not an oversight.</b> {@code drawHoveringText} is
 * Forge's, not Mojang's: Forge split vanilla's tooltip method in two so that an item
 * could supply its own font renderer, and the half it added keeps its readable name in
 * a production jar exactly as it has one here. Asking Mixin to remap it would send it
 * looking for an SRG name that was never issued — and this project has no refmap, so a
 * name that needed one would work in development and fail in a built pack.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreenTooltip {

    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void uky$drawOurTooltip(List lines, int x, int y, FontRenderer font, CallbackInfo ci) {
        if (UkyTooltip.draw((GuiScreen) (Object) this, lines, x, y, font)) {
            ci.cancel();
        }
    }
}

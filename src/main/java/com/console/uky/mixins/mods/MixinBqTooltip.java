package com.console.uky.mixins.mods;

import com.console.uky.client.gui.UkyTooltip;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * BetterQuesting's tooltips, drawn as one of our panels and made to fit the screen.
 *
 * <p><b>The third place tooltips come from.</b> The quest book reaches neither of the
 * hooks that already exist: it does not call {@code GuiScreen.drawHoveringText}, so
 * {@link com.console.uky.mixins.MixinGuiScreenTooltip} never sees it, and it does not go
 * through CodeChickenLib either, so {@link MixinCclTooltip} does not either. It has its
 * own copy of vanilla's tooltip code in {@code RenderUtils}, and a copy is exactly what
 * nothing can hook.
 *
 * <p><b>Only one method needs taking.</b> There are two overloads and the one without an
 * {@code ItemStack} is a one-line delegation to the one with it, passing null — so the
 * eight-argument form is the whole of it. The stack it is named for is never rendered:
 * the body draws gradient rectangles and text and nothing else, which is what makes
 * cancelling it safe. Everything the tooltip says arrives in the list.
 *
 * <p><b>Why it matters more here than anywhere else.</b> Vanilla's layout assumes a
 * tooltip is a few short lines, and BetterQuesting inherited that assumption along with
 * the code. A quest chain listing every task in it produces forty lines, and the copy
 * wraps to a width but never checks the height — so the box runs off the top of the
 * screen and off the bottom at the same time, and the half a player wanted to read is
 * the half that is not there. Wrapping, scaling and counting the remainder is the whole
 * point of {@link UkyTooltip}, and this is the place with the most to gain from it.
 *
 * <p>{@code targets} rather than a class literal, and {@code remap = false} throughout:
 * this mod does not compile against BetterQuesting, and none of these names is
 * Minecraft's.
 *
 * @see com.console.uky.core.LateMixins for why this is registered late rather than early
 */
@Mixin(targets = "betterquesting.api.utils.RenderUtils", remap = false)
public abstract class MixinBqTooltip {

    /**
     * {@code drawHoveringText(stack, text, x, y, screenWidth, screenHeight, maxTextWidth, font)}.
     *
     * <p>{@code x} and {@code y} are the pointer rather than the box — the code is
     * vanilla's, offsets and all — so they go straight through.
     *
     * <p>{@code maxTextWidth} is dropped on purpose. It is the caller's idea of how wide
     * the box may be, and the width a tooltip wraps to is already a setting of this mod's
     * that applies to every other tooltip in the game; honouring a per-call figure here
     * would make the quest book the one place that ignores it.
     */
    @Inject(method = "drawHoveringText(Lnet/minecraft/item/ItemStack;Ljava/util/List;IIIIILnet/minecraft/client/gui/FontRenderer;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void uky$questTooltip(ItemStack stack, List text, int x, int y,
                                         int screenWidth, int screenHeight,
                                         int maxTextWidth, FontRenderer font,
                                         CallbackInfo ci) {
        if (UkyTooltip.draw(screenWidth, screenHeight, text, x, y, font)) {
            ci.cancel();
        }
    }
}

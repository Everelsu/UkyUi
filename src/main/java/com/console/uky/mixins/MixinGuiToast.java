package com.console.uky.mixins;

import com.console.uky.client.gui.AchievementToast;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the advancement toast over to {@link AchievementToast}.
 *
 * <p>There is no event for this. A toast is queued straight onto {@code GuiToast} by
 * whatever produced it and drawn by the same class a frame later, and nothing
 * announces either step. So both are taken here — the queue and the draw — and each is
 * cancelled only when we actually took it. With the config switch off every one of
 * them falls through and vanilla's own toast appears, which is the point of having the
 * switch.
 *
 * <p><b>Only advancements.</b> The same class carries the recipe unlock, the tutorial
 * hints and the "world backed up" notice, and those are the game's own business:
 * {@code AchievementToast} looks at what it was handed and says no to anything that is
 * not an advancement, which leaves that toast queued and drawn as usual. Our own draw
 * is cancelled the moment we have nothing on screen, so the two coexist rather than
 * one shutting the other out.
 *
 * <p>Every selector below is a {@code func_} name and stays one after reobfuscation,
 * so no refmap is involved: MCP left these unnamed, and SRG spells them the same way.
 * A method that <em>has</em> a readable name could not be targeted this way — see the
 * chat, which is done through Forge's own overlay event for exactly that reason.
 *
 * <p>Descriptors are spelled out. A descriptor that stops matching fails at start-up
 * with the method named in the log; a bare name that starts matching something else
 * fails at whatever it hit, months later.
 *
 * <p>This replaced a mixin on {@code GuiAchievement}, which is the same idea against
 * the system 1.7.10 had. 1.12 deleted achievements outright; the toast queue is where
 * the announcement lives now.
 */
@Mixin(GuiToast.class)
public abstract class MixinGuiToast {

    /**
     * A toast is being queued. Ours takes the advancement ones.
     *
     * {@code func_192988_a} is {@code add(IToast)}.
     */
    @Inject(method = "func_192988_a(Lnet/minecraft/client/gui/toasts/IToast;)V",
            at = @At("HEAD"), cancellable = true)
    private void uky$queue(IToast toast, CallbackInfo ci) {
        if (AchievementToast.showVanillaToast(toast)) {
            ci.cancel();
        }
    }

    /**
     * The draw pass. {@code func_191783_a} is {@code drawToast(ScaledResolution)}.
     *
     * Not cancelled unless our own panel is what is on screen: vanilla's queue may
     * still hold a recipe or a tutorial toast, and those have to go on being drawn.
     */
    @Inject(method = "func_191783_a(Lnet/minecraft/client/gui/ScaledResolution;)V",
            at = @At("HEAD"), cancellable = true)
    private void uky$draw(ScaledResolution resolution, CallbackInfo ci) {
        if (AchievementToast.draw()) {
            ci.cancel();
        }
    }

    /**
     * The game clearing its toasts — on disconnect, and when the advancements screen
     * takes over. Not cancelled: vanilla's own queue should still be emptied whether
     * or not we are the ones drawing. {@code func_191788_b} is {@code clear()}.
     */
    @Inject(method = "func_191788_b()V", at = @At("HEAD"))
    private void uky$clear(CallbackInfo ci) {
        AchievementToast.clear();
    }
}

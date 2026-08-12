package com.console.uky.mixins;

import com.console.uky.client.gui.AchievementToast;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.stats.Achievement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the achievement popup over to {@link AchievementToast}.
 *
 * There is no event for this. The popup is not an overlay element and not a screen:
 * {@code Minecraft.runGameLoop} calls {@code GuiAchievement} directly, after the world
 * and after whatever screen is open, and nothing announces it on the way past. So the
 * three methods that make it up are taken here — the two that queue a popup, and the
 * one that draws it — and each is cancelled only when we actually took it. With the
 * config switch off every one of them falls through and vanilla's own box appears,
 * which is the point of having the switch.
 *
 * <p>Every selector below is a {@code func_} name and stays one after reobfuscation,
 * so no refmap is involved: MCP left these unnamed, and SRG spells them the same way.
 * A method that <em>has</em> a readable name could not be targeted this way — see the
 * chat, which is done through Forge's own overlay event for exactly that reason.
 *
 * <p>Descriptors are spelled out. A descriptor that stops matching fails at start-up
 * with the method named in the log; a bare name that starts matching something else
 * fails at whatever it hit, months later.
 */
@Mixin(GuiAchievement.class)
public abstract class MixinGuiAchievement {

    /** An achievement was earned; vanilla would show "Achievement get!" over its name. */
    @Inject(method = "func_146256_a(Lnet/minecraft/stats/Achievement;)V",
            at = @At("HEAD"), cancellable = true)
    private void uky$queueUnlocked(Achievement achievement, CallbackInfo ci) {
        if (AchievementToast.show(achievement)) {
            ci.cancel();
        }
    }

    /** The inventory hint — the same popup used to describe rather than announce. */
    @Inject(method = "func_146255_b(Lnet/minecraft/stats/Achievement;)V",
            at = @At("HEAD"), cancellable = true)
    private void uky$queueHint(Achievement achievement, CallbackInfo ci) {
        if (AchievementToast.showHint(achievement)) {
            ci.cancel();
        }
    }

    @Inject(method = "func_146254_a()V", at = @At("HEAD"), cancellable = true)
    private void uky$draw(CallbackInfo ci) {
        if (AchievementToast.draw()) {
            ci.cancel();
        }
    }

    /**
     * The game clearing the popup — on disconnect, and when the stats screen takes
     * over. Not cancelled: vanilla's own state should still be cleared whether or not
     * we are the ones drawing.
     */
    @Inject(method = "func_146257_b()V", at = @At("HEAD"))
    private void uky$clear(CallbackInfo ci) {
        AchievementToast.clear();
    }
}

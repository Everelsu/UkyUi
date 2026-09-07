package com.console.uky.mixins;

import com.console.uky.client.splash.UkySplash;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of that heartbeat: every step of every bar.
 *
 * <p>A bar is pushed once and stepped many times, so this is what actually paces the
 * loading screen in the mode that has no thread — {@link MixinProgressManager} says
 * when a bar is finished with, and this says when it moved.
 *
 * <p>Drawing is capped inside {@code pump}, so a mod reporting a thousand steps a
 * second costs a field read a thousand times and twenty frames.
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.fml.common.ProgressManager$ProgressBar", remap = false)
public abstract class MixinProgressBar {

    @Inject(method = "step", at = @At("RETURN"), remap = false, require = 0)
    private void uky$pumpOnStep(CallbackInfo ci) {
        UkySplash.pump();
    }
}

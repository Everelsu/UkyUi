package com.console.uky.mixins;

import com.console.uky.client.splash.UkySplash;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the mod-loading screen over to {@link UkySplash}.
 *
 * Cancelling {@code start()} leaves FML's {@code enabled} flag false, which makes
 * its {@code pause}/{@code resume}/{@code finish} no-ops all by themselves. The
 * one method that behaves the other way round is {@code drawVanillaScreen}: when
 * disabled it draws Mojang's loading screen, which would paint straight over
 * ours — so that one is cancelled too.
 *
 * {@code remap = false} because SplashProgress is an FML class and is never
 * obfuscated; the same descriptor resolves in dev and in production.
 */
@Mixin(value = SplashProgress.class, remap = false)
public abstract class MixinSplashProgress {

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private static void uky$takeOverSplash(CallbackInfo ci) {
        if (UkySplash.start()) {
            ci.cancel();
        }
    }

    @Inject(method = "finish", at = @At("HEAD"), cancellable = true, remap = false)
    private static void uky$finishSplash(CallbackInfo ci) {
        if (UkySplash.isRunning()) {
            UkySplash.finish();
            ci.cancel();
        }
    }

    @Inject(method = "drawVanillaScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void uky$suppressVanillaScreen(CallbackInfo ci) {
        if (UkySplash.isRunning()) {
            ci.cancel();
        }
    }
}

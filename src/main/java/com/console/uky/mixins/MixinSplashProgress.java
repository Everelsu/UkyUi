package com.console.uky.mixins;

import com.console.uky.client.splash.UkySplash;
import cpw.mods.fml.client.SplashProgress;
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

    /**
     * The pause the game takes when it wants the graphics driver to itself.
     *
     * FML calls these around the texture stitch and the resource reload — the heavy
     * GL phases, and most of a big pack's start-up. Its own loading screen stops
     * drawing for the duration; ours never heard about it, because cancelling
     * {@code start} leaves {@code enabled} false and both of these then return before
     * doing anything. Hooked at HEAD, so they are seen whatever that flag says.
     *
     * <p>Not cancellable, and no need: with the flag false the rest of the method is
     * a no-op anyway, so letting it run changes nothing. {@code require = 0} because
     * these two are an optimisation rather than the takeover itself — on a Forge
     * build that has moved them, the screen should go on working, not refuse to load.
     */
    @Inject(method = "pause", at = @At("HEAD"), remap = false, require = 0)
    private static void uky$pause(CallbackInfo ci) {
        if (UkySplash.isRunning()) {
            UkySplash.pause();
        }
    }

    @Inject(method = "resume", at = @At("HEAD"), remap = false, require = 0)
    private static void uky$resume(CallbackInfo ci) {
        if (UkySplash.isRunning()) {
            UkySplash.resume();
        }
    }
}

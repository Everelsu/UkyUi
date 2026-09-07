package com.console.uky.mixins;

import com.console.uky.client.splash.UkySplash;
import net.minecraftforge.fml.common.ProgressManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The heartbeat the loading screen is drawn on when it has no thread of its own.
 *
 * <p>On a loader running this game on LWJGL 3 the screen cannot have a second GL
 * context, so it is drawn from the loading thread between the things being loaded —
 * and this is where those gaps are. Every mod that finishes a step says so through
 * FML's progress manager, and one frame is drawn if one is due.
 *
 * <p>The same calls the bars on that screen are read from, which is the point: a frame
 * is drawn exactly when there is something new to show on it, and never for nothing.
 *
 * <p>Costs nothing when the screen is drawing itself on a thread, and nothing at all
 * once loading is over: the first line of {@code pump} is a field nobody sets again.
 *
 * <p>{@code remap = false} because ProgressManager is an FML class and is never
 * obfuscated — the same names resolve in a dev run and in a production jar.
 */
@Mixin(value = ProgressManager.class, remap = false)
public abstract class MixinProgressManager {

    @Inject(method = "pop", at = @At("RETURN"), remap = false, require = 0)
    private static void uky$pumpOnPop(CallbackInfo ci) {
        UkySplash.pump();
    }
}

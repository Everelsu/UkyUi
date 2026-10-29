package com.console.uky.mixins.mods;

import com.console.uky.client.mods.QuestToast;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops BetterQuesting drawing its own "quest complete" title, when we have taken it.
 *
 * <p><b>Why the renderer and not the scheduler.</b> A notice is created by
 * {@code ScheduleNotice}, which would be the natural place to intercept one, and the
 * overload BetterQuesting's network handler actually calls takes a {@code NoticeConfig}
 * — one of its own classes. A Mixin callback must spell out its target's parameter
 * types exactly, so hooking that method would mean compiling against an optional mod,
 * which is the thing every integration in this package is arranged to avoid. The other
 * overload has no such parameter and is no use either: only BetterQuesting's own
 * settings preview goes through it.
 *
 * <p>{@code onDrawScreen} takes a Forge event and nothing else, so it can be named
 * here. What it would have drawn is read out of the list behind it by
 * {@link QuestToast}, which is also what decides whether this is cancelled at all — a
 * frame it could not read is a frame the quest book draws itself, unchanged.
 *
 * @see com.console.uky.core.LateMixins for why this is registered late rather than early
 */
@Mixin(targets = "betterquesting.client.QuestNotification", remap = false)
public abstract class MixinBqNotice {

    @Inject(method = "onDrawScreen(Lnet/minecraftforge/client/event/RenderGameOverlayEvent$Post;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void uky$questNotice(RenderGameOverlayEvent.Post event, CallbackInfo ci) {
        if (QuestToast.takeOver()) {
            ci.cancel();
        }
    }
}

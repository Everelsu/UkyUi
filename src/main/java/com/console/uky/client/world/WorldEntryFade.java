package com.console.uky.client.world;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Carries the loading still over the first moments of the live world.
 *
 * The loading screen already shows where you are about to arrive, but it used to
 * vanish the instant the world was ready — one frame a photograph, the next frame
 * the game, and the cut was the most noticeable thing about arriving. This keeps
 * the same picture on screen for a beat afterwards and dissolves it into the world
 * underneath, continuing the push-in it had already started so the two never
 * disagree about where the camera is.
 *
 * <p>Drawn from a render tick rather than a screen, because by this point there is
 * no screen left — that is the whole point. The projection is set up here instead
 * of borrowed, so it does not depend on what the world renderer happened to leave
 * behind.
 */
public final class WorldEntryFade {

    private static final float SECONDS = 0.85F;
    /** Native size of a stored preview; see {@link WorldPreviews}. */
    public static final int PREVIEW_W = 480;
    public static final int PREVIEW_H = 270;
    /**
     * How far the loading screen darkens its picture, shared so the first frame of
     * the dissolve is identical to the last frame of the loading screen. If these
     * two ever disagree the handover shows up as a brightness step, which is the one
     * thing this whole mechanism exists to avoid.
     */
    public static final float SCRIM = 0.55F;

    private static ResourceLocation texture;
    private static float zoomAtHandover;
    private static float elapsed;
    private static long lastFrameNanos;
    /**
     * Whether a dissolve is in flight, held separately from {@link #texture}.
     *
     * It used to be "texture is not null", and that quietly made the whole mechanism
     * conditional on there being a photograph — see {@link #begin}.
     */
    private static boolean running;

    private WorldEntryFade() {
    }

    /**
     * Starts the dissolve.
     *
     * <p><b>A missing picture is no longer a reason to do nothing.</b> This returned
     * immediately when {@code preview} was null, which meant the one case that needs a
     * dissolve most was the one case that never got one: a world being entered for the
     * first time has no capture, so the loading screen simply vanished and the lit world
     * appeared in the following frame. Every world anybody has played gets a soft arrival
     * and every brand new world gets a hard cut, which is exactly backwards — the new
     * world is the one nobody has seen before.
     *
     * <p>Without a picture there is still something to fade: the loading screen's own
     * look. It ends on {@link Theme#background} under a heavy vignette, so that is what
     * gets lifted off the world instead of a photograph. Same mechanism, same timing,
     * one fewer layer.
     *
     * @param preview the still the loading screen was showing, or null if it had none
     * @param zoom    the zoom it had reached, so the push-in carries on unbroken
     */
    public static void begin(ResourceLocation preview, float zoom) {
        texture = preview;
        zoomAtHandover = zoom;
        elapsed = 0.0F;
        lastFrameNanos = System.nanoTime();
        running = true;
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Drops a dissolve still in flight.
     *
     * Called when a world is about to be entered or has just been left. Without it, a
     * world entered before the previous one's dissolve had finished arrived underneath
     * the previous world's photograph — the fade is static and keyed to nothing, so it
     * has no way of noticing that what it is fading into is not what it was started for.
     */
    public static void cancel() {
        texture = null;
        running = false;
    }

    /** Registered on FML's bus, where {@link TickEvent} lives. */
    public static final class Handler {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !running) {
                return;
            }

            long now = System.nanoTime();
            // Clamped, so a stutter during chunk loading does not skip the fade.
            elapsed += Math.min((now - lastFrameNanos) / 1_000_000_000.0F, 0.1F);
            lastFrameNanos = now;

            if (elapsed >= SECONDS) {
                cancel();
                return;
            }
            draw(Ease.outCubic(elapsed / SECONDS));
        }
    }

    private static void draw(float progress) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            // Left again before the fade finished; there is nothing to fade into.
            cancel();
            return;
        }
        ScaledResolution resolution =
                new ScaledResolution(mc);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GL11.glLoadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);

        boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        float opacity = 1.0F - progress;
        if (texture != null) {
            // The push-in keeps going while it dissolves; stopping it dead would give
            // away the exact frame of the handover, which is what this exists to hide.
            float zoom = zoomAtHandover + progress * 0.02F;
            Draw.textureCover(texture, 0, 0, width, height, PREVIEW_W, PREVIEW_H,
                    zoom, 0.0F, 0.0F, Draw.withAlpha(0xFFFFFF, opacity));
            // The loading screen's own darkening, lifted at the same rate. Carrying it
            // over is what makes the first frame here indistinguishable from the last
            // frame there.
            Draw.rect(0, 0, width, height, Draw.withAlpha(Theme.background, SCRIM * opacity));
        } else {
            // No photograph — a world being entered for the first time. What the loading
            // screen had instead was the backdrop colour at full strength under its
            // vignette, so that is what gets lifted. Same reasoning as the branch above:
            // the first frame drawn here has to be the last frame drawn there, and the
            // two figures below are that screen's own.
            Draw.rect(0, 0, width, height, Draw.withAlpha(Theme.background, opacity));
            Draw.vignette(width, height, 0.8F * opacity, 0xFF000000);
        }

        GlStateManager.depthMask(true);
        if (depth) {
            GlStateManager.enableDepth();
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }
}

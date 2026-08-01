package com.console.uky.client.world;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
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

    private WorldEntryFade() {
    }

    /**
     * Starts the dissolve.
     *
     * @param preview the still the loading screen was showing, or null to do nothing
     * @param zoom    the zoom it had reached, so the push-in carries on unbroken
     */
    public static void begin(ResourceLocation preview, float zoom) {
        if (preview == null) {
            return;
        }
        texture = preview;
        zoomAtHandover = zoom;
        elapsed = 0.0F;
        lastFrameNanos = System.nanoTime();
    }

    public static boolean isRunning() {
        return texture != null;
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
    }

    /** Registered on FML's bus, where {@link TickEvent} lives. */
    public static final class Handler {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || texture == null) {
                return;
            }

            long now = System.nanoTime();
            // Clamped, so a stutter during chunk loading does not skip the fade.
            elapsed += Math.min((now - lastFrameNanos) / 1_000_000_000.0F, 0.1F);
            lastFrameNanos = now;

            if (elapsed >= SECONDS) {
                texture = null;
                return;
            }
            draw(Ease.outCubic(elapsed / SECONDS));
        }
    }

    private static void draw(float progress) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            // Left again before the fade finished; there is nothing to fade into.
            texture = null;
            return;
        }
        ScaledResolution resolution =
                new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        // The push-in keeps going while it dissolves; stopping it dead would give
        // away the exact frame of the handover, which is what this exists to hide.
        float zoom = zoomAtHandover + progress * 0.02F;
        float opacity = 1.0F - progress;
        Draw.textureCover(texture, 0, 0, width, height, PREVIEW_W, PREVIEW_H,
                zoom, 0.0F, 0.0F, Draw.withAlpha(0xFFFFFF, opacity));
        // The loading screen's own darkening, lifted at the same rate. Carrying it
        // over is what makes the first frame here indistinguishable from the last
        // frame there.
        Draw.rect(0, 0, width, height, Draw.withAlpha(Theme.background, SCRIM * opacity));

        GL11.glDepthMask(true);
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}

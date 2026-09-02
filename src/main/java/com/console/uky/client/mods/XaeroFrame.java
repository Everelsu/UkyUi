package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Xaero's minimap, given this mod's frame instead of its own.
 *
 * <p>The minimap is the one piece of interface that is on screen the entire time
 * somebody is playing, and its own frame is a beige bevel that has nothing to do with
 * the rest of the HUD. This replaces the box and only the box: the map, the entities,
 * the waypoints and the coordinates under it are Xaero's and are untouched.
 *
 * <h2>Their frame is switched off by hand here, unlike on 1.7.10</h2>
 *
 * <p>On the older branch this class sets {@code ModSettings.minimapFrame} to "off"
 * itself and puts the value back when the feature is turned off. That field does not
 * exist in this version: Xaero's 1.12 build is the rewritten HUD, and the frame is a
 * profiled config option living in a separate library ({@code xaerolib}), read and
 * written through a profile manager. Writing into another mod's profiled config from
 * outside is a good way to corrupt the file it keeps, and the value would then be wrong
 * for every world rather than for one session.
 *
 * <p>So it is left alone, and the one line of setup is in the config comment and in the
 * changelog: <b>set the frame to Off in Xaero's own settings</b> (Minimap settings,
 * "Frame"). Ours is drawn either way, so the two are only ever both on screen if
 * somebody has not read that.
 *
 * <h2>Where the box is</h2>
 *
 * <p>From Xaero's own module session, which is what their renderer measures the map
 * with: {@code BuiltInHudModules.MINIMAP} holds the session, and the session knows
 * whether it is being shown at all, how wide and tall it is at this GUI scale, and
 * where its corner ends up once the anchoring and flipping are resolved. Nothing is
 * computed on this side, so moving the map or resizing it moves the frame with it.
 */
public final class XaeroFrame {

    private static boolean resolved;
    private static boolean usable;

    /** {@code BuiltInHudModules.MINIMAP}, and the session it hands out. */
    private static Field minimapModule;
    private static Method currentSession;
    private static Method sessionActive;
    private static Method sessionWidth;
    private static Method sessionHeight;
    private static Method sessionX;
    private static Method sessionY;

    private XaeroFrame() {
    }

    /**
     * Draws the frame, if there is a minimap on screen to draw it around.
     *
     * Called from the HUD's own overlay pass rather than from inside Xaero's render, so
     * it is drawn in the plain scaled interface space every other overlay in this mod
     * uses, with no assumption about the projection their renderer leaves behind.
     */
    public static void draw(Minecraft mc, ScaledResolution resolution) {
        if (!UiConfig.restyleXaeroFrame || !resolve()
                || mc.world == null || mc.gameSettings.hideGUI) {
            return;
        }
        try {
            Object session = currentSession.invoke(minimapModule.get(null));
            if (session == null
                    || !((Boolean) sessionActive.invoke(session)).booleanValue()) {
                // No session, or the map is switched off. Their own renderer asks the
                // same question before it draws anything, and a frame around nothing is
                // exactly what this stops.
                return;
            }
            Double scale = Double.valueOf(resolution.getScaleFactor());
            int w = ((Integer) sessionWidth.invoke(session, scale)).intValue();
            int h = ((Integer) sessionHeight.invoke(session, scale)).intValue();
            if (w <= 0 || h <= 0) {
                return;
            }
            // The corner, with the anchoring already resolved: these take the screen
            // dimension and answer where the map actually is, which is what saves this
            // side from knowing which edge the player has parked it against.
            int x = ((Integer) sessionX.invoke(session,
                    Integer.valueOf(resolution.getScaledWidth()), scale)).intValue();
            int y = ((Integer) sessionY.invoke(session,
                    Integer.valueOf(resolution.getScaledHeight()), scale)).intValue();

            // Square only. The shape is a profiled config option in this version and
            // reading it means the same plumbing as writing one; a round map keeps
            // Xaero's own frame, which is the honest outcome rather than a guess.
            drawFrame(x, y, w, h, false);
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.warn("Could not draw the minimap frame; Xaero keeps its own", t);
        }
    }

    /**
     * The frame itself, in this mod's own language rather than a box.
     *
     * <p>A border alone was the wrong answer twice over: it read as a window somebody
     * had dropped on the HUD, and being a closed rectangle it had to line up with the
     * map to the pixel or look broken. What every other panel in this mod is marked
     * with is not an outline — it is a rail down one edge and the corners picked out —
     * so that is what the map gets.
     *
     * <p>Four corner brackets, the gold rail down the left, a hairline to close the
     * shape at very low opacity, and one slanted tick at the top right: the same lean
     * the achievement panel is cut with, which is what says this belongs to the same
     * interface as everything else. All of it outside the map, because every pixel
     * inside one is information.
     */
    private static void drawFrame(int x, int y, int w, int h, boolean round) {
        float x1 = x - 2.0F;
        float y1 = y - 2.0F;
        float x2 = x + w + 2.0F;
        float y2 = y + h + 2.0F;

        if (round) {
            drawRoundFrame(x + w / 2.0F, y + h / 2.0F, Math.min(w, h) / 2.0F + 2.0F);
            return;
        }

        // A seat under everything, so the marks sit on the HUD rather than float over
        // whatever the map happens to be showing at its edge.
        Draw.border(x1 - 1.0F, y1 - 1.0F, x2 + 1.0F, y2 + 1.0F, 1.0F,
                Draw.withAlpha(Theme.background, 0.8F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.10F));

        // The rail: full height, brightest at the top, the same gradient the panels use.
        Draw.gradientV(x1 - 1.0F, y1, x1 + 1.0F, y2,
                Draw.withAlpha(Theme.accent, 0.95F), Draw.withAlpha(Theme.accent, 0.30F));

        // Corners, drawn as brackets rather than joined up. The eye reads four marks as
        // a frame perfectly well, and unlike a closed box they do not have to agree with
        // the map's edge to the pixel.
        float arm = Math.max(6.0F, Math.min(w, h) * 0.14F);
        int bright = Draw.withAlpha(Theme.accent, 0.9F);
        bracket(x1, y1, arm, 1.0F, 1.0F, bright);
        bracket(x2, y1, arm, -1.0F, 1.0F, bright);
        bracket(x1, y2, arm, 1.0F, -1.0F, bright);
        bracket(x2, y2, arm, -1.0F, -1.0F, bright);

        // One tick with the lean this mod cuts its HUD panels with, so the frame is
        // recognisably ours at a glance and not merely dark.
        Draw.slant(x2 - arm - 3.0F, y1 - 4.0F, x2 - 3.0F, y1 - 2.0F, 3.0F,
                Draw.withAlpha(Theme.accentAlt, 0.85F));
    }

    /** One corner: two arms meeting at ({@code x}, {@code y}), pointing inwards. */
    private static void bracket(float x, float y, float arm, float dx, float dy,
                                int colour) {
        Draw.rect(x, y, x + arm * dx, y + 1.0F * dy, colour);
        Draw.rect(x, y, x + 1.0F * dx, y + arm * dy, colour);
    }

    /**
     * The round map's frame: a ring, and the corners' job done by four ticks.
     *
     * The ticks sit at the diagonals rather than at the compass points, which are
     * already taken — Xaero draws its own N/E/S/W letters there, and putting a mark
     * under one would read as part of it.
     */
    private static void drawRoundFrame(float cx, float cy, float radius) {
        Draw.ring(cx, cy, radius + 1.0F, 1.0F, Draw.withAlpha(Theme.background, 0.8F));
        Draw.ring(cx, cy, radius, 1.0F, Draw.withAlpha(Theme.accent, 0.55F));

        float diagonal = 0.70710677F;
        float inner = radius + 1.0F;
        float outer = radius + 4.0F;
        for (int i = 0; i < 4; i++) {
            float ux = (i == 0 || i == 3) ? diagonal : -diagonal;
            float uy = (i < 2) ? diagonal : -diagonal;
            Draw.line(cx + ux * inner, cy + uy * inner,
                    cx + ux * outer, cy + uy * outer, 1.6F,
                    Draw.withAlpha(Theme.accent, 0.9F));
        }
    }

    /**
     * Looks up everything this needs, once.
     *
     * All of it is Xaero's own shape, so a version that moves any of it costs the frame
     * and nothing else — the map keeps drawing, exactly as it did before this existed.
     */
    private static boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;
        try {
            // The class, not Loader.isModLoaded: their mod id is "xaerominimap" here and
            // was "XaeroMinimap" on 1.7.10, and FML's lookup is case sensitive. A class
            // that loads is the same question with no spelling to get wrong.
            minimapModule = Class.forName("xaero.hud.minimap.BuiltInHudModules")
                    .getField("MINIMAP");
            currentSession = Class.forName("xaero.hud.module.HudModule")
                    .getMethod("getCurrentSession");

            Class<?> moduleSession = Class.forName("xaero.hud.module.ModuleSession");
            sessionActive = moduleSession.getMethod("isActive");
            sessionX = moduleSession.getMethod("getEffectiveX", int.class, double.class);
            sessionY = moduleSession.getMethod("getEffectiveY", int.class, double.class);

            Class<?> minimapSession = Class.forName("xaero.hud.minimap.module.MinimapSession");
            sessionWidth = minimapSession.getMethod("getWidth", double.class);
            sessionHeight = minimapSession.getMethod("getHeight", double.class);
            // Their session classes are not public in every build; the methods are.
            sessionActive.setAccessible(true);
            sessionX.setAccessible(true);
            sessionY.setAccessible(true);
            sessionWidth.setAccessible(true);
            sessionHeight.setAccessible(true);

            usable = true;
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.info("Xaero's minimap keeps its own frame: {}", t.toString());
        }
        return usable;
    }
}

package com.console.uky.client.mods;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.FloatBuffer;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * A frame style for Xaero's minimap, added to the ones Xaero ships.
 *
 * <p>The minimap is on screen the whole time somebody is playing, and its own frame is a
 * beige bevel that has nothing to do with the rest of the HUD. This is another entry in
 * their <em>Frame Style</em> setting, picked in their menu next to Default, Colored and
 * Colored Thin: choose it and the map is framed in this mod's language, choose one of
 * theirs and this mod does not touch the map at all.
 *
 * <p>Only the frame either way. The map, the entities, the waypoints and the coordinates
 * under it are Xaero's and are untouched.
 *
 * <h2>Getting into their menu</h2>
 *
 * <p>Their frame styles are an array of names, and everything about the setting is read
 * off it: how far the button can be clicked comes from its length, the label comes from
 * the name at the chosen index, and "off" is defined as the last entry rather than as a
 * number. So the whole of adding a style is putting a name in that array, one place from
 * the end — the button gains a stop, ours is the name on it, and off stays off.
 *
 * <p>It goes in as their static holder finishes loading, which is before anything reads
 * the array — the setting is built from its length, so it has to be there by then. If
 * anything about that fails the array is left exactly as it was and this mod simply has
 * no style in their menu.
 *
 * <p>The one price is for somebody who had the frame set to off already: off moved along
 * by one, so it reads as ours until it is set to off again.
 *
 * <h2>Drawing it</h2>
 *
 * <p>Their renderer knows nothing about our style, so it draws its own frame for it —
 * eight textured rectangles through a single helper. Those are intercepted and cancelled
 * when ours is the style picked, which is what leaves the map bare for ours and what
 * leaves their three styles alone when one of them is picked instead.
 *
 * <p>Where the map is comes from the map. Their renderer draws the square map as one
 * textured quad carrying its x, y, width and height, so the frame is drawn on the same
 * four numbers the map is. Everything tried before that was a guess about that rectangle
 * and each guess was wrong in its own way: the sizes their settings expose include margins
 * the map does not have, the union of their frame pieces is the frame's outer edge, and
 * the hole those pieces are laid around is bounded by corners deeper than the edges.
 *
 * <p>Two things stand between that quad and the screen and both are handled rather than
 * assumed. With the frame buffer in use the map is drawn into the buffer first, at the
 * buffer's own origin; that pass is bracketed and ignored. And the quad is drawn under a
 * scale of Xaero's own, while the frame can only be drawn once that scale is gone, so the
 * modelview matrix is read at both ends and the rectangle is carried across — which makes
 * this right at any minimap scale and any GUI scale without knowing what either of them is.
 *
 * <p>A map set to round is left alone: that one is an ellipse drawn somewhere else, no
 * quad arrives, and their pieces are not cancelled, so it keeps the frame it had.
 */
public final class XaeroFrame {

    /** The name our style goes into Xaero's list under. */
    private static final String STYLE_NAME = "uky.xaero.frameStyle";

    /** Our place in their list of frame styles, or -1 if we never got into it. */
    private static int styleIndex = -1;

    /** The style picked this render, as their own setting has it. */
    private static int style = -1;

    /** Whether their minimap render is currently between its first and last instruction. */
    private static boolean rendering;

    /** Whether the map is currently being drawn into the frame buffer rather than seen. */
    private static boolean offScreen;

    /** The map's own quad, in the space it was drawn in. */
    private static boolean haveMap;
    private static float mapLeft;
    private static float mapTop;
    private static float mapRight;
    private static float mapBottom;
    private static final float[] mapSpace = new float[4];

    /** Scratch for reading the modelview matrix, and the four numbers taken from it. */
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] here = new float[4];

    /** The way to their current setting, found once and kept. */
    private static Method configs;
    private static Method clientManager;
    private static Method effective;
    private static Object frameOption;
    private static boolean looked;

    private XaeroFrame() {
    }

    /**
     * Puts our style into Xaero's list, one place from the end.
     *
     * <p>One place, not at the end, because their renderer treats the last entry as "off":
     * appending would have made their off draw a frame and ours draw none. Ours takes the
     * place their off had and their off moves along, which leaves every one of their own
     * styles meaning exactly what it did.
     *
     * <p>Called from their own static initialiser as it finishes. Reflection rather than a
     * shadowed field so that a version where any of this is different costs the style and
     * nothing else.
     */
    public static void addFrameStyle() {
        if (styleIndex >= 0 || !UiConfig.restyleXaeroFrame) {
            return;
        }
        try {
            Class<?> constants = Class.forName(
                    "xaero.hud.minimap.common.config.MinimapConfigConstants");
            Field names = constants.getDeclaredField("FRAME_NAMES");
            names.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(names, names.getModifiers() & ~Modifier.FINAL);

            ITextComponent[] theirs = (ITextComponent[]) names.get(null);
            if (theirs == null || theirs.length < 2) {
                return;
            }
            int last = theirs.length - 1;
            ITextComponent[] with = new ITextComponent[theirs.length + 1];
            System.arraycopy(theirs, 0, with, 0, last);
            with[last] = new TextComponentTranslation(STYLE_NAME);
            with[last + 1] = theirs[last];
            names.set(null, with);
            styleIndex = last;
        } catch (Throwable ignored) {
            // Their menu keeps the styles it shipped with, and nothing else changes.
        }
    }

    /** Starts a render: reads which style is picked, and forgets the last one's map. */
    public static void begin() {
        rendering = true;
        offScreen = false;
        haveMap = false;
        style = styleIndex < 0 ? -1 : currentStyle();
    }

    /** Whether the style picked in Xaero's own menu is ours. */
    private static boolean ours() {
        return styleIndex >= 0 && style == styleIndex && UiConfig.restyleXaeroFrame;
    }

    /**
     * Marks the pass that draws the map into the frame buffer instead of onto the screen.
     *
     * <p>Same call, same helper, a rectangle at the buffer's origin rather than at the
     * map's — the one quad of a render that must not be mistaken for the map.
     */
    public static void beginOffScreen() {
        offScreen = true;
    }

    /** Ends that pass; what is drawn after it is drawn where it can be seen. */
    public static void endOffScreen() {
        offScreen = false;
    }

    /**
     * Takes the map's rectangle from the first quad drawn on screen, which is the map.
     *
     * <p>Only the first: everything after it is an icon or an arrow that happens to use
     * the same helper, and the map is drawn before any of them.
     */
    public static void takeMapQuad(float x, float y, float width, float height) {
        if (!rendering || offScreen || haveMap || !ours()) {
            return;
        }
        if (width < 8.0F || height < 8.0F) {
            return;
        }
        haveMap = true;
        mapLeft = x;
        mapTop = y;
        mapRight = x + width;
        mapBottom = y + height;
        readSpace(mapSpace);
    }

    /**
     * Takes one piece of the frame their renderer is drawing.
     *
     * @return whether the caller should skip drawing it, which is true only when the
     *         style picked is ours — their own three are drawn by them, untouched
     */
    public static boolean takeFramePiece(float x, float y, int width, int height) {
        return rendering && ours() && haveMap;
    }

    /** Ends the render by drawing our frame around whatever the map turned out to be. */
    public static void end() {
        if (!rendering) {
            return;
        }
        rendering = false;
        offScreen = false;
        if (!ours() || !haveMap) {
            return;
        }
        readSpace(here);
        // On the map's own edge, one pixel out: the air a frame wants around it.
        draw(mapSpace, mapLeft, mapTop, mapRight, mapBottom, 1.0F);
    }

    /**
     * Their frame setting, as it stands right now.
     *
     * <p>Asked of their own config rather than remembered, because it is theirs: it is
     * changed in their menu, it belongs to whichever of their profiles is loaded, and it
     * can change between two frames. The way to it is found once; if it cannot be found
     * this returns nothing picked, and no style of ours is ever the one in use.
     */
    private static int currentStyle() {
        try {
            if (!looked) {
                looked = true;
                Class<?> hudMod = Class.forName("xaero.common.HudMod");
                configs = hudMod.getMethod("getHudConfigs");
                clientManager = configs.getReturnType().getMethod("getClientConfigManager");
                Class<?> options = Class.forName(
                        "xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions");
                frameOption = options.getField("FRAME").get(null);
                Class<?> option = Class.forName("xaero.lib.common.config.option.ConfigOption");
                effective = clientManager.getReturnType().getMethod("getEffective", option);
            }
            if (effective == null) {
                return -1;
            }
            Object instance = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
            if (instance == null) {
                return -1;
            }
            Object manager = clientManager.invoke(configs.invoke(instance));
            Object value = effective.invoke(manager, frameOption);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            effective = null;
            return -1;
        }
    }

    /**
     * Reads the scale and offset the current matrix is drawing with.
     *
     * <p>Only those four of its sixteen numbers, because that is all the minimap uses: the
     * map is drawn under a scale and a translation and nothing else. Rotation would need
     * the whole matrix and a frame that could be rotated with it, and there is none here.
     */
    private static void readSpace(float[] into) {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        into[0] = MATRIX.get(0);
        into[1] = MATRIX.get(5);
        into[2] = MATRIX.get(12);
        into[3] = MATRIX.get(13);
    }

    /**
     * Draws the frame around a rectangle measured in another space, in this one.
     *
     * <p>Both spaces are a scale and an offset away from the same screen, so a point goes
     * out through the one it was measured in and back through the one being drawn in. At
     * the same scale — the usual case — this is exactly the rectangle that arrived.
     */
    private static void draw(float[] space, float left, float top, float right,
                             float bottom, float air) {
        if (space[0] == 0.0F || space[1] == 0.0F || here[0] == 0.0F || here[1] == 0.0F) {
            return;
        }
        float x1 = ((left * space[0] + space[2]) - here[2]) / here[0];
        float y1 = ((top * space[1] + space[3]) - here[3]) / here[1];
        float x2 = ((right * space[0] + space[2]) - here[2]) / here[0];
        float y2 = ((bottom * space[1] + space[3]) - here[3]) / here[1];
        if (x2 - x1 < 8.0F || y2 - y1 < 8.0F) {
            return;
        }
        drawFrame(x1 - air, y1 - air, x2 + air, y2 + air);
    }

    /**
     * The frame itself.
     *
     * <p>A plain border was the wrong answer twice over: it read as a window somebody had
     * dropped on the HUD, and being a closed rectangle it had to line up with the map to
     * the pixel or look broken. What every other panel in this mod is marked with is not
     * an outline — it is a rail down one edge and the corners picked out — so that is what
     * the map gets.
     *
     * <p>Four corner brackets, the gold rail down the left, a hairline to close the shape
     * at very low opacity, and one slanted tick at the top right: the same lean the
     * achievement panel is cut with, which is what says this belongs to the same interface
     * as everything else.
     */
    private static void drawFrame(float left, float top, float right, float bottom) {
        // A seat under everything, so the marks sit on the HUD rather than float over
        // whatever the map happens to be showing at its edge.
        Draw.border(left - 1.0F, top - 1.0F, right + 1.0F, bottom + 1.0F, 1.0F,
                Draw.withAlpha(Theme.background, 0.8F));
        Draw.border(left, top, right, bottom, 1.0F, Draw.withAlpha(Theme.text, 0.10F));

        // The rail: full height, brightest at the top, the same gradient the panels use.
        Draw.gradientV(left - 1.0F, top, left + 1.0F, bottom,
                Draw.withAlpha(Theme.accent, 0.95F), Draw.withAlpha(Theme.accent, 0.30F));

        // Corners, drawn as brackets rather than joined up: four marks read as a frame
        // perfectly well, and they leave the map's own edge alone.
        float arm = Math.max(6.0F, Math.min(right - left, bottom - top) * 0.14F);
        int bright = Draw.withAlpha(Theme.accent, 0.9F);
        bracket(left, top, arm, 1.0F, 1.0F, bright);
        bracket(right, top, arm, -1.0F, 1.0F, bright);
        bracket(left, bottom, arm, 1.0F, -1.0F, bright);
        bracket(right, bottom, arm, -1.0F, -1.0F, bright);

        // One tick with the lean this mod cuts its HUD panels with, so the frame is
        // recognisably ours at a glance and not merely dark.
        Draw.slant(right - arm - 3.0F, top - 4.0F, right - 3.0F, top - 2.0F, 3.0F,
                Draw.withAlpha(Theme.accentAlt, 0.85F));
    }

    /** One corner: two arms meeting at ({@code x}, {@code y}), pointing inwards. */
    private static void bracket(float x, float y, float arm, float dx, float dy,
                                int colour) {
        Draw.rect(x, y, x + arm * dx, y + 1.0F * dy, colour);
        Draw.rect(x, y, x + 1.0F * dx, y + arm * dy, colour);
    }
}

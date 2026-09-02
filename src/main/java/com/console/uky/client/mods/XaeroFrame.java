package com.console.uky.client.mods;

import java.nio.FloatBuffer;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Xaero's minimap, framed in this mod's language rather than its own.
 *
 * <p>The minimap is the one piece of interface that is on screen the whole time somebody
 * is playing, and its own frame is a beige bevel that has nothing to do with the rest of
 * the HUD. This replaces the box and only the box: the map, the entities, the waypoints
 * and the coordinates under it are Xaero's and are untouched.
 *
 * <h2>Where the map is</h2>
 *
 * <p>Taken from the map, not worked out from anything around it. Their renderer draws the
 * square map as a single textured quad, and that call carries its x, y, width and height —
 * so the frame is drawn on the same four numbers the map is.
 *
 * <p>Everything tried before this was a guess about that rectangle, and each guess was
 * wrong in its own way: the sizes their settings expose include margins the map does not
 * have; the union of their frame's eight pieces is its outer edge, a band away from the
 * map; the hole in the middle of those pieces is closer, but their corners are deeper than
 * their edges, so it landed inside the map on large ones and off it on small ones. The
 * quad is not a guess.
 *
 * <h2>Which quad, and in whose coordinates</h2>
 *
 * <p>Two things stand between that quad and the screen, and both are handled here rather
 * than assumed away.
 *
 * <p>The first is that it is not the only quad of a render. With the frame buffer in use
 * the map is drawn once into the buffer, at the buffer's own origin, before it is drawn
 * where the player can see it; that pass is bracketed and ignored, so the quad taken is
 * always the one on the screen. Waypoint and entity icons come afterwards, and the first
 * quad that is not the buffer's is the map.
 *
 * <p>The second is that the quad is drawn under a scale of Xaero's own, so its numbers are
 * not screen pixels, and by the time the frame can safely be drawn that scale is gone.
 * The modelview matrix is read at both ends and the rectangle is carried across, which
 * makes this right at any minimap scale and any GUI scale without knowing what either of
 * them is.
 *
 * <h2>Standing in for their frame, or standing around it</h2>
 *
 * <p>Their frame is built from eight rectangles that all go through one helper, so it can
 * be removed exactly: cancel those eight and it was never drawn. {@code
 * mods.xaeroFrameReplace} decides whether to. On, theirs goes and ours is drawn on the
 * map's edge; off, theirs stays and ours goes around the outside of it — this mod's mark
 * on the map without taking anything away from somebody who likes the frame Xaero ships.
 *
 * <p>Either way this is their frame drawn differently, not a frame of ours that happens to
 * be near their map, so it appears exactly when theirs would have. Whichever of their
 * styles is picked, ours is what is seen; set their frame to "off" and there are no pieces
 * to take and nothing is drawn at all, theirs or ours. A map set to round is left alone
 * entirely — that one is an ellipse drawn somewhere else, no quad arrives, and a ring of
 * ours over a ring of theirs is worse than either.
 *
 * <p>What this cannot be is a fifth entry in Xaero's own frame menu. That setting is a
 * numeric range in their profiled config, and a value invented from outside would be
 * written into a file their own code does not know it in — and would still be sitting
 * there, meaning nothing, if this mod were removed.
 */
public final class XaeroFrame {

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

    /** Their frame's outer box, for when ours is drawn around it instead of over it. */
    private static boolean haveTheirs;
    private static float theirLeft;
    private static float theirTop;
    private static float theirRight;
    private static float theirBottom;
    private static final float[] theirSpace = new float[4];

    /** Scratch for reading the modelview matrix, and the four numbers taken from it. */
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] here = new float[4];

    private XaeroFrame() {
    }

    /** Starts a render: nothing is known about the map until it draws itself. */
    public static void begin() {
        rendering = true;
        offScreen = false;
        haveMap = false;
        haveTheirs = false;
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
        if (!rendering || offScreen || haveMap || !UiConfig.restyleXaeroFrame) {
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
     * Takes one piece of their frame, on its way to being drawn.
     *
     * @return whether the caller should skip drawing it, which is what makes ours a
     *         replacement rather than an addition
     */
    public static boolean takeFramePiece(float x, float y, int width, int height) {
        if (!rendering || !UiConfig.restyleXaeroFrame) {
            return false;
        }

        float right = x + width;
        float bottom = y + height;
        if (!haveTheirs) {
            haveTheirs = true;
            theirLeft = x;
            theirTop = y;
            theirRight = right;
            theirBottom = bottom;
            readSpace(theirSpace);
        } else {
            theirLeft = Math.min(theirLeft, x);
            theirTop = Math.min(theirTop, y);
            theirRight = Math.max(theirRight, right);
            theirBottom = Math.max(theirBottom, bottom);
        }

        // Their frame is only removed when ours is going to stand where it stood. On a
        // round map no quad arrived, there is nowhere to draw ours, and taking theirs
        // away would leave the map with no frame at all.
        return UiConfig.xaeroFrameReplace && haveMap;
    }

    /** Ends the render by drawing our frame around whatever the map turned out to be. */
    public static void end() {
        if (!rendering) {
            return;
        }
        rendering = false;
        offScreen = false;
        if (!UiConfig.restyleXaeroFrame) {
            return;
        }
        readSpace(here);

        // This is their frame, drawn differently — not a frame of our own that happens to
        // be near their map. So it appears when theirs would have, and their switch is
        // still the switch: set the minimap's frame to "off" and there are no pieces, and
        // ours is off with it.
        if (!haveTheirs) {
            return;
        }

        if (UiConfig.xaeroFrameReplace) {
            if (haveMap) {
                // On the map's own edge, one pixel out: the air a frame wants around it.
                draw(mapSpace, mapLeft, mapTop, mapRight, mapBottom, 1.0F);
            }
            return;
        }

        // Theirs was left to draw, so ours goes around the outside of it, clear by a
        // pixel — the two read as one thing with a mark on it rather than as two frames
        // that happen to be nested.
        draw(theirSpace, theirLeft, theirTop, theirRight, theirBottom, 1.0F);
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

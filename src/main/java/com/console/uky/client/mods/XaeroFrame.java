package com.console.uky.client.mods;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;

/**
 * Xaero's minimap, framed in this mod's language rather than its own.
 *
 * <p>The minimap is the one piece of interface that is on screen the entire time
 * somebody is playing, and its own frame is a beige bevel that has nothing to do with
 * the rest of the HUD. This replaces the box and only the box: the map, the entities,
 * the waypoints and the coordinates under it are Xaero's and are untouched.
 *
 * <h2>It is one of their frame styles, not a second frame</h2>
 *
 * <p>Their renderer builds the frame out of eight textured rectangles and hands every
 * one of them to a single helper method. Those are intercepted — see
 * {@code MixinXaeroFrame} — which does two things at once: their frame is not drawn,
 * and the rectangles say exactly where it would have been. So ours is drawn in its
 * place, in the same coordinate space, with no arithmetic on this side that could be
 * wrong about it.
 *
 * <p>It also means their own switch still means what it says. Whichever of their three
 * styles is picked, ours is what appears; set their frame to "off" and there are no
 * rectangles to take, so nothing is drawn at all — theirs or ours. Nothing here writes
 * to their settings, which on this version live in a profiled config in a separate
 * library that no other mod has any business editing.
 *
 * <p>A map set to round keeps Xaero's frame: that one is an ellipse drawn somewhere
 * else entirely, and a ring of ours over a ring of theirs is worse than either.
 *
 * <h2>Standing in for theirs, or standing around it</h2>
 *
 * <p>{@code mods.xaeroFrameReplace} decides which. On, the pieces are cancelled and ours
 * is drawn where the map's own edge is. Off, theirs is left to draw and ours goes around
 * the outside of it — this mod's mark on the map without taking anything away from
 * somebody who likes the frame Xaero ships.
 *
 * <p>What this cannot be is a fifth entry in Xaero's own frame menu. That setting is a
 * numeric range in their profiled config, and a value invented from outside would be
 * written into a file their own code does not know it in — and would still be sitting
 * there, meaning nothing, if this mod were removed.
 */
public final class XaeroFrame {

    /** The box the pieces of this frame add up to, while they are arriving. */
    private static float x1;
    private static float y1;
    private static float x2;
    private static float y2;
    private static boolean collecting;

    /**
     * The pieces themselves, kept until the end of the frame.
     *
     * <p>Because the box they add up to is not the box we want. Their frame is a nine
     * slice laid around the map — four corners, four edges, and the map in the hole in
     * the middle — so the union of the pieces is its <em>outer</em> edge, and the map is
     * the hole. Drawing on the union put ours a few pixels off the map; guessing the
     * hole from the thinnest piece put it inside the map, because their corners are
     * deeper than their edges and the thinnest piece is not the depth of either side.
     *
     * <p>Kept, the pieces answer it exactly: the hole is bounded by whichever piece
     * covers the middle of each side, and that is a comparison rather than an
     * assumption. It costs four small arrays and works for any of their styles at any
     * size.
     */
    private static final int MAX_PIECES = 32;
    private static final float[] pieceX = new float[MAX_PIECES];
    private static final float[] pieceY = new float[MAX_PIECES];
    private static final float[] pieceW = new float[MAX_PIECES];
    private static final float[] pieceH = new float[MAX_PIECES];
    private static int pieces;

    private XaeroFrame() {
    }

    /**
     * Takes one piece of their frame.
     *
     * @return whether the caller should skip drawing it, which is what makes this a
     *         replacement rather than an addition
     */
    public static boolean takeFramePiece(float x, float y, int width, int height) {
        if (!UiConfig.restyleXaeroFrame) {
            return false;
        }

        float right = x + width;
        float bottom = y + height;
        if (!collecting) {
            collecting = true;
            pieces = 0;
            x1 = x;
            y1 = y;
            x2 = right;
            y2 = bottom;
        } else {
            x1 = Math.min(x1, x);
            y1 = Math.min(y1, y);
            x2 = Math.max(x2, right);
            y2 = Math.max(y2, bottom);
        }
        if (pieces < MAX_PIECES) {
            pieceX[pieces] = x;
            pieceY[pieces] = y;
            pieceW[pieces] = width;
            pieceH[pieces] = height;
            pieces++;
        }
        // Cancelled only when ours is meant to stand in for theirs. Left alone, their
        // frame still draws and ours goes around the outside of it — which is what the
        // second switch is for.
        return UiConfig.xaeroFrameReplace;
    }

    /**
     * Draws our frame around everything collected this frame, and forgets it.
     *
     * Called at the end of their render. Nothing collected means their frame is off, or
     * the map is round, or this feature is — and in all three cases the right thing to
     * draw is nothing.
     */
    public static void drawCollected() {
        if (!collecting) {
            return;
        }
        collecting = false;
        if (x2 - x1 < 4.0F || y2 - y1 < 4.0F) {
            return;
        }
        if (!UiConfig.xaeroFrameReplace) {
            // Theirs is still there. Ours goes around the outside of it, clear by a
            // pixel, so the two read as one thing with a mark on it rather than as two
            // frames that happen to be nested.
            drawFrame(x1 - 1.0F, y1 - 1.0F, x2 + 1.0F, y2 + 1.0F);
            return;
        }
        // Their frame is gone, so ours takes the map's own edge: the hole their nine
        // slice was laid around, plus the pixel of air a frame wants.
        float left = x1;
        float top = y1;
        float right = x2;
        float bottom = y2;
        float midX = (x1 + x2) * 0.5F;
        float midY = (y1 + y2) * 0.5F;
        for (int i = 0; i < pieces; i++) {
            float px = pieceX[i];
            float py = pieceY[i];
            float pr = px + pieceW[i];
            float pb = py + pieceH[i];
            // A piece that crosses the middle of the box on one axis is one of the four
            // edges, and its inner side is where the map starts on that side. The
            // corners cross neither and say nothing, which is what stops their extra
            // depth from being mistaken for the frame's.
            if (px <= midX && pr >= midX) {
                if (pb <= midY) {
                    top = Math.max(top, pb);
                } else if (py >= midY) {
                    bottom = Math.min(bottom, py);
                }
            }
            if (py <= midY && pb >= midY) {
                if (pr <= midX) {
                    left = Math.max(left, pr);
                } else if (px >= midX) {
                    right = Math.min(right, px);
                }
            }
        }
        if (right - left < 4.0F || bottom - top < 4.0F) {
            // Nothing crossed the middle — a style built some other way. The outer box
            // is still a frame's worth of the right place, and it is better than none.
            left = x1;
            top = y1;
            right = x2;
            bottom = y2;
        }
        drawFrame(left - 1.0F, top - 1.0F, right + 1.0F, bottom + 1.0F);
    }

    /**
     * The frame itself.
     *
     * <p>A plain border was the wrong answer twice over: it read as a window somebody
     * had dropped on the HUD, and being a closed rectangle it had to line up with the
     * map to the pixel or look broken. What every other panel in this mod is marked
     * with is not an outline — it is a rail down one edge and the corners picked out —
     * so that is what the map gets.
     *
     * <p>Four corner brackets, the gold rail down the left, a hairline to close the
     * shape at very low opacity, and one slanted tick at the top right: the same lean
     * the achievement panel is cut with, which is what says this belongs to the same
     * interface as everything else.
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

package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;

import java.lang.reflect.Field;

/**
 * Waila's block tooltip, drawn as one of our panels.
 *
 * Waila builds that tooltip from every provider a pack has registered — the block's
 * name, its mod, its tank, its energy, whatever forty other jars decided to add — and
 * then paints a box behind it out of three colours from its own config. This replaces
 * the box and nothing else: the same rectangle, at the same place, with the same
 * contents drawn over it. Nothing here knows or asks what the tooltip says, which is
 * why it keeps working when a pack adds a mod that says something new.
 *
 * <p>The panel is deliberately the same one {@code PlayerListOverlay} draws — dark
 * fill, hairline border, the gold rail down the left edge. Those two are the only
 * things this mod puts on top of the world rather than in a menu, and if they did not
 * match, each would read as belonging to a different mod.
 *
 * <p>It arrives rather than appearing, and the arrival is deliberately decoration
 * only: the rail grows down the left edge and one accent highlight travels across the
 * top. Nothing about the panel or its contents is hidden while that happens.
 *
 * <p>It used to be a wipe — the panel drawn left to right with Waila's own text
 * clipped to the advancing edge, so the contents arrived with it. It read as a fault
 * rather than as an animation. A sixth of a second is four frames on a pack this
 * size, and four frames of a hard edge crossing half-drawn glyphs is a stutter, not a
 * reveal. Anything that moves the content of a HUD element that the player is in the
 * middle of reading has to be worth more than this was.
 *
 * @see com.console.uky.mixins.mods.MixinWailaOverlay the hook that calls this
 * @see WailaTooltips the widgets Waila draws inside the panel
 * @see WailaMining the breaking bar along its bottom edge
 */
public final class WailaPanel {

    /** How long the arrival flourish takes. */
    private static final float APPEAR_SECONDS = 0.22F;
    /**
     * A gap this long means the tooltip went away and came back, rather than being
     * the same one continuing. Two frames at any playable rate is far less.
     */
    private static final long GAP_NANOS = 150_000_000L;

    private static long lastDrawNanos;
    private static long appearedNanos;

    /**
     * Waila's own text colour, so the words match the panel they sit on.
     *
     * Waila draws every unformatted line in {@code OverlayConfig.fontcolor}, a grey
     * chosen for its own near-black box. Against this one it reads as a mod that was
     * not told about the palette. There is no hook for it — the field is simply what
     * the string renderer reads — so it is written here, from the frame that is about
     * to use it, rather than once at start-up where Waila's own config load would
     * later overwrite it.
     */
    private static Field fontColor;
    private static boolean fieldsSearched;
    /** Last colour written, so a frame does not pay for a reflective write it made already. */
    private static int fontColorWritten = -1;

    private WailaPanel() {
    }

    /**
     * Draws the tooltip background for a box Waila has already measured.
     *
     * @return whether it drew — false leaves Waila to paint its own box, which is
     *         what the config switch turns back on
     */
    public static boolean draw(int x, int y, int width, int height) {
        if (!UiConfig.restyleWaila) {
            return false;
        }
        // A box with no area is Waila's way of saying it has nothing to show. Drawing
        // a rail and a border into it would leave two stray gold pixels on the screen.
        if (width <= 0 || height <= 0) {
            return true;
        }

        readFields();
        applyFontColor();
        WailaTooltips.install();

        // The panel itself: whole, every frame, identical whether or not anything is
        // being animated. Nothing below this point moves it or hides part of it.
        //
        // Reads over anything: this floats on the world, not on a menu backdrop, and
        // the thing behind it is as likely to be a white sheep as a cave wall.
        Draw.rect(x - 1, y - 1, x + width + 1, y + height + 1,
                Draw.withAlpha(Theme.panelShadow, 0.35F));
        Draw.rect(x, y, x + width, y + height, Draw.withAlpha(Theme.background, 0.82F));
        Draw.border(x, y, x + width, y + height, 1.0F, Draw.withAlpha(Theme.text, 0.10F));

        float arrival = advance();
        drawRail(x, y, height, arrival);
        if (arrival < 1.0F) {
            drawSweep(x, y, width, arrival);
        }

        drawMining(x, y, width, height);
        return true;
    }

    /**
     * The gold rail down the left edge, drawing itself in from the top.
     *
     * Two pixels wide and entirely clear of the text, which is the whole reason the
     * arrival is built out of this and the sweep rather than out of the panel: a
     * flourish that cannot touch anything the player is reading cannot be mistaken for
     * the interface glitching.
     */
    private static void drawRail(int x, int y, int height, float arrival) {
        Draw.gradientV(x, y, x + 2, y + height * arrival,
                Draw.withAlpha(Theme.accent, 0.75F),
                Draw.withAlpha(Theme.accent, 0.75F * (1.0F - arrival)));
    }

    /** One highlight crossing the top edge, left to right, and gone. */
    private static void drawSweep(int x, int y, int width, float arrival) {
        float band = 26.0F;
        float head = x - band + (width + band * 2.0F) * arrival;
        float fade = 1.0F - arrival;

        Draw.gradientH(Math.max(x, head - band), y, Math.min(x + width, head), y + 1.0F,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.85F * fade));
        Draw.gradientH(Math.max(x, head), y, Math.min(x + width, head + band * 0.4F), y + 1.0F,
                Draw.withAlpha(Theme.accent, 0.85F * fade), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /**
     * How far the block being looked at has been mined, along the bottom edge.
     *
     * On the panel's own edge rather than as a line of its own, because the tooltip is
     * measured and laid out by Waila and anything that took height would push its text
     * around. The edge is already there, it is already the width of the panel, and the
     * one thing the player wants from it — how much longer — is exactly what a bar
     * across a known width says at a glance.
     *
     * <p>Nothing is drawn while nothing is being mined, so the tooltip is unchanged
     * until the moment the mouse goes down.
     */
    private static void drawMining(int x, int y, int width, int height) {
        float progress = WailaMining.progress();
        if (progress <= 0.0F) {
            return;
        }
        float top = y + height - 2.0F;
        float bottom = y + height;

        Draw.rect(x, top, x + width, bottom, Draw.withAlpha(Theme.text, 0.10F));

        float end = x + width * progress;
        Draw.gradientH(x, top, end, bottom,
                Draw.withAlpha(Theme.accentAlt, 0.95F), Draw.withAlpha(Theme.accent, 1.0F));

        // The head, a pixel taller than the bar and brighter than either accent, so
        // the eye tracks the edge rather than the fill.
        Draw.rect(end - 1.0F, top - 1.0F, end, bottom, Draw.withAlpha(Theme.textHover, 0.9F));
        Draw.radialGlow(end, bottom - 1.0F, 5.0F,
                Draw.withAlpha(Theme.accent, 0.30F), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /**
     * Advances the arrival animation and says how far it has got.
     *
     * Restarted on one thing only: the tooltip having been away, because you were
     * looking at the sky or at something Waila has nothing to say about. Not when its
     * contents change.
     *
     * <p>It used to restart when the height changed too, on the theory that a
     * different number of lines meant a different block. It also means the same block
     * gaining a line — a furnace lighting up and growing a progress bar — and replaying
     * an entrance because the thing being described changed state is exactly the
     * twitchiness a HUD must not have.
     */
    private static float advance() {
        long now = System.nanoTime();
        if (lastDrawNanos == 0L || now - lastDrawNanos > GAP_NANOS) {
            appearedNanos = now;
        }
        lastDrawNanos = now;
        return Ease.outCubic((now - appearedNanos) / 1_000_000_000.0F / APPEAR_SECONDS);
    }

    // -------------------------------------------------------------- reflection --

    /** Points Waila's string renderer at the palette's text colour. */
    private static void applyFontColor() {
        int wanted = Theme.text & 0xFFFFFF;
        if (wanted == fontColorWritten || fontColor == null) {
            return;
        }
        try {
            fontColor.setInt(null, wanted);
            fontColorWritten = wanted;
        } catch (Throwable t) {
            // Whatever went wrong will go wrong again next frame; stop trying.
            fontColor = null;
            fontColorWritten = wanted;
            UkyUI.LOGGER.warn("Could not set Waila's tooltip text colour", t);
        }
    }

    private static void readFields() {
        if (fieldsSearched) {
            return;
        }
        fieldsSearched = true;
        try {
            Class<?> config = Class.forName("mcp.mobius.waila.overlay.OverlayConfig");
            fontColor = config.getDeclaredField("fontcolor");
            fontColor.setAccessible(true);
        } catch (Throwable t) {
            // Not an error. This class is only ever reached from inside Waila's own
            // render path, so Waila is certainly present — but a fork of it is free to
            // keep its settings somewhere else, and the thing lost is a colour.
            UkyUI.LOGGER.info("Waila's overlay settings are not where we expected them;"
                    + " leaving its colours alone");
        }
    }

    /** Forgets the cached colour so a palette edit is picked up on the next frame. */
    public static void invalidate() {
        fontColorWritten = -1;
    }
}

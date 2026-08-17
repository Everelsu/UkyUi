package com.console.uky.client.gui;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.ItemIcon;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.stats.Achievement;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * The achievement popup, redrawn.
 *
 * Vanilla's is a 160x32 slice of {@code achievement_background.png} that slides down
 * from the top edge, waits, and slides back up — the same box, at the same size, in
 * the same place it has been since 2011, and the only thing that ever moves is the
 * box itself. This is the same event given the weight it deserves: a slanted panel
 * that cuts in from the right on a line of light, the item landing in its frame with
 * a shockwave behind it, the name typing itself out, and a hairline along the bottom
 * counting down what is left of it.
 *
 * <p>The lean is the point of the shape. Every panel this mod draws in a menu is
 * square, so a HUD element that is not cannot be mistaken for a menu that has come
 * loose — and it is the same lean on both ends, so the panel reads as one cut object
 * rather than a rectangle with a decoration on it.
 *
 * <p>Earning several at once queues them instead of replacing them, which vanilla
 * does not: a single field holds the current achievement, so the second one in the
 * same tick simply overwrites the first and it is never seen. Anything still waiting
 * also shortens the one on screen — the queue drains at pace rather than making the
 * player watch four full dwells.
 *
 * @see com.console.uky.mixins.MixinGuiAchievement the hook that hands the popup over
 * @see AchievementLinks the chat line the same event produces
 */
public final class AchievementToast {

    // ---- geometry, in GUI units ----
    private static final float PANEL_HEIGHT = 36.0F;
    private static final float SKEW = 8.0F;
    private static final float MARGIN = 8.0F;
    private static final float PADDING = 10.0F;
    private static final float ICON = 16.0F;

    // ---- timing, in seconds ----
    /** The line of light widening before there is a panel at all. */
    private static final float LINE_SECONDS = 0.09F;
    /** The panel opening out of that line. */
    private static final float OPEN_SECONDS = 0.21F;
    private static final float TYPE_START = 0.22F;
    private static final float TYPE_SECONDS = 0.40F;
    private static final float OUT_SECONDS = 0.30F;
    private static final float DWELL_UNLOCK = 3.6F;
    /**
     * The inventory hint has no moment to it — it is advice, not an event — so it
     * stays long enough to be read and acted on rather than long enough to land.
     */
    private static final float DWELL_HINT = 6.5F;
    /** What the dwell is cut to when something else is already waiting behind it. */
    private static final float DWELL_QUEUED = 1.5F;

    private static final int QUEUE_LIMIT = 6;

    /** One popup: what it says, what it shows, and which of the two kinds it is. */
    private static final class Toast {

        final Achievement achievement;
        final String kicker;
        final String title;
        /** True for the "press E" hint, which is not something that was earned. */
        final boolean hint;

        Toast(Achievement achievement, String kicker, String title, boolean hint) {
            this.achievement = achievement;
            this.kicker = kicker;
            this.title = title;
            this.hint = hint;
        }

        float dwell() {
            return this.hint ? DWELL_HINT : DWELL_UNLOCK;
        }
    }

    private static final List<Toast> queue = new ArrayList<Toast>();
    private static Toast current;
    /** Wall clock, matching what vanilla times this popup against. */
    private static long shownAt;

    private AchievementToast() {
    }

    // ------------------------------------------------------------------ input --

    /**
     * Queues the popup for an achievement that was just earned.
     *
     * @return whether we took it — false leaves vanilla's own popup to appear, which
     *         is what the config switch turns back on
     */
    public static boolean show(Achievement achievement) {
        if (!UiConfig.achievementToast || achievement == null) {
            return false;
        }
        enqueue(new Toast(achievement,
                I18n.format("uky.achievement.unlocked", new Object[0]),
                nameOf(achievement), false));
        return true;
    }

    /**
     * Queues the inventory hint — vanilla's second use of the same popup, which
     * describes an achievement rather than announcing one.
     */
    public static boolean showHint(Achievement achievement) {
        if (!UiConfig.achievementToast || achievement == null) {
            return false;
        }
        String description;
        try {
            description = achievement.getDescription();
        } catch (Throwable t) {
            description = "";
        }
        enqueue(new Toast(achievement, nameOf(achievement), description, true));
        return true;
    }

    private static void enqueue(Toast toast) {
        if (current == null) {
            current = toast;
            shownAt = Minecraft.getSystemTime();
            announce(toast);
            return;
        }
        // A duplicate can arrive when the server resends the stat block on a
        // reconnect; showing the same achievement twice in a row reads as a bug.
        if (current.achievement == toast.achievement) {
            return;
        }
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).achievement == toast.achievement) {
                return;
            }
        }
        if (queue.size() < QUEUE_LIMIT) {
            queue.add(toast);
        }
    }

    /** The hit, at the moment a panel starts arriving rather than when it is queued. */
    private static void announce(Toast toast) {
        if (toast.hint || !UiConfig.achievementSound) {
            return;
        }
        UkySounds.play(UkySounds.ACHIEVEMENT, (float) UiConfig.achievementVolume, 1.0F);
    }

    /** Drops everything, for {@code GuiAchievement}'s own clear. */
    public static void clear() {
        current = null;
        queue.clear();
    }

    // ----------------------------------------------------------------- drawing --

    /**
     * Draws whatever is on screen, if anything.
     *
     * @return whether this took the frame over — false hands it back to vanilla,
     *         which is the state the config switch leaves it in
     */
    public static boolean draw() {
        if (!UiConfig.achievementToast) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (current == null) {
            return true;
        }
        // Leaving a world with a panel still up would otherwise carry it onto the
        // title screen, where it belongs to nothing.
        if (mc.thePlayer == null || mc.theWorld == null) {
            clear();
            return true;
        }

        float elapsed = (Minecraft.getSystemTime() - shownAt) / 1000.0F;
        float dwell = queue.isEmpty() ? current.dwell() : Math.min(current.dwell(), DWELL_QUEUED);
        if (elapsed > dwell + OUT_SECONDS) {
            advance();
            return true;
        }

        beginOverlay(mc);
        try {
            drawToast(mc, current, elapsed, dwell);
        } catch (Throwable t) {
            // A modded achievement carrying an item whose renderer throws must not
            // take the game's frame with it. Drop the panel and carry on.
            clear();
        } finally {
            endOverlay();
        }
        return true;
    }

    private static void advance() {
        if (queue.isEmpty()) {
            current = null;
            return;
        }
        current = queue.remove(0);
        shownAt = Minecraft.getSystemTime();
        announce(current);
    }

    /**
     * The projection vanilla's own popup sets up, because this is drawn from the same
     * place in the frame: after the world, after any screen, with nothing else having
     * arranged a GUI-space matrix for us.
     */
    private static void beginOverlay(Minecraft mc) {
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, res.getScaledWidth(), res.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        // Lighting off, and this is not optional.
        //
        // This is the one thing this mod draws that is not inside a screen and not
        // inside the HUD: it happens at the very end of the frame, after everything
        // else has had its turn and left the state however it liked. The last things
        // to render before it are the hotbar's items and the held item, both of which
        // turn item lighting on — so a panel drawn here with lighting still enabled is
        // multiplied by an ambient of 0.4 and comes out grey, text and all, while the
        // identical panel inside a screen looks right because the screen pass turned
        // lighting off first. Vanilla's own popup has the same line for the same
        // reason, one call earlier than this one.
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        // Off by the time the world is done with it, in vanilla. A renderer
        // replacement is under no obligation to leave it that way, and fog over a
        // panel two thousand units from the camera is total.
        GL11.glDisable(GL11.GL_FOG);
    }

    private static void endOverlay() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private static void drawToast(Minecraft mc, Toast toast, float elapsed, float dwell) {
        FontRenderer font = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = res.getScaledWidth();

        float textWidth = Math.max(font.getStringWidth(toast.title),
                spacedWidth(font, toast.kicker));
        float width = Math.min(screenWidth * 0.55F,
                Math.max(150.0F, PADDING * 2.0F + ICON + 10.0F + textWidth));

        // Anchored to the right edge with the lean allowed for, so the top corner —
        // which reaches furthest right — clears the screen rather than the bottom one.
        float x2 = screenWidth - MARGIN - SKEW;
        float x1 = x2 - width;
        float y1 = MARGIN;
        float y2 = y1 + PANEL_HEIGHT;

        // ---- the arrival, and the departure, as one pair of numbers ----
        float open = Ease.outCubic((elapsed - LINE_SECONDS) / OPEN_SECONDS);
        float exit = Ease.inCubic((elapsed - dwell) / OUT_SECONDS);
        float alpha = 1.0F - exit;
        if (alpha <= 0.0F) {
            return;
        }

        // The line of light comes first and the panel grows out of it, so before the
        // panel exists there is exactly one thing on screen: a bright hairline
        // widening to the left. It is the whole reason the arrival reads as fast.
        if (elapsed < LINE_SECONDS) {
            float t = Ease.outQuint(elapsed / LINE_SECONDS);
            float midY = (y1 + y2) * 0.5F;
            float head = x2 - width * t;
            Draw.gradientH(head, midY - 0.5F, x2, midY + 0.5F,
                    Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.textHover, 0.95F));
            drawSpeedLines(x1, midY, width, t);
            return;
        }

        // Leaving: the panel slides back out the way it came, and the exit is a
        // shorter, harsher curve than the entrance — arriving is the event.
        float slide = exit * (width * 0.35F);
        x1 += slide;
        x2 += slide;

        // The height opens out of that same line, so the panel is only ever seen
        // growing from its own middle.
        // outBack rather than a plain curve, and its overshoot is deliberately left
        // in: the panel goes a shade past its own height and settles back, which is
        // the difference between something arriving and something appearing.
        float midY = (y1 + y2) * 0.5F;
        float half = PANEL_HEIGHT * 0.5F * Ease.outBack(open);
        float top = midY - half;
        float bottom = midY + half;

        drawPanel(x1, top, x2, bottom, alpha);

        // Everything inside is clipped to the opening panel, so the text and the item
        // are revealed by it rather than sitting on top of a panel that has not
        // finished arriving.
        boolean clipped = open < 0.999F;
        if (clipped) {
            Draw.beginClip(x1 - SKEW, top, (x2 - x1) + SKEW * 2.0F, bottom - top);
        }
        drawContents(mc, font, toast, x1, y1, x2, y2, elapsed, alpha);
        if (clipped) {
            Draw.endClip();
        }

        drawTimer(x1, y2, x2, elapsed, dwell, alpha);

        // The flash sits over everything, including the item, because it is the panel
        // being struck rather than lit.
        float flash = 1.0F - Ease.clamp01((elapsed - LINE_SECONDS) / 0.22F);
        if (flash > 0.0F && exit <= 0.0F) {
            Draw.slant(x1, y1, x2, y2, SKEW,
                    Draw.withAlpha(0xFFFFFF, 0.45F * flash * flash));
        }
    }

    /** Fill, rail, hairline frame and the one sweep that crosses it on arrival. */
    private static void drawPanel(float x1, float y1, float x2, float y2, float alpha) {
        // Reads over anything: this floats on the world, and what is behind it is as
        // likely to be a snowfield as a cave wall.
        Draw.slant(x1 - 1.0F, y1 - 1.0F, x2 + 1.0F, y2 + 1.0F, SKEW,
                Draw.withAlpha(Theme.panelShadow, 0.45F * alpha));
        Draw.slantGradientV(x1, y1, x2, y2, SKEW,
                Draw.withAlpha(Theme.background, 0.92F * alpha),
                Draw.withAlpha(Draw.mix(Theme.background, Theme.accentAlt, 0.18F), 0.80F * alpha));
        Draw.slantBorder(x1, y1, x2, y2, SKEW, 1.0F, Draw.withAlpha(Theme.text, 0.12F * alpha));

        // The rail is the gold edge every panel in this mod has, cut on the same lean.
        Draw.slant(x1, y1, x1 + 3.0F, y2, SKEW, Draw.withAlpha(Theme.accent, 0.95F * alpha));
        Draw.slantGradientH(x1 + 3.0F, y1, x1 + 40.0F, y2, SKEW,
                Draw.withAlpha(Theme.accent, 0.16F * alpha), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** Icon, frame, kicker and the name typing itself out. */
    private static void drawContents(Minecraft mc, FontRenderer font, Toast toast,
                                     float x1, float y1, float x2, float y2,
                                     float elapsed, float alpha) {
        float iconCx = x1 + PADDING + ICON * 0.5F + SKEW * 0.35F;
        float iconCy = (y1 + y2) * 0.5F;

        drawIconFrame(iconCx, iconCy, elapsed, alpha);
        drawShockwave(iconCx, iconCy, elapsed, alpha);
        drawItem(toast, iconCx, iconCy, elapsed);

        float textX = x1 + PADDING + ICON + 10.0F + SKEW * 0.2F;
        float textRight = x2 - 6.0F;

        // Kicker above, name below. Letter-spaced and small: it is a label for what
        // the line under it is, and it should be read once and then ignored.
        drawSpaced(font, toast.kicker, textX, y1 + 8.0F,
                Draw.withAlpha(Theme.accent, 0.95F * alpha), textRight - textX);

        float typed = Ease.clamp01((elapsed - TYPE_START) / TYPE_SECONDS);
        String title = toast.title == null ? "" : toast.title;
        int shown = (int) Math.ceil(title.length() * typed);
        String visible = title.substring(0, Math.min(title.length(), Math.max(0, shown)));
        visible = trim(font, visible, (int) (textRight - textX));
        beginText();
        font.drawString(visible, (int) textX, (int) (y1 + 19.0F),
                Draw.withAlpha(Theme.textHover, alpha));

        // A caret while it is still typing, and gone the moment it is not — a cursor
        // that lingers turns a finished line into one that looks unfinished.
        if (typed < 1.0F && typed > 0.0F) {
            float caretX = textX + font.getStringWidth(visible) + 1.0F;
            Draw.rect(caretX, y1 + 18.0F, caretX + 3.0F, y1 + 26.0F,
                    Draw.withAlpha(Theme.accent, 0.9F * alpha));
        }
    }

    /**
     * The frame the item lands in: a leaning box with its corners cut away.
     *
     * Drawn as four short bars rather than a border, because a closed box around an
     * item reads as a slot from an inventory screen — and this is not one.
     */
    private static void drawIconFrame(float cx, float cy, float elapsed, float alpha) {
        float half = ICON * 0.62F;
        float corner = half * 0.55F;
        int colour = Draw.withAlpha(Theme.accent, 0.75F * alpha);

        float x1 = cx - half;
        float x2 = cx + half;
        float top = cy - half;
        float bottom = cy + half;
        float skew = SKEW * (half * 2.0F / PANEL_HEIGHT);

        // top and bottom, each broken in the middle
        Draw.slant(x1, top, x1 + corner, top + 1.0F, skew, colour);
        Draw.slant(x2 - corner, top, x2, top + 1.0F, skew, colour);
        Draw.slant(x1, bottom - 1.0F, x1 + corner, bottom, skew, colour);
        Draw.slant(x2 - corner, bottom - 1.0F, x2, bottom, skew, colour);
        // sides, likewise
        Draw.slant(x1, top, x1 + 1.0F, top + corner, skew, colour);
        Draw.slant(x1, bottom - corner, x1 + 1.0F, bottom, skew, colour);
        Draw.slant(x2 - 1.0F, top, x2, top + corner, skew, colour);
        Draw.slant(x2 - 1.0F, bottom - corner, x2, bottom, skew, colour);

        // A slow breath on the glow behind it, so a panel sitting still is never
        // completely still.
        float pulse = 0.5F + 0.5F * (float) Math.sin(elapsed * 3.4F);
        Draw.radialGlow(cx, cy, half * 2.2F,
                Draw.withAlpha(Theme.accent, (0.12F + 0.06F * pulse) * alpha),
                Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** One ring leaving the icon as it lands, and nothing after it. */
    private static void drawShockwave(float cx, float cy, float elapsed, float alpha) {
        float t = Ease.clamp01((elapsed - LINE_SECONDS) / 0.45F);
        if (t <= 0.0F || t >= 1.0F) {
            return;
        }
        float radius = 5.0F + 34.0F * Ease.outQuint(t);
        float fade = (1.0F - t) * (1.0F - t);
        Draw.ring(cx, cy, radius, 2.5F, Draw.withAlpha(Theme.accent, 0.7F * fade * alpha));
        Draw.ring(cx, cy, radius * 0.62F, 1.5F, Draw.withAlpha(Theme.textHover, 0.4F * fade * alpha));
    }

    /**
     * The achievement's item, dropped in with a punch.
     *
     * Only once the panel has opened far enough to hold it: an item drawn over a
     * panel that is still two pixels tall is the one thing here that would look like
     * a mistake rather than an entrance.
     */
    private static void drawItem(Toast toast, float cx, float cy, float elapsed) {
        if (toast.achievement.theItemStack == null) {
            return;
        }
        float t = Ease.clamp01((elapsed - LINE_SECONDS - 0.04F) / 0.30F);
        if (t <= 0.0F) {
            return;
        }
        // Lands from slightly too big and slightly turned, which is what makes it
        // read as having been put there rather than having faded in. Both of those
        // are safe on a cube as well as on a sprite; see ItemIcon, which is where
        // the state that makes that true now lives.
        float scale = 1.0F + 0.65F * (1.0F - Ease.outBack(t));
        float spin = (1.0F - Ease.outCubic(t)) * -25.0F;
        ItemIcon.draw(toast.achievement.theItemStack, cx, cy, scale, spin, 1.0F);
    }

    /**
     * The hairline along the bottom, draining as the panel's time runs out.
     *
     * On the edge rather than inside, for the same reason Waila's mining bar is: the
     * edge is already there, it is already the width of the panel, and the one thing
     * it has to say is how much longer.
     */
    private static void drawTimer(float x1, float y2, float x2, float elapsed, float dwell,
                                  float alpha) {
        float left = 1.0F - Ease.clamp01(elapsed / dwell);
        if (left <= 0.0F) {
            return;
        }
        float end = x1 + (x2 - x1) * left;
        Draw.gradientH(x1, y2 - 1.0F, end, y2,
                Draw.withAlpha(Theme.accentAlt, 0.9F * alpha), Draw.withAlpha(Theme.accent, 0.95F * alpha));
        Draw.rect(end - 1.0F, y2 - 2.0F, end, y2, Draw.withAlpha(Theme.textHover, 0.8F * alpha));
    }

    /** Three short streaks trailing the line of light, and gone with it. */
    private static void drawSpeedLines(float x1, float midY, float width, float t) {
        float fade = 1.0F - t;
        for (int i = 0; i < 3; i++) {
            float offset = (i + 1) * 5.0F;
            float length = 18.0F + i * 10.0F;
            float head = x1 + width * (1.0F - t) - i * 12.0F;
            Draw.gradientH(head - length, midY - offset, head, midY - offset + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.0F),
                    Draw.withAlpha(Theme.accent, 0.55F * fade));
            Draw.gradientH(head - length * 0.7F, midY + offset, head, midY + offset + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.0F),
                    Draw.withAlpha(Theme.accent, 0.4F * fade));
        }
    }

    // -------------------------------------------------------------------- text --

    /**
     * Turns blending on, immediately before text that has an alpha in its colour.
     *
     * The font renderer enables alpha testing and nothing else, so alpha in a colour
     * only means anything while blending is on — and every panel drawn before this
     * turns blending off again on its way out. Without this the panel faded away and
     * the words stayed at full strength on top of nothing.
     */
    private static void beginText() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Letter-spaced small text, the same treatment the ghost buttons give their
     * labels — which is what makes this label read as belonging to this interface.
     */
    private static void drawSpaced(FontRenderer font, String text, float x, float y,
                                   int colour, float maxWidth) {
        if (text == null) {
            return;
        }
        beginText();
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String glyph = text.substring(i, i + 1);
            float w = font.getStringWidth(glyph) + 1.0F;
            if (cursor + w > x + maxWidth) {
                return;
            }
            font.drawString(glyph, (int) cursor, (int) y, colour);
            cursor += w;
        }
    }

    private static float spacedWidth(FontRenderer font, String text) {
        return text == null ? 0.0F : font.getStringWidth(text) + text.length();
    }

    private static String trim(FontRenderer font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        return font.trimStringToWidth(text, maxWidth);
    }

    private static String nameOf(Achievement achievement) {
        try {
            return achievement.func_150951_e().getUnformattedText();
        } catch (Throwable t) {
            // A modded achievement with a broken name must not cost the popup.
            return achievement.statId;
        }
    }
}

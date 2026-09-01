package com.console.uky.client.render;

import com.console.uky.config.UiConfig;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * The thing that crosses the sky, and the star that sends it.
 *
 * <p>The backdrop is a still image that moves: the stars drift, the disk turns, and
 * none of it ever <em>happens</em>. A comet is the one event in it.
 *
 * <p><b>It is drawn to be seen.</b> The first version was astronomically reasonable —
 * a thin streak, a couple of seconds, once in two minutes — and it was invisible: at
 * that size against a field of five hundred stars it read as one of them moving. So it
 * is now a head with a real glow, a tail a third of the screen long, four to six
 * seconds of crossing, and often enough that a player who waits will see one. A menu
 * backdrop is not a planetarium; the point is to be noticed.
 *
 * <h2>The star that answers</h2>
 *
 * <p>{@code wish} is a star of our own rather than one of the field's: always on
 * screen, always in the same corner of the sky, and it twinkles — brightening and
 * fading on its own while everything around it sits still. That is the whole hint. Put
 * the pointer on it and it flares and grows a ring; click it and a comet leaves from
 * exactly that point.
 *
 * <p>Being ours is what makes it findable. Picking one of the starfield's five hundred
 * meant a star that was usually off screen, sometimes behind the shadow, and never
 * twice in the same place — a secret nobody could have found by looking.
 */
public final class Comets {

    /** At most this many at once. Two is a coincidence; three is weather. */
    private static final int MAX = 3;

    /** Mean seconds between spontaneous comets. */
    private static final float MEAN_INTERVAL = 40.0F;

    /** Seconds one takes to cross, head to gone. */
    private static final float LIFE_MIN = 4.0F;
    private static final float LIFE_MAX = 6.5F;

    private final Random random = new Random();

    private final float[] x = new float[MAX];
    private final float[] y = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] age = new float[MAX];
    private final float[] life = new float[MAX];
    /** Tail length in units, and how bright the head is. */
    private final float[] length = new float[MAX];
    private final float[] magnitude = new float[MAX];
    /** 0 is the palette's second accent, 1 the hot inner disk colour. */
    private final float[] hue = new float[MAX];

    private int width;
    private int height;

    // ---- the star that answers ----

    /** Seconds of its own clock, for the twinkle. */
    private float wishTime;
    private float wishX;
    private float wishY;
    /** Eased 0..1 as the pointer arrives and leaves. */
    private float wishHover;
    /** Counts down after a click, for the flash. */
    private float wishFlash;
    private float pointerX = Float.NaN;
    private float pointerY;

    /** How close the pointer has to be, in interface units. Generous on purpose. */
    private static final float WISH_RADIUS = 9.0F;

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Where the pointer is, in the same units the backdrop is drawn in. */
    public void pointer(float x, float y) {
        this.pointerX = x;
        this.pointerY = y;
    }

    /**
     * Advances what is in flight and, once in a while, starts one.
     *
     * The chance is per second rather than per frame, so a comet is as rare at 300 fps
     * as at 30 — a menu backdrop that got busier on a better computer would be a
     * strange thing to have written.
     */
    public void update(float deltaSeconds) {
        this.wishTime += deltaSeconds;
        placeWish();
        boolean near = isPointerOnWish();
        this.wishHover = Ease.approach(this.wishHover, near ? 1.0F : 0.0F, 0.09F,
                deltaSeconds);
        if (this.wishFlash > 0.0F) {
            this.wishFlash -= deltaSeconds;
        }

        for (int i = 0; i < MAX; i++) {
            if (this.life[i] <= 0.0F) {
                continue;
            }
            this.age[i] += deltaSeconds;
            this.x[i] += this.vx[i] * deltaSeconds;
            this.y[i] += this.vy[i] * deltaSeconds;
            if (this.age[i] >= this.life[i]) {
                this.life[i] = 0.0F;
            }
        }
        if (this.width > 0 && this.random.nextFloat() < deltaSeconds / MEAN_INTERVAL) {
            launch();
        }
    }

    /**
     * Sends one across, from off one edge to off another.
     *
     * Entry is always outside the frame and the heading always crosses it, so a comet
     * is never seen to appear — it is already travelling by the time it is on screen,
     * which is the difference between a comet and a spark.
     */
    public boolean launch() {
        if (this.width <= 0) {
            return false;
        }
        boolean fromLeft = this.random.nextBoolean();
        float startX = fromLeft ? -this.width * 0.15F : this.width * 1.15F;
        float startY = this.height * (-0.15F + this.random.nextFloat() * 0.45F);
        float targetX = fromLeft ? this.width * 1.2F : -this.width * 0.2F;
        float targetY = this.height * (0.5F + this.random.nextFloat() * 0.7F);
        return launch(startX, startY, targetX, targetY);
    }

    /**
     * Sends one from a given point, on its way out of the frame.
     *
     * For the star: a comet that answers a click should leave from the thing that was
     * clicked, or the two are unrelated events that happened at the same moment.
     */
    public boolean launchFrom(float fromX, float fromY) {
        if (this.width <= 0) {
            return false;
        }
        // Away from the middle, so it crosses the frame rather than leaving by the
        // nearest edge; the vertical is always downward, which is what a comet does.
        boolean rightwards = fromX < this.width * 0.5F;
        float targetX = rightwards ? this.width * 1.25F : -this.width * 0.25F;
        float targetY = this.height * (0.75F + this.random.nextFloat() * 0.5F);
        this.wishFlash = 0.5F;
        return launch(fromX, fromY, targetX, targetY);
    }

    private boolean launch(float startX, float startY, float targetX, float targetY) {
        int slot = free();
        if (slot < 0) {
            return false;
        }
        float seconds = LIFE_MIN + this.random.nextFloat() * (LIFE_MAX - LIFE_MIN);
        this.x[slot] = startX;
        this.y[slot] = startY;
        this.vx[slot] = (targetX - startX) / seconds;
        this.vy[slot] = (targetY - startY) / seconds;
        this.age[slot] = 0.0F;
        this.life[slot] = seconds;
        // A third of the screen at the long end. Anything shorter reads as a spark.
        this.length[slot] = this.height * (0.22F + this.random.nextFloat() * 0.16F);
        this.magnitude[slot] = 0.8F + this.random.nextFloat() * 0.2F;
        this.hue[slot] = this.random.nextFloat();
        return true;
    }

    private int free() {
        for (int i = 0; i < MAX; i++) {
            if (this.life[i] <= 0.0F) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- the star --

    /**
     * Puts the star where it belongs on this window, with a slow drift.
     *
     * High on the left, which is empty on every screen this backdrop is behind — the
     * title sits above the middle, the menu below it, and the hole is off to the right.
     */
    private void placeWish() {
        this.wishX = this.width * 0.115F
                + (float) Math.sin(this.wishTime * 0.06F) * this.width * 0.012F;
        this.wishY = this.height * 0.20F
                + (float) Math.cos(this.wishTime * 0.045F) * this.height * 0.015F;
    }

    private boolean isPointerOnWish() {
        return !Float.isNaN(this.pointerX) && within(this.pointerX, this.pointerY);
    }

    private boolean within(float px, float py) {
        float dx = px - this.wishX;
        float dy = py - this.wishY;
        float reach = WISH_RADIUS + this.wishHover * 3.0F;
        return dx * dx + dy * dy <= reach * reach;
    }

    /**
     * Whether a click landed on the star, and if so sends its comet.
     *
     * @return true when the click was taken
     */
    public boolean clickWish(float px, float py) {
        if (this.width <= 0 || Float.isNaN(this.wishX) || !within(px, py)) {
            return false;
        }
        launchFrom(this.wishX, this.wishY);
        return true;
    }

    /**
     * How brightly the star is burning this frame.
     *
     * A slow breath with a faster flicker over it, so it is never quite steady — that
     * is the whole of what marks it out from the field behind it. The pointer and a
     * click both push it well past anything a real star does.
     */
    private float wishBrightness() {
        float breath = 0.55F + 0.45F * (float) Math.sin(this.wishTime * 1.35F);
        float flicker = 0.9F + 0.1F * (float) Math.sin(this.wishTime * 7.3F);
        float base = (0.45F + 0.55F * breath) * flicker;
        float flash = this.wishFlash > 0.0F ? this.wishFlash / 0.5F : 0.0F;
        return Math.min(1.6F, base + this.wishHover * 0.7F + flash * 0.9F);
    }

    /**
     * Draws the star.
     *
     * A four-pointed flare rather than a dot: a dot is what the other five hundred
     * stars are, and this one has to be picked out of them by somebody who does not
     * know it is there.
     */
    public void renderWish(float alpha) {
        if (alpha <= 0.01F || this.width <= 0) {
            return;
        }
        float bright = wishBrightness() * alpha;
        if (bright <= 0.02F) {
            return;
        }
        float size = 1.6F + this.wishHover * 1.4F + (this.wishFlash > 0.0F
                ? this.wishFlash * 3.0F : 0.0F);
        float spike = size * (3.2F + this.wishHover * 1.6F);

        int warm = Draw.mix(0xFF000000 | UiConfig.colorAccent,
                0xFF000000 | UiConfig.colorBlackHoleHot, 0.55F);
        float wr = ((warm >> 16) & 0xFF) / 255.0F;
        float wg = ((warm >> 8) & 0xFF) / 255.0F;
        float wb = (warm & 0xFF) / 255.0F;

        begin();
        GL11.glBegin(GL11.GL_QUADS);

        // Halo, then the core, then the two spikes. All additive, so they pile up into
        // a white centre without anything having to be drawn white.
        quad(this.wishX, this.wishY, size * 3.0F, size * 3.0F, wr, wg, wb, bright * 0.22F);
        quad(this.wishX, this.wishY, size * 1.5F, size * 1.5F, wr, wg, wb, bright * 0.5F);
        quad(this.wishX, this.wishY, size * 0.75F, size * 0.75F, 1.0F, 1.0F, 1.0F,
                bright * 0.9F);
        spike(this.wishX, this.wishY, spike, size * 0.32F, wr, wg, wb, bright * 0.55F);
        spike(this.wishX, this.wishY, size * 0.32F, spike, wr, wg, wb, bright * 0.55F);

        GL11.glEnd();

        if (this.wishHover > 0.01F) {
            // A ring the moment the pointer is on it: this is the one place in the
            // backdrop that answers, and a thing that can be clicked should say so.
            end();
            Draw.ring(this.wishX, this.wishY, WISH_RADIUS + 2.0F, 1.0F,
                    Draw.withAlpha(UiConfig.colorAccent, 0.55F * this.wishHover * alpha));
            return;
        }
        end();
    }

    // ------------------------------------------------------------- the comets --

    /**
     * Draws whatever is in flight.
     *
     * <p>Additively, like the stars, and in the same two colours the rest of the
     * backdrop is made of — a comet in some colour of its own would be the only thing
     * on screen that was not part of the palette.
     *
     * <p>Three layers: a wide soft wake, a bright core streak inside it, and a head
     * with its own halo. One of them alone is a line on the screen; the three together
     * are something with a front and a back.
     */
    public void render(float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        boolean any = false;
        for (int i = 0; i < MAX; i++) {
            if (this.life[i] > 0.0F) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < MAX; i++) {
            if (this.life[i] > 0.0F) {
                emit(i, alpha);
            }
        }
        GL11.glEnd();
        end();
    }

    private void emit(int i, float alpha) {
        float t = this.age[i] / this.life[i];
        // In over the first twentieth, out over the last quarter: a comet that winks
        // out at full brightness reads as a rendering fault rather than as distance.
        float fade = Math.min(1.0F, t / 0.05F) * Math.min(1.0F, (1.0F - t) / 0.25F);
        if (fade <= 0.0F) {
            return;
        }
        float speed = (float) Math.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i]);
        if (speed < 0.001F) {
            return;
        }
        float dx = this.vx[i] / speed;
        float dy = this.vy[i] / speed;
        float px = -dy;
        float py = dx;

        float head = this.magnitude[i] * fade * alpha;
        float hx = this.x[i];
        float hy = this.y[i];
        float tailX = hx - dx * this.length[i];
        float tailY = hy - dy * this.length[i];

        int warm = Draw.mix(0xFF000000 | UiConfig.colorAccentAlt,
                0xFF000000 | UiConfig.colorBlackHoleHot, this.hue[i]);
        float wr = ((warm >> 16) & 0xFF) / 255.0F;
        float wg = ((warm >> 8) & 0xFF) / 255.0F;
        float wb = (warm & 0xFF) / 255.0F;

        // The wake: wide at the head, nothing at the tip.
        streak(hx, hy, tailX, tailY, px, py, 4.5F, 0.6F, wr, wg, wb, head * 0.22F);
        // The core, half as wide and twice as bright.
        streak(hx, hy, tailX, tailY, px, py, 1.7F, 0.2F, wr, wg, wb, head * 0.75F);
        // And a short white lead, so the front of it is a point of light.
        streak(hx + dx * 3.0F, hy + dy * 3.0F, hx - dx * this.length[i] * 0.18F,
                hy - dy * this.length[i] * 0.18F, px, py, 0.9F, 0.2F,
                1.0F, 1.0F, 1.0F, head * 0.85F);

        // Head: a halo, and a core inside it.
        quad(hx, hy, 5.5F, 5.5F, wr, wg, wb, head * 0.30F);
        quad(hx, hy, 2.6F, 2.6F, wr, wg, wb, head * 0.6F);
        quad(hx, hy, 1.3F, 1.3F, 1.0F, 1.0F, 1.0F, head * 0.95F);
    }

    /** One tapering strip from the head back to the tail. */
    private void streak(float hx, float hy, float tx, float ty, float px, float py,
                        float halfHead, float halfTail,
                        float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(hx + px * halfHead, hy + py * halfHead);
        GL11.glVertex2f(hx - px * halfHead, hy - py * halfHead);
        GL11.glColor4f(r, g, b, 0.0F);
        GL11.glVertex2f(tx - px * halfTail, ty - py * halfTail);
        GL11.glVertex2f(tx + px * halfTail, ty + py * halfTail);
    }

    /** An axis-aligned blob, brightest in the middle by virtue of being piled up. */
    private void quad(float cx, float cy, float halfW, float halfH,
                      float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(cx - halfW, cy - halfH);
        GL11.glVertex2f(cx - halfW, cy + halfH);
        GL11.glVertex2f(cx + halfW, cy + halfH);
        GL11.glVertex2f(cx + halfW, cy - halfH);
    }

    /** A spike of the star: bright at the centre, gone at both ends. */
    private void spike(float cx, float cy, float halfW, float halfH,
                       float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, 0.0F);
        GL11.glVertex2f(cx - halfW, cy - halfH);
        GL11.glVertex2f(cx - halfW, cy + halfH);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(cx, cy + halfH);
        GL11.glVertex2f(cx, cy - halfH);

        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(cx, cy - halfH);
        GL11.glVertex2f(cx, cy + halfH);
        GL11.glColor4f(r, g, b, 0.0F);
        GL11.glVertex2f(cx + halfW, cy + halfH);
        GL11.glVertex2f(cx + halfW, cy - halfH);
    }

    private void begin() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private void end() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

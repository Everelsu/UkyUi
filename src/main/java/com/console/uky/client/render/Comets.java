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

    // ---- falling in ----

    /**
     * Chance that a spontaneous comet is aimed past the hole and does not come back.
     *
     * Rare enough to be an event, common enough that somebody who leaves the menu up
     * will see it: at one comet every forty seconds, one in four is a capture every
     * two or three minutes.
     */
    private static final float CAPTURE_CHANCE = 0.25F;
    /** How long the tail takes to drain into the horizon after the head has crossed it. */
    private static final float DRAIN_SECONDS = 0.45F;

    private static final int STRAIGHT = 0;
    /** Doomed, still on the way in, pulled by the hole. */
    private static final int FALLING = 1;
    /** Past its closest pass, winding down the spiral. */
    private static final int SPIRAL = 2;
    /** Head gone over the horizon, tail following it down. */
    private static final int DRAINING = 3;

    private final int[] phase = new int[MAX];
    /** Gravity strength for a doomed comet, scaled so every hole size gets the same orbit. */
    private final float[] pull = new float[MAX];
    /** Spiral state: radius, angle, angular speed at its start, starting radius, sense, decay. */
    private final float[] spiralR = new float[MAX];
    private final float[] spiralAngle = new float[MAX];
    private final float[] spiralSpin0 = new float[MAX];
    private final float[] spiralStart = new float[MAX];
    private final float[] spiralSense = new float[MAX];
    private final float[] spiralDecay = new float[MAX];
    private final float[] drain = new float[MAX];

    /**
     * Where each comet has been, newest last, so the tail follows the path it took
     * rather than pointing straight back — a comet whipping round the hole has to
     * drag its tail round with it, or the orbit reads as a stick spinning.
     */
    private static final int TRAIL = 72;
    private final float[][] trailX = new float[MAX][TRAIL];
    private final float[][] trailY = new float[MAX][TRAIL];
    private final int[] trailHead = new int[MAX];
    private final int[] trailCount = new int[MAX];

    /** The hole this frame, in backdrop units; NaN when there is none. */
    private float holeX = Float.NaN;
    private float holeY;
    private float holeR;
    /**
     * The hole as last drawn, kept across the end of the frame. A click is handled
     * between frames, after {@link #clearAttractor()} has run, so anything acting on
     * one reads this rather than the per-frame attractor — read from that, every
     * click on the sky found no hole at all.
     */
    private float lastHoleX = Float.NaN;
    private float lastHoleY;
    private float lastHoleR;
    /** Comets eaten since the last {@link #takeSwallowed()}. */
    private int swallowed;

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

    /**
     * Where the hole is this frame, and its shadow's radius. Set every frame the sky
     * draws it, cleared by {@link #clearAttractor()} once the comets have used it.
     */
    public void attractor(float x, float y, float radius) {
        if (radius <= 1.0F) {
            clearAttractor();
            return;
        }
        this.holeX = x;
        this.holeY = y;
        this.holeR = radius;
        this.lastHoleX = x;
        this.lastHoleY = y;
        this.lastHoleR = radius;
    }

    public void clearAttractor() {
        this.holeX = Float.NaN;
    }

    private boolean hasHole() {
        return !Float.isNaN(this.holeX);
    }

    /** How many comets crossed the horizon since last asked, so the hole can flare. */
    public int takeSwallowed() {
        int n = this.swallowed;
        this.swallowed = 0;
        return n;
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
            move(i, deltaSeconds);
            if (this.phase[i] != DRAINING) {
                record(i);
            }
            if (this.age[i] >= this.life[i] || isLost(i)) {
                this.life[i] = 0.0F;
            }
        }
        if (this.width > 0 && this.random.nextFloat() < deltaSeconds / MEAN_INTERVAL) {
            if (!hasHole() || this.random.nextFloat() >= CAPTURE_CHANCE || !launchDoomed()) {
                launch();
            }
        }
    }

    private void move(int i, float dt) {
        int phase = this.phase[i];
        if (phase != STRAIGHT && !hasHole()) {
            // The hole went out of frame (a screen without it). Nothing to fall into:
            // let it slip away along its last heading.
            if (phase == SPIRAL || phase == DRAINING) {
                this.life[i] = Math.min(this.life[i], this.age[i] + 0.3F);
            }
            this.phase[i] = phase = STRAIGHT;
        }
        if (phase == STRAIGHT) {
            this.x[i] += this.vx[i] * dt;
            this.y[i] += this.vy[i] * dt;
            return;
        }
        if (phase == DRAINING) {
            this.drain[i] += dt;
            if (this.drain[i] >= DRAIN_SECONDS) {
                this.life[i] = 0.0F;
            }
            return;
        }
        float dx = this.x[i] - this.holeX;
        float dy = this.y[i] - this.holeY;
        if (phase == FALLING) {
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            float soft = Math.max(r, this.holeR * 0.6F);
            float a = this.pull[i] / (soft * soft * soft);
            this.vx[i] -= a * dx * dt;
            this.vy[i] -= a * dy * dt;
            // Closest pass: from here a ballistic orbit would carry it back out, so it
            // hands over to the spiral, at the same speed it arrived with.
            if (dx * this.vx[i] + dy * this.vy[i] > 0.0F && r < this.holeR * 5.0F) {
                float speed = (float) Math.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i]);
                this.phase[i] = SPIRAL;
                this.spiralR[i] = r;
                this.spiralStart[i] = r;
                this.spiralAngle[i] = (float) Math.atan2(dy, dx);
                this.spiralSpin0[i] = speed / r;
                this.spiralSense[i] = dx * this.vy[i] - dy * this.vx[i] >= 0.0F ? 1.0F : -1.0F;
                return;
            }
            this.x[i] += this.vx[i] * dt;
            this.y[i] += this.vy[i] * dt;
            return;
        }
        // SPIRAL: the radius decays, and the angular speed climbs as Kepler says it
        // would for the tighter orbit — so it slings faster and faster the deeper it
        // goes, which is the whole drama of it.
        float r = this.spiralR[i] * (float) Math.exp(-this.spiralDecay[i] * dt);
        this.spiralR[i] = r;
        float ratio = this.spiralStart[i] / r;
        float spin = this.spiralSpin0[i] * ratio * (float) Math.sqrt(ratio);
        this.spiralAngle[i] += this.spiralSense[i] * spin * dt;
        // Settles into the plane of the disk on the way down: the orbit flattens
        // towards the disk's near edge-on ellipse.
        float settle = Ease.clamp01((this.spiralStart[i] - r) / Math.max(1.0F, this.spiralStart[i] - this.holeR));
        float squash = 1.0F - 0.7F * settle;
        float nx = this.holeX + (float) Math.cos(this.spiralAngle[i]) * r;
        float ny = this.holeY + (float) Math.sin(this.spiralAngle[i]) * r * squash;
        if (dt > 0.0F) {
            this.vx[i] = (nx - this.x[i]) / dt;
            this.vy[i] = (ny - this.y[i]) / dt;
        }
        this.x[i] = nx;
        this.y[i] = ny;
        if (r < this.holeR * 1.05F) {
            this.phase[i] = DRAINING;
            this.drain[i] = 0.0F;
            this.swallowed++;
        }
    }

    /** Well clear of the frame and not coming back: free the slot. */
    private boolean isLost(int i) {
        return this.age[i] > 2.0F
                && (this.x[i] < -this.width * 0.6F || this.x[i] > this.width * 1.6F
                || this.y[i] < -this.height * 0.6F || this.y[i] > this.height * 1.6F);
    }

    private void record(int i) {
        int head = this.trailHead[i];
        if (this.trailCount[i] > 0) {
            float dx = this.x[i] - this.trailX[i][head];
            float dy = this.y[i] - this.trailY[i][head];
            // Spaced by distance, so a slow comet does not spend the whole buffer on
            // the last half second and a fast one still has a smooth curve.
            if (dx * dx + dy * dy < 4.0F) {
                return;
            }
            head = (head + 1) % TRAIL;
        }
        this.trailHead[i] = head;
        this.trailX[i][head] = this.x[i];
        this.trailY[i][head] = this.y[i];
        this.trailCount[i] = Math.min(TRAIL, this.trailCount[i] + 1);
    }

    /**
     * Lets go of a clump of gas at a point near the hole, already moving sideways —
     * anything near a black hole is orbiting it — but too slowly to stay up, so it
     * falls in on the same tightening spiral a captured comet takes.
     *
     * @return false when there is no hole, no free slot, or the point is inside it
     */
    public boolean feedFrom(float fromX, float fromY) {
        int slot = free();
        if (slot < 0 || Float.isNaN(this.lastHoleX)) {
            return false;
        }
        float holeR = this.lastHoleR;
        float dx = fromX - this.lastHoleX;
        float dy = fromY - this.lastHoleY;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        if (r < holeR * 1.2F) {
            return false;
        }
        // At the same scale as a comet's capture, so the spiral has the same shape.
        float pull = 6.0F * holeR * holeR * holeR;
        // 85% of circular speed: the orbit's low point is about half the release
        // radius, inside the hand-over to the spiral.
        float speed = 0.85F * (float) Math.sqrt(pull / r);
        float sense = this.random.nextBoolean() ? 1.0F : -1.0F;
        reset(slot, fromX, fromY, -dy / r * speed * sense, dx / r * speed * sense);
        this.life[slot] = 30.0F;
        this.phase[slot] = FALLING;
        this.pull[slot] = pull;
        this.spiralDecay[slot] = 0.3F;
        // A clump, not a comet: short tail, a little dimmer.
        this.length[slot] = holeR * 1.4F;
        this.magnitude[slot] = 0.75F;
        return true;
    }

    /**
     * Sends one past the hole, close enough that it does not come back out.
     *
     * Aimed a few shadow radii to one side of it, not at it: a comet that dived
     * straight in would look like it was shot. The pull is scaled to the speed and the
     * hole's size so that every capture is the same shape — a swing round, then about
     * a turn and a half of tightening spiral — whatever the window or the screen.
     */
    private boolean launchDoomed() {
        int slot = free();
        if (slot < 0 || this.width <= 0) {
            return false;
        }
        boolean fromLeft = this.holeX > this.width * 0.5F
                ? this.random.nextFloat() < 0.8F : this.random.nextFloat() < 0.2F;
        float startX = fromLeft ? -this.width * 0.15F : this.width * 1.15F;
        float startY = Math.max(-this.height * 0.15F, Math.min(this.height * 1.15F,
                this.holeY + this.height * (this.random.nextFloat() - 0.6F) * 0.8F));
        float dx = this.holeX - startX;
        float dy = this.holeY - startY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < this.holeR * 3.0F) {
            return false;
        }
        float side = this.random.nextBoolean() ? 1.0F : -1.0F;
        float miss = this.holeR * (3.0F + this.random.nextFloat());
        float aimX = this.holeX - dy / dist * miss * side;
        float aimY = this.holeY + dx / dist * miss * side;

        float seconds = LIFE_MIN + this.random.nextFloat() * (LIFE_MAX - LIFE_MIN);
        float speed = this.width * 1.35F / seconds;
        float ax = aimX - startX;
        float ay = aimY - startY;
        float aim = (float) Math.sqrt(ax * ax + ay * ay);

        reset(slot, startX, startY, ax / aim * speed, ay / aim * speed);
        this.life[slot] = 30.0F; // ended by the horizon, not the clock
        this.phase[slot] = FALLING;
        // Shape-invariant: the trajectory scales with holeR, its timing with speed.
        float unit = speed / (2.5F * this.holeR);
        this.pull[slot] = 6.0F * this.holeR * this.holeR * this.holeR * unit * unit;
        this.spiralDecay[slot] = 0.3F * unit;
        return true;
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
        reset(slot, startX, startY, (targetX - startX) / seconds, (targetY - startY) / seconds);
        this.life[slot] = seconds;
        return true;
    }

    private void reset(int slot, float startX, float startY, float vx, float vy) {
        this.x[slot] = startX;
        this.y[slot] = startY;
        this.vx[slot] = vx;
        this.vy[slot] = vy;
        this.age[slot] = 0.0F;
        this.phase[slot] = STRAIGHT;
        this.trailCount[slot] = 0;
        this.trailHead[slot] = 0;
        // A third of the screen at the long end. Anything shorter reads as a spark.
        this.length[slot] = this.height * (0.22F + this.random.nextFloat() * 0.16F);
        this.magnitude[slot] = 0.8F + this.random.nextFloat() * 0.2F;
        this.hue[slot] = this.random.nextFloat();
        record(slot);
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

        int warm = Draw.mix(Theme.accent, Theme.holeHot, 0.55F);
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
                    Draw.withAlpha(Theme.accent, 0.55F * this.wishHover * alpha));
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
        // In over the first quarter second, out over the last quarter of its life: a
        // comet that winks out at full brightness reads as a rendering fault rather
        // than as distance.
        float fade = Math.min(1.0F, this.age[i] / 0.25F)
                * Math.min(1.0F, (this.life[i] - this.age[i]) / (this.life[i] * 0.25F));
        if (fade <= 0.0F) {
            return;
        }
        float speed = (float) Math.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i]);
        if (speed < 0.001F && this.phase[i] != DRAINING) {
            return;
        }

        float bright = this.magnitude[i] * fade * alpha;
        float length = this.length[i];
        boolean headShown = true;
        if (this.phase[i] == SPIRAL) {
            // Dimming into the horizon, the way light climbing out of a well does.
            float above = (this.spiralR[i] - this.holeR) / (this.holeR * 0.8F);
            bright *= 0.35F + 0.65F * Ease.clamp01(above);
            // And stretched along the orbit as it speeds up.
            length *= 1.0F + 0.6F * Ease.clamp01(1.0F - above);
        } else if (this.phase[i] == DRAINING) {
            float left = 1.0F - this.drain[i] / DRAIN_SECONDS;
            length *= left;
            bright *= 0.35F * left;
            headShown = false;
        }

        int warm = Draw.mix(Theme.accentAlt, Theme.holeHot, this.hue[i]);
        float wr = ((warm >> 16) & 0xFF) / 255.0F;
        float wg = ((warm >> 8) & 0xFF) / 255.0F;
        float wb = (warm & 0xFF) / 255.0F;

        // The wake: wide at the head, nothing at the tip.
        trail(i, length, 4.5F, 0.6F, wr, wg, wb, bright * 0.22F);
        // The core, half as wide and twice as bright.
        trail(i, length, 1.7F, 0.2F, wr, wg, wb, bright * 0.75F);
        // And a short white lead, so the front of it is a point of light.
        trail(i, length * 0.18F, 0.9F, 0.2F, 1.0F, 1.0F, 1.0F, bright * 0.85F);

        if (!headShown) {
            return;
        }
        float hx = this.x[i];
        float hy = this.y[i];
        float hidden = occlusion(i, hx, hy);
        float head = bright * hidden;
        // Head: a halo, and a core inside it.
        quad(hx, hy, 5.5F, 5.5F, wr, wg, wb, head * 0.30F);
        quad(hx, hy, 2.6F, 2.6F, wr, wg, wb, head * 0.6F);
        quad(hx, hy, 1.3F, 1.3F, 1.0F, 1.0F, 1.0F, head * 0.95F);
    }

    /**
     * 0 where the shadow is in front of the point, 1 where it is not.
     *
     * The far side of an orbit passes behind the hole, and a comet drawn over the
     * shadow there would put it in front of something it is behind. Near edge-on, the
     * far side is the half above the centre. Only for comets that are in the hole's
     * grip: one just crossing the sky is nearer than the hole and stays in front.
     */
    private float occlusion(int i, float px, float py) {
        if (this.phase[i] == STRAIGHT || !hasHole() || py >= this.holeY) {
            return 1.0F;
        }
        float dx = px - this.holeX;
        float dy = py - this.holeY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        return Ease.clamp01((d - this.holeR * 0.92F) / (this.holeR * 0.16F));
    }

    /**
     * One tapering strip back along the path the comet actually flew, {@code length}
     * units of it, from {@code halfHead} wide to {@code halfTail}.
     */
    private void trail(int i, float length, float halfHead, float halfTail,
                       float r, float g, float b, float a) {
        int count = this.trailCount[i];
        if (count < 2 || length <= 0.5F || a <= 0.003F) {
            return;
        }
        int idx = this.trailHead[i];
        float ax = this.x[i];
        float ay = this.y[i];
        float walked = 0.0F;
        for (int k = 0; k < count; k++) {
            int prev = (idx - k + TRAIL) % TRAIL;
            float bx = this.trailX[i][prev];
            float by = this.trailY[i][prev];
            float sx = ax - bx;
            float sy = ay - by;
            float seg = (float) Math.sqrt(sx * sx + sy * sy);
            if (seg < 0.001F) {
                continue;
            }
            float t0 = walked / length;
            float t1 = Math.min(1.0F, (walked + seg) / length);
            if (walked + seg > length) {
                float keep = (length - walked) / seg;
                bx = ax - sx * keep;
                by = ay - sy * keep;
            }
            float nx = -sy / seg;
            float ny = sx / seg;
            float w0 = halfHead + (halfTail - halfHead) * t0;
            float w1 = halfHead + (halfTail - halfHead) * t1;
            float a0 = a * (1.0F - t0) * occlusion(i, ax, ay);
            float a1 = a * (1.0F - t1) * occlusion(i, bx, by);
            GL11.glColor4f(r, g, b, a0);
            GL11.glVertex2f(ax + nx * w0, ay + ny * w0);
            GL11.glVertex2f(ax - nx * w0, ay - ny * w0);
            GL11.glColor4f(r, g, b, a1);
            GL11.glVertex2f(bx - nx * w1, by - ny * w1);
            GL11.glVertex2f(bx + nx * w1, by + ny * w1);
            walked += seg;
            if (walked >= length) {
                return;
            }
            ax = bx;
            ay = by;
        }
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

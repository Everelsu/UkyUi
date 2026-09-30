package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;
import net.minecraft.client.audio.ISound;

/**
 * The one-shot opening of the title screen: the second half of a star dying.
 *
 * The loading screen ends by pulling everything on it into a white-hot point in the
 * middle of the window (see {@code UkySplash}). This picks the story up from that
 * same point: the ember throbs faster and faster while matter spirals into it, drifts
 * to where the hole will live, implodes — and the black hole is born out of the
 * shock, its lens opening from nothing and overshooting before it settles.
 *
 * Timing lives here rather than in the screen so the screen only has to ask
 * "how big is the hole, how bright, how warped, how visible is the menu".
 *
 * Plays once per game launch. Any click or key skips straight to the end.
 */
public final class TitleIntro {

    /** The splash's ember throbbing back to life while matter falls into it. */
    private static final float GATHER = 1.05F;
    /** The ember collapsing in on itself; the moment of darkness before the hit. */
    private static final float IMPLODE = 0.22F;
    private static final float BIRTH = GATHER + IMPLODE;
    /** The hole opening out of the shock and settling. */
    private static final float FORM = 2.20F;
    private static final float TOTAL = BIRTH + FORM;

    /** How long the lens takes to open to full size (with its overshoot). */
    private static final float OPEN = 1.35F;

    /** Streaks of infalling matter drawn during the gather. */
    private static final int STREAKS = 56;

    /**
     * How long the intro refuses to be skipped.
     *
     * A click or a key press skips it, and a queued one arriving on the first frame
     * skips it before a single pixel of it has been drawn. That is not hypothetical:
     * a pack this size takes minutes to load, people click around while they wait,
     * and LWJGL hands the whole backlog to the first screen that asks for input —
     * which is this one. Three tenths of a second is under the reaction time of
     * somebody who meant it and well over the age of anything left in the buffer.
     */
    private static final float SKIP_GRACE = 0.30F;

    /** Only the first title screen of a session gets the intro. */
    private static boolean playedThisSession;
    /** Whether the log has already said something about the intro this session. */
    private static boolean reported;

    /**
     * Longest the intro holds in the dark for the sound engine.
     *
     * The resource reload at the end of start-up restarts it on its own thread, and
     * an intro without its hit is not worth playing on time. The wait is black, which
     * is what the splash ended on, so it reads as the pause before the ember.
     */
    private static final float MAX_SOUND_WAIT = 3.0F;

    private float time;
    private float waited;
    private boolean started;
    private boolean active;
    private boolean impactFired;
    private ISound riser;

    public static void resetForTesting() {
        playedThisSession = false;
    }

    /** Call from initGui. Returns true if the intro will actually run. */
    public boolean begin() {
        // Already running on this screen: a second call is a re-layout, not a second
        // opening. initGui runs again on every resize, and at start-up the window
        // settles into its size while the title screen is already up — so the intro
        // began, the resize called this a moment later, and the branch below cancelled
        // it. The animation simply never appeared, and the flag said it had played.
        if (this.active) {
            return true;
        }
        if (playedThisSession || !Quality.intro()) {
            report(playedThisSession
                    ? "Title intro: already played this session"
                    : "Title intro: off (mainmenu.intro=" + UiConfig.introEnabled
                            + ", effects.graphics=" + UiConfig.graphics + ")");
            this.active = false;
            return false;
        }
        report("Title intro: starting");
        // Deliberately not claiming the session here; see update().
        this.active = true;
        this.time = 0.0F;
        this.waited = 0.0F;
        this.started = false;
        this.impactFired = false;
        return true;
    }

    /**
     * One line per session about what the intro did.
     *
     * It earns its place. "The intro does not play" has no other symptom, no error and
     * no crash, and every explanation for it — a preset that caps it, a screen that
     * consumed it, an input that skipped it — is invisible from the outside. One line
     * turns the next report of it into an answer instead of a guess.
     */
    private static void report(String message) {
        if (!reported) {
            reported = true;
            UkyUI.LOGGER.info(message);
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public void update(float deltaSeconds) {
        if (!this.active) {
            return;
        }
        if (!this.started) {
            this.waited += deltaSeconds;
            if (!UkySounds.isReady() && this.waited < MAX_SOUND_WAIT) {
                return;
            }
            this.started = true;
            this.riser = UkySounds.play(UkySounds.INTRO_RISER, (float) UiConfig.introVolume, 1.0F);
        }
        this.time += deltaSeconds;

        if (!this.impactFired && this.time >= BIRTH) {
            this.impactFired = true;
            // The session's one intro is claimed here, at the hit — not in begin(),
            // and not on the first frame either.
            //
            // begin() is called from initGui, and a title screen can be built, have
            // its initGui run, and be gone again before anybody sees it: in a pack
            // this size the main menu is constructed more than once on the way to the
            // one that stays, because several mods open a screen of their own during
            // start-up. Claiming it there spent the intro on a screen nobody looked
            // at. Claiming it at the hit means an intro that was interrupted before
            // the hole appeared is still owed to the player.
            playedThisSession = true;
            UkySounds.play(UkySounds.INTRO_IMPACT, (float) UiConfig.introVolume, 1.0F);
        }
        if (this.time >= TOTAL) {
            this.active = false;
        }
    }

    /** Jumps to the end, leaving the menu in its resting state. */
    public void skip() {
        // Not in the first moments; see SKIP_GRACE.
        if (!this.active || this.time < SKIP_GRACE) {
            return;
        }
        // Skipping is a decision, so it counts as having been shown even if the hit
        // has not landed yet and update() has therefore not claimed it.
        playedThisSession = true;
        this.time = TOTAL;
        this.active = false;
        UkySounds.stop(this.riser);
        this.riser = null;
    }

    // ------------------------------------------------------------- readouts --

    private float sinceBirth() {
        return this.time - BIRTH;
    }

    /** Brightness of the disk, 0 until the hole is born and 1 once it has settled. */
    public float holeIntensity() {
        if (!this.active) {
            return 1.0F;
        }
        if (this.time < BIRTH) {
            return 0.0F;
        }
        return Ease.outCubic(sinceBirth() / (FORM * 0.6F));
    }

    /**
     * Size of the hole against its resting size.
     *
     * Zero until the birth, and the lens with it — the hole renderer puts every star
     * back where the unbent sky has it as the radius goes to nothing, so the stars
     * visibly get shoved aside as it opens. Overshoots, then settles.
     */
    public float holeScale() {
        if (!this.active) {
            return 1.0F;
        }
        if (this.time < BIRTH) {
            return 0.0F;
        }
        return Ease.outBack(sinceBirth() / OPEN);
    }

    /** Space-distortion multiplier: kicked hard by the birth, relaxing to 1. */
    public float warp() {
        if (!this.active || this.time < BIRTH) {
            return 1.0F;
        }
        float t = Ease.outQuint(sinceBirth() / FORM);
        return 1.0F + (1.0F - t) * 1.2F;
    }

    /**
     * Camera shake from the hit, in screen units, as {x, y} into {@code out}.
     * Two detuned sines rather than noise, so it rattles without jittering.
     */
    public void shake(float[] out) {
        out[0] = 0.0F;
        out[1] = 0.0F;
        if (!this.active || this.time < BIRTH) {
            return;
        }
        float since = sinceBirth();
        float amp = 9.0F * (float) Math.exp(-since * 4.5F);
        if (amp < 0.05F) {
            return;
        }
        out[0] = amp * (float) Math.sin(since * 71.0F);
        out[1] = amp * 0.7F * (float) Math.sin(since * 53.0F + 1.3F);
    }

    /** Zoom kick from the hit: 1 at rest, a few percent over it for an instant. */
    public float punch() {
        if (!this.active || this.time < BIRTH) {
            return 1.0F;
        }
        return 1.0F + 0.06F * (float) Math.exp(-sinceBirth() * 6.0F);
    }

    /** How much of the UI (logo, buttons, corners) is faded in. */
    public float uiAlpha() {
        if (!this.active) {
            return 1.0F;
        }
        // The menu only starts arriving once the hole has mostly opened.
        float start = BIRTH + FORM * 0.45F;
        return Ease.outCubic((this.time - start) / (TOTAL - start));
    }

    // -------------------------------------------------------------- drawing --

    /**
     * Everything the intro adds over the sky. Drawn over the backdrop, under the UI.
     *
     * {@code cx}, {@code cy}: where the hole is drawn this frame.
     */
    public void render(float width, float height, float cx, float cy, float radius) {
        if (!this.active) {
            return;
        }
        // The splash collapsed into the middle of the window; the ember starts there.
        float startX = width * 0.5F;
        float startY = height * 0.5F;

        if (this.time < BIRTH) {
            renderBeforeBirth(width, height, startX, startY, cx, cy, radius);
        } else {
            renderBirth(width, height, cx, cy, radius);
        }
    }

    private void renderBeforeBirth(float width, float height, float startX, float startY,
                                   float cx, float cy, float radius) {
        // The sky seeps in under a lifting black, then is swallowed again as the ember
        // implodes — the moment of darkness right before the hit.
        float lift = Ease.inOutCubic(this.time / GATHER);
        float swallow = Ease.inCubic((this.time - GATHER) / IMPLODE);
        float dark = 1.0F - lift * 0.55F + swallow * 0.55F;
        Draw.rect(0, 0, width, height, Draw.withAlpha(0x000000, dark));

        // Drifts from where the splash left it to where the hole will be.
        float move = Ease.inOutCubic(this.time / BIRTH);
        float x = Ease.lerp(startX, cx, move);
        float y = Ease.lerp(startY, cy, move);

        renderInfall(x, y, radius, lift * (1.0F - swallow));

        // Heartbeat that quickens as the collapse approaches: phase = t^2 speeds up.
        float beat = (float) Math.sin(this.time * this.time * 9.0F) * 0.5F + 0.5F;
        float grow = Ease.outCubic(this.time / 0.35F);
        // Everything that shone is pulled into a vanishing point during the implosion.
        float squeeze = 1.0F - Ease.inCubic((this.time - GATHER) / IMPLODE);
        float heat = grow * (0.55F + 0.45F * beat) * (0.6F + 0.4F * lift);

        float halo = radius * (1.4F + beat * 0.5F) * squeeze;
        Draw.radialGlow(x, y, halo * 2.4F,
                Draw.withAlpha(Theme.accent, 0.22F * heat), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.radialGlow(x, y, halo,
                Draw.withAlpha(0xFFFFFF, 0.55F * heat), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.circle(x, y, Math.max(0.6F, 1.8F * squeeze + beat * 0.6F * squeeze),
                Draw.withAlpha(0xFFFFFF, Math.min(1.0F, heat + 0.2F) * (0.3F + 0.7F * squeeze)));
    }

    /**
     * Matter spiralling into the ember: thin streaks falling inward along a tightening
     * spiral, each on its own clock so the stream never pulses as one.
     */
    private void renderInfall(float x, float y, float radius, float strength) {
        if (strength <= 0.01F) {
            return;
        }
        float reach = radius * 9.0F;
        for (int i = 0; i < STREAKS; i++) {
            // Cheap stable hash per streak; the same streak every frame.
            float seed = (float) ((i * 0.6180339887) % 1.0);
            float speed = 0.9F + seed * 0.8F;
            float life = ((this.time * speed + seed * 3.7F) % 1.0F);
            // Falls faster the closer it gets.
            float r = reach * (1.0F - Ease.inCubic(life)) * (0.55F + 0.45F * seed);
            float angle = i * 2.39996F + life * 2.6F;
            float tail = Math.min(reach * 0.12F, r * 0.35F);
            float a0 = angle - tail / Math.max(r, 1.0F) * 0.8F;

            float x1 = x + (float) Math.cos(angle) * r;
            float y1 = y + (float) Math.sin(angle) * r * 0.55F;
            float x2 = x + (float) Math.cos(a0) * (r + tail);
            float y2 = y + (float) Math.sin(a0) * (r + tail) * 0.55F;

            // Fades in at the rim, burns brighter as it nears the centre.
            float alpha = strength * Ease.clamp01(life * 4.0F) * (0.15F + 0.55F * life);
            int color = Draw.mix(Theme.accent, 0xFFFFFF, life);
            Draw.line(x2, y2, x1, y1, 1.0F, Draw.withAlpha(color, alpha));
        }
    }

    private void renderBirth(float width, float height, float cx, float cy, float radius) {
        float since = sinceBirth();

        // Two shockwaves: a white front, and a slower accent echo behind it.
        float span = Math.max(width, height);
        renderShock(cx, cy, radius, since, 1.0F, span, 0xFFFFFF, 0.85F);
        renderShock(cx, cy, radius, since - 0.12F, 1.4F, span * 0.8F, Theme.accent, 0.6F);

        // Flash: a hot core that blooms out and decays, not a wall of white.
        float flash = 1.0F - Ease.clamp01(since / 0.45F);
        if (flash > 0.0F) {
            flash *= flash;
            Draw.radialGlow(cx, cy, radius * 3.0F + span * 0.6F * (1.0F - flash),
                    Draw.withAlpha(0xFFFFFF, 0.9F * flash), Draw.withAlpha(Theme.accent, 0.0F));
            Draw.rect(0, 0, width, height, Draw.withAlpha(0xFFFFFF, 0.35F * flash));
        }
    }

    private static void renderShock(float cx, float cy, float radius, float since,
                                    float life, float reach, int rgb, float strength) {
        if (since < 0.0F || since >= life) {
            return;
        }
        float t = since / life;
        float r = radius + Ease.outQuint(t) * reach;
        float alpha = (1.0F - t) * (1.0F - t) * strength;
        float thickness = 2.0F + (1.0F - t) * 12.0F;
        Draw.ring(cx, cy, r, thickness, Draw.withAlpha(rgb, alpha));
    }

}

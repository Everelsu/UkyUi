package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;

/**
 * The one-shot opening of the title screen: black, a hit, then the black hole
 * tearing itself open and settling.
 *
 * Timing lives here rather than in the screen so the screen only has to ask
 * "how bright is the hole right now, how warped is space, how visible is the
 * menu" — everything else on the title screen reads those three numbers.
 *
 * Plays once per game launch. Any click or key skips straight to the end.
 */
public final class TitleIntro {

    /** Silence before the hit — long enough to feel deliberate, short enough not to annoy. */
    private static final float SILENCE = 1.10F;
    /** The flash itself. */
    private static final float FLASH = 0.35F;
    /** Black hole spinning up out of the shock. */
    private static final float FORM = 2.30F;
    private static final float TOTAL = SILENCE + FLASH + FORM;

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

    private float time;
    private boolean active;
    private boolean impactFired;

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
        this.time += deltaSeconds;

        if (!this.impactFired && this.time >= SILENCE) {
            this.impactFired = true;
            // The session's one intro is claimed here, at the hit — not in begin(),
            // and not on the first frame either.
            //
            // begin() is called from initGui, and a title screen can be built, have
            // its initGui run, and be gone again before anybody sees it: in a pack
            // this size the main menu is constructed more than once on the way to the
            // one that stays, because several mods open a screen of their own during
            // start-up. Claiming it there spent the intro on a screen nobody looked
            // at. Claiming it at the hit means an intro that was interrupted during
            // its silent opening — which is indistinguishable from not having played
            // at all — is still owed to the player.
            playedThisSession = true;
            UkySounds.play(UkySounds.INTRO_IMPACT, 1.0F, 1.0F);
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
    }

    // ------------------------------------------------------------- readouts --

    /** Brightness of the black hole, 0 before the hit and 1 once it has settled. */
    public float holeIntensity() {
        if (!this.active) {
            return 1.0F;
        }
        if (this.time < SILENCE) {
            return 0.0F;
        }
        return Ease.outCubic((this.time - SILENCE) / (FLASH + FORM * 0.45F));
    }

    /**
     * Space-distortion multiplier. Starts huge at the moment of impact and
     * relaxes to 1, so the lensing visibly snaps back into shape.
     */
    public float warp() {
        if (!this.active || this.time < SILENCE) {
            return 1.0F;
        }
        float t = Ease.outQuint((this.time - SILENCE) / (FLASH + FORM));
        return 1.0F + (1.0F - t) * 2.6F;
    }

    /** How much of the UI (logo, buttons, corners) is faded in. */
    public float uiAlpha() {
        if (!this.active) {
            return 1.0F;
        }
        // The menu only starts arriving once the hole has mostly formed.
        float start = SILENCE + FLASH + FORM * 0.55F;
        return Ease.outCubic((this.time - start) / (TOTAL - start));
    }

    /** Full-screen white flash at the moment of the hit. */
    public float flashAlpha() {
        if (!this.active || this.time < SILENCE) {
            return 0.0F;
        }
        float t = (this.time - SILENCE) / FLASH;
        if (t >= 1.0F) {
            return 0.0F;
        }
        // Instant on, fast decay.
        return (1.0F - t) * (1.0F - t);
    }

    // -------------------------------------------------------------- drawing --

    /** Shockwave ring + flash. Drawn over the backdrop, under the UI. */
    public void render(float width, float height, float cx, float cy, float radius) {
        if (!this.active) {
            return;
        }

        if (this.time < SILENCE) {
            // Pure black, with one faint breath of light hinting at what is coming.
            Draw.rect(0, 0, width, height, 0xFF000000);
            float pulse = (float) Math.sin(this.time / SILENCE * Math.PI) * 0.10F;
            Draw.radialGlow(cx, cy, radius * 2.2F,
                    Draw.withAlpha(Theme.accent, pulse),
                    Draw.withAlpha(Theme.accent, 0.0F));
            return;
        }

        float since = this.time - SILENCE;

        // Expanding shockwave: a thin bright ring racing off screen.
        float shockLife = 1.1F;
        if (since < shockLife) {
            float t = since / shockLife;
            float ringR = radius + Ease.outQuint(t) * Math.max(width, height) * 0.9F;
            float alpha = (1.0F - t) * (1.0F - t) * 0.8F;
            float thickness = 3.0F + (1.0F - t) * 14.0F;
            Draw.ring(cx, cy, ringR, thickness, Draw.withAlpha(0xFFFFFF, alpha));
            Draw.ring(cx, cy, ringR * 0.93F, thickness * 0.7F,
                    Draw.withAlpha(Theme.accent, alpha * 0.7F));
        }

        float flash = flashAlpha();
        if (flash > 0.0F) {
            Draw.rect(0, 0, width, height, Draw.withAlpha(0xFFFFFF, flash * 0.9F));
        }
    }

}

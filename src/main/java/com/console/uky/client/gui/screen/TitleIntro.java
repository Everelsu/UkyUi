package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.Quality;

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

    /** Only the first title screen of a session gets the intro. */
    private static boolean playedThisSession;

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
            this.active = false;
            return false;
        }
        playedThisSession = true;
        this.active = true;
        this.time = 0.0F;
        this.impactFired = false;
        return true;
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
            UkySounds.play(UkySounds.INTRO_IMPACT, 1.0F, 1.0F);
        }
        if (this.time >= TOTAL) {
            this.active = false;
        }
    }

    /** Jumps to the end, leaving the menu in its resting state. */
    public void skip() {
        if (!this.active) {
            return;
        }
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

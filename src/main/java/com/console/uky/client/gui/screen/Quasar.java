package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import org.lwjgl.opengl.GL11;

/**
 * What the hole does when it has been fed the whole title: it lights up as a quasar.
 *
 * <p>That is the real thing a black hole does with more than it can swallow at once.
 * The infalling matter heats until the disk outshines everything around it, and the
 * magnetic field wound up by the spin throws part of it back out along the poles as
 * two relativistic jets.
 *
 * <p>The jets themselves are not drawn here. They are traced in the hole's own shader
 * (see {@code blackhole.fsh}), along the same bent light paths as the disk, so they
 * are lensed round the shadow — the counter-jet seen through the lens under it — and
 * the one leaning towards the camera is beamed brighter, as real ones are. This class
 * is the timing: the disk charging, the moment of eruption, the jets reaching out and
 * dying away, and what the camera and the speakers do meanwhile.
 */
final class Quasar {

    /** The disk heating up as the last of the title falls in. Matches the riser. */
    private static final float CHARGE = 1.27F;
    /** Jets at full strength, then dying away. */
    private static final float BURN = 4.2F;
    private static final float TOTAL = CHARGE + BURN;

    /** Peak brightness handed to the shader; tuned against renders of the trace. */
    private static final float JET_PEAK = 0.8F;

    private float time = -1.0F;
    private boolean erupted;
    /** True once, on the frame it finishes; the title uses it to bring the letters back. */
    private boolean justFinished;

    boolean isActive() {
        return this.time >= 0.0F && this.time < TOTAL;
    }

    boolean takeFinished() {
        boolean f = this.justFinished;
        this.justFinished = false;
        return f;
    }

    void start() {
        if (isActive()) {
            return;
        }
        this.time = 0.0F;
        this.erupted = false;
        UkySounds.play(UkySounds.INTRO_RISER, (float) UiConfig.introVolume, 1.0F);
    }

    void update(float delta) {
        if (!isActive()) {
            return;
        }
        this.time += delta;
        if (!this.erupted && this.time >= CHARGE) {
            this.erupted = true;
            UkySounds.play(UkySounds.INTRO_IMPACT, (float) UiConfig.introVolume, 0.85F);
        }
        if (this.time >= TOTAL) {
            this.justFinished = true;
        }
    }

    private float sinceEruption() {
        return this.time - CHARGE;
    }

    /** Multiplier on the disk's brightness: climbing through the charge, peaking, settling. */
    float diskBoost() {
        if (!isActive()) {
            return 1.0F;
        }
        if (this.time < CHARGE) {
            return 1.0F + 1.6F * Ease.inCubic(this.time / CHARGE);
        }
        return 1.0F + 1.6F * (float) Math.exp(-sinceEruption() * 1.1F);
    }

    /** Jet brightness for the shader: flaring up at the eruption, fading over the burn. */
    float jetStrength() {
        if (!isActive() || this.time < CHARGE) {
            return 0.0F;
        }
        float since = sinceEruption();
        return JET_PEAK * Ease.outCubic(since / 0.35F)
                * Ease.clamp01(1.0F - Ease.inCubic(since / BURN));
    }

    /** Camera shake into {@code out}: a growing tremble, a kick at the eruption, a rumble. */
    void shake(float[] out) {
        if (!isActive()) {
            return;
        }
        float amp;
        if (this.time < CHARGE) {
            amp = 1.6F * Ease.inCubic(this.time / CHARGE);
        } else {
            amp = 10.0F * (float) Math.exp(-sinceEruption() * 4.0F) + 1.2F * jetStrength() / JET_PEAK;
        }
        out[0] += amp * (float) Math.sin(this.time * 61.0F);
        out[1] += amp * 0.7F * (float) Math.sin(this.time * 47.0F + 1.1F);
    }

    /**
     * The swelling glow of the charge and the flash of the eruption. Over the sky,
     * under the interface.
     *
     * @param radius the shadow's radius on screen
     */
    void render(float width, float height, float cx, float cy, float radius) {
        if (!isActive()) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        if (this.time < CHARGE) {
            float heat = Ease.inCubic(this.time / CHARGE);
            glow(cx, cy, radius * (2.0F + heat * 1.5F), Theme.accent, 0.25F * heat);
        }
        float since = sinceEruption();
        if (since >= 0.0F && since < 0.5F) {
            float flash = 1.0F - since / 0.5F;
            flash *= flash;
            glow(cx, cy, radius * 3.0F + Math.max(width, height) * 0.5F * (1.0F - flash),
                    0xFFFFFF, 0.8F * flash);
        }

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void glow(float cx, float cy, float radius, int rgb, float alpha) {
        if (alpha <= 0.003F || radius <= 0.3F) {
            return;
        }
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glVertex2f(cx, cy);
        GL11.glColor4f(r, g, b, 0.0F);
        for (int i = 0; i <= 24; i++) {
            double a = i * Math.PI * 2.0 / 24;
            GL11.glVertex2f(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
    }
}

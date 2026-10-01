package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Letters knocked out of the wordmark, falling into the hole.
 *
 * Click a letter and it comes loose. From then on it is under the same rule as the
 * lensed starfield behind it — an inverse-square pull towards the singularity — and
 * it falls the way anything falls towards one: not straight in, but around. A letter
 * with any sideways speed at all takes an orbit that decays, so it winds inwards over
 * several turns rather than dropping.
 *
 * <p>Two liberties are taken with the physics, both on purpose. The pull is softened
 * near the centre, because a true inverse square goes to infinity there and the letter
 * would leave in one frame with nothing to watch. And a letter is swallowed at the
 * photon ring rather than at the horizon, because past that point it would be drawn
 * over the black disc where nothing can be seen anyway.
 */
final class FallingLetters {

    /** How hard the hole pulls, in pixels per second squared at one radius out. */
    private static final float PULL = 620.0F;
    /** Softening on the pull, as a fraction of the radius; see the class comment. */
    private static final float SOFTENING = 0.55F;
    /** Speed given to a letter by the click that frees it. */
    private static final float KICK = 26.0F;
    /** Below this, in radii, the letter is inside the hole and gone. */
    private static final float SWALLOWED = 0.42F;

    private static final class Letter {
        final char glyph;
        /** Where in the wordmark it came from, so the gap stays where the letter was. */
        final int index;
        float x;
        float y;
        float vx;
        float vy;
        float spin;
        float spinRate;
        float scale;

        Letter(char glyph, int index, float x, float y) {
            this.glyph = glyph;
            this.index = index;
            this.x = x;
            this.y = y;
            this.scale = 2.0F;
        }
    }

    private final List<Letter> loose = new ArrayList<Letter>();
    /** Indices already knocked out, so the wordmark leaves their gaps empty. */
    private final boolean[] taken;

    private final String text;

    FallingLetters(String text) {
        this.text = text == null ? "" : text;
        this.taken = new boolean[this.text.length()];
    }

    /** Whether this belongs to {@code candidate}, or the title has been changed since. */
    boolean matches(String candidate) {
        return this.text.equals(candidate == null ? "" : candidate);
    }

    /** Whether the wordmark should still draw the glyph at {@code index}. */
    boolean isInPlace(int index) {
        return index < 0 || index >= this.taken.length || !this.taken[index];
    }

    boolean isEmpty() {
        return this.loose.isEmpty();
    }

    /** Every letter is back where it started. */
    void reset() {
        this.loose.clear();
        for (int i = 0; i < this.taken.length; i++) {
            this.taken[i] = false;
        }
        this.returning = -1.0F;
    }

    /** Seconds into the letters re-forming, or negative when they are simply there. */
    private float returning = -1.0F;
    private static final float RETURN_STAGGER = 0.07F;
    private static final float RETURN_FADE = 0.45F;

    /** Puts every letter back, fading them in one after another. */
    void restore() {
        reset();
        this.returning = 0.0F;
    }

    /** Opacity of the glyph at {@code index} while the title re-forms; 1 otherwise. */
    float glyphAlpha(int index) {
        if (this.returning < 0.0F) {
            return 1.0F;
        }
        float t = (this.returning - index * RETURN_STAGGER) / RETURN_FADE;
        return t <= 0.0F ? 0.0F : (t >= 1.0F ? 1.0F : t * t * (3.0F - 2.0F * t));
    }

    /** Every letter has been knocked out and has gone into the hole. */
    boolean allEaten() {
        return this.loose.isEmpty() && allTaken();
    }

    /**
     * Knocks the letter at {@code index} loose, if it is still there.
     *
     * @param awayX horizontal direction of the click relative to the letter, which
     *              becomes the sideways speed that turns the fall into an orbit
     */
    void knockOut(int index, float x, float y, float awayX) {
        if (index < 0 || index >= this.taken.length || this.taken[index]
                || Character.isWhitespace(this.text.charAt(index))) {
            return;
        }
        this.taken[index] = true;
        Letter letter = new Letter(this.text.charAt(index), index, x, y);
        letter.vx = awayX * KICK;
        letter.vy = -KICK * 0.5F;
        letter.spinRate = awayX * 90.0F;
        this.loose.add(letter);
    }

    /** @return how many letters crossed into the hole this frame */
    int update(float delta, float holeX, float holeY, float holeRadius) {
        if (this.returning >= 0.0F) {
            this.returning += delta;
            if (this.returning > this.taken.length * RETURN_STAGGER + RETURN_FADE) {
                this.returning = -1.0F;
            }
        }
        if (this.loose.isEmpty() || holeRadius <= 0.0F) {
            return 0;
        }
        int eaten = 0;
        // Clamped so a frame that took a quarter of a second does not teleport a
        // letter through the hole and out the far side.
        float step = Math.min(delta, 0.05F);

        for (int i = this.loose.size() - 1; i >= 0; i--) {
            Letter letter = this.loose.get(i);

            float dx = holeX - letter.x;
            float dy = holeY - letter.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float soft = holeRadius * SOFTENING;
            float denominator = distance * distance + soft * soft;

            float acceleration = PULL * holeRadius * holeRadius / denominator;
            if (distance > 0.001F) {
                letter.vx += dx / distance * acceleration * step;
                letter.vy += dy / distance * acceleration * step;
            }

            letter.x += letter.vx * step;
            letter.y += letter.vy * step;
            letter.spin += letter.spinRate * step;

            // Shrinks with the last stretch rather than all the way in, so it is still
            // a letter when it reaches the ring and not a dot halfway there.
            float closeness = 1.0F - Math.min(1.0F, distance / (holeRadius * 2.2F));
            letter.scale = 2.0F * (1.0F - closeness * 0.75F);
            letter.spinRate += closeness * 220.0F * step;

            if (distance < holeRadius * SWALLOWED) {
                this.loose.remove(i);
                eaten++;
            }
        }
        // The title does not come back here any more: the screen sees allEaten(),
        // lets the hole answer, and calls restore() once it has.
        return eaten;
    }

    private boolean allTaken() {
        // Spaces count as gone: there is nothing there to click, and "ULTRAKILL
        // YOURSELF" with every letter eaten was waiting on its space forever.
        boolean any = false;
        for (int i = 0; i < this.taken.length; i++) {
            if (Character.isWhitespace(this.text.charAt(i))) {
                continue;
            }
            if (!this.taken[i]) {
                return false;
            }
            any = true;
        }
        return any;
    }

    void draw(FontRenderer font, float alpha) {
        for (int i = 0; i < this.loose.size(); i++) {
            Letter letter = this.loose.get(i);
            String glyph = String.valueOf(letter.glyph);
            int width = font.getCharWidth(letter.glyph);

            GL11.glPushMatrix();
            GL11.glTranslatef(letter.x, letter.y, 0.0F);
            GL11.glRotatef(letter.spin, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(letter.scale, letter.scale, 1.0F);
            // Drawn about its own centre, so it spins on the spot instead of orbiting
            // its top-left corner.
            font.drawString(glyph, -width / 2, -4,
                    Draw.withAlpha(Theme.text, alpha), false);
            GL11.glPopMatrix();
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

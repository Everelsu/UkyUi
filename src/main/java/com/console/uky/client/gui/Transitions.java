package com.console.uky.client.gui;

import com.console.uky.client.render.Ease;

/**
 * The dive into the hole and back out again.
 *
 * Leaving the title screen for a list — worlds, servers — is staged as falling
 * in: the hole rushes up to meet the camera and everything goes black, and the
 * list is what is on the other side. Coming back reverses it.
 *
 * State is static because the transition outlives the screen that started it:
 * the dive is drawn by the title screen, the arrival by whatever opens next.
 */
public final class Transitions {

    /** Seconds the fall takes. Long enough to read as motion, short enough to sit through. */
    private static final float DIVE_SECONDS = 0.75F;
    private static final float EMERGE_SECONDS = 0.55F;
    /** How much the shadow grows on the way in. */
    private static final float DIVE_SCALE = 16.0F;

    private static float diveTime = -1.0F;
    private static float emergeTime = -1.0F;
    private static Runnable pending;

    private Transitions() {
    }

    /** Starts the fall; {@code then} runs once the screen is fully black. */
    public static void dive(Runnable then) {
        if (diveTime >= 0.0F) {
            return;
        }
        diveTime = 0.0F;
        emergeTime = -1.0F;
        pending = then;
    }

    /** Starts the climb back out, for a screen returning to the title. */
    public static void emerge() {
        emergeTime = 0.0F;
        diveTime = -1.0F;
        pending = null;
    }

    /** True while either half is running; screens use it to lock input. */
    public static boolean isBusy() {
        return diveTime >= 0.0F;
    }

    public static void update(float deltaSeconds) {
        if (diveTime >= 0.0F) {
            diveTime += deltaSeconds;
            if (diveTime >= DIVE_SECONDS) {
                Runnable action = pending;
                pending = null;
                diveTime = -1.0F;
                // Whatever opens next arrives out of black.
                emergeTime = 0.0F;
                if (action != null) {
                    action.run();
                }
            }
        } else if (emergeTime >= 0.0F) {
            emergeTime += deltaSeconds;
            if (emergeTime >= EMERGE_SECONDS) {
                emergeTime = -1.0F;
            }
        }
    }

    /**
     * Multiplier on the hole's radius. Accelerating rather than linear — falling
     * in should feel like it is being pulled, not driven.
     */
    public static float holeScale() {
        if (diveTime >= 0.0F) {
            return 1.0F + Ease.inCubic(diveTime / DIVE_SECONDS) * (DIVE_SCALE - 1.0F);
        }
        if (emergeTime >= 0.0F) {
            float t = emergeTime / EMERGE_SECONDS;
            return 1.0F + (1.0F - Ease.outCubic(t)) * (DIVE_SCALE - 1.0F);
        }
        return 1.0F;
    }

    /** Opacity of the black covering everything, 0..1. */
    public static float blackout() {
        if (diveTime >= 0.0F) {
            // Holds off until the hole has visibly closed in, then takes over fast.
            return Ease.clamp01((diveTime / DIVE_SECONDS - 0.45F) / 0.55F);
        }
        if (emergeTime >= 0.0F) {
            return 1.0F - Ease.outCubic(emergeTime / EMERGE_SECONDS);
        }
        return 0.0F;
    }
}

package com.console.uky.client.render;

/**
 * Easing curves and a frame-rate independent smoothing helper.
 *
 * All curves map t in [0,1] to [0,1]. Screens drive them from wall-clock delta
 * time rather than ticks, so animations run at the same speed whether the client
 * is at 30 or 300 fps.
 */
public final class Ease {

    private Ease() {
    }

    public static float clamp01(float t) {
        return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /**
     * The t that {@link #outCubic} maps to {@code value}.
     *
     * For resuming a curve part-way through: a screen handed a fade that is already
     * half up has to know how far along the curve that is, or continuing from it
     * jumps.
     */
    public static float outCubicInverse(float value) {
        value = clamp01(value);
        return 1.0F - (float) Math.cbrt(1.0F - value);
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    public static float outQuint(float t) {
        t = clamp01(t);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv * inv * inv;
    }

    /** Overshoots slightly past 1 before settling — good for pop-in accents. */
    public static float outBack(float t) {
        t = clamp01(t);
        final float c1 = 1.70158F;
        final float c3 = c1 + 1.0F;
        float inv = t - 1.0F;
        return 1.0F + c3 * inv * inv * inv + c1 * inv * inv;
    }

    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0.0F || t == 1.0F) {
            return t;
        }
        final float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0F - 0.75F) * c4) + 1.0);
    }

    /**
     * Exponential smoothing toward {@code target}. {@code halfLife} is the time in
     * seconds for the remaining distance to halve, which makes the motion look
     * identical at any frame rate — unlike a raw {@code lerp(current, target, 0.2)}
     * per frame, which speeds up as fps rises.
     */
    public static float approach(float current, float target, float halfLife, float deltaSeconds) {
        if (halfLife <= 0.0F) {
            return target;
        }
        float factor = 1.0F - (float) Math.pow(2.0, -deltaSeconds / halfLife);
        return current + (target - current) * factor;
    }

    /**
     * Progress of item {@code index} in a staggered entrance: each item starts
     * {@code stagger} seconds after the previous one and takes {@code duration}
     * to complete.
     */
    public static float stagger(float elapsed, int index, float stagger, float duration) {
        return clamp01((elapsed - index * stagger) / duration);
    }
}

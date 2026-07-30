package com.console.uky.client.render;

/**
 * The handful of glyphs the tile screens need, drawn from primitives.
 *
 * Shipping them as textures would mean an atlas, a resource path and a size that
 * only looks right at one GUI scale. Drawn from rectangles and triangles they
 * stay sharp at any size and pick up the theme colour for free.
 *
 * Every icon is drawn inside a square box centred on (cx, cy) with edge {@code size}.
 */
public final class Icons {

    private Icons() {
    }

    /** Filled triangle pointing right. */
    public static void play(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h * 0.55F, cy - h,
                      cx - h * 0.55F, cy + h,
                      cx + h * 0.75F, cy, colour);
    }

    /** Two crossed bars. */
    public static void plus(float cx, float cy, float size, float thickness, int colour) {
        float h = size * 0.5F;
        float t = thickness * 0.5F;
        Draw.rect(cx - h, cy - t, cx + h, cy + t, colour);
        Draw.rect(cx - t, cy - h, cx + t, cy + h, colour);
    }

    /**
     * Pencil: a shaft on the diagonal with a tip at one end. Built from stacked
     * one-pixel rows rather than a rotated quad, so it stays crisp.
     */
    public static void pencil(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float thickness = Math.max(1.0F, size * 0.22F);
        int steps = Math.max(4, (int) size);

        for (int i = 0; i < steps; i++) {
            float t = (float) i / (steps - 1);
            float x = cx - h + size * t;
            float y = cy + h - size * t;
            // The last fifth tapers to the point.
            float w = t > 0.8F ? thickness * (1.0F - (t - 0.8F) / 0.2F) : thickness;
            if (w <= 0.0F) {
                continue;
            }
            Draw.rect(x, y - w * 0.5F, x + size / steps + 0.6F, y + w * 0.5F, colour);
        }
    }

    /** Bin: lid, body and two ribs. */
    public static void trash(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float bodyTop = cy - h * 0.45F;

        // Lid and handle.
        Draw.rect(cx - h * 0.85F, cy - h * 0.75F, cx + h * 0.85F, cy - h * 0.55F, colour);
        Draw.rect(cx - h * 0.3F, cy - h, cx + h * 0.3F, cy - h * 0.8F, colour);

        // Body outline, hollow so it reads as a container rather than a block.
        Draw.rect(cx - h * 0.65F, bodyTop, cx - h * 0.45F, cy + h, colour);
        Draw.rect(cx + h * 0.45F, bodyTop, cx + h * 0.65F, cy + h, colour);
        Draw.rect(cx - h * 0.65F, cy + h * 0.8F, cx + h * 0.65F, cy + h, colour);

        // Ribs.
        Draw.rect(cx - h * 0.12F, bodyTop + h * 0.15F, cx + h * 0.02F, cy + h * 0.7F, colour);
    }

    /** Left-pointing chevron, for "back". */
    public static void back(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx + h * 0.4F, cy - h,
                      cx + h * 0.4F, cy + h,
                      cx - h * 0.6F, cy, colour);
    }

    /** Tick, for a checked toggle. */
    public static void check(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float t = Math.max(1.0F, size * 0.2F);
        // Two strokes drawn as stacked steps: short down-right, then long up-right.
        int steps = Math.max(3, (int) (size * 0.4F));
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            Draw.rect(cx - h + h * 0.6F * f, cy + h * 0.55F * f - t * 0.5F,
                    cx - h + h * 0.6F * f + t, cy + h * 0.55F * f + t * 0.5F, colour);
        }
        int longer = steps * 2;
        for (int i = 0; i < longer; i++) {
            float f = (float) i / longer;
            Draw.rect(cx - h * 0.4F + h * 1.4F * f, cy + h * 0.55F - h * 1.35F * f - t * 0.5F,
                    cx - h * 0.4F + h * 1.4F * f + t, cy + h * 0.55F - h * 1.35F * f + t * 0.5F,
                    colour);
        }
    }

    /** Blade and crossguard, for survival. */
    public static void sword(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float t = Math.max(1.0F, size * 0.16F);
        Draw.rect(cx - t * 0.5F, cy - h, cx + t * 0.5F, cy + h * 0.35F, colour);
        Draw.rect(cx - h * 0.5F, cy + h * 0.3F, cx + h * 0.5F, cy + h * 0.3F + t, colour);
        Draw.rect(cx - t * 0.5F, cy + h * 0.3F, cx + t * 0.5F, cy + h, colour);
    }

    /**
     * Skull, for hardcore. There is no subtractive drawing here, so the sockets are
     * painted in whatever sits behind the icon — the caller passes it in.
     */
    public static void skull(float cx, float cy, float size, int colour, int behind) {
        float h = size * 0.5F;
        Draw.rect(cx - h * 0.8F, cy - h, cx + h * 0.8F, cy + h * 0.25F, colour);
        Draw.rect(cx - h * 0.5F, cy + h * 0.25F, cx + h * 0.5F, cy + h * 0.8F, colour);
        Draw.rect(cx - h * 0.5F, cy - h * 0.55F, cx - h * 0.12F, cy - h * 0.1F, behind);
        Draw.rect(cx + h * 0.12F, cy - h * 0.55F, cx + h * 0.5F, cy - h * 0.1F, behind);
        Draw.rect(cx - h * 0.12F, cy + h * 0.4F, cx + h * 0.12F, cy + h * 0.8F, behind);
    }

    /** Four-pointed sparkle, for creative. */
    public static void spark(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx, cy - h, cx - h * 0.28F, cy, cx + h * 0.28F, cy, colour);
        Draw.triangle(cx, cy + h, cx - h * 0.28F, cy, cx + h * 0.28F, cy, colour);
        Draw.triangle(cx - h, cy, cx, cy - h * 0.28F, cx, cy + h * 0.28F, colour);
        Draw.triangle(cx + h, cy, cx, cy - h * 0.28F, cx, cy + h * 0.28F, colour);
    }

    /** Two peaks, for the default terrain type. */
    public static void terrain(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h * 0.15F, cy - h, cx - h, cy + h * 0.6F, cx + h * 0.7F, cy + h * 0.6F,
                colour);
        Draw.triangle(cx + h * 0.55F, cy - h * 0.35F, cx - h * 0.1F, cy + h * 0.6F,
                cx + h, cy + h * 0.6F, colour);
        Draw.rect(cx - h, cy + h * 0.6F, cx + h, cy + h * 0.8F, colour);
    }

    /** Stacked strata, for superflat. */
    public static void flat(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float t = Math.max(1.0F, size * 0.13F);
        for (int i = 0; i < 3; i++) {
            float y = cy - h * 0.3F + i * (t * 2.1F);
            Draw.rect(cx - h, y, cx + h, y + t, colour);
        }
    }

    /**
     * Circle with a band, for large biomes and other whole-world types.
     *
     * Stamped from small squares rather than {@link Draw#ring}, which fades out at
     * both edges of its band and so all but disappears at icon size.
     */
    public static void globe(float cx, float cy, float size, int colour) {
        float r = size * 0.45F;
        float t = Math.max(1.0F, size * 0.12F);
        int segments = Math.max(16, (int) (size * 1.6F));
        for (int i = 0; i < segments; i++) {
            double a = i * 2.0 * Math.PI / segments;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            Draw.rect(x - t * 0.5F, y - t * 0.5F, x + t * 0.5F, y + t * 0.5F, colour);
        }
        Draw.rect(cx - r, cy - t * 0.5F, cx + r, cy + t * 0.5F, colour);
    }

    /** Die face, for the seed. */
    public static void dice(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        float pip = Math.max(1.0F, size * 0.14F);
        Draw.border(cx - h, cy - h, cx + h, cy + h, Math.max(1.0F, size * 0.1F), colour);
        Draw.rect(cx - pip * 0.5F, cy - pip * 0.5F, cx + pip * 0.5F, cy + pip * 0.5F, colour);
        Draw.rect(cx - h * 0.55F - pip * 0.5F, cy - h * 0.55F - pip * 0.5F,
                cx - h * 0.55F + pip * 0.5F, cy - h * 0.55F + pip * 0.5F, colour);
        Draw.rect(cx + h * 0.55F - pip * 0.5F, cy + h * 0.55F - pip * 0.5F,
                cx + h * 0.55F + pip * 0.5F, cy + h * 0.55F + pip * 0.5F, colour);
    }

    /** Circular arrow, for "refresh". */
    public static void refresh(float cx, float cy, float size, int colour) {
        float r = size * 0.42F;
        float thickness = Math.max(1.0F, size * 0.16F);
        int segments = 22;
        // Open ring: three quarters of a circle, leaving a gap for the arrowhead.
        for (int i = 0; i < segments; i++) {
            double a = -Math.PI * 0.35 + i * (Math.PI * 1.65) / segments;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            Draw.rect(x - thickness * 0.5F, y - thickness * 0.5F,
                    x + thickness * 0.5F, y + thickness * 0.5F, colour);
        }
        Draw.triangle(cx + r * 0.55F, cy - r * 1.1F,
                      cx + r * 1.45F, cy - r * 0.75F,
                      cx + r * 0.75F, cy - r * 0.15F, colour);
    }
}

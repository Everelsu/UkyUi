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

    /** Right-pointing chevron, the mirror of {@link #back}. */
    public static void forward(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h * 0.4F, cy - h,
                      cx - h * 0.4F, cy + h,
                      cx + h * 0.6F, cy, colour);
    }

    /** Upward chevron, for moving a row up an ordered list. */
    public static void arrowUp(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h, cy + h * 0.4F,
                      cx + h, cy + h * 0.4F,
                      cx, cy - h * 0.6F, colour);
    }

    /** Downward chevron. */
    public static void arrowDown(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h, cy - h * 0.4F,
                      cx + h, cy - h * 0.4F,
                      cx, cy + h * 0.6F, colour);
    }

    /** Rounded speech bubble with a tail, for chat and community links. */
    public static void chat(float cx, float cy, float size, int colour) {
        float w = size * 0.46F;
        float h = size * 0.34F;
        float r = Math.max(1.0F, size * 0.14F);
        Draw.roundedRect(cx - w, cy - h - size * 0.06F, cx + w, cy + h - size * 0.06F, r, colour);
        // Tail off the bottom-left, the way a bubble hangs from the speaker.
        Draw.triangle(cx - w * 0.55F, cy + h - size * 0.10F,
                      cx - w * 0.05F, cy + h - size * 0.10F,
                      cx - w * 0.45F, cy + size * 0.46F, colour);
    }

    /**
     * Heart, for supporting-the-pack links.
     *
     * Two lobes and a wedge rather than a curve: at sixteen pixels a proper bezier
     * would be four indistinguishable blobs, and this reads as a heart at any size.
     */
    public static void heart(float cx, float cy, float size, int colour) {
        float r = size * 0.24F;
        float top = cy - size * 0.10F;
        Draw.circle(cx - r * 0.92F, top, r, colour);
        Draw.circle(cx + r * 0.92F, top, r, colour);
        Draw.triangle(cx - r * 1.84F, top + r * 0.35F,
                      cx + r * 1.84F, top + r * 0.35F,
                      cx, cy + size * 0.44F, colour);
    }

    /** Five-pointed star, for repositories and anything bookmarked. */
    public static void star(float cx, float cy, float size, int colour) {
        float outer = size * 0.5F;
        float inner = outer * 0.42F;
        // Ten points around the circle, alternating radius, stitched as a fan.
        float px = cx;
        float py = cy - outer;
        for (int i = 1; i <= 10; i++) {
            double a = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float r = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            Draw.triangle(cx, cy, px, py, x, y, colour);
            px = x;
            py = y;
        }
    }

    // ------------------------------------------------------------------ marks --
    //
    // Stylised versions of the marks a pack links to. Drawn rather than shipped as
    // artwork for the same reason as everything else here — they take the theme
    // colour, stay sharp at any GUI scale and cost no files — and stylised rather
    // than copied because a rounded rectangle with a triangle in it is what makes a
    // row of links legible at sixteen pixels, not fidelity to someone's brand book.
    //
    // Each takes a {@code behind} colour for the parts that are cut out of the
    // shape, which the caller sets to whatever the mark is being drawn on.

    /** Rounded screen with a play triangle punched out of it. */
    public static void youtube(float cx, float cy, float size, int colour, int behind) {
        float w = size * 0.50F;
        float h = size * 0.36F;
        Draw.roundedRect(cx - w, cy - h, cx + w, cy + h, size * 0.13F, colour);
        float t = h * 0.55F;
        Draw.triangle(cx - t * 0.5F, cy - t,
                      cx - t * 0.5F, cy + t,
                      cx + t * 0.8F, cy, behind);
    }

    /** The rounded face with two eyes and a pair of tails. */
    public static void discord(float cx, float cy, float size, int colour, int behind) {
        float w = size * 0.44F;
        float h = size * 0.30F;
        Draw.roundedRect(cx - w, cy - h, cx + w, cy + h * 0.75F, size * 0.15F, colour);
        // Tails flaring out of the bottom corners, which is what makes it read as
        // this mark rather than as any other rounded blob.
        Draw.triangle(cx - w, cy + h * 0.10F,
                      cx - w * 0.30F, cy + h * 0.70F,
                      cx - w * 1.00F, cy + h * 1.15F, colour);
        Draw.triangle(cx + w, cy + h * 0.10F,
                      cx + w * 0.30F, cy + h * 0.70F,
                      cx + w * 1.00F, cy + h * 1.15F, colour);
        float e = Math.max(1.0F, size * 0.10F);
        Draw.rect(cx - w * 0.46F - e * 0.5F, cy - e * 0.6F,
                  cx - w * 0.46F + e * 0.5F, cy + e * 0.6F, behind);
        Draw.rect(cx + w * 0.46F - e * 0.5F, cy - e * 0.6F,
                  cx + w * 0.46F + e * 0.5F, cy + e * 0.6F, behind);
    }

    /** Paper plane, from a wing and a fin. */
    public static void telegram(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx - h, cy - h * 0.10F,
                      cx + h, cy - h * 0.80F,
                      cx - h * 0.05F, cy + h * 0.25F, colour);
        Draw.triangle(cx - h * 0.05F, cy + h * 0.25F,
                      cx + h, cy - h * 0.80F,
                      cx + h * 0.20F, cy + h * 0.85F, colour);
    }

    /** Lightning bolt, for the boost. */
    public static void boosty(float cx, float cy, float size, int colour) {
        float h = size * 0.5F;
        Draw.triangle(cx + h * 0.40F, cy - h,
                      cx - h * 0.50F, cy + h * 0.14F,
                      cx + h * 0.18F, cy + h * 0.14F, colour);
        Draw.triangle(cx - h * 0.40F, cy + h,
                      cx + h * 0.50F, cy - h * 0.14F,
                      cx - h * 0.18F, cy - h * 0.14F, colour);
    }

    /** The chat glyph: a screen with a chin and two bars cut out. */
    public static void twitch(float cx, float cy, float size, int colour, int behind) {
        float w = size * 0.36F;
        float h = size * 0.42F;
        Draw.rect(cx - w, cy - h, cx + w, cy + h * 0.18F, colour);
        Draw.triangle(cx - w, cy + h * 0.18F,
                      cx + w * 0.10F, cy + h * 0.18F,
                      cx - w * 0.45F, cy + h, colour);
        float bw = Math.max(1.0F, size * 0.09F);
        Draw.rect(cx - w * 0.42F - bw * 0.5F, cy - h * 0.55F,
                  cx - w * 0.42F + bw * 0.5F, cy - h * 0.02F, behind);
        Draw.rect(cx + w * 0.32F - bw * 0.5F, cy - h * 0.55F,
                  cx + w * 0.32F + bw * 0.5F, cy - h * 0.02F, behind);
    }

    /** A circle beside a bar — the support mark. */
    public static void patreon(float cx, float cy, float size, int colour) {
        Draw.circle(cx + size * 0.13F, cy - size * 0.04F, size * 0.29F, colour);
        float bw = Math.max(1.5F, size * 0.14F);
        Draw.rect(cx - size * 0.42F, cy - size * 0.42F,
                  cx - size * 0.42F + bw, cy + size * 0.46F, colour);
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

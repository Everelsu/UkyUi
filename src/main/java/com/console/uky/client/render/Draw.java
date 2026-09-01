package com.console.uky.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Immediate-mode drawing primitives for the UltraKill Yourself UI.
 *
 * Everything here works in GUI space (the scaled coordinate system a GuiScreen
 * sees) and takes ARGB colors, so the whole UI can be built without a single
 * bespoke widget texture. Kept free of GuiScreen/Gui inheritance on purpose:
 * Gui.drawRect is protected, and widgets that are not Gui subclasses still need
 * to draw.
 *
 * Every method leaves the GL state as it found it (texturing enabled, blending
 * disabled, color white), so callers can mix these freely with vanilla drawing.
 */
public final class Draw {

    private Draw() {
    }

    // ---------------------------------------------------------------- state --

    private static void beginShapes() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        // Face culling has to go, and it is not optional.
        //
        // The circles and glows here are triangle fans wound by increasing angle,
        // which is counter-clockwise in maths but clockwise on a GUI where y points
        // down — so they are back-facing, and culling discards every one of them.
        // Nothing enables culling until a world is rendered, and nothing disables it
        // again before the menus are drawn, which is exactly why the toggle knobs
        // vanished only after entering and leaving a world.
        //
        // Alpha testing goes for the same reason: the world render leaves it on with
        // a threshold of 0.1, and the soft glows here are deliberately fainter than
        // that, so they were being thrown away a fragment at a time.
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

    private static void endShapes() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // Alpha testing is put back because vanilla's own widget and font drawing
        // relies on it. Culling is not: 2D drawing never wants it, and the world
        // renderer turns it back on itself every frame.
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void color(int argb) {
        GL11.glColor4f(
                (argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                (argb >>> 24) / 255.0F);
    }

    // ---------------------------------------------------------------- colors --

    /** Replaces the alpha channel of {@code argb} with {@code alpha} (0..1). */
    public static int withAlpha(int argb, float alpha) {
        int a = (int) (clamp01(alpha) * 255.0F);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /** Multiplies the existing alpha of {@code argb} by {@code factor} (0..1). */
    public static int fade(int argb, float factor) {
        int a = (int) ((argb >>> 24) * clamp01(factor));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /** Component-wise blend; {@code t} = 0 returns {@code a}, 1 returns {@code b}. */
    public static int mix(int a, int b, float t) {
        t = clamp01(t);
        int aa = (int) ((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        int rr = (int) ((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int gg = (int) ((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return aa << 24 | rr << 16 | gg << 8 | bb;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    // ----------------------------------------------------------------- rects --

    public static void rect(float x1, float y1, float x2, float y2, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        beginShapes();
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        endShapes();
    }

    /** Vertical gradient: {@code top} at y1 fading to {@code bottom} at y2. */
    public static void gradientV(float x1, float y1, float x2, float y2, int top, int bottom) {
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        color(bottom);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        color(top);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        endShapes();
    }

    /** Horizontal gradient: {@code left} at x1 fading to {@code right} at x2. */
    public static void gradientH(float x1, float y1, float x2, float y2, int left, int right) {
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        color(left);
        GL11.glVertex2f(x1, y2);
        color(right);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        color(left);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        endShapes();
    }

    /** 1px-ish outline drawn inside the given bounds. */
    public static void border(float x1, float y1, float x2, float y2, float thickness, int argb) {
        rect(x1, y1, x2, y1 + thickness, argb);
        rect(x1, y2 - thickness, x2, y2, argb);
        rect(x1, y1 + thickness, x1 + thickness, y2 - thickness, argb);
        rect(x2 - thickness, y1 + thickness, x2, y2 - thickness, argb);
    }

    /**
     * Soft outer glow: {@code layers} concentric frames stepping outwards, each
     * one fainter than the last. Cheap stand-in for a real blur.
     */
    public static void glow(float x1, float y1, float x2, float y2, float radius, int argb, int layers) {
        if (layers < 1) {
            return;
        }
        float step = radius / layers;
        for (int i = layers; i >= 1; i--) {
            float grow = step * i;
            // quadratic falloff reads much closer to a gaussian than a linear one
            float t = 1.0F - (float) i / layers;
            int c = fade(argb, t * t);
            border(x1 - grow, y1 - grow, x2 + grow, y2 + grow, step + 0.5F, c);
        }
    }

    /** Rectangle with the four corner pixels omitted — reads as a soft corner at GUI scale. */
    public static void roundedRect(float x1, float y1, float x2, float y2, float r, int argb) {
        rect(x1 + r, y1, x2 - r, y2, argb);
        rect(x1, y1 + r, x1 + r, y2 - r, argb);
        rect(x2 - r, y1 + r, x2, y2 - r, argb);
    }

    /**
     * A rectangle whose vertical edges lean right, by {@code skew} units at the top.
     *
     * The HUD panels this mod draws over live gameplay — the achievement toast above
     * all — are cut this way so they cannot be mistaken for a menu panel that has
     * wandered onto the screen. Everything else about them is the same, so the lean
     * is a primitive rather than a shape each one builds by hand.
     */
    public static void slant(float x1, float y1, float x2, float y2, float skew, int argb) {
        slantGradientV(x1, y1, x2, y2, skew, argb, argb);
    }

    /** {@link #slant} with a vertical gradient: {@code top} at y1, {@code bottom} at y2. */
    public static void slantGradientV(float x1, float y1, float x2, float y2, float skew,
                                      int top, int bottom) {
        if ((top >>> 24) == 0 && (bottom >>> 24) == 0) {
            return;
        }
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        color(bottom);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        color(top);
        GL11.glVertex2f(x2 + skew, y1);
        GL11.glVertex2f(x1 + skew, y1);
        GL11.glEnd();
        endShapes();
    }

    /** {@link #slant} with a horizontal gradient, for sweeps along a leaning panel. */
    public static void slantGradientH(float x1, float y1, float x2, float y2, float skew,
                                      int left, int right) {
        if ((left >>> 24) == 0 && (right >>> 24) == 0) {
            return;
        }
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        color(left);
        GL11.glVertex2f(x1, y2);
        color(right);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2 + skew, y1);
        color(left);
        GL11.glVertex2f(x1 + skew, y1);
        GL11.glEnd();
        endShapes();
    }

    /** Hairline frame around a {@link #slant}, drawn inside the given bounds. */
    public static void slantBorder(float x1, float y1, float x2, float y2, float skew,
                                   float thickness, int argb) {
        // Top and bottom are ordinary bars, offset by the lean; the sides are thin
        // slants of their own, which is what keeps the corners closed.
        slant(x1, y1, x2, y1 + thickness, skew, argb);
        slant(x1, y2 - thickness, x2, y2, skew, argb);
        slant(x1, y1, x1 + thickness, y2, skew, argb);
        slant(x2 - thickness, y1, x2, y2, skew, argb);
    }

    // --------------------------------------------------------------- shapes --

    public static void line(float x1, float y1, float x2, float y2, float width, int argb) {
        beginShapes();
        color(argb);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        endShapes();
    }

    public static void circle(float cx, float cy, float radius, int argb) {
        beginShapes();
        color(argb);
        // segment count scaled to radius keeps small dots cheap and big rings smooth
        int segments = Math.max(10, Math.min(64, (int) (radius * 2.5F)));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double a = i * 2.0 * Math.PI / segments;
            GL11.glVertex2f(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
        endShapes();
    }

    /** Filled circle fading from {@code inner} at the centre to {@code outer} at the rim. */
    public static void radialGlow(float cx, float cy, float radius, int inner, int outer) {
        beginShapes();
        int segments = Math.max(12, Math.min(64, (int) (radius * 1.5F)));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(inner);
        GL11.glVertex2f(cx, cy);
        color(outer);
        for (int i = 0; i <= segments; i++) {
            double a = i * 2.0 * Math.PI / segments;
            GL11.glVertex2f(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
        endShapes();
    }

    /**
     * Soft-edged annulus: transparent at both edges, {@code argb} at the middle of
     * the band. Used for shockwaves and focus rings — a stroked circle would alias
     * badly at these radii.
     */
    public static void ring(float cx, float cy, float radius, float thickness, int argb) {
        if ((argb >>> 24) == 0 || radius <= 0.0F) {
            return;
        }
        int segments = Math.max(24, Math.min(128, (int) (radius * 0.6F)));
        int clear = withAlpha(argb, 0.0F);
        float half = thickness * 0.5F;

        beginShapes();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double a = i * 2.0 * Math.PI / segments;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            color(clear);
            GL11.glVertex2f(cx + cos * (radius - half), cy + sin * (radius - half));
            color(argb);
            GL11.glVertex2f(cx + cos * radius, cy + sin * radius);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double a = i * 2.0 * Math.PI / segments;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            color(argb);
            GL11.glVertex2f(cx + cos * radius, cy + sin * radius);
            color(clear);
            GL11.glVertex2f(cx + cos * (radius + half), cy + sin * (radius + half));
        }
        GL11.glEnd();
        endShapes();
    }

    public static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        beginShapes();
        color(argb);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        endShapes();
    }

    /** Equilateral-ish chevron used for arrows and dropdown markers. */
    public static void arrow(float cx, float cy, float size, boolean pointLeft, int argb) {
        float half = size * 0.5F;
        if (pointLeft) {
            triangle(cx + half * 0.6F, cy - half, cx + half * 0.6F, cy + half, cx - half * 0.6F, cy, argb);
        } else {
            triangle(cx - half * 0.6F, cy - half, cx - half * 0.6F, cy + half, cx + half * 0.6F, cy, argb);
        }
    }

    // -------------------------------------------------------------- textures --

    /**
     * Draws a whole texture stretched into the given rect, tinted by {@code argb}.
     * Unlike Gui.drawTexturedModalRect this does not assume a 256x256 sheet.
     */
    /**
     * A textured quad taking its pixels from an arbitrary part of the texture, drawn
     * rotated about its own centre.
     *
     * This exists for the shards a tile breaks into. Cutting the world's own preview
     * into a grid and giving each piece its own corner of it is what makes the tile
     * shatter into that world rather than into an anonymous rectangle, and each piece
     * has to be able to tumble independently.
     */
    public static void textureShard(ResourceLocation tex, float cx, float cy,
                                    float halfW, float halfH, float degrees,
                                    float u1, float v1, float u2, float v2, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        color(argb);

        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glRotatef(degrees, 0.0F, 0.0F, 1.0F);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(-halfW, halfH, 0.0D).tex(u1, v2).endVertex();
        b.pos(halfW, halfH, 0.0D).tex(u2, v2).endVertex();
        b.pos(halfW, -halfH, 0.0D).tex(u2, v1).endVertex();
        b.pos(-halfW, -halfH, 0.0D).tex(u1, v1).endVertex();
        t.draw();

        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A textured triangle, given absolute vertices and the texture coordinates for each.
     *
     * <p>Exists because {@link #textureShard} cannot break anything. It draws a rotated
     * rectangle, so a picture cut up with it comes apart into rectangles however fine the
     * cut is — which read as tiles sliding apart rather than as something shattering. A
     * shard is a triangle, and it has to be an arbitrary one: the whole character of
     * broken glass is in the long thin pieces radiating from the impact, and those have
     * no axis to be aligned to.
     *
     * <p>Vertices are absolute rather than local-plus-rotation, so a caller that has
     * already worked out where its corners are — which anything tumbling a shard about
     * its own centroid has — does not pay for a matrix push per piece.
     */
    public static void textureTriangle(ResourceLocation tex,
                                       float x1, float y1, float u1, float v1,
                                       float x2, float y2, float u2, float v2,
                                       float x3, float y3, float u3, float v3,
                                       int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Shards tumble, so half of them are wound the other way by the time they land.
        GL11.glDisable(GL11.GL_CULL_FACE);
        color(argb);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        b.pos(x1, y1, 0.0D).tex(u1, v1).endVertex();
        b.pos(x2, y2, 0.0D).tex(u2, v2).endVertex();
        b.pos(x3, y3, 0.0D).tex(u3, v3).endVertex();
        t.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void texture(ResourceLocation tex, float x, float y, float w, float h, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        color(argb);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + h, 0.0D).tex(0.0D, 1.0D).endVertex();
        b.pos(x + w, y + h, 0.0D).tex(1.0D, 1.0D).endVertex();
        b.pos(x + w, y, 0.0D).tex(1.0D, 0.0D).endVertex();
        b.pos(x, y, 0.0D).tex(0.0D, 0.0D).endVertex();
        t.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws {@code tex} covering the whole rect without distortion (CSS
     * {@code background-size: cover}), cropping the overflowing axis. {@code zoom}
     * > 1 crops in further, which is what drives the slow Ken Burns drift on the
     * menu background.
     */
    public static void textureCover(ResourceLocation tex, float x, float y, float w, float h,
                                    float texW, float texH, float zoom, float panX, float panY, int argb) {
        float scale = Math.max(w / texW, h / texH) * zoom;
        float drawW = texW * scale;
        float drawH = texH * scale;

        // fraction of the texture actually visible on each axis
        float uSpan = w / drawW;
        float vSpan = h / drawH;
        // pan is in units of the hidden remainder, so -1..1 never shows an edge
        float u0 = (1.0F - uSpan) * 0.5F * (1.0F + panX);
        float v0 = (1.0F - vSpan) * 0.5F * (1.0F + panY);

        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        color(argb);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + h, 0.0D).tex(u0, v0 + vSpan).endVertex();
        b.pos(x + w, y + h, 0.0D).tex(u0 + uSpan, v0 + vSpan).endVertex();
        b.pos(x + w, y, 0.0D).tex(u0 + uSpan, v0).endVertex();
        b.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        t.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // --------------------------------------------------------------- effects --

    /** Darkened frame around the screen edges. */
    public static void vignette(float w, float h, float strength, int argb) {
        int steps = 18;
        float thickness = Math.max(w, h) * 0.035F;
        for (int i = 0; i < steps; i++) {
            float ratio = 1.0F - (float) i / steps;
            int c = fade(argb, ratio * ratio * strength);
            float o = i * thickness / steps * 3.0F;
            rect(0, o, w, o + thickness / steps * 3.0F, c);
            rect(0, h - o - thickness / steps * 3.0F, w, h - o, c);
            rect(o, 0, o + thickness / steps * 3.0F, h, c);
            rect(w - o - thickness / steps * 3.0F, 0, w - o, h, c);
        }
    }

    /** Horizontal CRT scanlines. */
    public static void scanlines(float w, float h, float spacing, int argb) {
        for (float y = 0; y < h; y += spacing) {
            rect(0, y, w, y + 1, argb);
        }
    }

    // --------------------------------------------------------------- scissor --

    /**
     * Clips subsequent drawing to the given GUI-space rect. Always pair with
     * {@link #endClip()}.
     */
    /**
     * Device pixels per GUI unit for the space currently being drawn in, or -1 to
     * take the game's own. UKY screens lay out in their own units — see
     * {@code MenuScreen} — and glScissor takes real pixels, so it has to be told.
     */
    private static float clipScale = -1.0F;

    public static void setClipScale(float pixelsPerUnit) {
        clipScale = pixelsPerUnit;
    }

    public static void clearClipScale() {
        clipScale = -1.0F;
    }

    public static void beginClip(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getMinecraft();
        float f = clipScale;
        if (f <= 0.0F) {
            f = new ScaledResolution(mc).getScaleFactor();
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // glScissor origin is bottom-left in real pixels, GUI space is top-left in scaled units
        GL11.glScissor((int) (x * f), (int) (mc.displayHeight - (y + h) * f), (int) (w * f), (int) (h * f));
    }

    public static void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ----------------------------------------------------------- transforms --

    /** Scales around ({@code cx}, {@code cy}); pair with {@link #popScale()}. */
    public static void pushScale(float cx, float cy, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        GL11.glTranslatef(-cx, -cy, 0.0F);
    }

    public static void popScale() {
        GL11.glPopMatrix();
    }
}

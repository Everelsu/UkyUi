package com.console.uky.compat;

/**
 * 1.7.10's Tessellator, as far as this mod used it: quads (or any GL mode) with
 * optional texture coordinates and a colour, drawn on {@link #draw()}.
 *
 * <p>Routed through the immediate-mode emulation in {@link GL11}, which already knows
 * how to turn a vertex list into a core-profile draw. Whether the draw is textured
 * follows {@code GL_TEXTURE_2D}, exactly as it did on the fixed-function pipeline.
 */
public final class Tessellator {

    public static final Tessellator instance = new Tessellator();

    private boolean hasColour;
    private int colour = 0xFFFFFFFF;
    /** The GL current colour when drawing started; a tessellator colour never outlived its draw. */
    private int glColour;

    private Tessellator() {
    }

    public void startDrawingQuads() {
        startDrawing(GL11.GL_QUADS);
    }

    public void startDrawing(int mode) {
        this.hasColour = false;
        this.glColour = GL11.currentColour();
        GL11.glBegin(mode);
    }

    public void setColorRGBA_F(float r, float g, float b, float a) {
        setColorRGBA((int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F), (int) (a * 255.0F));
    }

    public void setColorRGBA_I(int rgb, int a) {
        setColorRGBA(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a);
    }

    public void setColorOpaque_I(int rgb) {
        setColorRGBA_I(rgb, 255);
    }

    public void setColorRGBA(int r, int g, int b, int a) {
        this.hasColour = true;
        this.colour = (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        GL11.vertexColour(this.colour);
    }

    private static int clamp(int c) {
        return c < 0 ? 0 : (c > 255 ? 255 : c);
    }

    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        GL11.glTexCoord2f((float) u, (float) v);
        addVertex(x, y, z);
    }

    public void addVertex(double x, double y, double z) {
        GL11.glVertex3f((float) x, (float) y, (float) z);
    }

    public int draw() {
        GL11.glEnd();
        if (this.hasColour) {
            GL11.vertexColour(this.glColour);
        }
        return 0;
    }
}

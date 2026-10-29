package com.console.uky.compat;

/** 1.7.10's Gui: the handful of drawing helpers every screen and button inherited. */
public class Gui {

    protected float zLevel;

    public static void drawRect(int x1, int y1, int x2, int y2, int argb) {
        if (x1 > x2) {
            int t = x1; x1 = x2; x2 = t;
        }
        if (y1 > y2) {
            int t = y1; y1 = y2; y2 = t;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.vertexColour(argb);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void drawGradientRect(int x1, int y1, int x2, int y2, int top, int bottom) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.vertexColour(bottom);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.vertexColour(top);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void drawCenteredString(FontRenderer font, String text, int x, int y, int colour) {
        font.drawStringWithShadow(text, x - font.getStringWidth(text) / 2, y, colour);
    }

    public static void drawString(FontRenderer font, String text, int x, int y, int colour) {
        font.drawStringWithShadow(text, x, y, colour);
    }

    /** A 1/256 sub-rectangle of the bound texture, as vanilla's widget sheets are laid out. */
    public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
        float f = 0.00390625F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u * f, (v + h) * f);
        GL11.glVertex3f(x, y + h, this.zLevel);
        GL11.glTexCoord2f((u + w) * f, (v + h) * f);
        GL11.glVertex3f(x + w, y + h, this.zLevel);
        GL11.glTexCoord2f((u + w) * f, v * f);
        GL11.glVertex3f(x + w, y, this.zLevel);
        GL11.glTexCoord2f(u * f, v * f);
        GL11.glVertex3f(x, y, this.zLevel);
        GL11.glEnd();
    }
}

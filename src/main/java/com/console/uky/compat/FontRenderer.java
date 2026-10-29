package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.10's FontRenderer over the game's {@link Font}.
 *
 * <p>Strings are drawn with an identity pose: whatever the caller set up with
 * {@link GL11#glPushMatrix()} and friends is on the model-view stack the text shader
 * reads, so it applies to text the way it always did. Legacy {@code §} codes in a plain
 * string are still understood by the font itself.
 */
public final class FontRenderer {

    private static final FontRenderer INSTANCE = new FontRenderer();
    private static final Matrix4f IDENTITY = new Matrix4f();

    /**
     * Whether a plain {@link #drawString} gets a shadow.
     *
     * Set by this mod's own screens while they draw — the rest of the game draws its
     * text with a shadow, and these screens were flat everywhere without it — and
     * cleared again after, so it never follows the game out of them.
     */
    private static boolean shadowed;

    public final int FONT_HEIGHT = 9;

    private FontRenderer() {
    }

    public static FontRenderer get() {
        return INSTANCE;
    }

    public static void setShadowed(boolean value) {
        shadowed = value;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    public int drawString(String text, int x, int y, int colour) {
        return draw(text, x, y, colour, shadowed);
    }

    public int drawString(String text, float x, float y, int colour) {
        return draw(text, x, y, colour, shadowed);
    }

    public int drawString(String text, int x, int y, int colour, boolean shadow) {
        return draw(text, x, y, colour, shadow);
    }

    public int drawStringWithShadow(String text, int x, int y, int colour) {
        return draw(text, x, y, colour, true);
    }

    public int drawStringWithShadow(String text, float x, float y, int colour) {
        return draw(text, x, y, colour, true);
    }

    private static int draw(String text, float x, float y, int colour, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return (int) x;
        }
        // 1.7.10 drew a colour with no alpha byte as opaque, and so many callers pass
        // 0xRRGGBB that the rule has to stay. A faded-out string — alpha below 4 —
        // drew nothing at all there, rather than flashing opaque.
        if ((colour & 0xFC000000) == 0) {
            if ((colour >>> 24) != 0) {
                return (int) x;
            }
            colour |= 0xFF000000;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        int end = font().drawInBatch(text, x, y, colour, shadow, IDENTITY, buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        return end;
    }

    public int getStringWidth(String text) {
        return text == null ? 0 : font().width(text);
    }

    public int getCharWidth(char c) {
        return font().width(String.valueOf(c));
    }

    public String trimStringToWidth(String text, int width) {
        return font().plainSubstrByWidth(text, width);
    }

    public String trimStringToWidth(String text, int width, boolean fromEnd) {
        return font().plainSubstrByWidth(text, width, fromEnd);
    }

    public List<String> listFormattedStringToWidth(String text, int width) {
        List<String> lines = new ArrayList<String>();
        for (FormattedText line : font().getSplitter().splitLines(text, width, Style.EMPTY)) {
            lines.add(line.getString());
        }
        return lines;
    }

    public void drawSplitString(String text, int x, int y, int width, int colour) {
        for (String line : listFormattedStringToWidth(text, width)) {
            drawString(line, x, y, colour);
            y += FONT_HEIGHT;
        }
    }

    public void setUnicodeFlag(boolean value) {
    }

    public boolean getUnicodeFlag() {
        return false;
    }

    public void setBidiFlag(boolean value) {
    }
}

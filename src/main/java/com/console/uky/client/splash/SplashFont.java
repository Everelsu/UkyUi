package com.console.uky.client.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * The ascii.png bitmap font, drawn directly.
 *
 * <p><b>Deliberately not a {@code FontRenderer} subclass.</b> It was one, which was
 * the obvious way to reuse vanilla's glyph metrics and drawing — and it took the game
 * down with a crash that pointed nowhere near here. {@code FontRenderer} is one of the
 * most heavily mixed-into classes in a modern 1.7.10 pack: Angelica replaces the whole
 * text path with a batching renderer, GTNHLib patches it as well. Constructing a
 * subclass of it runs all of that code — on the splash thread, which owns a GL context
 * none of them know about, at a moment when the resource system does not exist yet.
 *
 * <p>Angelica's unicode font provider is initialised on that path, and initialising it
 * there throws. A class whose static initialiser throws is marked erroneous by the JVM
 * <em>for the life of the process</em>: every later use fails with
 * {@code NoClassDefFoundError: Could not initialize class ...FontProviderUnicode}. The
 * original exception was caught here and turned into "loading text will be hidden", so
 * the only visible symptom was the title screen crashing minutes later, in code that
 * had done nothing wrong.
 *
 * <p>So this reads the sheet and emits the quads itself, and touches nothing anybody
 * else has an opinion about. The metrics and the quad layout are vanilla's, copied
 * exactly — {@code FontRenderer.readFontTexture} and {@code renderDefaultChar} — so the
 * text is laid out identically to the game's own.
 */
final class SplashFont {

    /**
     * Sheet order, from {@code FontRenderer.renderCharAtPos}.
     *
     * A character's index in this string is its cell in the sheet. Printable ASCII
     * lands on itself, which is all the splash actually uses; the rest is kept so a
     * pack that puts an accented character in its title still gets one.
     */
    private static final String SHEET_ORDER =
            "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5"
            + "\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e"
            + "\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000"
            + "\u0000\u0000 !\"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7"
            + "\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8"
            + "\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6"
            + "\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7"
            + "\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf"
            + "\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593"
            + "\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d"
            + "\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e"
            + "\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568"
            + "\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c"
            + "\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3"
            + "\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208"
            + "\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0"
            + "\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";

    /** Height of a glyph cell in the units this font lays out in. */
    private static final float CELL = 8.0F;
    /** The sheet is sixteen cells across, so it is 128 units wide however big it is. */
    private static final float SHEET = 128.0F;

    private final SplashTexture texture;
    private final int[] charWidth = new int[256];

    /**
     * Per-codepoint glyph extents from {@code font/glyph_sizes.bin}, and the pages
     * they are drawn from.
     *
     * ascii.png covers Latin and nothing else — no Cyrillic, no Greek, none of it.
     * Everything outside it lives in {@code unicode_page_XX.png}, sixteen by sixteen
     * cells of sixteen pixels, and the widths come from a separate 64K table. Vanilla
     * loads a page the first time a character on it is drawn; so does this, because a
     * loading screen that reads "Загрузка" should not pay for 255 pages it will never
     * touch.
     */
    private byte[] glyphWidth;
    private final SplashTexture[] pages = new SplashTexture[256];
    private final boolean[] pageTried = new boolean[256];

    SplashFont(SplashTexture texture, ResourceLocation location) throws IOException {
        this.texture = texture;
        readWidths(location);
        readGlyphSizes();
    }

    /**
     * Reads the unicode width table. Optional: without it the Latin sheet still works.
     */
    private void readGlyphSizes() {
        InputStream stream = null;
        try {
            stream = Minecraft.getMinecraft().mcDefaultResourcePack
                    .getInputStream(new ResourceLocation("font/glyph_sizes.bin"));
            byte[] table = new byte[65536];
            int off = 0;
            int n;
            while (off < table.length && (n = stream.read(table, off, table.length - off)) > 0) {
                off += n;
            }
            this.glyphWidth = off == table.length ? table : null;
        } catch (Throwable t) {
            this.glyphWidth = null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing useful to do with a failed close here.
                }
            }
        }
    }

    /** The page a character lives on, loaded on first use; null if unavailable. */
    private SplashTexture page(char c) {
        int index = c / 256;
        if (!this.pageTried[index]) {
            this.pageTried[index] = true;
            InputStream stream = null;
            try {
                stream = Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(
                        new ResourceLocation(
                                String.format("textures/font/unicode_page_%02x.png",
                                        Integer.valueOf(index))));
                this.pages[index] = new SplashTexture(stream, false);
            } catch (Throwable t) {
                this.pages[index] = null;
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // Nothing useful to do with a failed close here.
                    }
                }
            }
        }
        return this.pages[index];
    }

    /** Frees every page this font loaded. Called with the GL context still current. */
    void delete() {
        for (int i = 0; i < this.pages.length; i++) {
            if (this.pages[i] != null) {
                this.pages[i].delete();
                this.pages[i] = null;
            }
        }
    }

    /** Whether this character has to come from a unicode page rather than ascii.png. */
    private boolean isUnicodeOnly(char c) {
        return SHEET_ORDER.indexOf(c) < 0 && this.glyphWidth != null
                && (this.glyphWidth[c] & 0xFF) != 0;
    }

    /** Screen advance of a unicode glyph, by vanilla's arithmetic. */
    private int unicodeAdvance(char c) {
        int packed = this.glyphWidth[c] & 0xFF;
        int start = packed >>> 4;
        int end = (packed & 15) + 1;
        return (end - start) / 2 + 1;
    }

    /**
     * Measures every glyph, exactly as {@code FontRenderer.readFontTexture} does.
     *
     * The sheet is read a second time rather than shared with {@link SplashTexture}:
     * that class exists to get an image onto the GPU and throws the pixels away, and
     * decoding a 128x128 png once more during a load that takes half a minute is not
     * worth coupling the two over.
     */
    private void readWidths(ResourceLocation location) throws IOException {
        InputStream stream = null;
        BufferedImage image;
        try {
            stream = Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(location);
            image = javax.imageio.ImageIO.read(stream);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing useful to do with a failed close here.
                }
            }
        }
        if (image == null) {
            throw new IOException("Unsupported or corrupt font sheet");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        int cellHeight = height / 16;
        int cellWidth = width / 16;
        // The sheet may be higher resolution than 128x128; widths are reported in the
        // 8-unit cell the layout works in either way.
        float scale = CELL / cellWidth;

        for (int i = 0; i < 256; i++) {
            int column = i % 16;
            int row = i / 16;
            if (i == 32) {
                // Space has no ink to measure, so vanilla writes its width outright.
                this.charWidth[i] = 4;
                continue;
            }
            int last = cellWidth - 1;
            while (last >= 0 && isColumnEmpty(pixels, width, column, row, cellWidth,
                    cellHeight, last)) {
                last--;
            }
            // Vanilla's expression, rounding included: it is the +0.5 that makes a
            // higher-resolution sheet measure the same as the stock one.
            this.charWidth[i] = (int) (0.5D + (last + 1) * scale) + 1;
        }
    }

    /** True if column {@code x} of this cell has no opaque pixel in it. */
    private static boolean isColumnEmpty(int[] pixels, int imageWidth, int column, int row,
                                         int cellWidth, int cellHeight, int x) {
        int px = column * cellWidth + x;
        for (int y = 0; y < cellHeight; y++) {
            int offset = (row * cellHeight + y) * imageWidth;
            if ((pixels[px + offset] >> 24 & 255) != 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ text --

    /** Width of {@code text} in the same units {@link #drawString} lays it out in. */
    int getStringWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
                // A formatting code. The splash draws plain text, but a stray one in
                // a pack's title should disappear rather than come out as a glyph.
                i++;
                continue;
            }
            if (isUnicodeOnly(c)) {
                width += unicodeAdvance(c);
                continue;
            }
            int index = SHEET_ORDER.indexOf(c);
            if (index >= 0) {
                width += this.charWidth[index];
            }
        }
        return width;
    }

    /**
     * Draws {@code text} with its top-left corner at {@code x, y}.
     *
     * The caller owns the GL state — blending, the projection, any scale — exactly as
     * it did when this was a {@code FontRenderer}. Only the texture binding and the
     * colour are set here, because both are per-string.
     */
    void drawString(String text, int x, int y, int colour, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (shadow) {
            // Vanilla's offset and its flat quarter-brightness shadow colour.
            drawPlain(text, x + 1, y + 1, shade(colour));
        }
        drawPlain(text, x, y, colour);
    }

    /** Vanilla's shadow colour: each channel quartered, alpha kept. */
    private static int shade(int colour) {
        return (colour & 0xFC000000) | ((colour & 0xFCFCFC) >> 2);
    }

    private void drawPlain(String text, int x, int y, int colour) {
        if ((colour & 0xFC000000) == 0) {
            // No alpha given at all means opaque, which is what vanilla assumes too.
            colour |= 0xFF000000;
        }
        GL11.glColor4f((colour >> 16 & 255) / 255.0F, (colour >> 8 & 255) / 255.0F,
                (colour & 255) / 255.0F, (colour >>> 24) / 255.0F);

        // Latin comes off one sheet and everything else off a per-page one, so the
        // bound texture changes mid-string. Quads are batched between changes rather
        // than one begin/end per glyph: a mixed line is two or three batches, and a
        // line that is all one script — which is nearly all of them — is still one.
        SplashTexture bound = null;
        boolean batching = false;
        float cursor = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
                i++;
                continue;
            }

            boolean unicode = isUnicodeOnly(c);
            SplashTexture sheet = unicode ? page(c) : this.texture;
            if (sheet == null) {
                continue;
            }
            if (sheet != bound) {
                if (batching) {
                    GL11.glEnd();
                }
                sheet.bind();
                bound = sheet;
                GL11.glBegin(GL11.GL_QUADS);
                batching = true;
            }

            float maxU = sheet.maxU();
            float maxV = sheet.maxV();

            if (unicode) {
                // renderUnicodeChar, unchanged: cells are 16 units on a 256-unit page,
                // the glyph is drawn at half its texture width, and the 0.02 nudge
                // keeps the neighbouring cell out of it.
                int packed = this.glyphWidth[c] & 0xFF;
                float start = packed >>> 4;
                float end = (packed & 15) + 1;
                float texX = c % 16 * PAGE_CELL + start;
                float texY = (c & 255) / 16 * PAGE_CELL;
                float texSpan = end - start - 0.02F;
                float span = texSpan / 2.0F;

                float u0 = texX / PAGE * maxU;
                float v0 = texY / PAGE * maxV;
                float u1 = (texX + texSpan) / PAGE * maxU;
                float v1 = (texY + 15.98F) / PAGE * maxV;

                GL11.glTexCoord2f(u0, v0);
                GL11.glVertex2f(cursor, y);
                GL11.glTexCoord2f(u0, v1);
                GL11.glVertex2f(cursor, y + 7.99F);
                GL11.glTexCoord2f(u1, v1);
                GL11.glVertex2f(cursor + span, y + 7.99F);
                GL11.glTexCoord2f(u1, v0);
                GL11.glVertex2f(cursor + span, y);

                cursor += unicodeAdvance(c);
                continue;
            }

            int index = SHEET_ORDER.indexOf(c);
            if (index < 0) {
                continue;
            }
            int advance = this.charWidth[index];
            if (c != ' ') {
                // Straight from renderDefaultChar: the cell's top-left in font units,
                // the glyph one unit narrower than its advance, and the 0.01 nudge
                // that keeps the next cell's first column out of this glyph.
                float cellX = index % 16 * CELL;
                float cellY = index / 16 * CELL;
                float span = advance - 0.01F - 1.0F;

                float u0 = cellX / SHEET * maxU;
                float v0 = cellY / SHEET * maxV;
                float u1 = (cellX + span) / SHEET * maxU;
                float v1 = (cellY + 7.99F) / SHEET * maxV;

                GL11.glTexCoord2f(u0, v0);
                GL11.glVertex2f(cursor, y);
                GL11.glTexCoord2f(u0, v1);
                GL11.glVertex2f(cursor, y + 7.99F);
                GL11.glTexCoord2f(u1, v1);
                GL11.glVertex2f(cursor + span, y + 7.99F);
                GL11.glTexCoord2f(u1, v0);
                GL11.glVertex2f(cursor + span, y);
            }
            cursor += advance;
        }

        if (batching) {
            GL11.glEnd();
        }
    }

    /** A unicode page is sixteen 16-unit cells across, so 256 units wide. */
    private static final float PAGE_CELL = 16.0F;
    private static final float PAGE = 256.0F;
}

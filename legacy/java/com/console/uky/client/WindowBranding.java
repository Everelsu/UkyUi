package com.console.uky.client;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Theme;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * What the operating system calls this game.
 *
 * The window says "Minecraft 1.7.10" and the icon is the grass block, which is the one
 * part of the interface this mod cannot reach by drawing: it belongs to the window
 * manager, not to any screen. On a pack that has replaced every menu it is the last
 * thing left over from before — visible in the taskbar, in alt-tab, and in every
 * screenshot anyone posts of it.
 *
 * <p>Applied from {@code init}, which runs from {@code finishMinecraftLoading} — well
 * after {@code startGame} has set both — and never again, because nothing in 1.7.10
 * sets either a second time.
 */
public final class WindowBranding {

    private static final String TITLE = "ULTRAKILL YOURSELF";

    /**
     * The mark, two rows of two.
     *
     * The initials of the name split down the middle, which is the only arrangement
     * that fills a square: one row of four would be a strip with the height wasted, and
     * a single letter says nothing. Capitals because in this font they are all one
     * height and one width, so the four of them pack into an even block — the lowercase
     * forms differ in both and the descender on the y throws the whole thing off centre.
     */
    private static final String[] MARK = {"UK", "YS"};

    /**
     * The pack's own font, which is a Minecraft unicode page: a 16 by 16 grid of cells
     * in code point order, so a character's cell is its low nibble across and its high
     * nibble down. Read straight off the classpath — this runs before any resource pack
     * is in play, and it is our own file either way.
     */
    private static final String FONT =
            "/assets/minecraft/textures/font/unicode_page_00.png";

    /** Pixels between glyphs and between the two rows, at the font's own scale. */
    private static final int GAP = 2;

    /**
     * The sizes handed to LWJGL; it gives the platform whichever it asks for.
     *
     * 32 and 64 are what Windows actually shows in the taskbar and in alt-tab, and both
     * land on a whole-number scale of the mark, so the font stays a pixel font. 16 is
     * for the title bar and is the one that has to be resampled.
     */
    private static final int[] ICON_SIZES = {16, 32, 64};

    private WindowBranding() {
    }

    /** Never throws: a window title is not worth failing a launch over. */
    public static void apply() {
        try {
            Display.setTitle(TITLE);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not set the window title", t);
        }
        try {
            BufferedImage mark = mark();
            if (mark == null) {
                return;
            }
            ByteBuffer[] icons = new ByteBuffer[ICON_SIZES.length];
            for (int i = 0; i < ICON_SIZES.length; i++) {
                icons[i] = toRgba(icon(mark, ICON_SIZES[i]));
            }
            // The return value is the number of icons the platform actually took, and
            // throwing it away is why "the icon did not change" had no answer: this
            // method warned when it failed, said nothing when it worked, and said
            // nothing when it silently did nothing either.
            //
            // Zero is a real outcome rather than a hypothetical. LWJGL's Windows backend
            // walks the array, works each buffer's dimension out from its length, and
            // uses only the ones that come to 16 and 32 — everything else is skipped
            // without a word. Anything that changed those sizes, or made a buffer the
            // wrong length, would land here as a silent no-op.
            int used = Display.setIcon(icons);
            if (used <= 0) {
                UkyUI.LOGGER.warn("Window icon: the platform accepted none of the {}"
                        + " sizes offered {}", ICON_SIZES.length, java.util.Arrays.toString(ICON_SIZES));
            } else {
                UkyUI.LOGGER.info("Window icon set ({} of {} sizes accepted)",
                        used, ICON_SIZES.length);
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not set the window icon", t);
        }
    }

    // -------------------------------------------------------------- the letters --

    /**
     * The mark at the font's own scale, white on transparent.
     *
     * Built from each glyph's inked area rather than from its cell. A cell is 16 wide
     * whatever is drawn in it, and the space around the ink differs per glyph, so
     * packing whole cells left the block a few pixels bigger than it needed to be —
     * which is the difference between the 32-pixel icon landing on a whole-number scale
     * and having to be resampled. Packed by ink it comes to 22 by 30, and 30 is exactly
     * what a 32-pixel icon has room for.
     *
     * @return the mark, or null if the font could not be read
     */
    private static BufferedImage mark() {
        BufferedImage page = font();
        if (page == null) {
            return null;
        }
        int cell = page.getWidth() / 16;

        int width = 0;
        int height = 0;
        int[] rowHeight = new int[MARK.length];
        int[] rowWidth = new int[MARK.length];
        for (int r = 0; r < MARK.length; r++) {
            for (int i = 0; i < MARK[r].length(); i++) {
                int[] box = ink(page, cell, MARK[r].charAt(i));
                if (box == null) {
                    continue;
                }
                rowWidth[r] += box[2] + GAP;
                rowHeight[r] = Math.max(rowHeight[r], box[3]);
            }
            rowWidth[r] = Math.max(0, rowWidth[r] - GAP);
            width = Math.max(width, rowWidth[r]);
            height += rowHeight[r];
        }
        height += GAP * (MARK.length - 1);
        if (width <= 0 || height <= 0) {
            return null;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int y = 0;
        for (int r = 0; r < MARK.length; r++) {
            int x = (width - rowWidth[r]) / 2;
            for (int i = 0; i < MARK[r].length(); i++) {
                int[] box = ink(page, cell, MARK[r].charAt(i));
                if (box == null) {
                    continue;
                }
                g.drawImage(page.getSubimage(box[0], box[1], box[2], box[3]), x, y, null);
                x += box[2] + GAP;
            }
            y += rowHeight[r] + GAP;
        }
        g.dispose();
        return out;
    }

    /**
     * The inked bounds of one character within the page.
     *
     * Measured rather than looked up: the widths Minecraft keeps for this font live in
     * a separate binary that is not ours, and scanning four glyphs once at start-up is
     * cheaper than reading it would be.
     *
     * @return {@code {x, y, width, height}} in page coordinates, or null if blank
     */
    private static int[] ink(BufferedImage page, int cell, char c) {
        int originX = (c & 15) * cell;
        int originY = (c >> 4) * cell;
        int minX = cell;
        int maxX = -1;
        int minY = cell;
        int maxY = -1;
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                if (((page.getRGB(originX + x, originY + y) >>> 24) & 0xFF) <= 16) {
                    continue;
                }
                if (x < minX) {
                    minX = x;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        return maxX < 0 ? null
                : new int[]{originX + minX, originY + minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static BufferedImage font() {
        InputStream in = WindowBranding.class.getResourceAsStream(FONT);
        if (in == null) {
            UkyUI.LOGGER.warn("Window icon: {} is not on the classpath", FONT);
            return null;
        }
        try {
            return ImageIO.read(in);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Window icon: could not read {}", FONT, t);
            return null;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
                // Nothing to do; the image is already read or already lost.
            }
        }
    }

    // ---------------------------------------------------------------- the icon --

    /** The mark on the pack's own colours, at one icon size. */
    private static BufferedImage icon(BufferedImage mark, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // A hard square, like everything else this interface draws. A rounded icon
        // would be the one soft corner in the whole mod.
        g.setColor(new Color(Theme.background | 0xFF000000, true));
        g.fillRect(0, 0, size, size);

        int border = Math.max(1, size / 32);
        g.setColor(new Color(Theme.accent | 0xFF000000, true));
        g.setStroke(new BasicStroke(border));
        g.drawRect(0, 0, size - 1, size - 1);

        int room = size - border * 2;
        float fit = Math.min(room / (float) mark.getWidth(), room / (float) mark.getHeight());
        // A whole-number scale wherever one fits, and nearest-neighbour with it: this is
        // a pixel font, and half a pixel of smoothing is what makes one look like a
        // photograph of itself. Below 1:1 there is no honest option left, so the
        // smallest icon is resampled and reads as a mark rather than as letters.
        int whole = (int) fit;
        boolean crisp = whole >= 1;
        int width = crisp ? mark.getWidth() * whole
                : Math.max(1, Math.round(mark.getWidth() * fit));
        int height = crisp ? mark.getHeight() * whole
                : Math.max(1, Math.round(mark.getHeight() * fit));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, crisp
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g.drawImage(tinted(mark), (size - width) / 2, (size - height) / 2, width, height, null);
        g.dispose();
        return image;
    }

    /** The glyphs in the theme's text colour, keeping their coverage as the alpha. */
    private static BufferedImage tinted(BufferedImage mark) {
        BufferedImage out =
                new BufferedImage(mark.getWidth(), mark.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mark.getHeight(); y++) {
            for (int x = 0; x < mark.getWidth(); x++) {
                out.setRGB(x, y, (mark.getRGB(x, y) & 0xFF000000) | (Theme.text & 0xFFFFFF));
            }
        }
        return out;
    }

    /**
     * Converts to the byte order LWJGL wants.
     *
     * {@code Display.setIcon} takes tightly packed RGBA, top row first — which is the
     * one arrangement {@code BufferedImage} does not hand over directly.
     */
    private static ByteBuffer toRgba(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xFF));
                buffer.put((byte) ((argb >> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buffer.flip();
        return buffer;
    }
}

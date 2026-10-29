package com.console.uky.client.mods;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The widget sheet BetterQuesting's quest book is drawn from, painted at runtime.
 *
 * BetterQuesting's themes are a texture atlas plus a table saying which rectangle of
 * it each widget takes and how much of each edge is the border — nine-slice, so one
 * 8x8 button stretches to any size without its corners smearing. Every theme that
 * ships with it is a PNG someone drew. This one is not: it is painted here, from
 * {@link Theme}, on the frame the quest book is first opened.
 *
 * <p>That is the whole reason it exists. The palette is seven colours in a config
 * file, and the promise made there is that changing the accent restyles the entire
 * interface — a shipped PNG would keep that promise everywhere except the one screen a
 * quest pack lives in. Painting it costs a quarter of a millisecond, once, and the
 * quest book then follows a palette edit exactly as the menus do.
 *
 * <p>The sheet is 256x256 and has to be: nine-slice drawing goes through Forge's
 * {@code GuiUtils}, which divides every coordinate by 256 to get its texture
 * coordinates and offers no way to say otherwise. Only the top 136 rows are used.
 *
 * @see QuestBookTheme which hands these rectangles to BetterQuesting
 */
public final class QuestBookAtlas {

    /** Forced by Forge's nine-slice helper; see the class note. */
    public static final int SIZE = 256;

    /**
     * One widget's rectangle on the sheet, and how much of each edge is border.
     *
     * {@code preset} is the name of a constant in BetterQuesting's {@code PresetTexture}
     * enum. Named rather than referenced because that enum only exists when
     * BetterQuesting is installed, and this class is compiled without it.
     */
    public static final class Slot {

        public final String preset;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final int padLeft;
        public final int padTop;
        public final int padRight;
        public final int padBottom;

        Slot(String preset, int x, int y, int width, int height, int pad) {
            this(preset, x, y, width, height, pad, pad, pad, pad);
        }

        Slot(String preset, int x, int y, int width, int height,
             int padLeft, int padTop, int padRight, int padBottom) {
            this.preset = preset;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.padLeft = padLeft;
            this.padTop = padTop;
            this.padRight = padRight;
            this.padBottom = padBottom;
        }
    }

    // ---------------------------------------------------------------- layout --
    //
    // Rows, top to bottom:
    //     y=0    panels and frames, 24x24 with an 8px border
    //     y=24   buttons, slots and text boxes, 8x8 with a 2px border
    //     y=32   vertical bars (scrollbar, meter), 8x16
    //     y=48   horizontal bars, 16x8
    //     y=64   quest nodes, 24x24, three rows of five states
    //
    // The border widths are what make a widget survive being stretched: an 8x8 button
    // with a 2px border keeps a crisp 2px frame at any size, because only the 4x4
    // middle is ever scaled.

    private static final int PANEL = 24;
    private static final int PANEL_PAD = 8;
    private static final int BUTTON = 8;
    private static final int BUTTON_PAD = 2;

    private static final List<Slot> SLOTS = new ArrayList<Slot>();

    static {
        // panels
        panelSlot("PANEL_MAIN", 0);
        panelSlot("PANEL_DARK", 1);
        panelSlot("PANEL_INNER", 2);
        panelSlot("AUX_FRAME_0", 3);
        panelSlot("AUX_FRAME_1", 4);

        // buttons and boxes
        buttonSlot("BTN_NORMAL_0", 0);
        buttonSlot("BTN_NORMAL_1", 1);
        buttonSlot("BTN_NORMAL_2", 2);
        buttonSlot("BTN_CLEAN_0", 3);
        buttonSlot("BTN_CLEAN_1", 4);
        buttonSlot("BTN_CLEAN_2", 5);
        buttonSlot("BTN_ALT_0", 6);
        buttonSlot("BTN_ALT_1", 7);
        buttonSlot("BTN_ALT_2", 8);
        buttonSlot("ITEM_FRAME", 9);
        buttonSlot("TEXT_BOX_0", 10);
        buttonSlot("TEXT_BOX_1", 11);
        buttonSlot("TEXT_BOX_2", 12);
        buttonSlot("HOTBAR_0", 13);
        buttonSlot("HOTBAR_1", 14);

        // vertical bars
        verticalSlot("SCROLL_V_BG", 0);
        verticalSlot("SCROLL_V_0", 1);
        verticalSlot("SCROLL_V_1", 2);
        verticalSlot("SCROLL_V_2", 3);
        verticalSlot("METER_V_0", 4);
        verticalSlot("METER_V_1", 5);

        // horizontal bars
        horizontalSlot("SCROLL_H_BG", 0);
        horizontalSlot("SCROLL_H_0", 1);
        horizontalSlot("SCROLL_H_1", 2);
        horizontalSlot("SCROLL_H_2", 3);
        horizontalSlot("METER_H_0", 4);
        horizontalSlot("METER_H_1", 5);

        // quest nodes: normal, main-line, auxiliary — five states each
        for (int state = 0; state < 5; state++) {
            questSlot("QUEST_NORM_" + state, state, 64);
            questSlot("QUEST_MAIN_" + state, state, 88);
            questSlot("QUEST_AUX_" + state, state, 112);
        }
    }

    private static void panelSlot(String preset, int column) {
        SLOTS.add(new Slot(preset, column * PANEL, 0, PANEL, PANEL, PANEL_PAD));
    }

    private static void buttonSlot(String preset, int column) {
        SLOTS.add(new Slot(preset, column * BUTTON, 24, BUTTON, BUTTON, BUTTON_PAD));
    }

    private static void verticalSlot(String preset, int column) {
        SLOTS.add(new Slot(preset, column * 8, 32, 8, 16, BUTTON_PAD));
    }

    private static void horizontalSlot(String preset, int column) {
        SLOTS.add(new Slot(preset, column * 16, 48, 16, 8, BUTTON_PAD));
    }

    private static void questSlot(String preset, int column, int y) {
        SLOTS.add(new Slot(preset, column * PANEL, y, PANEL, PANEL, PANEL_PAD));
    }

    private QuestBookAtlas() {
    }

    /** Every widget on the sheet, in no particular order. */
    public static List<Slot> slots() {
        return SLOTS;
    }

    private static Slot slot(String preset) {
        for (int i = 0; i < SLOTS.size(); i++) {
            if (SLOTS.get(i).preset.equals(preset)) {
                return SLOTS.get(i);
            }
        }
        throw new IllegalArgumentException("No atlas slot named " + preset);
    }

    // ---------------------------------------------------------------- painting --

    /** Paints the whole sheet from the palette as it stands right now. */
    public static BufferedImage paint() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            paintPanels(g);
            paintButtons(g);
            paintBars(g);
            paintQuestNodes(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Panels: a dark fill, a hairline border and gold ticks in the corners.
     *
     * The corner ticks are the point of doing this in nine-slice at all. A panel's
     * corners are the one part of it that is never stretched, so a 7px bracket drawn
     * there stays a 7px bracket whether the panel ends up 60 pixels wide or 600 —
     * which is what the menus in this mod use to frame a surface, and what would be
     * impossible with a border that scaled.
     */
    private static void paintPanels(Graphics2D g) {
        panel(g, slot("PANEL_MAIN"),
                Draw.withAlpha(Theme.background, 0.94F),
                Draw.withAlpha(Theme.text, 0.12F),
                Draw.withAlpha(Theme.accent, 0.85F));
        // The backdrop the quest lines are drawn over: darker, and with no ticks,
        // because it is usually the largest thing on the screen and four bright
        // corners on it would frame the window rather than the content.
        panel(g, slot("PANEL_DARK"),
                Draw.withAlpha(Theme.background, 0.98F),
                Draw.withAlpha(Theme.text, 0.08F),
                0);
        panel(g, slot("PANEL_INNER"),
                Draw.withAlpha(Theme.surface, 0.55F),
                Draw.withAlpha(Theme.text, 0.10F),
                0);
        panel(g, slot("AUX_FRAME_0"),
                Draw.withAlpha(Theme.surface, 0.45F),
                Draw.withAlpha(Theme.text, 0.12F),
                Draw.withAlpha(Theme.accent, 0.45F));
        panel(g, slot("AUX_FRAME_1"),
                Draw.withAlpha(Theme.surface, 0.30F),
                Draw.withAlpha(Theme.accent, 0.28F),
                0);
    }

    /**
     * Buttons, slots and text boxes.
     *
     * Three families, and they are not three styles of the same thing. NORMAL carries
     * the gold rail down its left edge and the fill fading away to the right, which is
     * how every button in this mod's own menus is drawn. CLEAN is for the small square
     * icon buttons a quest book is full of, where a 2px rail on a 16px button is most
     * of the button. ALT is the accented one, for the action a screen is asking for.
     */
    private static void paintButtons(Graphics2D g) {
        int idle = Draw.withAlpha(Theme.surface, 0.62F);
        int hover = Draw.withAlpha(Draw.mix(Theme.surface, Theme.accent, 0.16F), 0.85F);

        railButton(g, slot("BTN_NORMAL_0"), Draw.withAlpha(Theme.surface, 0.28F),
                Draw.withAlpha(Theme.textDim, 0.30F));
        railButton(g, slot("BTN_NORMAL_1"), idle, Draw.withAlpha(Theme.accent, 0.75F));
        railButton(g, slot("BTN_NORMAL_2"), hover, Theme.accent);

        flatButton(g, slot("BTN_CLEAN_0"), Draw.withAlpha(Theme.surface, 0.22F),
                Draw.withAlpha(Theme.text, 0.08F));
        flatButton(g, slot("BTN_CLEAN_1"), idle, Draw.withAlpha(Theme.text, 0.20F));
        flatButton(g, slot("BTN_CLEAN_2"), hover, Draw.withAlpha(Theme.accent, 0.85F));

        flatButton(g, slot("BTN_ALT_0"), Draw.withAlpha(Theme.accent, 0.10F),
                Draw.withAlpha(Theme.accent, 0.25F));
        flatButton(g, slot("BTN_ALT_1"), Draw.withAlpha(Theme.accent, 0.22F),
                Draw.withAlpha(Theme.accent, 0.55F));
        flatButton(g, slot("BTN_ALT_2"), Draw.withAlpha(Theme.accent, 0.38F), Theme.accent);

        // An item slot is a hole in the panel, not a raised control.
        flatButton(g, slot("ITEM_FRAME"), Draw.withAlpha(Theme.background, 0.72F),
                Draw.withAlpha(Theme.text, 0.20F));

        flatButton(g, slot("TEXT_BOX_0"), Draw.withAlpha(Theme.background, 0.40F),
                Draw.withAlpha(Theme.text, 0.09F));
        flatButton(g, slot("TEXT_BOX_1"), Draw.withAlpha(Theme.background, 0.65F),
                Draw.withAlpha(Theme.text, 0.24F));
        // Focused. The border is the whole signal — a field being typed into has to be
        // findable at a glance on a screen with six of them.
        flatButton(g, slot("TEXT_BOX_2"), Draw.withAlpha(Theme.background, 0.65F),
                Draw.withAlpha(Theme.accent, 0.85F));

        flatButton(g, slot("HOTBAR_0"), Draw.withAlpha(Theme.background, 0.72F),
                Draw.withAlpha(Theme.text, 0.18F));
        flatButton(g, slot("HOTBAR_1"), Draw.withAlpha(Theme.accent, 0.16F),
                Draw.withAlpha(Theme.accent, 0.80F));
    }

    /** Scrollbars and progress meters, both axes. */
    private static void paintBars(Graphics2D g) {
        int track = Draw.withAlpha(Theme.background, 0.75F);
        int trackBorder = Draw.withAlpha(Theme.text, 0.12F);

        flatButton(g, slot("SCROLL_V_BG"), track, trackBorder);
        flatButton(g, slot("SCROLL_H_BG"), track, trackBorder);

        // The thumb carries the same gold rail as a button, on the edge that faces
        // the direction it does not travel in. Its fill is lifted well clear of the
        // surface colour: a scrollbar sits on the darkest panel there is, and the
        // one drawn at the surface's own value was a gold line with nothing under it.
        int thumbIdle = Draw.withAlpha(Draw.mix(Theme.surface, Theme.text, 0.20F), 0.85F);
        int thumbHover = Draw.withAlpha(Draw.mix(Theme.surface, Theme.accent, 0.35F), 0.95F);

        thumb(g, slot("SCROLL_V_0"), Draw.withAlpha(Theme.surface, 0.45F),
                Draw.withAlpha(Theme.textDim, 0.30F), true);
        thumb(g, slot("SCROLL_V_1"), thumbIdle, Draw.withAlpha(Theme.accent, 0.75F), true);
        thumb(g, slot("SCROLL_V_2"), thumbHover, Theme.accent, true);

        thumb(g, slot("SCROLL_H_0"), Draw.withAlpha(Theme.surface, 0.45F),
                Draw.withAlpha(Theme.textDim, 0.30F), false);
        thumb(g, slot("SCROLL_H_1"), thumbIdle, Draw.withAlpha(Theme.accent, 0.75F), false);
        thumb(g, slot("SCROLL_H_2"), thumbHover, Theme.accent, false);

        flatButton(g, slot("METER_V_0"), track, trackBorder);
        flatButton(g, slot("METER_H_0"), track, trackBorder);

        // A meter's fill runs along its own axis, from the dim end of the palette to
        // the bright one, so a bar that is nearly full reads as nearly full from the
        // colour alone.
        Slot meterV = slot("METER_V_1");
        vgrad(g, meterV.x, meterV.y, meterV.width, meterV.height,
                Draw.withAlpha(Theme.accent, 0.95F), Draw.withAlpha(Theme.accentAlt, 0.95F));
        Slot meterH = slot("METER_H_1");
        hgrad(g, meterH.x, meterH.y, meterH.width, meterH.height,
                Draw.withAlpha(Theme.accentAlt, 0.95F), Draw.withAlpha(Theme.accent, 0.95F));
    }

    /**
     * The quest nodes themselves — the squares on the quest map.
     *
     * Painted white, and that is not an oversight. BetterQuesting draws these tinted
     * with a colour it picks per state (locked, available, in progress, done,
     * repeatable), which {@link QuestBookTheme} also fills in from the palette. A node
     * painted gold here would be tinted gold a second time and come out brown.
     *
     * <p>So the shape carries the kind and the tint carries the state: a normal quest
     * is a hairline frame with corner ticks, a main-line quest is the same frame drawn
     * twice as heavy, and an auxiliary one is lighter than either. The five states of
     * each differ only in how filled-in they look, which is a hint underneath the
     * colour rather than a second way of saying the same thing.
     */
    private static void paintQuestNodes(Graphics2D g) {
        // The frame is deliberately faint. Four bright corners and a whisper of an
        // edge is the same figure the panels above are drawn with, and it is what
        // stops a quest map at full zoom from reading as a page of white boxes.
        float[] frame = {0.14F, 0.24F, 0.24F, 0.38F, 0.24F};
        float[] ticks = {0.45F, 1.00F, 1.00F, 1.00F, 1.00F};
        float[] inner = {0.00F, 0.05F, 0.09F, 0.16F, 0.05F};

        for (int state = 0; state < 5; state++) {
            questNode(g, slot("QUEST_NORM_" + state), 1, 6,
                    frame[state], ticks[state], inner[state]);
            questNode(g, slot("QUEST_MAIN_" + state), 2, 8,
                    frame[state], ticks[state], inner[state] + 0.05F);
            questNode(g, slot("QUEST_AUX_" + state), 1, 4,
                    frame[state] * 0.70F, ticks[state] * 0.85F, inner[state] * 0.5F);
        }
    }

    // --------------------------------------------------------------- recipes --

    private static void panel(Graphics2D g, Slot s, int fill, int border, int tick) {
        fill(g, s.x, s.y, s.width, s.height, fill);
        frame(g, s.x, s.y, s.width, s.height, 1, border);
        if ((tick >>> 24) != 0) {
            corners(g, s.x, s.y, s.width, s.height, 7, 1, tick);
        }
    }

    /** A button with the gold rail down its left edge and the fill fading right. */
    private static void railButton(Graphics2D g, Slot s, int fill, int rail) {
        hgrad(g, s.x, s.y, s.width, s.height, fill, Draw.fade(fill, 0.30F));
        fill(g, s.x, s.y, s.padLeft, s.height, rail);
    }

    private static void flatButton(Graphics2D g, Slot s, int fill, int border) {
        fill(g, s.x, s.y, s.width, s.height, fill);
        frame(g, s.x, s.y, s.width, s.height, 1, border);
    }

    private static void thumb(Graphics2D g, Slot s, int fill, int rail, boolean vertical) {
        fill(g, s.x, s.y, s.width, s.height, fill);
        if (vertical) {
            fill(g, s.x, s.y, s.padLeft, s.height, rail);
        } else {
            fill(g, s.x, s.y, s.width, s.padTop, rail);
        }
    }

    private static void questNode(Graphics2D g, Slot s, int thickness, int arm,
                                  float frameAlpha, float tickAlpha, float innerAlpha) {
        int white = 0xFFFFFFFF;
        fill(g, s.x, s.y, s.width, s.height, Draw.withAlpha(white, innerAlpha));
        frame(g, s.x, s.y, s.width, s.height, thickness, Draw.withAlpha(white, frameAlpha));
        corners(g, s.x, s.y, s.width, s.height, arm, thickness, Draw.withAlpha(white, tickAlpha));
    }

    // ------------------------------------------------------------ primitives --

    private static void fill(Graphics2D g, int x, int y, int w, int h, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        g.setColor(new Color(argb, true));
        g.fillRect(x, y, w, h);
    }

    private static void frame(Graphics2D g, int x, int y, int w, int h, int thickness, int argb) {
        fill(g, x, y, w, thickness, argb);
        fill(g, x, y + h - thickness, w, thickness, argb);
        fill(g, x, y + thickness, thickness, h - thickness * 2, argb);
        fill(g, x + w - thickness, y + thickness, thickness, h - thickness * 2, argb);
    }

    /** An L in each corner, arms pointing along the edges. */
    private static void corners(Graphics2D g, int x, int y, int w, int h,
                                int arm, int thickness, int argb) {
        for (int i = 0; i < 4; i++) {
            boolean right = (i & 1) != 0;
            boolean bottom = (i & 2) != 0;
            int cx = right ? x + w - arm : x;
            int cy = bottom ? y + h - thickness : y;
            fill(g, cx, cy, arm, thickness, argb);
            cx = right ? x + w - thickness : x;
            cy = bottom ? y + h - arm : y;
            fill(g, cx, cy, thickness, arm, argb);
        }
    }

    private static void hgrad(Graphics2D g, int x, int y, int w, int h, int left, int right) {
        for (int i = 0; i < w; i++) {
            fill(g, x + i, y, 1, h, Draw.mix(left, right, w == 1 ? 0.0F : (float) i / (w - 1)));
        }
    }

    private static void vgrad(Graphics2D g, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            fill(g, x, y + i, w, 1, Draw.mix(top, bottom, h == 1 ? 0.0F : (float) i / (h - 1)));
        }
    }
}

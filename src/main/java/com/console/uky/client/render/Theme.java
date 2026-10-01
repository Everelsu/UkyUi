package com.console.uky.client.render;

import com.console.uky.config.UiConfig;

/**
 * Derived palette. {@link UiConfig} stores the seven base colours an author
 * actually picks; everything the widgets need (hover fills, borders, shadows) is
 * computed from those here so a single accent change restyles the whole UI
 * consistently.
 *
 * All values are ARGB. Call {@link #rebuild()} after the config is (re)loaded.
 */
public final class Theme {

    // base
    public static int background;
    public static int surface;
    public static int accent;
    public static int accentAlt;
    public static int text;
    public static int textDim;
    public static int danger;

    // the black hole's disk, inner to outer
    public static int holeHot;
    public static int holeMid;
    public static int holeCold;

    // derived — panels
    public static int panelFill;
    public static int panelFillHover;
    public static int panelBorder;
    public static int panelBorderHover;
    public static int panelShadow;

    // derived — text
    public static int textHover;
    public static int textDisabled;

    // derived — misc
    public static int overlay;
    public static int separator;
    public static int trackFill;

    /**
     * The Easy palette's accents: a soft mint and a pale sky blue in place of the
     * pack's gold and its second accent. Friendlier, and still clearly the same
     * interface — only the two colours everything else is derived from move.
     */
    private static final int EASY_ACCENT = 0xFF86D9BE;
    private static final int EASY_ACCENT_ALT = 0xFF9CC9F2;
    /**
     * The disk in Easy: a white-mint core cooling through sea green to a deep teal,
     * so the hole matches the interface over it instead of burning gold under mint.
     */
    private static final int EASY_HOLE_HOT = 0xFFF0FFF8;
    private static final int EASY_HOLE_MID = 0xFF7FCFB4;
    private static final int EASY_HOLE_COLD = 0xFF2F6F78;

    /** How far towards the Easy palette, 0..1; animated by PackLook. */
    private static float easy;

    private Theme() {
    }

    /** Moves the palette towards Easy (1) or Standard (0), rebuilding what derives from it. */
    public static void setEasy(float t) {
        t = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        if (t != easy) {
            easy = t;
            rebuild();
        }
    }

    public static float easy() {
        return easy;
    }

    static {
        rebuild();
    }

    public static void rebuild() {
        background = 0xFF000000 | UiConfig.colorBackground;
        surface = 0xFF000000 | UiConfig.colorSurface;
        accent = Draw.mix(0xFF000000 | UiConfig.colorAccent, EASY_ACCENT, easy);
        accentAlt = Draw.mix(0xFF000000 | UiConfig.colorAccentAlt, EASY_ACCENT_ALT, easy);
        holeHot = Draw.mix(0xFF000000 | UiConfig.colorBlackHoleHot, EASY_HOLE_HOT, easy);
        holeMid = Draw.mix(0xFF000000 | UiConfig.colorBlackHoleMid, EASY_HOLE_MID, easy);
        holeCold = Draw.mix(0xFF000000 | UiConfig.colorBlackHoleCold, EASY_HOLE_COLD, easy);
        text = 0xFF000000 | UiConfig.colorText;
        textDim = 0xFF000000 | UiConfig.colorTextDim;
        danger = 0xFF000000 | UiConfig.colorDanger;

        // Buttons sit on top of a photographic background, so the fill is
        // deliberately translucent — the artwork should still read through it.
        panelFill = Draw.withAlpha(surface, 0.62F);
        panelFillHover = Draw.withAlpha(Draw.mix(surface, accent, 0.14F), 0.82F);
        panelBorder = Draw.withAlpha(Draw.mix(surface, text, 0.22F), 0.55F);
        panelBorderHover = Draw.withAlpha(accent, 0.9F);
        panelShadow = 0x50000000;

        textHover = 0xFFFFFFFF;
        textDisabled = Draw.withAlpha(textDim, 0.45F);

        overlay = Draw.withAlpha(background, 0.72F);
        separator = Draw.withAlpha(text, 0.12F);
        trackFill = Draw.withAlpha(background, 0.85F);
    }
}

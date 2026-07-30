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

    private Theme() {
    }

    static {
        rebuild();
    }

    public static void rebuild() {
        background = 0xFF000000 | UiConfig.colorBackground;
        surface = 0xFF000000 | UiConfig.colorSurface;
        accent = 0xFF000000 | UiConfig.colorAccent;
        accentAlt = 0xFF000000 | UiConfig.colorAccentAlt;
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

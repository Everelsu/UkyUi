package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/**
 * The one button style the whole mod uses. Draws itself entirely from
 * {@link Theme} — no widgets.png, no nine-slice, so it scales to any size and
 * restyles from the config.
 *
 * Motion is driven by {@link #advance} which the owning screen calls once per
 * frame with the real frame delta; that keeps hover/press animation speed
 * identical at any frame rate. A button that is never advanced still renders,
 * just without animation, so it degrades safely if dropped into a vanilla screen.
 */
public class MenuButton extends GuiButton {

    public enum Style {
        /** Filled accent bar — the one obvious next action. */
        PRIMARY,
        /** Default translucent panel. */
        NORMAL,
        /** Destructive: quit, disconnect, delete. */
        DANGER,
        /** Text-only, no panel; for footers and secondary links. */
        GHOST
    }

    public enum Align {
        LEFT, CENTER
    }

    private final Style style;
    private Align align = Align.CENTER;
    /** Forces the danger colour without forcing the DANGER panel style. */
    private boolean destructive;
    /** Held-open state, e.g. the active tab: drawn as if permanently hovered. */
    private boolean selected;
    /**
     * Suppresses the GHOST connector and underline, leaving just the label.
     *
     * Those marks are what makes a main-menu entry feel like an instrument panel,
     * but a row of tabs already has a rule under it and a marker on that rule —
     * adding two more strokes per tab gave every one of them a small forest of
     * lines.
     */
    private boolean plain;

    /** Seconds to wait before this button plays its entrance animation. */
    private float entranceDelay;

    protected float hover;      // 0..1 smoothed hover
    protected float press;      // 0..1 smoothed press
    protected float entrance;   // 0..1 entrance progress
    protected float age;
    protected float screenFade = 1.0F;
    protected boolean pressed;

    /** Optional right-aligned value text, e.g. "Fullscreen: ON". */
    private String valueText;
    /** Optional index shown ahead of the label, e.g. "01". */
    private String ordinal;

    public MenuButton(int id, int x, int y, int width, int height, String text) {
        this(id, x, y, width, height, text, Style.NORMAL);
    }

    public MenuButton(int id, int x, int y, int width, int height, String text, Style style) {
        super(id, x, y, width, height, text);
        this.style = style;
    }

    public MenuButton entrance(float delaySeconds) {
        this.entranceDelay = delaySeconds;
        return this;
    }

    public MenuButton align(Align align) {
        this.align = align;
        return this;
    }

    /** Label only — no connector, no underline. See {@link #plain}. */
    public MenuButton plain() {
        this.plain = true;
        return this;
    }

    /** Colours this entry as destructive while keeping its current style. */
    public MenuButton destructive() {
        this.destructive = true;
        return this;
    }

    /** Marks this entry as the currently open one. */
    public MenuButton selected() {
        this.selected = true;
        return this;
    }

    public MenuButton ordinal(String ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /** Smoothed hover, 0..1 — lets the owning screen draw the shared rail. */
    public float hoverAmount() {
        return this.hover;
    }

    public boolean isDestructive() {
        return this.destructive || this.style == Style.DANGER;
    }

    public MenuButton value(String value) {
        this.valueText = value;
        return this;
    }

    public String getValue() {
        return this.valueText;
    }

    /** Called once per frame by {@link com.console.uky.client.gui.MenuScreen}. */
    public void advance(float deltaSeconds, float screenFade) {
        this.age += deltaSeconds;
        this.screenFade = screenFade;

        // A selected entry stays lit whether or not the pointer is on it, so the
        // active tab reads as open rather than as merely hovered a moment ago.
        float target = this.selected || (this.field_146123_n && this.enabled) ? 1.0F : 0.0F;
        this.hover = Ease.approach(this.hover, target, 0.055F, deltaSeconds);
        this.press = Ease.approach(this.press, this.pressed ? 1.0F : 0.0F, 0.030F, deltaSeconds);

        float t = (this.age - this.entranceDelay) / 0.35F;
        this.entrance = Ease.outQuint(t);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;

        float alpha = this.entrance * this.screenFade;
        if (alpha <= 0.01F) {
            return;
        }

        // Entrance slides in from the left; press nudges down a hair.
        float slide = (1.0F - this.entrance) * 14.0F;
        float x1 = this.xPosition - slide;
        float y1 = this.yPosition + this.press * 1.0F;
        float x2 = x1 + this.width;
        float y2 = y1 + this.height;

        if (style != Style.GHOST) {
            drawPanel(x1, y1, x2, y2, alpha);
        }
        drawLabel(mc.fontRenderer, x1, y1, x2, y2, alpha);
    }

    /**
     * Panel chrome shared with sliders and toggles.
     *
     * No outline: a boxed-in border fights the black-hole backdrop and makes a
     * screen full of controls read as a spreadsheet. The button is instead defined
     * by a fill that fades out to the right and an accent rail on the left, so the
     * eye follows the same left edge down the whole column.
     */
    protected void drawPanel(float x1, float y1, float x2, float y2, float alpha) {
        int accent = accentColor();
        float h = this.enabled ? this.hover : 0.0F;

        int fillLeft = Draw.mix(Theme.panelFill, Theme.panelFillHover, h);
        if (style == Style.PRIMARY) {
            // Solid enough to actually read as the committing action. At 0.14 it was
            // a faint tint that disappeared entirely against a bright backdrop, which
            // is not what the one button you are meant to press should look like.
            fillLeft = Draw.mix(Draw.withAlpha(accent, 0.52F), Draw.withAlpha(accent, 0.72F), h);
        }
        if (!this.enabled) {
            fillLeft = Draw.withAlpha(Theme.surface, 0.30F);
        }
        // Fades toward the right edge so the control dissolves into the backdrop
        // instead of ending on a hard line. A PRIMARY button keeps most of its fill
        // all the way across: it is the committing action, and on a wide one the
        // steep fade left the label floating past the end of a small coloured box.
        int fillRight = style == Style.PRIMARY
                ? Draw.fade(fillLeft, 0.70F + 0.30F * h)
                : Draw.fade(fillLeft, 0.25F + 0.45F * h);
        Draw.gradientH(x1, y1, x2, y2, Draw.fade(fillLeft, alpha), Draw.fade(fillRight, alpha));

        // Deliberately no rule along the top. One per control turned a screen of
        // settings into ruled notepaper; spacing separates the rows, and the accent
        // rail below is what the eye actually tracks down the column.

        if (h > 0.01F && this.enabled) {
            // Light bleeding out of the left edge, not a box around the whole thing.
            Draw.gradientH(x1, y1, x1 + (x2 - x1) * 0.45F, y2,
                    Draw.fade(accent, 0.16F * h * alpha), Draw.withAlpha(accent, 0.0F));
        }

        // Accent rail on the left edge, growing from the vertical centre on hover.
        // With the hairlines gone this is the only structure holding the column
        // together, so it is drawn bright enough to be read as navigation.
        float railH = (y2 - y1) * (0.34F + 0.66F * h);
        float railY = (y1 + y2) / 2.0F - railH / 2.0F;
        Draw.rect(x1, railY, x1 + 2.0F, railY + railH,
                Draw.fade(accent, (0.8F + 0.2F * h) * alpha * (this.enabled ? 1.0F : 0.3F)));
    }

    private void drawLabel(FontRenderer font, float x1, float y1, float x2, float y2, float alpha) {
        int color;
        if (!this.enabled) {
            color = Theme.textDisabled;
        } else if (style == Style.GHOST) {
            // Ghost entries have no panel, so the label itself carries the colour:
            // accent at rest, white under the pointer.
            color = Draw.mix(accentColor(), Theme.textHover, this.hover);
        } else if (style == Style.DANGER) {
            color = Draw.mix(Theme.textDim, Theme.danger, this.hover);
        } else {
            color = Draw.mix(Theme.text, Theme.textHover, this.hover);
        }
        color = Draw.fade(color, alpha);

        int textY = (int) (y1 + (this.height - 8) / 2.0F);

        if (valueText != null) {
            // label left, value right — the layout used by the options screen
            int padding = 10;
            font.drawString(this.displayString, (int) (x1 + padding), textY, color);
            int valueColor = Draw.fade(Draw.mix(Theme.textDim, accentColor(), this.hover), alpha);
            int valueWidth = font.getStringWidth(valueText);
            font.drawString(valueText, (int) (x2 - padding - valueWidth), textY, valueColor);
            return;
        }

        int labelWidth = font.getStringWidth(this.displayString);
        if (this.align == Align.LEFT) {
            float padding = 12.0F + this.hover * 6.0F;
            float textX = x1 + padding;
            float markY = (y1 + y2) / 2.0F;

            if (style == Style.GHOST) {
                int accent = accentColor();

                // Index ahead of the label. Numbering the entries is what turns a
                // stack of words into something that reads as an instrument panel.
                if (this.ordinal != null) {
                    int numberColor = Draw.fade(
                            Draw.mix(Draw.withAlpha(accent, 0.35F), accent, this.hover), alpha);
                    font.drawString(this.ordinal, (int) textX, textY, numberColor);
                    textX += font.getStringWidth(this.ordinal) + 8.0F;
                }

                // Letter-spaced, so a text-only menu reads as typography rather
                // than as unstyled labels sitting on the artwork.
                float end = drawTracked(font, this.displayString, textX, textY, 1, color);

                if (this.hover > 0.02F && this.enabled && !this.plain) {
                    // Connector running from the rail to the label, drawn in.
                    float tickEnd = x1 + (textX - x1) * this.hover;
                    Draw.rect(x1, markY - 0.5F, tickEnd, markY + 0.5F,
                            Draw.fade(accent, 0.55F * this.hover * alpha));
                    // Underline under the label itself.
                    float ruleW = (end - textX) * this.hover;
                    Draw.gradientH(textX, textY + 10.0F, textX + ruleW, textY + 11.0F,
                            Draw.fade(accent, 0.7F * this.hover * alpha),
                            Draw.withAlpha(accent, 0.0F));
                }
                return;
            }

            font.drawString(this.displayString, (int) textX, textY, color);
            if (this.hover > 0.02F && this.enabled) {
                Draw.rect(x1, markY - 3.0F, x1 + 2.0F + this.hover * 2.0F, markY + 3.0F,
                        Draw.fade(accentColor(), this.hover * alpha));
            }
            return;
        }

        // Centred label drifts right a touch on hover, following the accent rail.
        float cx = (x1 + x2) / 2.0F + this.hover * 2.0F;
        font.drawString(this.displayString, (int) (cx - labelWidth / 2.0F), textY, color);
    }

    /**
     * Draws {@code text} with extra space between glyphs and returns the x the
     * label ends at, so callers can size a rule to match it.
     */
    private static float drawTracked(FontRenderer font, String text, float x, float y,
                                     int tracking, int color) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            font.drawString(String.valueOf(c), (int) cursor, (int) y, color, false);
            cursor += font.getCharWidth(c) + tracking;
        }
        return cursor - tracking;
    }

    protected int accentColor() {
        return (this.destructive || style == Style.DANGER) ? Theme.danger : Theme.accent;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean hit = super.mousePressed(mc, mouseX, mouseY);
        if (hit) {
            this.pressed = true;
        }
        return hit;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.pressed = false;
    }

    @Override
    public void func_146113_a(SoundHandler soundHandler) {
        if (!UiConfig.buttonSounds) {
            return;
        }
        // Our own click, not the vanilla wooden thunk.
        UkySounds.play(UkySounds.BUTTON, 0.7F, 1.0F);
    }
}

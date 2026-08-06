package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Themed replacement for {@code GuiOptionButton}. Cycles an option on click;
 * boolean options additionally render an animated pill switch instead of the
 * "ON"/"OFF" word, which reads much faster in a long options list.
 *
 * <p>What it is cycling is behind {@link Source}. Vanilla's {@link GameSettings}
 * options are one implementation and the one this was written for, but a settings
 * screen that gathers other mods' options into itself has to drive values it cannot
 * name at compile time — see {@code AngelicaOptions} — and those are the same control
 * as far as the player is concerned, so they are the same widget here.
 */
public class MenuOptionButton extends MenuButton {

    /** Track width of the pill switch; the label is budgeted around it. */
    private static final float SWITCH_WIDTH = 22.0F;

    /**
     * Where a row's label, value and behaviour come from.
     *
     * Read every frame rather than cached, because an option's text and its
     * availability can both depend on what other options are set to.
     */
    public interface Source {

        /** Left-hand text: what the setting is called. */
        String label();

        /** Right-hand text: what it is currently set to. Unused by a toggle. */
        String value();

        /** True to draw the pill switch rather than a value. */
        boolean toggle();

        /** State of the switch. Only asked of a toggle. */
        boolean on();

        /** Advance to the next value. */
        void cycle();

        /** False greys the row out and stops it being clicked. */
        boolean available();
    }

    private final Source source;
    private final boolean isToggle;
    private float switchPos;

    public MenuOptionButton(int id, int x, int y, int width, int height, GameSettings.Options option) {
        this(id, x, y, width, height, new VanillaSource(option));
    }

    public MenuOptionButton(int id, int x, int y, int width, int height, Source source) {
        super(id, x, y, width, height, "", Style.NORMAL);
        this.source = source;
        this.isToggle = source.toggle();
        refresh();
        // Start the switch where it belongs so it does not slide on first draw.
        this.switchPos = isOn() ? 1.0F : 0.0F;
    }

    /** Applies the next value and re-reads the label. */
    public void cycle() {
        this.source.cycle();
        refresh();
    }

    private void refresh() {
        this.displayString = this.source.label();
        value(this.isToggle ? null : this.source.value());
    }

    private boolean isOn() {
        return this.source.on();
    }

    @Override
    public void advance(float deltaSeconds, float screenFade) {
        // Re-read rather than refresh on click only. Neither the value nor whether the
        // row can be edited at all is settled at layout time: several settings only
        // become editable once a related one is turned on, and that related one is
        // frequently the row directly above this. Read before the base class advances,
        // which is what decides whether this frame counts as hovered.
        this.enabled = this.source.available();
        refresh();
        super.advance(deltaSeconds, screenFade);
        if (this.isToggle) {
            this.switchPos = Ease.approach(this.switchPos, isOn() ? 1.0F : 0.0F, 0.045F, deltaSeconds);
        }
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.isToggle) {
            super.drawButton(mc, mouseX, mouseY, partialTicks);
            return;
        }
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;

        float alpha = this.entrance * this.screenFade;
        if (alpha <= 0.01F) {
            return;
        }

        float slide = (1.0F - this.entrance) * 14.0F;
        float x1 = this.x - slide;
        float y1 = this.y + this.press;
        float x2 = x1 + this.width;
        float y2 = y1 + this.height;

        drawPanel(x1, y1, x2, y2, alpha);

        FontRenderer font = mc.fontRenderer;
        int labelColor = Draw.fade(this.enabled
                ? Draw.mix(Theme.text, Theme.textHover, this.hover)
                : Theme.textDisabled, alpha);

        // The switch owns the right end of the row, so the label gets whatever is
        // left of it and no more. Drawn at a fixed position it ran underneath the
        // switch and out the far side — "Инверсия мыши" and "Полноэкранный режим"
        // both did, since the English they were laid out against is shorter.
        float switchX = x2 - trailingInset();
        drawFitting(font, this.displayString, x1 + 10.0F,
                y1 + (this.height - 8) / 2.0F, switchX - 6.0F - (x1 + 10.0F), labelColor);

        // A switch that cannot be flipped is drawn faint rather than absent: the setting
        // is still there, it is simply not available yet.
        drawSwitch(switchX, (y1 + y2) / 2.0F, alpha * (this.enabled ? 1.0F : 0.4F));
    }

    /** The switch owns the right end of the row; the label stops short of it. */
    @Override
    protected float trailingInset() {
        return 10.0F + SWITCH_WIDTH;
    }

    /**
     * A vanilla {@link GameSettings} option.
     *
     * The game hands out its caption as one "Label: Value" string, so the two are split
     * back apart here — the widget aligns them at opposite ends of the row and cannot
     * do that with them stuck together.
     */
    private static final class VanillaSource implements Source {

        private final GameSettings.Options option;

        VanillaSource(GameSettings.Options option) {
            this.option = option;
        }

        @Override
        public String label() {
            return I18n.format(this.option.getTranslation(), new Object[0]);
        }

        @Override
        public String value() {
            String full = Minecraft.getMinecraft().gameSettings.getKeyBinding(this.option);
            String label = label();
            String remainder = full.startsWith(label) ? full.substring(label.length()) : full;
            if (remainder.startsWith(":")) {
                remainder = remainder.substring(1);
            }
            return remainder.trim();
        }

        @Override
        public boolean toggle() {
            return this.option.isBoolean();
        }

        @Override
        public boolean on() {
            return Minecraft.getMinecraft().gameSettings.getOptionOrdinalValue(this.option);
        }

        @Override
        public void cycle() {
            Minecraft.getMinecraft().gameSettings.setOptionValue(this.option, 1);
        }

        /** Vanilla has no notion of an option being temporarily unavailable. */
        @Override
        public boolean available() {
            return true;
        }
    }

    private void drawSwitch(float x, float cy, float alpha) {
        float w = SWITCH_WIDTH;
        float h = 10.0F;
        float y1 = cy - h / 2.0F;
        float y2 = cy + h / 2.0F;

        int trackOff = Draw.withAlpha(Theme.background, 0.8F);
        int trackOn = Draw.withAlpha(Theme.accent, 0.45F);
        Draw.roundedRect(x, y1, x + w, y2, 1.0F, Draw.fade(Draw.mix(trackOff, trackOn, this.switchPos), alpha));
        Draw.border(x, y1, x + w, y2, 1.0F,
                Draw.fade(Draw.mix(Theme.panelBorder, Theme.accent, this.switchPos), alpha));

        // A square knob, not a round one. Every other edge in this interface is a
        // hard corner — panels, tiles, checkboxes, the sliders — so a circle sliding
        // in a rectangular track was the one shape that did not belong.
        float inset = 1.5F;
        float knobW = (w - inset * 2.0F) * 0.42F;
        float knobH = h - inset * 2.0F;
        float knobX = x + inset + (w - inset * 2.0F - knobW) * this.switchPos;
        float knobY = cy - knobH / 2.0F;
        int knobColor = Draw.mix(Theme.textDim, Theme.accent, this.switchPos);

        if (this.switchPos > 0.05F) {
            Draw.glow(knobX, knobY, knobX + knobW, knobY + knobH, 4.0F,
                    Draw.fade(Theme.accent, 0.28F * this.switchPos * alpha), 3);
        }
        Draw.rect(knobX, knobY, knobX + knobW, knobY + knobH, Draw.fade(knobColor, alpha));
    }
}

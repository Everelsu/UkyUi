package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Themed replacement for {@code GuiOptionButton}. Cycles a
 * {@link GameSettings} option on click; boolean options additionally render an
 * animated pill switch instead of the "ON"/"OFF" word, which reads much faster
 * in a long options list.
 */
public class MenuOptionButton extends MenuButton {

    /** Track width of the pill switch; the label is budgeted around it. */
    private static final float SWITCH_WIDTH = 22.0F;

    private final GameSettings.Options option;
    private final boolean isToggle;
    private float switchPos;

    public MenuOptionButton(int id, int x, int y, int width, int height, GameSettings.Options option) {
        super(id, x, y, width, height, "", Style.NORMAL);
        this.option = option;
        this.isToggle = option.getEnumBoolean();
        refresh();
        // Start the switch where it belongs so it does not slide on first draw.
        this.switchPos = isOn() ? 1.0F : 0.0F;
    }

    public GameSettings.Options getOption() {
        return this.option;
    }

    /** Applies the next value and re-reads the label. */
    public void cycle() {
        Minecraft.getMinecraft().gameSettings.setOptionValue(this.option, 1);
        refresh();
    }

    private void refresh() {
        Minecraft mc = Minecraft.getMinecraft();
        // getKeyBinding returns "Label: Value"; split so the two can be aligned apart.
        String full = mc.gameSettings.getKeyBinding(this.option);
        String label = I18n.format(this.option.getEnumString(), new Object[0]);
        this.displayString = label;
        String remainder = full.startsWith(label) ? full.substring(label.length()) : full;
        if (remainder.startsWith(":")) {
            remainder = remainder.substring(1);
        }
        value(remainder.trim());
    }

    private boolean isOn() {
        return Minecraft.getMinecraft().gameSettings.getOptionOrdinalValue(this.option);
    }

    @Override
    public void advance(float deltaSeconds, float screenFade) {
        super.advance(deltaSeconds, screenFade);
        if (this.isToggle) {
            this.switchPos = Ease.approach(this.switchPos, isOn() ? 1.0F : 0.0F, 0.045F, deltaSeconds);
        }
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.isToggle) {
            super.drawButton(mc, mouseX, mouseY);
            return;
        }
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

        float slide = (1.0F - this.entrance) * 14.0F;
        float x1 = this.xPosition - slide;
        float y1 = this.yPosition + this.press;
        float x2 = x1 + this.width;
        float y2 = y1 + this.height;

        drawPanel(x1, y1, x2, y2, alpha);

        FontRenderer font = mc.fontRenderer;
        int labelColor = Draw.fade(Draw.mix(Theme.text, Theme.textHover, this.hover), alpha);

        // The switch owns the right end of the row, so the label gets whatever is
        // left of it and no more. Drawn at a fixed position it ran underneath the
        // switch and out the far side — "Инверсия мыши" and "Полноэкранный режим"
        // both did, since the English they were laid out against is shorter.
        float switchX = x2 - trailingInset();
        drawFitting(font, this.displayString, x1 + 10.0F,
                y1 + (this.height - 8) / 2.0F, switchX - 6.0F - (x1 + 10.0F), labelColor);

        drawSwitch(switchX, (y1 + y2) / 2.0F, alpha);
    }

    /** The switch owns the right end of the row; the label stops short of it. */
    @Override
    protected float trailingInset() {
        return 10.0F + SWITCH_WIDTH;
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

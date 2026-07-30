package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.GameSettings;

/**
 * Themed replacement for {@code GuiOptionSlider}: a thin track with an
 * accent fill and a knob that grows on hover. Writes straight through to
 * {@link GameSettings}, so it stays in sync with anything else that reads the
 * same option.
 */
public class MenuSlider extends MenuButton {

    private final GameSettings.Options option;
    private float normalized;
    private boolean dragging;
    private float knobScale;

    public MenuSlider(int id, int x, int y, int width, int height, GameSettings.Options option) {
        super(id, x, y, width, height, "", Style.NORMAL);
        this.option = option;
        Minecraft mc = Minecraft.getMinecraft();
        this.normalized = option.normalizeValue(mc.gameSettings.getOptionFloatValue(option));
        this.displayString = mc.gameSettings.getKeyBinding(option);
    }

    @Override
    public void advance(float deltaSeconds, float screenFade) {
        super.advance(deltaSeconds, screenFade);
        float target = (this.field_146123_n || this.dragging) ? 1.0F : 0.0F;
        this.knobScale = Ease.approach(this.knobScale, target, 0.05F, deltaSeconds);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;

        if (this.dragging) {
            updateFromMouse(mc, mouseX);
        }

        float alpha = this.entrance * this.screenFade;
        if (alpha <= 0.01F) {
            return;
        }

        float slide = (1.0F - this.entrance) * 14.0F;
        float x1 = this.xPosition - slide;
        float y1 = this.yPosition;
        float x2 = x1 + this.width;
        float y2 = y1 + this.height;

        drawPanel(x1, y1, x2, y2, alpha);

        // Both of these used to be fixed offsets from opposite edges, which is fine
        // at a comfortable row height and collides at a tight one: on a maximised
        // window the settings grid drops to 14-unit rows and the track ran straight
        // through the middle of the label. Deriving them from the actual height keeps
        // them apart at any size.
        boolean tight = this.height < 20;
        float trackInset = 10.0F;
        float trackX1 = x1 + trackInset;
        float trackX2 = x2 - trackInset;
        float trackH = 2.0F;
        float trackY = y2 - (tight ? 3.0F : 6.0F);

        FontRenderer font = mc.fontRenderer;
        int labelColor = Draw.fade(Draw.mix(Theme.text, Theme.textHover, this.hover), alpha);
        // Text is 8 units tall; sit it just above the track with a unit to spare.
        float labelY = Math.max(y1 + 1.0F, trackY - 10.0F);
        drawFittedCaption(font, this.displayString, (int) (x1 + 10), (int) labelY,
                this.width - 20, labelColor, alpha);

        Draw.rect(trackX1, trackY, trackX2, trackY + trackH, Draw.fade(Theme.trackFill, alpha));
        float fillX = trackX1 + (trackX2 - trackX1) * this.normalized;
        Draw.gradientH(trackX1, trackY, fillX, trackY + trackH,
                Draw.fade(Theme.accentAlt, alpha), Draw.fade(Theme.accent, alpha));

        // Square knob, matching the toggles and everything else here.
        float knobHalf = 2.5F + this.knobScale * 1.5F;
        float knobCy = trackY + trackH / 2.0F;
        if (this.knobScale > 0.01F) {
            Draw.glow(fillX - knobHalf, knobCy - knobHalf, fillX + knobHalf, knobCy + knobHalf,
                    4.0F, Draw.fade(Theme.accent, 0.28F * this.knobScale * alpha), 3);
        }
        Draw.rect(fillX - knobHalf, knobCy - knobHalf, fillX + knobHalf, knobCy + knobHalf,
                Draw.fade(Theme.accent, alpha));
    }

    private void updateFromMouse(Minecraft mc, int mouseX) {
        float raw = (float) (mouseX - (this.xPosition + 10)) / (float) (this.width - 20);
        this.normalized = Ease.clamp01(raw);
        float value = this.option.denormalizeValue(this.normalized);
        mc.gameSettings.setOptionFloatValue(this.option, value);
        // Re-read: the option may snap to a step, and the label must show the snapped value.
        this.normalized = this.option.normalizeValue(value);
        this.displayString = mc.gameSettings.getKeyBinding(this.option);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!this.enabled || !this.visible) {
            return false;
        }
        boolean hit = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
        if (hit) {
            this.dragging = true;
            updateFromMouse(mc, mouseX);
        }
        return hit;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
        this.pressed = false;
    }

    /** Sliders act on drag, so the click sound would fire continuously. */
    @Override
    public void func_146113_a(net.minecraft.client.audio.SoundHandler soundHandler) {
    }

    /**
     * Draws a "Label: Value" caption inside {@code available} units.
     *
     * The game hands sliders their caption as a single string with the value on the
     * end, and a translated caption is routinely wider than the row it was laid out
     * against — "Наибольшая частота кадров: 120 fps" ran clean out of its column and
     * across the one beside it. Trimming the whole string would eat the value, which
     * is the one part that changes, so the caption is split at its colon and the
     * label alone gives up the space.
     */
    private void drawFittedCaption(FontRenderer font, String text, int x, int y,
                                   int available, int colour, float alpha) {
        if (available <= 0) {
            return;
        }
        if (font.getStringWidth(text) <= available) {
            font.drawString(text, x, y, colour);
            return;
        }

        int split = text.lastIndexOf(": ");
        if (split < 0) {
            drawFitting(font, text, x, y, available, colour);
            return;
        }

        String value = font.trimStringToWidth(text.substring(split + 2), available);
        int valueWidth = font.getStringWidth(value);

        drawFitting(font, text.substring(0, split + 1), x, y,
                available - valueWidth - 6, colour);
        font.drawString(value, x + available - valueWidth, y,
                Draw.fade(Draw.mix(Theme.textDim, Theme.accent, this.hover), alpha));
    }

}

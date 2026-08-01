package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.GameSettings;

/**
 * Themed replacement for {@code GuiOptionSlider}: a thin track with an
 * accent fill and a knob that grows on hover. Writes straight through to the value it
 * is given, so it stays in sync with anything else reading the same option.
 *
 * <p>What that value is lives behind {@link Source}. Vanilla's {@link GameSettings}
 * options are one implementation; another mod's settings, reached by reflection and so
 * unnameable at compile time, are another — and a range is a range, so both are drawn
 * by this.
 */
public class MenuSlider extends MenuButton {

    /**
     * A value that runs from one end of a track to the other.
     *
     * Positions are normalised to 0..1 rather than passed raw: the widget has no
     * business knowing whether it is moving a percentage, a chunk count or a frame
     * limit, and the source is the only thing that knows what its own steps are.
     */
    public interface Source {

        /** "Label: value", the way the game captions its own sliders. */
        String caption();

        /** Where the knob sits, 0..1. */
        float normalized();

        /**
         * Moves the value. The source is expected to snap to its own step, and the
         * widget re-reads {@link #normalized} afterwards so the knob shows where the
         * value actually landed rather than where the pointer was.
         */
        void setNormalized(float t);

        /** False greys the row out and stops it being dragged. */
        boolean available();
    }

    private final Source source;
    private float normalized;
    private boolean dragging;
    private float knobScale;

    public MenuSlider(int id, int x, int y, int width, int height, GameSettings.Options option) {
        this(id, x, y, width, height, new VanillaSource(option));
    }

    public MenuSlider(int id, int x, int y, int width, int height, Source source) {
        super(id, x, y, width, height, "", Style.NORMAL);
        this.source = source;
        this.normalized = source.normalized();
        this.displayString = source.caption();
    }

    @Override
    public void advance(float deltaSeconds, float screenFade) {
        // Re-read every frame: a slider's own value can be moved by another setting, and
        // whether it may be dragged at all frequently depends on one. Not while
        // dragging, though — the pointer is the authority then, and re-reading would
        // fight it. Before the base class advances, which decides this frame's hover.
        this.enabled = this.source.available();
        if (!this.dragging) {
            this.normalized = this.source.normalized();
            this.displayString = this.source.caption();
        }
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
        int labelColor = Draw.fade(this.enabled
                ? Draw.mix(Theme.text, Theme.textHover, this.hover)
                : Theme.textDisabled, alpha);
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
        this.source.setNormalized(Ease.clamp01(raw));
        // Re-read: the option may snap to a step, and both the knob and the label must
        // show the snapped value rather than the pointer's position.
        this.normalized = this.source.normalized();
        this.displayString = this.source.caption();
    }

    /** A vanilla {@link GameSettings} option, which normalises its own range. */
    private static final class VanillaSource implements Source {

        private final GameSettings.Options option;

        VanillaSource(GameSettings.Options option) {
            this.option = option;
        }

        @Override
        public String caption() {
            return Minecraft.getMinecraft().gameSettings.getKeyBinding(this.option);
        }

        @Override
        public float normalized() {
            Minecraft mc = Minecraft.getMinecraft();
            return this.option.normalizeValue(mc.gameSettings.getOptionFloatValue(this.option));
        }

        @Override
        public void setNormalized(float t) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.gameSettings.setOptionFloatValue(this.option, this.option.denormalizeValue(t));
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!acceptsInput()) {
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

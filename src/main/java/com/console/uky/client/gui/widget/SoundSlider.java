package com.console.uky.client.gui.widget;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Volume slider for one {@link SoundCategory}.
 *
 * Separate from {@link MenuSlider} because sound levels are not
 * {@code GameSettings.Options} — they live behind {@code getSoundLevel} and
 * {@code setSoundLevel} and are keyed by category, so none of the option
 * normalise/denormalise machinery applies. The drawing deliberately matches
 * MenuSlider so the two read as the same control.
 */
public class SoundSlider extends MenuButton {

    private final SoundCategory category;
    private final GameSettings settings;
    private boolean dragging;
    private float knobScale;

    public SoundSlider(int id, int x, int y, int width, int height,
                       SoundCategory category, GameSettings settings) {
        super(id, x, y, width, height, "", Style.NORMAL);
        this.category = category;
        this.settings = settings;
    }

    private float level() {
        return this.settings.getSoundLevel(this.category);
    }

    /** "Music: 70%", or "Music: OFF" at zero, which is what the value means there. */
    private String label() {
        String name = I18n.format("soundCategory." + this.category.getCategoryName(),
                new Object[0]);
        float value = level();
        String shown = value <= 0.0F
                ? I18n.format("options.off", new Object[0])
                : ((int) (value * 100.0F) + "%");
        return name + ": " + shown;
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
            updateFromMouse(mouseX);
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

        // Same height-derived placement as MenuSlider: a fixed offset from each edge
        // collides once the row drops to 14 units on a maximised window.
        boolean tight = this.height < 20;
        float trackX1 = x1 + 10.0F;
        float trackX2 = x2 - 10.0F;
        float trackH = 2.0F;
        float trackY = y2 - (tight ? 3.0F : 6.0F);

        FontRenderer font = mc.fontRenderer;
        int labelColor = Draw.fade(Draw.mix(Theme.text, Theme.textHover, this.hover), alpha);
        float labelY = Math.max(y1 + 1.0F, trackY - 10.0F);
        font.drawString(font.trimStringToWidth(label(), this.width - 20),
                (int) (x1 + 10), (int) labelY, labelColor);

        Draw.rect(trackX1, trackY, trackX2, trackY + trackH, Draw.fade(Theme.trackFill, alpha));
        float fillX = trackX1 + (trackX2 - trackX1) * Ease.clamp01(level());
        Draw.gradientH(trackX1, trackY, fillX, trackY + trackH,
                Draw.fade(Theme.accentAlt, alpha), Draw.fade(Theme.accent, alpha));

        float knobHalf = 2.5F + this.knobScale * 1.5F;
        float knobCy = trackY + trackH / 2.0F;
        if (this.knobScale > 0.01F) {
            Draw.glow(fillX - knobHalf, knobCy - knobHalf, fillX + knobHalf, knobCy + knobHalf,
                    4.0F, Draw.fade(Theme.accent, 0.28F * this.knobScale * alpha), 3);
        }
        Draw.rect(fillX - knobHalf, knobCy - knobHalf, fillX + knobHalf, knobCy + knobHalf,
                Draw.fade(Theme.accent, alpha));
    }

    private void updateFromMouse(int mouseX) {
        float raw = (float) (mouseX - (this.xPosition + 10)) / (float) (this.width - 20);
        this.settings.setSoundLevel(this.category, Ease.clamp01(raw));
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
            updateFromMouse(mouseX);
        }
        return hit;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
        this.pressed = false;
    }

    /** Dragging would fire the click sound every frame. */
    @Override
    public void func_146113_a(net.minecraft.client.audio.SoundHandler soundHandler) {
    }
}

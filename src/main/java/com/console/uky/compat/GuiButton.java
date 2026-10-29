package com.console.uky.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;

/**
 * 1.7.10's GuiButton: an id, a rectangle and a label, drawn and clicked by the screen
 * that holds it. Not a 1.21 widget — {@link GuiScreen} drives it itself — because the
 * screens built on it decide everything about it, focus and narration included.
 */
public class GuiButton extends Gui {

    public int id;
    public int xPosition;
    public int yPosition;
    public int width;
    public int height;
    public String displayString;
    public boolean enabled = true;
    public boolean visible = true;
    /** Hovered, as of the last draw. */
    protected boolean field_146123_n;
    public int packedFGColour;

    public GuiButton(int id, int x, int y, String text) {
        this(id, x, y, 200, 20, text);
    }

    public GuiButton(int id, int x, int y, int width, int height, String text) {
        this.id = id;
        this.xPosition = x;
        this.yPosition = y;
        this.width = width;
        this.height = height;
        this.displayString = text;
    }

    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        int fill = !this.enabled ? 0x80202020 : (this.field_146123_n ? 0xC0505050 : 0xC0303030);
        drawRect(this.xPosition, this.yPosition, this.xPosition + this.width,
                this.yPosition + this.height, fill);
        int colour = !this.enabled ? 0xA0A0A0 : (this.field_146123_n ? 0xFFFFA0 : 0xE0E0E0);
        drawCenteredString(FontRenderer.get(), this.displayString, this.xPosition + this.width / 2,
                this.yPosition + (this.height - 8) / 2, colour);
    }

    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return this.enabled && this.visible && mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
    }

    public void mouseReleased(int mouseX, int mouseY) {
    }

    /** playPressSound. */
    public void func_146113_a(SoundManager sounds) {
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public boolean func_146115_a() {
        return this.field_146123_n;
    }

    public int getButtonWidth() {
        return this.width;
    }
}

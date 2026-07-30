package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import com.console.uky.client.world.WorldPreviews;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;

import java.util.List;

/**
 * Renaming and deleting a world, in the same language as the world list.
 *
 * Both actions start from a tile, so both are answered with one: the world's own
 * picture, at the size it had in the grid, with the question underneath. Vanilla
 * puts up an anonymous grey text box for one and a wall of text for the other,
 * and neither tells you which save you are about to act on — which is exactly the
 * thing worth being sure of before deleting something.
 *
 * The two modes share everything but the control under the card and the colour of
 * the confirm button.
 */
public class GuiWorldPromptScreen extends MenuScreen {

    public enum Mode {
        RENAME, DELETE
    }

    private static final int ID_CONFIRM = 1;
    private static final int ID_CANCEL = 2;

    private final Mode mode;
    private final String folder;
    private String worldName = "";

    private GuiTextField nameField;

    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;

    private float confirmHover;
    private float cancelHover;
    private int mouseX;
    private int mouseY;

    private boolean done;

    public GuiWorldPromptScreen(GuiScreen parent, Mode mode, String folder) {
        super(parent);
        this.mode = mode;
        this.folder = folder;
    }

    @Override
    protected boolean isVoid() {
        // Reached from the world list, which is already past the horizon.
        return true;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    public void initGui() {
        super.initGui();
        if (this.mode == Mode.RENAME) {
            org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        }
    }

    @Override
    public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    protected void buildLayout() {
        this.worldName = readWorldName();

        this.cardWidth = clamp((int) (this.width * 0.42F), 180, 300);
        this.cardHeight = this.cardWidth * 9 / 16;
        this.cardX = (this.width - this.cardWidth) / 2;

        // The whole block — title, card, control, buttons — centred as one, so it
        // sits in the middle of the screen at any size.
        int blockHeight = 20 + this.cardHeight + 14 + 20 + 12 + 20;
        this.cardY = Math.max(46, (this.height - blockHeight) / 2 + 20);

        String previous = this.nameField == null ? this.worldName : this.nameField.getText();
        this.nameField = new GuiTextField(this.fontRendererObj,
                this.cardX + 1, this.cardY + this.cardHeight + 18, this.cardWidth - 2, 16);
        this.nameField.setMaxStringLength(32);
        this.nameField.setEnableBackgroundDrawing(false);
        this.nameField.setText(previous);
        this.nameField.setFocused(true);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private String readWorldName() {
        try {
            WorldInfo info = this.mc.getSaveLoader().getWorldInfo(this.folder);
            if (info != null && info.getWorldName() != null) {
                return info.getWorldName();
            }
        } catch (Exception e) {
            UkyUI.LOGGER.warn("Could not read the name of world '{}'", this.folder, e);
        }
        return this.folder;
    }

    /** Y of the button row, which sits under the card's control strip. */
    private int buttonRowY() {
        return this.cardY + this.cardHeight + (this.mode == Mode.RENAME ? 46 : 34);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        drawTitle();
        drawCard();
        if (this.mode == Mode.RENAME) {
            drawNameField();
        } else {
            drawWarning();
        }
        drawButtons();
    }

    private void drawTitle() {
        String title = this.mode == Mode.RENAME
                ? I18n.format("selectWorld.renameTitle", new Object[0])
                : I18n.format("selectWorld.deleteQuestion", new Object[0]);
        int width = this.fontRendererObj.getStringWidth(title);
        this.fontRendererObj.drawString(title, (this.width - width) / 2, this.cardY - 22,
                Draw.withAlpha(Theme.text, this.fadeAlpha));
    }

    /** The same tile as in the list, so there is no doubt which world this is. */
    private void drawCard() {
        float x2 = this.cardX + this.cardWidth;
        float y2 = this.cardY + this.cardHeight;
        int accent = this.mode == Mode.DELETE ? Theme.danger : Theme.accent;

        Draw.rect(this.cardX, this.cardY, x2, y2, Draw.withAlpha(0x000000, 0.9F * this.fadeAlpha));

        ResourceLocation preview = WorldPreviews.texture(this.folder);
        if (preview != null) {
            // Dimmer than in the grid: here the picture identifies the world, it is
            // not the thing being chosen.
            int tint = this.mode == Mode.DELETE ? 0x6E5A5A : 0x8C8C8C;
            Draw.texture(preview, this.cardX, this.cardY, this.cardWidth, this.cardHeight,
                    Draw.withAlpha(tint, this.fadeAlpha));
        }

        Draw.gradientV(this.cardX, y2 - 20, x2, y2,
                Draw.withAlpha(0x000000, 0.0F), Draw.withAlpha(0x000000, 0.88F * this.fadeAlpha));
        String name = fit(this.worldName, this.cardWidth - 12);
        this.fontRendererObj.drawString(name, this.cardX + 6, (int) (y2 - 14),
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        Draw.border(this.cardX, this.cardY, x2, y2, 1.0F,
                Draw.withAlpha(accent, 0.85F * this.fadeAlpha));
        Draw.glow(this.cardX, this.cardY, x2, y2, 6.0F,
                Draw.withAlpha(accent, 0.22F * this.fadeAlpha), 4);

        if (this.mode == Mode.DELETE) {
            // A bin on the picture itself: the icon that was clicked to get here.
            float cx = this.cardX + this.cardWidth / 2.0F;
            float cy = this.cardY + this.cardHeight / 2.0F;
            Draw.circle(cx, cy, 18.0F, Draw.withAlpha(0x000000, 0.55F * this.fadeAlpha));
            Draw.ring(cx, cy, 18.0F, 1.5F, Draw.withAlpha(Theme.danger, 0.9F * this.fadeAlpha));
            Icons.trash(cx, cy, 18.0F, Draw.withAlpha(Theme.danger, this.fadeAlpha));
        }
    }

    private void drawNameField() {
        float x1 = this.nameField.xPosition - 1;
        float x2 = this.nameField.xPosition + this.nameField.getWidth() + 1;
        float y1 = this.nameField.yPosition - 3;
        float y2 = this.nameField.yPosition + 15;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(0x000000, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2,
                Draw.withAlpha(this.nameField.isFocused() ? Theme.accent : Theme.separator,
                        this.fadeAlpha));
        this.nameField.drawTextBox();
    }

    private void drawWarning() {
        // The vanilla string is a sentence fragment written to follow the world's
        // name ("'X' will be lost forever!"), so on its own it reads as a dangling
        // clause. Put the name back in front of it.
        String warning = "'" + this.worldName + "' "
                + I18n.format("selectWorld.deleteWarning", new Object[0]);
        List<?> lines = this.fontRendererObj.listFormattedStringToWidth(warning, this.cardWidth + 40);
        int y = this.cardY + this.cardHeight + 10;
        for (int i = 0; i < lines.size() && i < 2; i++) {
            String line = String.valueOf(lines.get(i));
            int width = this.fontRendererObj.getStringWidth(line);
            this.fontRendererObj.drawString(line, (this.width - width) / 2, y + i * 10,
                    Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
        }
    }

    private void drawButtons() {
        int gap = 8;
        int buttonWidth = (this.cardWidth - gap) / 2;
        int y = buttonRowY();

        boolean overConfirm = inside(this.cardX, y, buttonWidth, 20);
        boolean overCancel = inside(this.cardX + buttonWidth + gap, y, buttonWidth, 20);
        this.confirmHover = Ease.approach(this.confirmHover, overConfirm ? 1.0F : 0.0F,
                0.05F, this.delta);
        this.cancelHover = Ease.approach(this.cancelHover, overCancel ? 1.0F : 0.0F,
                0.05F, this.delta);

        int accent = this.mode == Mode.DELETE ? Theme.danger : Theme.accent;
        boolean enabled = canConfirm();
        float confirmAlpha = this.fadeAlpha * (enabled ? 1.0F : 0.4F);

        int fill = Draw.mix(accent, Theme.textHover, this.confirmHover * 0.2F);
        Draw.rect(this.cardX, y, this.cardX + buttonWidth, y + 20,
                Draw.withAlpha(fill, (0.85F + this.confirmHover * 0.15F) * confirmAlpha));
        if (enabled && this.confirmHover > 0.02F) {
            Draw.glow(this.cardX, y, this.cardX + buttonWidth, y + 20, 5.0F,
                    Draw.withAlpha(accent, 0.3F * this.confirmHover * this.fadeAlpha), 4);
        }
        centred(confirmLabel(), this.cardX, buttonWidth, y + 6,
                Draw.withAlpha(0x0B0B0E, confirmAlpha));

        int cancelX = this.cardX + buttonWidth + gap;
        Draw.rect(cancelX, y, cancelX + buttonWidth, y + 20,
                Draw.withAlpha(0x000000, (0.5F + this.cancelHover * 0.2F) * this.fadeAlpha));
        Draw.border(cancelX, y, cancelX + buttonWidth, y + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.cancelHover), this.fadeAlpha));
        centred(I18n.format("gui.cancel", new Object[0]), cancelX, buttonWidth, y + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.cancelHover),
                        this.fadeAlpha));
    }

    private String confirmLabel() {
        return this.mode == Mode.RENAME
                ? I18n.format("selectWorld.renameButton", new Object[0])
                : I18n.format("selectWorld.deleteButton", new Object[0]);
    }

    private void centred(String text, int x, int width, int y, int colour) {
        String trimmed = fit(text, width - 8);
        int w = this.fontRendererObj.getStringWidth(trimmed);
        this.fontRendererObj.drawString(trimmed, x + (width - w) / 2, y, colour);
    }

    private boolean inside(int x, int y, int width, int height) {
        return this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + height;
    }

    private boolean canConfirm() {
        return this.mode == Mode.DELETE || this.nameField.getText().trim().length() > 0;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (this.mode == Mode.RENAME) {
            this.nameField.mouseClicked(mouseX, mouseY, button);
        }

        int gap = 8;
        int buttonWidth = (this.cardWidth - gap) / 2;
        int y = buttonRowY();
        if (inside(this.cardX, y, buttonWidth, 20)) {
            confirm();
            return;
        }
        if (inside(this.cardX + buttonWidth + gap, y, buttonWidth, 20)) {
            cancel();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            cancel();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            confirm();
            return;
        }
        if (this.mode == Mode.RENAME) {
            this.nameField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void updateScreen() {
        if (this.mode == Mode.RENAME) {
            this.nameField.updateCursorCounter();
        }
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control here is drawn, not a GuiButton.
    }

    private void confirm() {
        if (this.done || !canConfirm()) {
            return;
        }
        this.done = true;

        ISaveFormat saveFormat = this.mc.getSaveLoader();
        if (this.mode == Mode.RENAME) {
            saveFormat.renameWorld(this.folder, this.nameField.getText().trim());
        } else {
            // flushCache first: the save format keeps the directory open, and on
            // Windows deleting it out from under that fails silently.
            saveFormat.flushCache();
            saveFormat.deleteWorldDirectory(this.folder);
            WorldPreviews.invalidate(this.folder);
        }
        this.mc.displayGuiScreen(this.parent);
    }

    private void cancel() {
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

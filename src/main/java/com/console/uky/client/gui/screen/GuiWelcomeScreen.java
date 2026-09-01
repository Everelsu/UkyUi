package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * Shown once, the first time the title screen is reached.
 *
 * It used to open the settings screen instead, which is a worse thing to arrive at
 * than it sounds: a wall of forty rows across four tabs, no explanation of what any
 * of it belongs to, and the one setting that actually matters on a first run buried
 * among the rest. A player who has just watched an unfamiliar menu appear needs to
 * be told what it is and asked one question.
 *
 * <p>That question is the graphics preset. Everything else in this mod is taste and
 * can be found later; the preset is the only setting that decides whether the menu
 * runs at all on the machine in front of it, and it has to be answered before the
 * player forms an opinion about the menu from a version of it that stutters.
 */
public class GuiWelcomeScreen extends MenuScreen {

    private static final int ID_PRESET_BASE = 400;
    private static final int ID_SETTINGS = 200;
    private static final int ID_DONE = 201;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    /** Eases in behind the preset row when the choice changes, so it is not a hard cut. */
    private float pickFlash;

    public GuiWelcomeScreen(GuiScreen parent) {
        super(parent);
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.60F), 340);
        this.panelX1 = Math.max(12, (int) (this.width * 0.08F));
        this.panelX2 = this.panelX1 + panelWidth;

        int panelHeight = Math.min(this.height - 40, 210);
        this.panelY1 = Math.max(14, (this.height - panelHeight) / 2);
        this.panelY2 = this.panelY1 + panelHeight;

        int inner = panelWidth - 28;
        int x = this.panelX1 + 14;

        // The presets across one row, cheapest on the left, so the row reads as a
        // scale rather than as four unrelated buttons.
        String[] levels = Quality.LEVELS;
        int gap = 4;
        int cellWidth = (inner - gap * (levels.length - 1)) / levels.length;
        int presetY = this.panelY1 + 96;
        for (int i = 0; i < levels.length; i++) {
            MenuButton button = new MenuButton(ID_PRESET_BASE + i,
                    x + i * (cellWidth + gap), presetY, cellWidth, 20,
                    I18n.format("uky.graphics." + levels[i], new Object[0]),
                    levels[i].equals(Quality.current())
                            ? MenuButton.Style.PRIMARY
                            : MenuButton.Style.NORMAL);
            button.entrance(0.04F + i * 0.03F);
            this.buttonList.add(button);
        }

        MenuButton settings = new MenuButton(ID_SETTINGS, x, this.panelY2 - 56, inner, 20,
                I18n.format("uky.welcome.settings", new Object[0]), MenuButton.Style.NORMAL);
        settings.entrance(0.18F);
        this.buttonList.add(settings);

        MenuButton done = new MenuButton(ID_DONE, x, this.panelY2 - 30, inner, 20,
                I18n.format("uky.welcome.start", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.22F);
        this.buttonList.add(done);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.76F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.48F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.26F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.pickFlash = Ease.approach(this.pickFlash, 0.0F, 0.12F, this.delta);

        Draw.rect(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.82F * this.fadeAlpha));
        Draw.gradientH(panelX1, panelY1, panelX1 + 2, panelY2,
                Draw.withAlpha(Theme.accent, (0.6F + this.pickFlash * 0.4F) * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(panelX1, panelY1, panelX2, panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.09F * this.fadeAlpha));

        int x = panelX1 + 14;
        int wrapWidth = panelX2 - 14 - x;

        // The pack's own name, not the mod's: the player is being welcomed to what
        // they installed, and they have never heard of this interface by name.
        String heading = UiConfig.title == null || UiConfig.title.isEmpty()
                ? I18n.format("uky.welcome.title", new Object[0])
                : UiConfig.title;
        this.fontRendererObj.drawString(fit(heading, wrapWidth), x, panelY1 + 16,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawWrapped(I18n.format("uky.welcome.body", new Object[0]), x, panelY1 + 34, wrapWidth);

        this.fontRendererObj.drawString(
                I18n.format("uky.welcome.quality", new Object[0]), x, panelY1 + 84,
                Draw.withAlpha(Theme.textDim, 0.85F * this.fadeAlpha));

        drawWrapped(I18n.format("uky.graphics." + Quality.current() + ".hint", new Object[0]),
                x, panelY1 + 120, wrapWidth);
    }

    /** Body copy, wrapped to the panel; the font renderer does the breaking. */
    @SuppressWarnings("unchecked")
    private void drawWrapped(String text, int x, int y, int width) {
        java.util.List<String> lines =
                this.fontRendererObj.listFormattedStringToWidth(text, Math.max(20, width));
        for (int i = 0; i < lines.size() && i < 3; i++) {
            this.fontRendererObj.drawString(lines.get(i), x, y + i * 10,
                    Draw.withAlpha(Theme.textDim, 0.8F * this.fadeAlpha));
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        if (button.id >= ID_PRESET_BASE && button.id < ID_PRESET_BASE + Quality.LEVELS.length) {
            UiConfig.setGraphics(Quality.LEVELS[button.id - ID_PRESET_BASE]);
            this.pickFlash = 1.0F;
            // Rebuilt rather than repainted: which button is the primary one is part
            // of the layout, and the backdrop may have just been switched off.
            relayout();
            return;
        }
        if (button.id == ID_SETTINGS) {
            switchTo(GuiSettingsScreen.onVideoTab(this, this.mc.gameSettings));
            return;
        }
        if (button.id == ID_DONE) {
            switchBack();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 || keyCode == 28) {
            switchBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

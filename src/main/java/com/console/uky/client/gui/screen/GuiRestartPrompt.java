package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.PackLook;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * Offered once a change of pack mode has played: the menus have changed, the mods
 * only will on the next launch, so — leave now?
 *
 * "Quit" rather than "restart": the game is started by the launcher, which is the
 * one that knows the account, the memory and the arguments. Starting it again from
 * in here would bypass all of that.
 */
public class GuiRestartPrompt extends MenuScreen {

    private static final int ID_QUIT = 1;
    private static final int ID_LATER = 2;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    public GuiRestartPrompt(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.6F), 300);
        int panelHeight = 104;
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = (this.height - panelHeight) / 2;
        this.panelY2 = this.panelY1 + panelHeight;

        int inner = panelWidth - 28;
        int half = (inner - 4) / 2;
        int x = this.panelX1 + 14;
        int y = this.panelY2 - 30;
        MenuButton quit = new MenuButton(ID_QUIT, x, y, half, 20,
                I18n.format("uky.restart.quit", new Object[0]), MenuButton.Style.PRIMARY);
        quit.entrance(0.05F);
        this.buttonList.add(quit);
        MenuButton later = new MenuButton(ID_LATER, x + half + 4, y, half, 20,
                I18n.format("uky.restart.later", new Object[0]), MenuButton.Style.NORMAL);
        later.entrance(0.08F);
        this.buttonList.add(later);
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        Draw.rect(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.86F * this.fadeAlpha));
        Draw.gradientH(panelX1, panelY1, panelX1 + 2, panelY2,
                Draw.withAlpha(Theme.accent, 0.7F * this.fadeAlpha), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(panelX1, panelY1, panelX2, panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.09F * this.fadeAlpha));

        int x = panelX1 + 14;
        int width = panelX2 - 14 - x;
        this.fontRendererObj.drawString(I18n.format("uky.restart.title",
                new Object[] {GuiSettingsScreen.modeName(PackLook.chosen())}), x, panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));
        @SuppressWarnings("unchecked")
        java.util.List<String> lines = this.fontRendererObj.listFormattedStringToWidth(
                I18n.format("uky.restart.body", new Object[0]), Math.max(20, width));
        for (int i = 0; i < lines.size() && i < 3; i++) {
            this.fontRendererObj.drawString(lines.get(i), x, panelY1 + 32 + i * 10,
                    Draw.withAlpha(Theme.textDim, 0.8F * this.fadeAlpha));
        }
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button.id == ID_QUIT) {
            this.mc.shutdown();
        } else if (button.id == ID_LATER) {
            switchBack();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            switchBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}

package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.handler.WorldCaptureHandler;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;

/**
 * Themed pause menu. Unlike the other screens this keeps the live world
 * visible behind a dark scrim instead of drawing the menu artwork — the player
 * should still see where they left off.
 */
public class GuiPauseScreen extends MenuScreen {

    private static final int ID_RETURN = 4;
    private static final int ID_OPTIONS = 0;
    private static final int ID_ACHIEVEMENTS = 5;
    private static final int ID_STATS = 6;
    private static final int ID_LAN = 7;
    private static final int ID_QUIT = 1;

    private static final int ROW_HEIGHT = 22;
    /**
     * Rows are close together here on purpose: this is one block of choices, not a
     * form. The gap between the two columns uses the same value.
     */
    private static final int ROW_GAP = 6;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    public GuiPauseScreen() {
        super(null);
    }

    @Override
    protected void buildLayout() {
        // Proportional rather than a flat 200: at GUI scale 1 on a large monitor a
        // fixed-width stack ends up as a postage stamp in the middle of the screen.
        int fullWidth = Math.max(200, Math.min((int) (this.width * 0.34F), 300));
        int halfWidth = (fullWidth - ROW_GAP) / 2;
        int left = this.width / 2 - fullWidth / 2;
        int right = left + halfWidth + ROW_GAP;

        int rows = 4;
        int stackHeight = rows * ROW_HEIGHT + (rows - 1) * ROW_GAP;
        int y = Math.max(64, (this.height - stackHeight) / 2 + 6);
        this.panelY1 = y - 34;

        int index = 0;

        add(new MenuButton(ID_RETURN, left, y, fullWidth, ROW_HEIGHT,
                I18n.format("menu.returnToGame", new Object[0]), MenuButton.Style.PRIMARY), index++);
        y += ROW_HEIGHT + ROW_GAP;

        add(new MenuButton(ID_ACHIEVEMENTS, left, y, halfWidth, ROW_HEIGHT,
                I18n.format("gui.advancements", new Object[0]), MenuButton.Style.NORMAL), index++);
        add(new MenuButton(ID_STATS, right, y, halfWidth, ROW_HEIGHT,
                I18n.format("gui.stats", new Object[0]), MenuButton.Style.NORMAL), index++);
        y += ROW_HEIGHT + ROW_GAP;

        // No Mod Options here. FML's in-game screen lists only mods that registered a
        // config GUI, which for this pack is nothing — it opened an empty panel.
        // Mod configs are reachable from Mods in the settings screen instead.
        add(new MenuButton(ID_OPTIONS, left, y, fullWidth, ROW_HEIGHT,
                I18n.format("menu.options", new Object[0]), MenuButton.Style.NORMAL), index++);
        y += ROW_HEIGHT + ROW_GAP;

        // Full width: it is alone on its row. It was left at half width when the
        // multiplayer entry that used to sit beside it was removed, which read as a
        // button that had lost its pair.
        MenuButton lan = new MenuButton(ID_LAN, left, y, fullWidth, ROW_HEIGHT,
                I18n.format("menu.shareToLan", new Object[0]), MenuButton.Style.NORMAL);
        lan.enabled = this.mc.isSingleplayer() && !this.mc.getIntegratedServer().getPublic();
        add(lan, index++);
        y += ROW_HEIGHT + ROW_GAP;

        String quitKey = this.mc.isIntegratedServerRunning() ? "menu.returnToMenu" : "menu.disconnect";
        add(new MenuButton(ID_QUIT, left, y, fullWidth, ROW_HEIGHT,
                I18n.format(quitKey, new Object[0]), MenuButton.Style.DANGER), index);

        this.panelX1 = left - 14;
        this.panelX2 = left + fullWidth + 14;
        this.panelY2 = y + ROW_HEIGHT + 14;
    }

    private void add(MenuButton button, int index) {
        button.entrance(0.05F + index * 0.045F);
        this.buttonList.add(button);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawBackdrop() {
        // Keep the world visible; just push it back with a scrim and a vertical wash.
        Draw.rect(0, 0, this.width, this.height, Draw.withAlpha(Theme.background, 0.60F * this.fadeAlpha));
        Draw.gradientV(0, 0, this.width, this.height * 0.35F,
                Draw.withAlpha(Theme.background, 0.55F * this.fadeAlpha),
                Draw.withAlpha(Theme.background, 0.0F));
        Draw.gradientV(0, this.height * 0.65F, this.width, this.height,
                Draw.withAlpha(Theme.background, 0.0F),
                Draw.withAlpha(Theme.background, 0.55F * this.fadeAlpha));
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        Draw.rect(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.72F * this.fadeAlpha));
        Draw.border(panelX1, panelY1, panelX2, panelY2, 1.0F,
                Draw.fade(Theme.separator, this.fadeAlpha));
        Draw.gradientH(panelX1, panelY1, panelX2, panelY1 + 1,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha));

        drawHeading(I18n.format("menu.game", new Object[0]), this.width / 2, panelY1 + 12);
    }

    @Override
    protected void drawOverlay() {
        // No grain or scanlines over live gameplay — only the vignette.
        Draw.vignette(this.width, this.height, 0.55F * this.fadeAlpha, 0xFF000000);
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        switch (button.id) {
            case ID_RETURN:
                // Animated out rather than cut: the world is visible behind this the
                // whole time, so vanishing on a frame boundary reads as the menu
                // being ripped away.
                closeWith(new Runnable() {
                    @Override
                    public void run() {
                        GuiPauseScreen.this.mc.displayGuiScreen(null);
                        GuiPauseScreen.this.mc.setIngameFocus();
                    }
                });
                break;
            case ID_OPTIONS:
                switchTo(new GuiSettingsScreen(GuiPauseScreen.this, mc.gameSettings));
                break;
            case ID_ACHIEVEMENTS:
                if (mc.player != null) {
                    switchTo(new GuiProgressScreen(GuiPauseScreen.this, mc.player.getStatFileWriter(), GuiProgressScreen.advancementsTab()));
                }
                break;
            case ID_STATS:
                if (mc.player != null) {
                    switchTo(new GuiProgressScreen(GuiPauseScreen.this, mc.player.getStatFileWriter(), GuiProgressScreen.statsTab()));
                }
                break;
            case ID_LAN:
                switchTo(new GuiShareScreen(GuiPauseScreen.this));
                break;
            case ID_QUIT:
                button.enabled = false;
                // Take the world's parting picture first — the handler draws one
                // clean frame and then runs this.
                WorldCaptureHandler.captureThen(new Runnable() {
                    @Override
                    public void run() {
                        quitToMenu();
                    }
                });
                break;
            default:
                break;
        }
    }

    /**
     * Escape closes through the same animation as the button.
     *
     * Vanilla's handler goes straight to {@code displayGuiScreen(null)}, and Escape is
     * how most people leave this screen — so without this the animation would only
     * ever be seen by whoever clicked.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            if (!isClosing()) {
                closeWith(new Runnable() {
                    @Override
                    public void run() {
                        GuiPauseScreen.this.mc.displayGuiScreen(null);
                        GuiPauseScreen.this.mc.setIngameFocus();
                    }
                });
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void quitToMenu() {
        // Our GuiOpenEvent handler swaps this for the UKY menu.
        quitTo(new GuiMainMenu());
    }

    /**
     * Leaves the world and lands on {@code destination}.
     *
     * The disconnect packet and {@code loadWorld(null)} are the whole of leaving; what
     * comes up afterwards is free, which is what lets the multiplayer entry go
     * straight to the server list rather than via the title screen.
     */
    private void quitTo(net.minecraft.client.gui.GuiScreen destination) {
        if (this.mc.world != null) {
            this.mc.world.sendQuittingDisconnectingPacket();
        }
        this.mc.loadWorld((WorldClient) null);
        this.mc.displayGuiScreen(destination);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

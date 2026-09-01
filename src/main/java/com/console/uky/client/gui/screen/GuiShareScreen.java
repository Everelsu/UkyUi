package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;

import java.util.List;
import net.minecraft.world.WorldSettings;

/**
 * Opening a world to the local network.
 *
 * Vanilla offers this as two cycling grey buttons whose labels are whole sentences,
 * on the dirt background, with no indication of what actually happens when you press
 * Start. Here the two choices are tiles like the world-creation screen's, so the
 * options are all visible at once rather than one-at-a-time behind a click, and the
 * consequence of each is written on it.
 *
 * <p>Kept on plain dark like the rest of the multiplayer path.
 */
public class GuiShareScreen extends MenuScreen {

    private static final String[] MODES = {"survival", "creative", "adventure"};

    private int selectedMode;
    private boolean allowCheats;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int modeY;
    private int modeHeight;
    private int cheatsY;
    private int actionY;

    private final float[] modeHover = new float[MODES.length];
    private float cheatsHover;
    private float startHover;
    private float cancelHover;

    private int mouseX;
    private int mouseY;
    private boolean started;

    public GuiShareScreen(GuiScreen parent) {
        super(parent);
    }

    /** Plain dark, matching the server list and the world list. */
    @Override
    protected boolean isVoid() {
        return true;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        // Default to the mode the world is already running, which is nearly always
        // what the guests should get. Read off the integrated server rather than the
        // player controller: 1.7.10 has a setter for the client's game type but no
        // getter, and the server's is the authoritative one anyway.
        if (this.mc.getIntegratedServer() != null) {
            GameType current = this.mc.getIntegratedServer().getGameType();
            if (current != null) {
                for (int i = 0; i < MODES.length; i++) {
                    if (MODES[i].equals(current.getName())) {
                        this.selectedMode = i;
                        break;
                    }
                }
            }
        }

        int panelWidth = Math.min((int) (this.width * 0.72F), 420);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;

        boolean cramped = this.height < 300;
        // Tall enough for a wrapped description rather than a truncated one. The
        // blurb used to be drawn as a single line cut off with an ellipsis — "Ищите
        // ресурсы, мас…" — which tells a player choosing a mode nothing at all, and
        // was the more visible for the panel having a band of empty space under it.
        this.modeHeight = cramped ? 34 : 62;
        int panelHeight = 52 + this.modeHeight + 12 + 22 + 16 + 20 + 20;
        this.panelY1 = Math.max(16, (this.height - panelHeight) / 2);
        this.panelY2 = this.panelY1 + panelHeight;

        this.modeY = this.panelY1 + 46;
        this.cheatsY = this.modeY + this.modeHeight + 12;
        this.actionY = this.panelY2 - 32;
    }

    private int columnWidth() {
        int gap = 6;
        return (this.panelX2 - 18 - (this.panelX1 + 18) - gap * (MODES.length - 1)) / MODES.length;
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        drawPanel();

        String title = I18n.format("lanServer.title", new Object[0]);
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, (this.width - titleWidth) / 2, this.panelY1 + 16,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawModes();
        drawCheats();
        drawActions();
    }

    private void drawPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.62F * a),
                Draw.withAlpha(Theme.background, 0.40F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(this.panelX1, this.panelY1, this.panelX2, this.panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.10F * a));
    }

    private void drawModes() {
        this.fontRenderer.drawString(
                I18n.format("selectWorld.gameMode", new Object[0]).toUpperCase(),
                this.panelX1 + 18, this.modeY - 12,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));

        int gap = 6;
        int width = columnWidth();
        for (int i = 0; i < MODES.length; i++) {
            int x = this.panelX1 + 18 + i * (width + gap);
            boolean over = inside(x, this.modeY, width, this.modeHeight);
            this.modeHover[i] = Ease.approach(this.modeHover[i], over ? 1.0F : 0.0F,
                    0.05F, this.delta);
            boolean selected = i == this.selectedMode;

            Draw.rect(x, this.modeY, x + width, this.modeY + this.modeHeight,
                    Draw.withAlpha(0x000000,
                            (0.55F + (selected ? 0.28F : this.modeHover[i] * 0.2F)) * this.fadeAlpha));
            if (selected) {
                Draw.rect(x, this.modeY, x + 2, this.modeY + this.modeHeight,
                        Draw.withAlpha(Theme.accent, this.fadeAlpha));
            }
            Draw.border(x, this.modeY, x + width, this.modeY + this.modeHeight, 1.0F,
                    selected
                            ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                            : Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, this.modeHover[i]),
                                    (0.13F + this.modeHover[i] * 0.6F) * this.fadeAlpha));

            String label = I18n.format("selectWorld.gameMode." + MODES[i], new Object[0]);
            this.fontRenderer.drawString(
                    fit(label, width - 16),
                    x + 8, this.modeY + 8,
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));

            if (this.modeHeight >= 40) {
                // Both halves, and wrapped to the card rather than cut at it. Vanilla
                // splits this text across two keys precisely because it does not fit on
                // one line, and taking only the first and trimming it threw away the
                // half that says what the mode actually does.
                drawBlurb(i, x + 8, this.modeY + 22, width - 16);
            }
        }
    }

    /**
     * A mode's description, wrapped into the card.
     *
     * The two vanilla keys are joined before wrapping rather than drawn as the two
     * lines they are: where they break is a decision made for vanilla's own card width,
     * and this card is a different width on every window. Joining them and re-flowing
     * puts the break where this layout needs it.
     *
     * <p>Lines past what the card can hold are dropped rather than drawn over its
     * edge. That is a real case at {@code cramped} sizes and in the longer
     * translations, and a description spilling onto the button below it would be worse
     * than a description that stops.
     */
    private void drawBlurb(int mode, int x, int y, int maxWidth) {
        String key = "selectWorld.gameMode." + MODES[mode];
        String blurb = I18n.format(key + ".line1", new Object[0]);
        String second = I18n.format(key + ".line2", new Object[0]);
        // A key with no translation comes back as the key itself; that is not a line.
        if (!second.isEmpty() && !second.startsWith(key)) {
            blurb = blurb + " " + second;
        }

        int lineHeight = this.fontRendererObj.FONT_HEIGHT;
        int room = (this.modeY + this.modeHeight - 6 - y) / lineHeight;
        if (room <= 0) {
            return;
        }
        List<?> lines = this.fontRendererObj.listFormattedStringToWidth(blurb, maxWidth);
        for (int i = 0; i < lines.size() && i < room; i++) {
            this.fontRendererObj.drawString(String.valueOf(lines.get(i)), x, y + i * lineHeight,
                    Draw.withAlpha(Theme.textDim, 0.7F * this.fadeAlpha));
        }
    }

    private void drawCheats() {
        int x1 = this.panelX1 + 18;
        int x2 = this.panelX2 - 18;
        boolean over = inside(x1, this.cheatsY, x2 - x1, 22);
        this.cheatsHover = Ease.approach(this.cheatsHover, over ? 1.0F : 0.0F, 0.05F, this.delta);

        Draw.rect(x1, this.cheatsY, x2, this.cheatsY + 22,
                Draw.withAlpha(0x000000, (0.5F + this.cheatsHover * 0.2F) * this.fadeAlpha));
        Draw.border(x1, this.cheatsY, x2, this.cheatsY + 22, 1.0F,
                this.allowCheats
                        ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                        : Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, this.cheatsHover),
                                (0.13F + this.cheatsHover * 0.6F) * this.fadeAlpha));

        float box = 12.0F;
        float bx = x1 + 6;
        float by = this.cheatsY + 5;
        Draw.border(bx, by, bx + box, by + box, 1.0F,
                this.allowCheats
                        ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                        : Draw.withAlpha(Theme.text, 0.3F * this.fadeAlpha));
        if (this.allowCheats) {
            Icons.check(bx + box / 2.0F, by + box / 2.0F, box * 0.8F,
                    Draw.withAlpha(Theme.accent, this.fadeAlpha));
        }

        String label = I18n.format("selectWorld.allowCommands", new Object[0]).trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        this.fontRenderer.drawString(label, (int) (bx + box + 8), this.cheatsY + 7,
                Draw.withAlpha(this.allowCheats ? Theme.text : Theme.textDim, this.fadeAlpha));
    }

    private void drawActions() {
        int gap = 8;
        int x1 = this.panelX1 + 18;
        int total = this.panelX2 - 18 - x1;
        int half = (total - gap) / 2;

        boolean overStart = inside(x1, this.actionY, half, 20);
        boolean overCancel = inside(x1 + half + gap, this.actionY, half, 20);
        this.startHover = Ease.approach(this.startHover, overStart ? 1.0F : 0.0F, 0.05F, this.delta);
        this.cancelHover = Ease.approach(this.cancelHover, overCancel ? 1.0F : 0.0F, 0.05F, this.delta);

        int fill = Draw.mix(Theme.accent, Theme.textHover, this.startHover * 0.2F);
        Draw.rect(x1, this.actionY, x1 + half, this.actionY + 20,
                Draw.withAlpha(fill, (0.62F + this.startHover * 0.18F) * this.fadeAlpha));
        if (this.startHover > 0.02F) {
            Draw.glow(x1, this.actionY, x1 + half, this.actionY + 20, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.3F * this.startHover * this.fadeAlpha), 4);
        }
        centred(I18n.format("lanServer.start", new Object[0]), x1, half, this.actionY + 6,
                Draw.withAlpha(0x0B0B0E, this.fadeAlpha));

        int cancelX = x1 + half + gap;
        Draw.rect(cancelX, this.actionY, cancelX + half, this.actionY + 20,
                Draw.withAlpha(0x000000, (0.5F + this.cancelHover * 0.2F) * this.fadeAlpha));
        Draw.border(cancelX, this.actionY, cancelX + half, this.actionY + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.cancelHover), this.fadeAlpha));
        centred(I18n.format("gui.cancel", new Object[0]), cancelX, half, this.actionY + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.cancelHover),
                        this.fadeAlpha));
    }

    private void centred(String text, int x, int width, int y, int colour) {
        String trimmed = fit(text, width - 8);
        int w = this.fontRenderer.getStringWidth(trimmed);
        this.fontRenderer.drawString(trimmed, x + (width - w) / 2, y, colour);
    }

    private boolean inside(int x, int y, int width, int height) {
        return this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + height;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        int gap = 6;
        int width = columnWidth();
        for (int i = 0; i < MODES.length; i++) {
            int x = this.panelX1 + 18 + i * (width + gap);
            if (inside(x, this.modeY, width, this.modeHeight)) {
                this.selectedMode = i;
                return;
            }
        }
        if (inside(this.panelX1 + 18, this.cheatsY, this.panelX2 - 18 - (this.panelX1 + 18), 22)) {
            this.allowCheats = !this.allowCheats;
            return;
        }

        int x1 = this.panelX1 + 18;
        int half = (this.panelX2 - 18 - x1 - 8) / 2;
        if (inside(x1, this.actionY, half, 20)) {
            start();
            return;
        }
        if (inside(x1 + half + 8, this.actionY, half, 20)) {
            switchBack();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            switchBack();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            start();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Opens the world and reports the port in chat, exactly as vanilla does — the
     * port is the one thing a guest actually needs, so losing it would make this
     * screen worse than the one it replaces.
     */
    private void start() {
        if (this.started || this.mc.getIntegratedServer() == null) {
            return;
        }
        this.started = true;
        this.mc.displayGuiScreen(null);

        String port = this.mc.getIntegratedServer().shareToLAN(
                GameType.getByName(MODES[this.selectedMode]), this.allowCheats);
        String message = port != null
                ? I18n.format("commands.publish.started", new Object[]{port})
                : I18n.format("commands.publish.failed", new Object[0]);
        this.mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message));
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control here is drawn, not a GuiButton.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

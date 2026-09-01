package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * The kicked/lost-connection screen.
 *
 * Unlike the connecting screen there is nothing to delegate here — by this point the
 * connection is already gone and the screen is purely a message and a way out — so
 * this is a straight replacement rather than a wrapper.
 *
 * <p>The reason is wrapped to the panel instead of vanilla's fixed width, because a
 * kick message is arbitrary server-authored text and the interesting ones are long.
 * Plain dark, matching the rest of the multiplayer path.
 */
public class GuiDisconnectedScreen extends GuiScreen {

    private static final int ID_BACK = 0;

    private final GuiScreen parent;
    private final String heading;
    private final ITextComponent reason;

    private List<?> lines;
    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    /** Size the layout above was computed for; -1 until it has been. */
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    private long lastFrameNanos = System.nanoTime();
    private float fade;

    private float backHover;
    private int mouseX;
    private int mouseY;

    public GuiDisconnectedScreen(GuiScreen parent, String heading, ITextComponent reason) {
        this.parent = parent;
        this.heading = heading == null ? "" : heading;
        this.reason = reason;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        layout();
    }

    /**
     * Lays the panel out, if that has not already happened at this size.
     *
     * Called from the draw and from the click rather than only from {@code initGui},
     * because {@code initGui} is not guaranteed to run at all: Forge lets any mod
     * cancel {@code GuiScreenEvent.InitGuiEvent.Pre}, and
     * {@code GuiScreen.setWorldAndResolution} then fills in the width, the height and
     * the font renderer and skips it. This screen came up on a lost connection with
     * {@code lines} still null and took the game down on its first frame — and the
     * crash report named this mod, since this mod is what was drawing.
     *
     * <p>The layout depends on nothing but the size and the kick message, so
     * recomputing it whenever the size has changed is both the fix and the resize
     * handling, and it costs two integer comparisons a frame.
     */
    private void ensureLayout() {
        if (this.laidOutWidth != this.width || this.laidOutHeight != this.height) {
            layout();
        }
    }

    private void layout() {
        this.laidOutWidth = this.width;
        this.laidOutHeight = this.height;

        int panelWidth = Math.min((int) (this.width * 0.66F), 380);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;

        String text = this.reason == null ? "" : this.reason.getFormattedText();
        // Never below a width the wrapper can work with: a tiny window would
        // otherwise ask it to fit text into nothing.
        this.lines = font().listFormattedStringToWidth(text, Math.max(20, panelWidth - 36));

        int body = Math.max(1, this.lines.size()) * 10;
        int panelHeight = 44 + body + 46;
        this.panelY1 = Math.max(20, (this.height - panelHeight) / 2);
        this.panelY2 = this.panelY1 + panelHeight;
    }

    /**
     * The font renderer, from the game if this screen was never handed one.
     *
     * Same reason as {@link #ensureLayout}: {@code fontRendererObj} is assigned in
     * {@code setWorldAndResolution}, and a screen can reach a draw without that
     * having happened in the order it expects.
     */
    private net.minecraft.client.gui.FontRenderer font() {
        return this.fontRendererObj != null
                ? this.fontRendererObj
                : net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
    }

    private int buttonY() {
        return this.panelY2 - 32;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ensureLayout();
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        long now = System.nanoTime();
        float delta = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;
        this.fade = Ease.approach(this.fade, 1.0F, 0.09F, delta);

        Draw.rect(0, 0, this.width, this.height, Theme.background);

        float a = this.fade;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.62F * a),
                Draw.withAlpha(Theme.background, 0.40F * a));
        // A red rail rather than the usual gold: this screen is a failure, and it
        // should be legible as one before the text is read.
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.danger, 0.85F * a), Draw.withAlpha(Theme.danger, 0.0F));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.border(this.panelX1, this.panelY1, this.panelX2, this.panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.10F * a));

        int headingWidth = font().getStringWidth(this.heading);
        font().drawString(this.heading,
                (this.width - headingWidth) / 2, this.panelY1 + 16,
                Draw.withAlpha(Theme.danger, 0.95F * a));

        int y = this.panelY1 + 40;
        for (int i = 0; i < this.lines.size(); i++) {
            String line = String.valueOf(this.lines.get(i));
            int width = font().getStringWidth(line);
            font().drawString(line, (this.width - width) / 2, y + i * 10,
                    Draw.withAlpha(Theme.text, 0.85F * a));
        }

        drawBackButton();
        Draw.vignette(this.width, this.height, 0.7F * a, 0xFF000000);
    }

    private void drawBackButton() {
        int width = this.panelX2 - 18 - (this.panelX1 + 18);
        int x = this.panelX1 + 18;
        int y = buttonY();
        boolean over = this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + 20;
        this.backHover = Ease.approach(this.backHover, over ? 1.0F : 0.0F, 0.05F, 0.016F);

        int fill = Draw.mix(Theme.accent, Theme.textHover, this.backHover * 0.2F);
        Draw.rect(x, y, x + width, y + 20,
                Draw.withAlpha(fill, (0.62F + this.backHover * 0.18F) * this.fade));
        String label = I18n.format("gui.toMenu", new Object[0]);
        int labelWidth = font().getStringWidth(label);
        font().drawString(label, x + (width - labelWidth) / 2, y + 6,
                Draw.withAlpha(0x0B0B0E, this.fade));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        ensureLayout();
        int width = this.panelX2 - 18 - (this.panelX1 + 18);
        int x = this.panelX1 + 18;
        int y = buttonY();
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 20) {
            leave();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1 || keyCode == 28 || keyCode == 156) {
            leave();
        }
    }

    private void leave() {
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button.id == ID_BACK) {
            leave();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

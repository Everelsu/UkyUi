package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * The "connecting to the server" screen.
 *
 * Wrapped rather than replaced, the same way the world loading screen is: vanilla's
 * {@code GuiConnecting} owns the network manager, the handshake and the timeout, and
 * reimplementing that to change how it looks would be trading a working connection
 * for a nicer spinner. Its {@code updateScreen} still runs every tick, so the
 * connection proceeds exactly as it would have; only the drawing is ours.
 *
 * <p>Deliberately not a {@code MenuScreen}: everything on the multiplayer path sits
 * on flat dark, and this is the middle of that path.
 */
public class GuiConnectingScreen extends GuiScreen {

    private final GuiScreen delegate;

    private long lastFrameNanos = System.nanoTime();
    private float elapsed;

    public GuiConnectingScreen(GuiScreen delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initGui() {
        this.delegate.mc = this.mc;
        this.delegate.setWorldAndResolution(this.mc, this.width, this.height);
    }

    @Override
    public void updateScreen() {
        // This is what actually drives the connection; skipping it would hang here.
        this.delegate.updateScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        this.elapsed += Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        Draw.rect(0, 0, this.width, this.height, Theme.background);
        Draw.vignette(this.width, this.height, 0.7F, 0xFF000000);

        String message = I18n.format("connect.connecting", new Object[0]);
        this.drawCenteredString(this.fontRenderer, message,
                this.width / 2, this.height / 2 - 16, Draw.withAlpha(Theme.text, 0.9F));

        drawIndicator();
    }

    /** The same indeterminate sweep the world loading screen uses. */
    private void drawIndicator() {
        float barWidth = Math.min(this.width * 0.34F, 220.0F);
        float x = (this.width - barWidth) / 2.0F;
        float y = this.height / 2.0F + 2.0F;

        Draw.rect(x, y, x + barWidth, y + 1.0F, Draw.withAlpha(Theme.textDim, 0.20F));

        float sweep = barWidth * 0.28F;
        float t = (this.elapsed * 0.55F) % 1.0F;
        float head = x - sweep + (barWidth + sweep) * Ease.inOutCubic(t);
        float from = Math.max(x, head);
        float to = Math.min(x + barWidth, head + sweep);
        if (to > from) {
            Draw.gradientH(from, y, (from + to) / 2.0F, y + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.9F));
            Draw.gradientH((from + to) / 2.0F, y, to, y + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.9F), Draw.withAlpha(Theme.accent, 0.0F));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        // Vanilla swallows input here too; cancelling mid-handshake is not offered.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

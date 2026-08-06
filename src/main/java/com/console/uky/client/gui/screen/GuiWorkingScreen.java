package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.world.WorldEntryFade;
import com.console.uky.client.world.WorldPreviews;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.util.ResourceLocation;

/**
 * The "Working..." screen the game puts up while the integrated server starts.
 *
 * This is the one loading surface the mod never covered, and it is why the dirt
 * tiling still flashed through a load: {@code GuiScreenWorking.drawScreen} opens with
 * {@code drawDefaultBackground()}, which with no world loaded is Mojang's tiled dirt.
 * {@link com.console.uky.client.world.UkyLoadingScreen} paints over the top of it,
 * but only when progress changes — every frame in between is drawn by the normal
 * render loop, and every one of those frames was dirt.
 *
 * <p>A subclass rather than a replacement. The game hands this screen its progress
 * through {@link net.minecraft.util.IProgressUpdate}, and it is the screen's own
 * business to close itself when the work is done, so it has to stay one of these.
 * Everything it reports is kept again here because the fields it keeps are private.
 */
public class GuiWorkingScreen extends GuiScreenWorking {

    private final ResourceLocation preview;

    private String title = "";
    private String stage = "";
    private int progress;
    private boolean done;

    private long lastFrameNanos = System.nanoTime();
    private float elapsed;

    public GuiWorkingScreen() {
        String folder = WorldPreviews.getEnteringWorld();
        this.preview = folder == null ? null : WorldPreviews.texture(folder);
    }

    // ---- IProgressUpdate. Kept in step with the superclass so both agree. ----

    @Override
    public void displaySavingString(String message) {
        this.title = message == null ? "" : message;
        super.displaySavingString(message);
    }

    @Override
    public void resetProgressAndMessage(String message) {
        this.title = message == null ? "" : message;
        super.resetProgressAndMessage(message);
    }

    @Override
    public void displayLoadingString(String message) {
        this.stage = message == null ? "" : message;
        super.displayLoadingString(message);
    }

    @Override
    public void setLoadingProgress(int progress) {
        this.progress = progress;
        super.setLoadingProgress(progress);
    }

    @Override
    public void setDoneWorking() {
        this.done = true;
        super.setDoneWorking();
    }

    // ------------------------------------------------------------- drawing --

    /** Slow push-in, matching {@link GuiWorldLoadingScreen} so the two read as one shot. */
    private float zoom() {
        return 1.04F + this.elapsed * 0.008F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Closing is the superclass's job and the reason this stays a subclass; done
        // first so a finished load never draws another frame of loading.
        if (this.done) {
            if (!this.mc.isConnectedToRealms()) {
                this.mc.displayGuiScreen((GuiScreen) null);
            }
            return;
        }

        long now = System.nanoTime();
        this.elapsed += Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        // Deliberately not super.drawScreen: that is the dirt this class exists to
        // get rid of, and the two labels it draws are redrawn below in our own type.
        Draw.rect(0, 0, this.width, this.height, Theme.background);

        if (this.preview != null) {
            float fade = Ease.outCubic(this.elapsed / 0.6F);
            Draw.textureCover(this.preview, 0, 0, this.width, this.height,
                    WorldEntryFade.PREVIEW_W, WorldEntryFade.PREVIEW_H,
                    zoom(), 0.0F, 0.0F, Draw.withAlpha(0xFFFFFF, fade));
            Draw.rect(0, 0, this.width, this.height,
                    Draw.withAlpha(Theme.background, WorldEntryFade.SCRIM));
        }

        Draw.vignette(this.width, this.height, 0.8F, 0xFF000000);

        if (!this.title.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.title, this.width / 2,
                    this.height - 52, Draw.withAlpha(Theme.text, 0.9F));
        }
        if (!this.stage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.stage, this.width / 2,
                    this.height - 40, Draw.withAlpha(Theme.textDim, 0.75F));
        }

        drawBar();
    }

    /**
     * The real percentage where there is one, and a sweep where there is not.
     *
     * This screen is handed a progress value, unlike the terrain download, but it
     * spends much of a load sitting at the same number — so a bar that only ever sat
     * still would read as a hang. Below zero it sweeps instead.
     */
    private void drawBar() {
        float barWidth = Math.min(this.width * 0.34F, 220.0F);
        float x = (this.width - barWidth) / 2.0F;
        float y = this.height - 28.0F;

        Draw.rect(x, y, x + barWidth, y + 1.0F, Draw.withAlpha(Theme.textDim, 0.20F));

        if (this.progress > 0) {
            float filled = barWidth * Ease.clamp01(this.progress / 100.0F);
            Draw.gradientH(x, y, x + filled, y + 1.0F,
                    Draw.withAlpha(Theme.accentAlt, 0.9F), Draw.withAlpha(Theme.accent, 0.9F));
            return;
        }

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
        // Vanilla swallows input here too; a keypress must not cancel a world load.
    }
}

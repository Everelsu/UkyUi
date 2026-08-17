package com.console.uky.client.gui.screen;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.world.WorldEntryFade;
import com.console.uky.client.world.WorldPreviews;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**
 * Shown while a world comes up, in place of vanilla's flat "Loading terrain".
 *
 * The backdrop is the picture taken when that world was last left, pushed in
 * slowly and darkened: you are looking at where you are about to arrive. A world
 * with no capture yet simply gets black, which is the same signal its tile gives.
 *
 * Deliberately not a {@link com.console.uky.client.gui.MenuScreen}: the black hole
 * belongs to the menus, and the game is past them by this point.
 */
public class GuiWorldLoadingScreen extends GuiScreen {

    private final GuiScreen delegate;
    private final ResourceLocation preview;
    /** Size the delegate was set up at; -1 until it has been. */
    private int delegateWidth = -1;
    private int delegateHeight = -1;

    private long lastFrameNanos = System.nanoTime();
    private float elapsed;

    /**
     * @param delegate the screen this replaces; its {@code updateScreen} still runs
     *                 so the world keeps loading exactly as it otherwise would
     */
    public GuiWorldLoadingScreen(GuiScreen delegate) {
        this.delegate = delegate;
        String folder = WorldPreviews.getEnteringWorld();
        this.preview = folder == null ? null : WorldPreviews.texture(folder);
    }

    @Override
    public void initGui() {
        prepareDelegate();
    }

    /**
     * Hands the delegate a world and a resolution, if that has not happened yet.
     *
     * Same reasoning as the connecting screen's: {@code initGui} is skipped outright
     * whenever a mod cancels {@code GuiScreenEvent.InitGuiEvent.Pre}, and the delegate
     * is what notices the world is ready. Started from whichever of the tick and the
     * draw arrives first, so nothing can leave it unstarted.
     */
    private void prepareDelegate() {
        if (this.delegateWidth == this.width && this.delegateHeight == this.height) {
            return;
        }
        this.delegateWidth = this.width;
        this.delegateHeight = this.height;
        this.delegate.mc = this.mc;
        this.delegate.setWorldAndResolution(this.mc, this.width, this.height);
    }

    @Override
    public void updateScreen() {
        // The delegate is what actually watches for the world becoming ready and
        // hands control back to the game; skipping it would hang here forever.
        prepareDelegate();
        this.delegate.updateScreen();
    }

    /** Slow push-in, so a long load never looks like a frozen still. */
    private float zoom() {
        return 1.04F + this.elapsed * 0.008F;
    }

    /**
     * Hands the picture to {@link WorldEntryFade} on the way out.
     *
     * This screen is only ever closed because the world became ready, so this is the
     * handover point. Passing the zoom across is what lets the dissolve pick up
     * exactly where this frame left off instead of snapping back.
     */
    @Override
    public void onGuiClosed() {
        WorldEntryFade.begin(this.preview, zoom());
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        prepareDelegate();
        long now = System.nanoTime();
        this.elapsed += Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        Draw.rect(0, 0, this.width, this.height, Theme.background);

        if (this.preview != null) {
            // Slow push-in, so a long load never looks like a frozen still.
            float fade = Ease.outCubic(this.elapsed / 0.6F);
            Draw.textureCover(this.preview, 0, 0, this.width, this.height,
                    WorldEntryFade.PREVIEW_W, WorldEntryFade.PREVIEW_H,
                    zoom(), 0.0F, 0.0F, Draw.withAlpha(0xFFFFFF, fade));
            // Darkened so the text on top stays readable over any scene.
            Draw.rect(0, 0, this.width, this.height,
                    Draw.withAlpha(Theme.background, WorldEntryFade.SCRIM));
        }

        Draw.vignette(this.width, this.height, 0.8F, 0xFF000000);

        // Full strength from the first frame, and deliberately not faded in.
        //
        // Fading it was tried and is wrong. This screen does not follow the dark — it
        // follows UkyLoadingScreen, which draws its own headline at this exact position
        // with this exact alpha and its bar at the same height, precisely so the two
        // phases of loading read as one continuous shot. Fading in here would make the
        // caption dip out and return at the handover between them, which is a seam where
        // there had not been one.
        String message = I18n.format("multiplayer.downloadingTerrain", new Object[0]);
        this.drawCenteredString(this.fontRendererObj, message,
                this.width / 2, this.height - 40, Draw.withAlpha(Theme.text, 0.9F));

        drawIndicator();
    }

    /** Indeterminate sweep: the world load reports no progress worth showing. */
    private void drawIndicator() {
        float barWidth = Math.min(this.width * 0.34F, 220.0F);
        float x = (this.width - barWidth) / 2.0F;
        float y = this.height - 28.0F;

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
    protected void keyTyped(char typedChar, int keyCode) {
        // Nothing to do here; vanilla's version swallows input too.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

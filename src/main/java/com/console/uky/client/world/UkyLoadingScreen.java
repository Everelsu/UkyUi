package com.console.uky.client.world;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Replaces the dirt-tiled "Loading world / Building terrain" screen.
 *
 * This is the phase before {@code GuiDownloadTerrain} — the integrated server
 * starting and generating chunks — and it is not a {@code GuiScreen} at all, so
 * swapping screens never touched it. It paints itself directly whenever progress
 * changes, which is why the world picture appeared only for the last moment of a
 * load and the dirt background for all of it.
 *
 * <p>Painting here mirrors what vanilla does, because it has to: this runs on the
 * main thread inside a blocking generation loop, with no game tick and no render
 * tick, so the frame has to be set up and presented by hand. If any of that throws,
 * the screen permanently hands back to vanilla rather than risk a black window
 * during a world load.
 */
public class UkyLoadingScreen extends LoadingScreenRenderer {

    /**
     * Repaint interval. Vanilla uses 100ms; this is quicker because there is a slow
     * push-in to animate and 10fps makes it judder.
     */
    private static final long REPAINT_MS = 40L;

    private final Minecraft mc;

    private String headline = "";
    private String detail = "";
    private ResourceLocation preview;

    private long lastPaint;
    private long startedAt;
    private float elapsed;
    /** Latches on the first failure; from then on vanilla draws. */
    private boolean broken;

    public UkyLoadingScreen(Minecraft mc) {
        super(mc);
        this.mc = mc;
    }

    // The three entry points all defer to super as well, which is what keeps the
    // MinecraftError-on-shutdown behaviour and the projection setup intact.

    @Override
    public void resetProgressAndMessage(String message) {
        this.headline = message == null ? "" : message;
        beginIfNeeded();
        super.resetProgressAndMessage(message);
    }

    @Override
    public void displayProgressMessage(String message) {
        this.headline = message == null ? "" : message;
        beginIfNeeded();
        super.displayProgressMessage(message);
    }

    @Override
    public void resetProgresAndWorkingMessage(String message) {
        this.detail = message == null ? "" : message;
        beginIfNeeded();
        // Calls setLoadingProgress(-1) virtually, so the paint below still runs.
        super.resetProgresAndWorkingMessage(message);
    }

    @Override
    public void setLoadingProgress(int progress) {
        // On shutdown vanilla throws MinecraftError from here to abort the load, and
        // that has to keep working or closing the window mid-load would hang.
        if (this.broken || Display.isCloseRequested()) {
            super.setLoadingProgress(progress);
            return;
        }
        try {
            paint(progress);
        } catch (Throwable t) {
            this.broken = true;
            UkyUI.LOGGER.warn("Custom loading screen failed; using the vanilla one", t);
            super.setLoadingProgress(progress);
        }
    }

    /** Picks up the picture and the clock on the first message of a load. */
    private void beginIfNeeded() {
        if (this.startedAt != 0L) {
            return;
        }
        this.startedAt = System.nanoTime();
        String folder = WorldPreviews.getEnteringWorld();
        this.preview = folder == null ? null : WorldPreviews.texture(folder);
    }

    /** Lets the next load start its animation from the beginning. */
    public void rearm() {
        this.startedAt = 0L;
        this.elapsed = 0.0F;
        this.preview = null;
        this.headline = "";
        this.detail = "";
    }

    /**
     * Re-arms whatever is installed, if it is ours.
     *
     * Called when a world is left. Leaving also goes through this screen — "Saving
     * world" — and without resetting, that paint reuses the picture and the clock
     * from the load that is now ending.
     */
    public static void rearmIfInstalled(Minecraft mc) {
        if (mc.loadingScreen instanceof UkyLoadingScreen) {
            ((UkyLoadingScreen) mc.loadingScreen).rearm();
        }
    }

    private void paint(int progress) {
        long now = Minecraft.getSystemTime();
        if (now - this.lastPaint < REPAINT_MS) {
            return;
        }
        this.lastPaint = now;
        this.elapsed = (System.nanoTime() - this.startedAt) / 1_000_000_000.0F;

        ScaledResolution resolution =
                new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        // Vanilla renders into a framebuffer and blits it; with FBOs on, drawing
        // straight to the back buffer would be discarded. Its own buffer is private,
        // so this borrows the game's main one.
        Framebuffer target = this.mc.getFramebuffer();
        boolean useFramebuffer = OpenGlHelper.isFramebufferEnabled() && target != null;
        if (useFramebuffer) {
            target.framebufferClear();
            target.bindFramebuffer(false);
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, resolution.getScaledWidth_double(),
                resolution.getScaledHeight_double(), 0.0D, 100.0D, 300.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -200.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Nothing else has set this state up: there is no render tick here.
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        drawContents(width, height, progress);

        if (useFramebuffer) {
            target.unbindFramebuffer();
            target.framebufferRender(this.mc.displayWidth, this.mc.displayHeight);
        }
        this.mc.func_147120_f();

        // Vanilla yields here so the server thread it is waiting on can make
        // progress. Dropping that would starve the very load being drawn.
        try {
            Thread.yield();
        } catch (Exception ignored) {
            // Nothing sensible to do; the next repaint tries again.
        }
    }

    private void drawContents(int width, int height, int progress) {
        Draw.rect(0, 0, width, height, Theme.background);

        if (this.preview != null) {
            // Stretched to fill and pushed in slowly, matching what the terrain
            // screen after this does, so the two phases read as one shot.
            //
            // The zoom is capped. Unbounded, a clock left running from an earlier
            // load reaches a point where the whole screen is a handful of magnified
            // texels — which is what the grey screen on leaving a world was.
            float zoom = Math.min(1.30F, 1.04F + this.elapsed * 0.008F);
            float fade = Ease.outCubic(this.elapsed / 0.6F);
            Draw.textureCover(this.preview, 0, 0, width, height,
                    WorldEntryFade.PREVIEW_W, WorldEntryFade.PREVIEW_H,
                    zoom, 0.0F, 0.0F, Draw.withAlpha(0xFFFFFF, fade));
            Draw.rect(0, 0, width, height,
                    Draw.withAlpha(Theme.background, WorldEntryFade.SCRIM));
        }

        Draw.vignette(width, height, 0.8F, 0xFF000000);

        if (!this.headline.isEmpty()) {
            int textWidth = this.mc.fontRenderer.getStringWidth(this.headline);
            this.mc.fontRenderer.drawString(this.headline,
                    (width - textWidth) / 2, height - 40,
                    Draw.withAlpha(Theme.text, 0.9F));
        }
        if (!this.detail.isEmpty()) {
            int textWidth = this.mc.fontRenderer.getStringWidth(this.detail);
            this.mc.fontRenderer.drawString(this.detail,
                    (width - textWidth) / 2, height - 52,
                    Draw.withAlpha(Theme.textDim, 0.75F));
        }

        drawProgress(width, height, progress);
    }

    private void drawProgress(int width, int height, int progress) {
        float barWidth = Math.min(width * 0.34F, 220.0F);
        float x = (width - barWidth) / 2.0F;
        float y = height - 28.0F;

        Draw.rect(x, y, x + barWidth, y + 1.0F, Draw.withAlpha(Theme.textDim, 0.20F));

        if (progress >= 0) {
            // Real progress when the server reports it — chunk generation does.
            float filled = barWidth * Math.min(1.0F, progress / 100.0F);
            Draw.rect(x, y, x + filled, y + 1.0F, Draw.withAlpha(Theme.accent, 0.95F));
            return;
        }

        // Otherwise an indeterminate sweep, identical to the terrain screen's.
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

    /**
     * Installs this in place of the game's loading screen.
     *
     * Called immediately before launching a world rather than at start-up, because
     * {@code Minecraft.startGame} assigns a fresh {@code LoadingScreenRenderer}
     * after mod initialisation has finished, and {@code resize} assigns another one
     * on every window resize. Installing right before it is needed sidesteps both.
     */
    public static UkyLoadingScreen install(Minecraft mc) {
        if (mc.loadingScreen instanceof UkyLoadingScreen) {
            UkyLoadingScreen existing = (UkyLoadingScreen) mc.loadingScreen;
            existing.rearm();
            return existing;
        }
        try {
            UkyLoadingScreen screen = new UkyLoadingScreen(mc);
            mc.loadingScreen = screen;
            return screen;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not install the custom loading screen", t);
            return null;
        }
    }
}

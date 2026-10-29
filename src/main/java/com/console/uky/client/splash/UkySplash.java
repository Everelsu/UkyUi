package com.console.uky.client.splash;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import com.console.uky.compat.GL11;
import com.console.uky.config.UiConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The loading screen: game start-up and every resource reload after it.
 *
 * <p>Takes over from vanilla's overlay — and from NeoForge's, which is that one plus the
 * early window's picture — the first frame it is up, and does exactly its job: follows
 * the reload, hands its result on, and gets out of the way. What it draws instead is
 * this mod's: the backdrop colour, the wordmark, one bar for the reload with what the
 * loader is doing above it, and the tips from the config underneath. On a reload over
 * a screen that is already up it veils that screen rather than blanking it.
 *
 * <p>NeoForge's early window draws before the game window exists and cannot be
 * replaced; this starts where it ends.
 */
public final class UkySplash extends Overlay {

    private static final float FADE_OUT_SECONDS = 0.7F;
    private static final float FADE_IN_SECONDS = 0.5F;

    private static long finishedAtNanos;
    private static final SplashFont FONT = new SplashFont();

    private final Minecraft mc = Minecraft.getInstance();
    private static final boolean DEBUG_SHOTS = Boolean.getBoolean("uky.debugShots");
    private int debugFrames;
    private final LoadingOverlay original;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;

    private final long startNanos = System.nanoTime();
    private long fadeOutStart = -1L;
    private long fadeInStart = -1L;
    private float shownProgress;
    private int tipIndex = -1;
    private String tipText;

    private UkySplash(LoadingOverlay original) {
        this.original = original;
        this.reload = original.reload;
        this.onFinish = original.onFinish;
        this.fadeIn = original.fadeIn;
    }

    /** When the start-up screen finished, for the title screen's arrival; 0 if it never ran. */
    public static long finishedAtNanos() {
        return finishedAtNanos;
    }

    /**
     * This screen in place of a loading overlay, or the overlay as it was.
     *
     * Called for every overlay the game puts up (see MixinMinecraftOverlay), the first
     * of them before this mod is even constructed — so the config is read off disk here
     * if NeoForge has not loaded it yet, and the palette built from it.
     */
    public static Overlay wrap(Overlay overlay) {
        if (!(overlay instanceof LoadingOverlay)) {
            return overlay;
        }
        try {
            UiConfig.loadEarly();
            if (Theme.background == 0) {
                Theme.rebuild();
            }
            return UiConfig.customSplash ? new UkySplash((LoadingOverlay) overlay) : overlay;
        } catch (Throwable t) {
            // The loading screen is the one place a failure must not take the game down.
            UkyUI.LOGGER.error("Loading screen unavailable; keeping the game's own", t);
            return overlay;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = now;
        }
        float out = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F / FADE_OUT_SECONDS : -1.0F;
        float in = this.fadeInStart > -1L ? (now - this.fadeInStart) / 1000.0F / FADE_IN_SECONDS : 1.0F;
        float elapsed = (System.nanoTime() - this.startNanos) / 1_000_000_000.0F;

        // Whatever is behind shows through only while fading: over a screen on the way
        // in, and over the title screen on the way out.
        float veil;
        if (out >= 0.0F) {
            veil = 1.0F - Mth.clamp(out, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            veil = Mth.clamp(in, 0.0F, 1.0F);
        } else {
            veil = 1.0F;
        }
        if (veil < 1.0F && this.mc.screen != null) {
            this.mc.screen.render(g, 0, 0, partialTick);
            g.flush();
        }
        Draw.rect(0, 0, w, h, Draw.withAlpha(Theme.background, veil));
        Draw.vignette(w, h, 0.85F * veil, 0xFF000000);

        float content = veil * Mth.clamp(elapsed / 0.4F, 0.0F, 1.0F);
        drawWordmark(w, h, content);
        drawProgress(w, h, content * (out >= 0.0F ? 1.0F - Mth.clamp(out * 2.0F, 0.0F, 1.0F) : 1.0F));
        drawTip(w, h, content, elapsed);
        if (DEBUG_SHOTS && ++this.debugFrames % 120 == 0) {
            net.minecraft.client.Screenshot.grab(this.mc.gameDirectory,
                    "uky_splash_" + this.debugFrames + ".png", this.mc.getMainRenderTarget(), msg -> { });
        }

        if (out >= 1.0F) {
            this.mc.setOverlay(null);
            finishedAtNanos = System.nanoTime();
            closeEarlyWindow();
        }
        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || in >= 1.0F)) {
            this.fadeOutStart = Util.getMillis();
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }
            if (this.mc.screen != null) {
                this.mc.screen.init(this.mc, w, h);
            }
        }
    }

    private void drawWordmark(int w, int h, float alpha) {
        String text = UiConfig.splashSafe(UiConfig.title);
        if (text == null || text.isEmpty() || alpha <= 0.01F) {
            return;
        }
        float scale = 2.0F;
        int width = FONT.width(text);
        float y = h * 0.32F;
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        FONT.draw(text, w / 2.0F / scale - width / 2.0F, y / scale, Draw.withAlpha(Theme.text, alpha));
        GL11.glPopMatrix();
        float half = width * scale * 0.5F;
        float ruleY = y + 8.0F * scale + 5.0F;
        Draw.gradientH(w / 2.0F - half, ruleY, w / 2.0F, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.8F * alpha));
        Draw.gradientH(w / 2.0F, ruleY, w / 2.0F + half, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.8F * alpha), Draw.withAlpha(Theme.accent, 0.0F));
    }

    private void drawProgress(int w, int h, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        float target = this.reload.getActualProgress();
        this.shownProgress = Mth.clamp(this.shownProgress * 0.9F + target * 0.1F, 0.0F, 1.0F);

        float barWidth = Math.min(w * 0.52F, 320.0F);
        float x = (w - barWidth) / 2.0F;
        float y = h * 0.62F;

        String caption = caption();
        if (caption != null) {
            String safe = UiConfig.splashSafe(caption);
            FONT.draw(safe, w / 2.0F - FONT.width(safe) / 2.0F, y - 12.0F,
                    Draw.withAlpha(Theme.textDim, 0.85F * alpha));
        }
        if (UiConfig.showPercent) {
            FONT.draw((int) (this.shownProgress * 100.0F) + "%", x + barWidth + 5.0F, y - 4.0F,
                    Draw.withAlpha(Theme.textDim, 0.6F * alpha));
        }
        Draw.rect(x, y, x + barWidth, y + 1.0F, Draw.withAlpha(Theme.textDim, 0.25F * alpha));
        float fill = barWidth * this.shownProgress;
        if (fill > 0.0F) {
            Draw.gradientH(x, y, x + fill, y + 1.0F,
                    Draw.withAlpha(Theme.accentAlt, 0.85F * alpha),
                    Draw.withAlpha(Theme.accent, 0.95F * alpha));
            Draw.rect(Math.max(x, x + fill - 12.0F), y - 0.5F, x + fill, y + 1.5F,
                    Draw.withAlpha(Theme.textHover, 0.5F * alpha));
        }
    }

    /** What the loader says it is doing, from its newest progress bar with a label. */
    private static String caption() {
        try {
            List<ProgressMeter> meters = StartupNotificationManager.getCurrentProgress();
            for (int i = meters.size() - 1; i >= 0; i--) {
                String label = meters.get(i).label().getText();
                if (label != null && !label.isEmpty()) {
                    return label;
                }
            }
        } catch (Throwable ignored) {
            // A caption is decoration; the bar is the information.
        }
        return null;
    }

    private void drawTip(int w, int h, float alpha, float elapsed) {
        String[] tips = UiConfig.splashTips;
        if (!UiConfig.showTips || tips.length == 0 || alpha <= 0.01F) {
            return;
        }
        float period = 4.5F;
        int index = (int) (elapsed / period) % tips.length;
        float phase = (elapsed % period) / period;
        float tipAlpha = Mth.clamp(Math.min(phase / 0.15F, (1.0F - phase) / 0.15F), 0.0F, 1.0F);
        if (index != this.tipIndex) {
            this.tipIndex = index;
            this.tipText = UiConfig.splashSafe(tips[index]);
        }
        FONT.draw(this.tipText, w / 2.0F - FONT.width(this.tipText) / 2.0F, h - 26.0F,
                Draw.withAlpha(Theme.textDim, tipAlpha * 0.7F * alpha));
    }

    /**
     * NeoForge's overlay owns the early window and closes it when it finishes; since
     * this one finished instead, it does that part on its behalf.
     */
    private void closeEarlyWindow() {
        for (Field field : this.original.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(this.original);
                if (value instanceof ProgressMeter) {
                    ((ProgressMeter) value).complete();
                } else if (value != null && value.getClass().getName().endsWith("DisplayWindow")) {
                    value.getClass().getMethod("close").invoke(value);
                }
            } catch (Throwable t) {
                UkyUI.LOGGER.debug("Could not release {} of the loading overlay", field.getName(), t);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

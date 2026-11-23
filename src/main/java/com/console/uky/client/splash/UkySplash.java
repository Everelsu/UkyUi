package com.console.uky.client.splash;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.OrbitScene;
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
 * this mod's: the backdrop colour (or in {@code "orbit"} mode, the live 3D orbital ascent
 * over the blocky Earth with orbital telemetry and a trajectory arc), the wordmark, one
 * bar for the reload with what the loader is doing above it, and the tips from the config
 * underneath. On a reload over a screen that is already up it veils that screen rather
 * than blanking it.
 *
 * <p>NeoForge's early window draws before the game window exists and cannot be
 * replaced; this starts where it ends.
 */
public final class UkySplash extends Overlay {

    private static final float FADE_OUT_SECONDS = 1.05F;
    private static final float FADE_IN_SECONDS = 0.5F;

    private static long finishedAtNanos;
    /** 0..1 while an orbital splash is handing off to the title screen, or -1 when idle. */
    private static float orbitTransitionProgress = -1.0F;
    /** True once an orbital splash has handed off to the title screen this session. */
    private static boolean orbitArrivedFromSplash;
    private static final SplashFont FONT = new SplashFont();

    private final Minecraft mc = Minecraft.getInstance();
    private static final boolean DEBUG_SHOTS = Boolean.getBoolean("uky.debugShots");
    private int debugFrames;
    private final LoadingOverlay original;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;

    private final long startNanos = System.nanoTime();
    private long lastFrameNanos = System.nanoTime();
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

    /** Whether an orbital splash screen is currently crossfading into the title screen. */
    public static boolean isOrbitTransitionActive() {
        return orbitTransitionProgress >= 0.0F;
    }

    /** 0..1 progress of the orbital splash fade-out handoff, or -1 when inactive. */
    public static float orbitTransitionProgress() {
        return orbitTransitionProgress;
    }

    /** Whether the title screen arrived smoothly from an orbital splash (wordmark already at top). */
    public static boolean orbitArrivedFromSplash() {
        return orbitArrivedFromSplash;
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
        long nowNanos = System.nanoTime();
        float dt = Math.min(0.05F, Math.max(0.0F, (nowNanos - this.lastFrameNanos) / 1_000_000_000.0F));
        this.lastFrameNanos = nowNanos;
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = now;
        }
        float out = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F / FADE_OUT_SECONDS : -1.0F;
        float in = this.fadeInStart > -1L ? (now - this.fadeInStart) / 1000.0F / FADE_IN_SECONDS : 1.0F;
        float elapsed = (nowNanos - this.startNanos) / 1_000_000_000.0F;
        boolean orbitMode = "orbit".equals(UiConfig.background);
        boolean orbitHandoff = orbitMode && out >= 0.0F && this.mc.screen != null;
        orbitTransitionProgress = orbitHandoff ? Mth.clamp(out, 0.0F, 1.0F) : -1.0F;

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
        if ((veil < 1.0F || orbitHandoff) && this.mc.screen != null) {
            this.mc.screen.render(g, 0, 0, partialTick);
            g.flush();
        }

        // In orbit mode, lay out and draw in MenuScreen's exact coordinate space (autoScaleFactor)
        // so the wordmark, font size, tracking, and 3D Earth horizon match GuiTitleScreen 1:1
        // regardless of the player's GUI Scale setting.
        int drawW = w;
        int drawH = h;
        boolean scaled = false;
        if (orbitMode) {
            int factor = MenuScreen.autoScaleFactor(this.mc);
            int uiW = Mth.ceil((double) this.mc.getWindow().getWidth() / factor);
            int uiH = Mth.ceil((double) this.mc.getWindow().getHeight() / factor);
            if (uiW > 0 && uiH > 0) {
                float scaleX = (float) w / uiW;
                float scaleY = (float) h / uiH;
                drawW = uiW;
                drawH = uiH;
                GL11.glPushMatrix();
                GL11.glScalef(scaleX, scaleY, 1.0F);
                scaled = true;
            }
        }
        try {
            if (orbitMode) {
                // Before the underlying title screen begins drawing, render the live shared 3D
                // orbital scene here; once fade-out starts (orbitHandoff), GuiTitleScreen beneath
                // us renders that exact shared OrbitScene at full opacity so there is zero flicker.
                if (!orbitHandoff) {
                    Draw.rect(-drawW, -drawH, drawW * 2, drawH * 2, Theme.background);
                    OrbitScene orbit = MenuScreen.sharedOrbit();
                    orbit.update(dt);
                    float settle = Mth.clamp(elapsed / 1.6F, 0.0F, 1.0F);
                    orbit.renderSplash(drawW, drawH, settle, 1.0F);
                }
                // Soft dark scrim behind the loading HUD that lifts smoothly during fade-out.
                float scrimAlpha = orbitHandoff ? (1.0F - Ease.outCubic(Mth.clamp(out, 0.0F, 1.0F))) : veil;
                if (scrimAlpha > 0.005F) {
                    Draw.radialGlow(drawW * 0.5F, drawH * 0.45F, drawH * 0.48F,
                            Draw.withAlpha(Theme.background, 0.42F * scrimAlpha),
                            Draw.withAlpha(Theme.background, 0.0F));
                }
                if (!orbitHandoff) {
                    Draw.vignette(drawW, drawH, 0.85F * veil, 0xFF000000);
                }
            } else {
                Draw.rect(0, 0, drawW, drawH, Draw.withAlpha(Theme.background, veil));
                Draw.vignette(drawW, drawH, 0.85F * veil, 0xFF000000);
            }

            float entrance = Mth.clamp(elapsed / 0.4F, 0.0F, 1.0F);
            float content = veil * entrance;
            if (orbitMode) {
                // Progress bar and tip dissolve cleanly in the first 35% of fade-out.
                float hudFade = out >= 0.0F
                        ? 1.0F - Ease.outCubic(Mth.clamp(out / 0.35F, 0.0F, 1.0F))
                        : 1.0F;
                float progressAlpha = entrance * (orbitHandoff ? hudFade : veil);

                // Wordmark glides smoothly from splash centre (0.28*h) to GuiTitleScreen's exact
                // header baseline (int(h * 0.05F) + 4) over out 0.0 -> 0.82, then crossfades
                // pixel-for-pixel into GuiTitleScreen's own wordmark over out 0.82 -> 1.0.
                float slide = out >= 0.0F ? Ease.inOutCubic(Mth.clamp(out / 0.82F, 0.0F, 1.0F)) : 0.0F;
                float targetY = (int) (drawH * 0.05F) + 4.0F;
                float wordmarkY = Mth.lerp(slide, drawH * 0.28F, targetY);
                float wordmarkAlpha;
                if (orbitHandoff) {
                    float handoff = Mth.clamp((out - 0.82F) / 0.18F, 0.0F, 1.0F);
                    wordmarkAlpha = entrance * (1.0F - handoff);
                } else {
                    wordmarkAlpha = content;
                }
                drawOrbitWordmark(drawW, drawH, wordmarkY, wordmarkAlpha);
                drawProgress(drawW, drawH, progressAlpha, true);
                drawTip(drawW, drawH, progressAlpha, elapsed);
            } else {
                float progressAlpha = content * (out >= 0.0F ? 1.0F - Mth.clamp(out * 1.8F, 0.0F, 1.0F) : 1.0F);
                drawWordmark(drawW, drawH, content);
                drawProgress(drawW, drawH, progressAlpha, false);
                drawTip(drawW, drawH, progressAlpha, elapsed);
            }
        } finally {
            if (scaled) {
                GL11.glPopMatrix();
            }
        }

        ++this.debugFrames;
        if (DEBUG_SHOTS && (this.debugFrames == 8 || this.debugFrames % 90 == 0)) {
            net.minecraft.client.Screenshot.grab(this.mc.gameDirectory,
                    "uky_splash_" + this.debugFrames + ".png", this.mc.getMainRenderTarget(), msg -> { });
        }

        if (out >= 1.0F) {
            orbitTransitionProgress = -1.0F;
            if (orbitMode) {
                orbitArrivedFromSplash = true;
            }
            this.mc.setOverlay(null);
            finishedAtNanos = System.nanoTime();
            closeEarlyWindow();
        }
        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || in >= 1.0F)) {
            this.fadeOutStart = Util.getMillis();
            if (orbitMode) {
                orbitTransitionProgress = 0.0F;
                // Begin the space station's horizon rise right as the splash screen hands off
                // to the main menu, so the station crests the Earth's limb as the HUD clears.
                MenuScreen.sharedOrbit().restartApproach();
            }
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

    /**
     * Orbital splash wordmark matched 1:1 with {@code GuiTitleScreen.drawWordmark}
     * (same scale = 2, tracking = 2, drop shadow, radial glow, and accent rule) so
     * the slide-up handoff into the main menu is completely seamless.
     */
    private void drawOrbitWordmark(int w, int h, float y, float alpha) {
        String text = UiConfig.splashSafe(UiConfig.title);
        if (text == null || text.isEmpty() || alpha <= 0.01F) {
            return;
        }
        final float scale = 2.0F;
        final int tracking = 2;
        int totalWidth = FONT.width(text, tracking);
        int centerX = w / 2;

        Draw.radialGlow(centerX, y + 8.0F * scale / 2.0F, totalWidth * scale * 0.7F,
                Draw.withAlpha(Theme.accent, 0.14F * alpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        float cursor = centerX / scale - totalWidth / 2.0F;
        float baseY = y / scale;
        FONT.draw(text, cursor + 1.0F, baseY + 1.0F, tracking, Draw.withAlpha(0x000000, 0.6F * alpha));
        FONT.draw(text, cursor, baseY, tracking, Draw.withAlpha(Theme.text, alpha));
        GL11.glPopMatrix();

        float ruleHalf = totalWidth * scale * 0.5F;
        float ruleY = y + 8.0F * scale + 4.0F;
        Draw.gradientH(centerX - ruleHalf, ruleY, centerX, ruleY + 1.0F,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.75F * alpha));
        Draw.gradientH(centerX, ruleY, centerX + ruleHalf, ruleY + 1.0F,
                Draw.withAlpha(Theme.accent, 0.75F * alpha), Draw.withAlpha(Theme.accent, 0.0F));
    }

    private void drawProgress(int w, int h, float alpha, boolean orbitMode) {
        if (alpha <= 0.01F) {
            return;
        }
        float target = this.reload.getActualProgress();
        this.shownProgress = Mth.clamp(this.shownProgress * 0.9F + target * 0.1F, 0.0F, 1.0F);

        float barWidth = Math.min(w * 0.56F, 340.0F);
        float barHeight = orbitMode ? 3.0F : 1.0F;
        float x = (w - barWidth) / 2.0F;
        float y = orbitMode ? h * 0.50F : h * 0.62F;

        String caption = caption();
        if (caption != null) {
            String safe = UiConfig.splashSafe(caption);
            FONT.draw(safe, w / 2.0F - FONT.width(safe) / 2.0F, y - 13.0F,
                    Draw.withAlpha(Theme.textDim, 0.90F * alpha));
        }
        if (UiConfig.showPercent) {
            FONT.draw((int) (this.shownProgress * 100.0F) + "%", x + barWidth + 7.0F,
                    y + (barHeight - 8.0F) * 0.5F,
                    Draw.withAlpha(Theme.text, 0.85F * alpha));
        }
        // Track background
        Draw.rect(x, y, x + barWidth, y + barHeight,
                Draw.withAlpha(0x060C18, (orbitMode ? 0.65F : 0.25F) * alpha));
        if (orbitMode) {
            Draw.border(x - 1.0F, y - 1.0F, x + barWidth + 1.0F, y + barHeight + 1.0F, 1.0F,
                    Draw.withAlpha(Theme.textDim, 0.28F * alpha));
        }
        float fill = barWidth * this.shownProgress;
        if (fill > 0.0F) {
            int barStart = orbitMode ? Theme.accentAlt : Theme.accentAlt;
            int barEnd = orbitMode ? Theme.accent : Theme.accent;
            if (orbitMode) {
                Draw.glow(x, y, x + fill, y + barHeight, 4.0F,
                        Draw.withAlpha(barEnd, 0.22F * alpha), 3);
            }
            Draw.gradientH(x, y, x + fill, y + barHeight,
                    Draw.withAlpha(barStart, 0.95F * alpha),
                    Draw.withAlpha(barEnd, 1.0F * alpha));
            Draw.rect(Math.max(x, x + fill - 14.0F), y, x + fill, y + barHeight,
                    Draw.withAlpha(0xFFFFFF, 0.65F * alpha));
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

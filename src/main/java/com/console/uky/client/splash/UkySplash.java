package com.console.uky.client.splash;

import com.console.uky.config.UiConfig;
import cpw.mods.fml.client.SplashProgress;
import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The mod-loading screen, replacing FML's.
 *
 * Same mechanism FML uses — a second thread with a shared GL context drawing
 * while the main thread loads mods — but with our own artwork, typography and
 * progress presentation. FML's version is not extensible enough to restyle
 * (its renderer is an anonymous inner class), so this is a fork rather than a
 * set of hooks.
 *
 * <p><b>Failure is always survivable.</b> {@link #start()} returns false if
 * anything goes wrong, and the mixin then lets FML's original splash run. A
 * broken loading screen must never be a broken game.
 */
public final class UkySplash {

    private static final ResourceLocation FONT_LOCATION =
            new ResourceLocation("textures/font/ascii.png");

    /** Classpath location of the optional logo, and the folder authors override from. */
    private static final String LOGO_RESOURCE = "/assets/uky/textures/gui/logo.png";
    private static final String OVERRIDE_DIR = "config/uky-splash";

    private static final Lock lock = new ReentrantLock(true);

    private static Drawable drawable;
    private static Thread thread;
    private static volatile boolean done;
    private static volatile Throwable threadError;
    private static boolean running;

    /**
     * FML's own display mutex. {@code FMLClientHandler.processWindowMessages} takes
     * it before pumping window events, so our {@code Display.update()} must hold it
     * too or the two threads fight over the display lock.
     */
    private static Semaphore displayMutex;

    private UkySplash() {
    }

    // ----------------------------------------------------------- lifecycle --

    /**
     * Takes over the loading screen.
     *
     * @return true if we are now driving it, false if the caller should fall
     *         back to FML's splash
     */
    public static boolean start() {
        try {
            UiConfig.loadEarly(Minecraft.getMinecraft().mcDataDir);
            if (!UiConfig.customSplash) {
                return false;
            }
            displayMutex = findDisplayMutex();

            drawable = new SharedDrawable(Display.getDrawable());
            Display.getDrawable().releaseContext();
            drawable.makeCurrent();
        } catch (Throwable t) {
            log("could not acquire a GL context for the loading screen", t);
            releaseToMainThread();
            return false;
        }

        done = false;
        threadError = null;
        thread = new Thread(new Renderer(), "UKY Splash");
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                threadError = e;
                log("loading screen thread crashed", e);
            }
        });
        thread.setDaemon(true);
        // Below the loading thread. Everything this thread does is decoration, and on
        // a machine whose cores are all busy classloading, decoration should be what
        // gives way — a loading screen that renders a frame late costs nothing, a
        // load that finishes later costs exactly what this change is about.
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();

        running = true;
        return true;
    }

    /** Stops the render thread and hands the GL context back to the main thread. */
    public static void finish() {
        if (!running) {
            return;
        }
        running = false;
        done = true;
        finishedAt = System.nanoTime();
        try {
            thread.join(5000L);
            GL11.glFlush();
            drawable.releaseContext();
            Display.getDrawable().makeCurrent();
        } catch (Throwable t) {
            log("error shutting the loading screen down", t);
            releaseToMainThread();
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Frames a second while the game loads.
     *
     * This is a progress bar and a line of text, not a game. Every frame costs a
     * full-screen redraw and a buffer swap on a context the loading thread's own
     * texture uploads are queued behind, and the difference between 60 and 30 of them
     * is not something anybody has ever noticed on a loading screen.
     */
    private static final int LOADING_FPS = 30;
    /** Ticks a second while paused: nothing is drawn, so this is only a poll. */
    private static final int PAUSED_FPS = 20;

    /**
     * Set while something needs the graphics driver to itself.
     *
     * {@code SplashProgress.pause} is a public API for mods that are about to modify
     * GL state during loading — "call resume() when you're done". FML itself never
     * calls it (checked: no call sites anywhere in FML or Forge), so this is not on
     * the path of a normal start-up and buys nothing there. It is honoured because a
     * mod that does call it is about to do exactly the thing this screen must not be
     * drawing through, and taking the screen over leaves FML's {@code enabled} flag
     * false, so its own pause would ignore such a mod outright.
     *
     * <p>Deliberately only a flag, where FML hands the GL context back and forth under
     * a lock. Handing a context between threads is worth the complexity when the other
     * thread cannot proceed without it, and the loading thread here can: it has been
     * uploading textures alongside this screen all along. Simply drawing nothing gets
     * out of the way with no handshake to deadlock on.
     */
    private static volatile boolean paused;
    /** When the current pause began, for {@link #PAUSE_LIMIT_NANOS}. */
    private static volatile long pausedAt;

    /**
     * How long a pause is believed before the screen starts drawing again.
     *
     * A pause is only ever ended by whoever started it, and "whoever" here is
     * arbitrary third-party code that may throw between its two calls. Without a
     * bound, one lost {@code resume} freezes the loading screen for the rest of the
     * launch — a far worse outcome than the contention the pause avoids, and one that
     * would look exactly like the mod having hung. Anything legitimate is a few
     * frames of GL setup, so a second is generous by orders of magnitude.
     */
    private static final long PAUSE_LIMIT_NANOS = 1_000_000_000L;

    /** Called on the main thread when something wants the driver to itself. */
    public static void pause() {
        pausedAt = System.nanoTime();
        paused = true;
    }

    public static void resume() {
        paused = false;
    }

    private static boolean isPaused() {
        if (!paused) {
            return false;
        }
        if (System.nanoTime() - pausedAt > PAUSE_LIMIT_NANOS) {
            paused = false;
            return false;
        }
        return true;
    }

    /**
     * When the loading screen released the context, or 0 if it never ran.
     *
     * The title screen uses this to carry on from where the splash stopped rather
     * than starting its own fade from scratch. Between the two there is a stretch of
     * frames nobody owns — Minecraft finishing start-up, the resource reload, the
     * main menu being constructed and swapped — and timing the arrival from this
     * instant covers all of it, however long it happens to take on a given machine.
     */
    public static long finishedAtNanos() {
        return finishedAt;
    }

    private static long finishedAt;

    /**
     * Best-effort restore of the GL context to the main thread after a failure.
     * Without this the game would carry on with no current context and die on the
     * first draw call.
     */
    private static void releaseToMainThread() {
        try {
            if (drawable != null && drawable.isCurrent()) {
                drawable.releaseContext();
            }
            if (!Display.getDrawable().isCurrent()) {
                Display.getDrawable().makeCurrent();
            }
        } catch (LWJGLException ignored) {
            // Nothing left to try; FML's splash or the game itself will report it.
        }
    }

    private static Semaphore findDisplayMutex() {
        try {
            // SplashProgress is an FML class and is never obfuscated, so the field
            // name is stable in both a dev run and a production jar.
            Field field = SplashProgress.class.getDeclaredField("mutex");
            field.setAccessible(true);
            return (Semaphore) field.get(null);
        } catch (Exception e) {
            log("could not reach FML's display mutex; window events may stutter", e);
            return null;
        }
    }

    private static void log(String message, Throwable t) {
        System.err.println("[UKY] " + message);
        if (t != null) {
            t.printStackTrace();
        }
    }

    // ----------------------------------------------------------- resources --

    /**
     * Opens {@code overrideName} from the override folder if the author dropped a
     * file there, otherwise from {@code classpathResource} inside the mod jar. Pass
     * a null classpath resource for images that only exist as an override.
     */
    private static InputStream open(String classpathResource, String overrideName) throws IOException {
        File override = new File(Minecraft.getMinecraft().mcDataDir, OVERRIDE_DIR + "/" + overrideName);
        if (override.isFile()) {
            return new FileInputStream(override);
        }
        if (classpathResource == null) {
            return null;
        }
        // Null rather than an exception when the mod ships no such image. The logo is
        // optional by design and none is bundled, so throwing here logged "could not
        // load splash image logo.png" on every single launch — an error line, for the
        // expected case, in the one file anybody reads when start-up misbehaves.
        return UkySplash.class.getResourceAsStream(classpathResource);
    }

    private static SplashTexture load(String classpathResource, String overrideName, boolean smooth) {
        InputStream stream = null;
        try {
            stream = open(classpathResource, overrideName);
            if (stream == null) {
                return null; // optional image, nothing supplied
            }
            return new SplashTexture(stream, smooth);
        } catch (Throwable t) {
            log("could not load splash image " + overrideName, t);
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // nothing useful to do while the game is still starting
            }
        }
    }

    // ------------------------------------------------------------ renderer --

    private static final class Renderer implements Runnable {

        private SplashTexture background;
        private SplashTexture logo;
        private SplashTexture fontTexture;
        private SplashFont font;

        private long startNanos;
        private float elapsed;

        /** Smoothed progress so the bar glides instead of jumping between steps. */
        private float shownProgress;

        @Override
        public void run() {
            acquireContext();
            try {
                // The loading screen is black by design. A backdrop only appears if
                // a pack explicitly drops one into config/uky-splash/.
                background = load(null, "background.png", true);
                logo = load(LOGO_RESOURCE, "logo.png", true);
                loadFont();

                startNanos = System.nanoTime();
                while (!done) {
                    if (isPaused()) {
                        // Not a single GL call while the loading thread has the driver.
                        // The picture stays on screen; nothing else happens.
                        Display.sync(PAUSED_FPS);
                        continue;
                    }
                    elapsed = (System.nanoTime() - startNanos) / 1_000_000_000.0F;
                    drawFrame();
                    present();
                    Display.sync(LOADING_FPS);
                }
            } finally {
                deleteTextures();
                releaseContext();
            }
        }

        private void loadFont() {
            InputStream stream = null;
            try {
                stream = Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(FONT_LOCATION);
                fontTexture = new SplashTexture(stream, false);
                font = new SplashFont(fontTexture, FONT_LOCATION);
            } catch (Throwable t) {
                log("could not build the splash font; loading text will be hidden", t);
                font = null;
            } finally {
                closeQuietly(stream);
            }
        }

        // ------------------------------------------------------------ frame --

        private void drawFrame() {
            int pixelWidth = Display.getWidth();
            int pixelHeight = Display.getHeight();
            int scale = guiScale(pixelWidth, pixelHeight);
            float w = (float) pixelWidth / scale;
            float h = (float) pixelHeight / scale;

            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glViewport(0, 0, pixelWidth, pixelHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, w, h, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            // A short fade-in hides the moment the window first appears.
            float alpha = clamp01(elapsed / 0.5F);

            drawBackground(w, h, alpha);
            drawLogo(w, h, alpha);
            drawProgress(w, h, alpha);
            drawFooter(w, h, alpha);
        }

        /** Mirrors vanilla's GUI scale so the splash matches the menus that follow. */
        private int guiScale(int pixelWidth, int pixelHeight) {
            int scale = 1;
            while (scale < 4
                    && pixelWidth / (scale + 1) >= 320
                    && pixelHeight / (scale + 1) >= 240) {
                scale++;
            }
            return scale;
        }

        private void drawBackground(float w, float h, float alpha) {
            // Black, always — the loading screen is deliberately just dark plus
            // white rules. A background image is opt-in via the override folder.
            rectRGBA(0, 0, w, h, 0.0F, 0.0F, 0.0F, 1.0F);
            if (background == null) {
                return;
            }
            // Cover-fit with a slow push-in, so a long load never looks frozen.
            float zoom = 1.02F + Math.min(elapsed, 60.0F) * 0.0012F;
            float scale = Math.max(w / background.getWidth(), h / background.getHeight()) * zoom;
            float drawW = background.getWidth() * scale;
            float drawH = background.getHeight() * scale;
            float x = (w - drawW) / 2.0F;
            float y = (h - drawH) / 2.0F;

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            background.bind();
            // Dimmed so the logo and text stay readable over any artwork.
            GL11.glColor4f(0.55F, 0.55F, 0.55F, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0.0F, background.maxV());
            GL11.glVertex2f(x, y + drawH);
            GL11.glTexCoord2f(background.maxU(), background.maxV());
            GL11.glVertex2f(x + drawW, y + drawH);
            GL11.glTexCoord2f(background.maxU(), 0.0F);
            GL11.glVertex2f(x + drawW, y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        private void drawVignette(float w, float h, float alpha) {
            int steps = 14;
            float band = Math.max(w, h) * 0.045F / steps;
            for (int i = 0; i < steps; i++) {
                float ratio = 1.0F - (float) i / steps;
                float a = ratio * ratio * 0.9F * alpha;
                float o = i * band;
                rectRGBA(0, o, w, o + band, 0, 0, 0, a);
                rectRGBA(0, h - o - band, w, h - o, 0, 0, 0, a);
                rectRGBA(o, 0, o + band, h, 0, 0, 0, a);
                rectRGBA(w - o - band, 0, w - o, h, 0, 0, 0, a);
            }
        }

        private void drawLogo(float w, float h, float alpha) {
            if (logo == null) {
                drawWordmark(w, h, alpha);
                return;
            }
            float size = clamp(h * 0.24F, 56.0F, 128.0F);
            float breathe = 1.0F + (float) Math.sin(elapsed * 1.1F) * 0.02F;
            size *= breathe;
            float x = (w - size) / 2.0F;
            float y = h * 0.32F - size / 2.0F;

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            logo.bind();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0.0F, logo.maxV());
            GL11.glVertex2f(x, y + size);
            GL11.glTexCoord2f(logo.maxU(), logo.maxV());
            GL11.glVertex2f(x + size, y + size);
            GL11.glTexCoord2f(logo.maxU(), 0.0F);
            GL11.glVertex2f(x + size, y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        /** Type-only header used when the pack ships no logo image. */
        private void drawWordmark(float w, float h, float alpha) {
            String text = UiConfig.title;
            if (font == null || text == null || text.isEmpty()) {
                return;
            }
            final float scale = 2.0F;
            int width = font.getStringWidth(text);
            float y = h * 0.32F;

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glPushMatrix();
            GL11.glScalef(scale, scale, 1.0F);
            int color = ((int) (clamp01(alpha) * 255.0F) << 24) | 0xFFFFFF;
            font.drawString(text, (int) (w / 2.0F / scale - width / 2.0F), (int) (y / scale), color, false);
            GL11.glPopMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // Thin white rule under the wordmark, same white as the bars.
            float ruleHalf = width * scale * 0.5F;
            float ruleY = y + 8.0F * scale + 5.0F;
            rectRGBA(w / 2.0F - ruleHalf, ruleY, w / 2.0F + ruleHalf, ruleY + 1.0F,
                    1.0F, 1.0F, 1.0F, alpha * 0.35F);
        }

        /**
         * FML's progress stack, drawn as plain white rules.
         *
         * The outermost bar is the overall load; the ones under it are whatever is
         * nested inside it right now. Showing all of them (rather than one merged
         * percentage) is what makes a long modpack load legible — you can see which
         * stage is actually stuck.
         */
        private void drawProgress(float w, float h, float alpha) {
            ProgressBar first = null;
            ProgressBar penult = null;
            ProgressBar last = null;
            for (Iterator<ProgressBar> it = ProgressManager.barIterator(); it.hasNext(); ) {
                ProgressBar bar = it.next();
                if (first == null) {
                    first = bar;
                } else {
                    penult = last;
                    last = bar;
                }
            }

            float target = first == null ? 0.0F : progressOf(first);
            shownProgress += (target - shownProgress) * 0.12F;
            if (target < shownProgress) {
                shownProgress = target; // a new bar restarts, so snap rather than rewind
            }

            float barWidth = Math.min(w * 0.52F, 320.0F);
            float barX = (w - barWidth) / 2.0F;
            float y = h * 0.62F;
            float spacing = 26.0F;

            if (first == null) {
                // Nothing is reporting progress, and that is not the same as nothing
                // happening. Mod discovery, class transformation and the long stretch
                // before FML opens its first bar are exactly that: on a small pack
                // they pass in a blink, on a large one they are tens of seconds during
                // which this screen has no bar, no logo (it is optional and often
                // missing), no tips (off by default) and nothing but a wordmark on
                // black — which reads, correctly, as a hung game.
                //
                // An indeterminate sweep says the one thing that is actually known:
                // it is still working.
                drawSweep(barX, y, barWidth, alpha);
                return;
            }

            drawBar(first, barX, y, barWidth, alpha, shownProgress);
            y += spacing;
            if (penult != null) {
                drawBar(penult, barX, y, barWidth, alpha * 0.75F, progressOf(penult));
                y += spacing;
            }
            if (last != null) {
                drawBar(last, barX, y, barWidth, alpha * 0.75F, progressOf(last));
            }
        }

        /**
         * The indeterminate sweep, for when there is no progress to report.
         *
         * Same shape as the track a real bar draws, so the moment FML opens its first
         * bar the sweep is replaced in place rather than the layout jumping.
         */
        private void drawSweep(float x, float y, float width, float alpha) {
            rectRGBA(x, y, x + width, y + 1.0F, 1.0F, 1.0F, 1.0F, 0.18F * alpha);

            float sweep = width * 0.26F;
            float t = (elapsed * 0.5F) % 1.0F;
            // Eased, so it slows at the ends instead of wrapping at full speed.
            float eased = t < 0.5F ? 2.0F * t * t : 1.0F - 2.0F * (1.0F - t) * (1.0F - t);
            float head = x - sweep + (width + sweep) * eased;
            float from = Math.max(x, head);
            float to = Math.min(x + width, head + sweep);
            if (to > from) {
                rectRGBA(from, y, to, y + 1.0F, 1.0F, 1.0F, 1.0F, 0.9F * alpha);
            }
        }

        private void drawBar(ProgressBar bar, float x, float y, float width, float alpha, float progress) {
            if (font != null) {
                drawCentered(describe(bar), x + width / 2.0F, y - 12.0F, 0xFFFFFF, 0.75F * alpha);
            }
            // Track: a hairline so the full length always reads, even at 0%.
            rectRGBA(x, y, x + width, y + 1.0F, 1.0F, 1.0F, 1.0F, 0.18F * alpha);
            float fill = width * clamp01(progress);
            if (fill > 0.0F) {
                rectRGBA(x, y, x + fill, y + 1.0F, 1.0F, 1.0F, 1.0F, 0.95F * alpha);
                // Short bright head, the one bit of motion on an otherwise static frame.
                rectRGBA(Math.max(x, x + fill - 12.0F), y - 0.5F, x + fill, y + 1.5F,
                        1.0F, 1.0F, 1.0F, 0.55F * alpha);
            }
        }

        private void drawFooter(float w, float h, float alpha) {
            if (font == null) {
                return;
            }
            String[] tips = UiConfig.splashTips;
            if (!UiConfig.showTips || tips.length == 0) {
                return;
            }
            // One tip every 4.5s, cross-fading so the swap is not a hard cut.
            float period = 4.5F;
            int index = (int) (elapsed / period) % tips.length;
            float phase = (elapsed % period) / period;
            float tipAlpha = clamp01(Math.min(phase / 0.15F, (1.0F - phase) / 0.15F));

            drawCentered(tips[index], w / 2.0F, h - 26.0F, 0xFFFFFF, tipAlpha * 0.5F * alpha);
        }

        private static String describe(ProgressBar bar) {
            String message = bar.getMessage();
            if (message == null || message.isEmpty()) {
                return bar.getTitle();
            }
            // Plain ASCII only: Minecraft's default font sheet has no em dash and
            // renders anything outside it as a section sign.
            return bar.getTitle() + " - " + message;
        }

        private static float progressOf(ProgressBar bar) {
            int steps = bar.getSteps();
            if (steps <= 0) {
                return 0.0F;
            }
            return clamp01((float) bar.getStep() / steps);
        }

        // ------------------------------------------------------- primitives --

        private void drawCentered(String text, float centerX, float y, int rgb, float alpha) {
            if (alpha <= 0.01F) {
                return;
            }
            int color = ((int) (clamp01(alpha) * 255.0F) << 24) | (rgb & 0xFFFFFF);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int width = font.getStringWidth(text);
            font.drawString(text, (int) (centerX - width / 2.0F), (int) y, color, false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private void rect(float x1, float y1, float x2, float y2, int rgb, float alpha) {
            rectRGBA(x1, y1, x2, y2,
                    (rgb >> 16 & 0xFF) / 255.0F,
                    (rgb >> 8 & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F,
                    alpha);
        }

        private void rectRGBA(float x1, float y1, float x2, float y2, float r, float g, float b, float a) {
            if (a <= 0.003F) {
                return;
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(r, g, b, a);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
            GL11.glEnd();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private void gradient(float x1, float y1, float x2, float y2, int leftRgb, int rightRgb, float alpha) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_QUADS);
            setColor(leftRgb, alpha);
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            setColor(rightRgb, alpha);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
            GL11.glEnd();
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void setColor(int rgb, float alpha) {
            GL11.glColor4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F, alpha);
        }

        // ---------------------------------------------------------- context --

        private void acquireContext() {
            lock.lock();
            try {
                Display.getDrawable().makeCurrent();
            } catch (LWJGLException e) {
                throw new RuntimeException("Splash thread could not take the GL context", e);
            }
            int bg = UiConfig.colorBackground;
            GL11.glClearColor((bg >> 16 & 0xFF) / 255.0F, (bg >> 8 & 0xFF) / 255.0F,
                    (bg & 0xFF) / 255.0F, 1.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        private void releaseContext() {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayWidth = Display.getWidth();
            mc.displayHeight = Display.getHeight();
            mc.resize(mc.displayWidth, mc.displayHeight);
            // Restore the state the game expects to find on its own context, but
            // clear to black rather than vanilla's white: any gap between the
            // loading screen and the title screen should stay dark, not flash.
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            try {
                Display.getDrawable().releaseContext();
            } catch (LWJGLException e) {
                log("splash thread could not release the GL context", e);
            } finally {
                lock.unlock();
            }
        }

        private void present() {
            // Hold FML's mutex across the swap so the main thread's window-message
            // pump does not contend for the display lock at the same moment.
            if (displayMutex != null) {
                displayMutex.acquireUninterruptibly();
            }
            try {
                Display.update();
            } finally {
                if (displayMutex != null) {
                    displayMutex.release();
                }
            }
        }

        private void deleteTextures() {
            if (background != null) {
                background.delete();
            }
            if (logo != null) {
                logo.delete();
            }
            if (fontTexture != null) {
                fontTexture.delete();
            }
            if (font != null) {
                // The unicode pages the font loaded on demand are its own textures.
                font.delete();
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}

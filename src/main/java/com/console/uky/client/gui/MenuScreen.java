package com.console.uky.client.gui;

import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.AmbientParticles;
import com.console.uky.client.render.BlackHole;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.Quality;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

/**
 * Shared base for every UKY screen: wall-clock timing, an entrance fade, and
 * the layered ambient backdrop (artwork, particles, vignette, grain, scanlines).
 *
 * Subclasses override {@link #drawContent} instead of {@code drawScreen} so the
 * backdrop, the widget pass and the overlay pass always happen in the right
 * order.
 */
public abstract class MenuScreen extends GuiScreen {

    protected static final ResourceLocation BACKGROUND =
            new ResourceLocation("uky", "textures/gui/background.png");
    /** Native size of {@link #BACKGROUND}; used to letterbox-cover it correctly. */
    protected static final int BACKGROUND_W = 1920;
    protected static final int BACKGROUND_H = 1080;

    private static final float FADE_IN_SECONDS = 0.45F;

    private static final AmbientParticles particles = new AmbientParticles();
    private final Random grainRandom = new Random();
    /** Shared so the starfield keeps its position when moving between screens. */
    protected static final BlackHole blackHole = new BlackHole();

    // Smoothed camera on the hole. Static because it has to survive the screen
    // swap: that is precisely what makes moving between screens read as one
    // continuous shot rather than a cut.
    protected static float cameraX;
    protected static float cameraY;
    protected static float cameraRadius;
    private static int cameraWidth;
    private static int cameraHeight;
    /** False until the first menu of the session has placed the camera. */
    private static boolean cameraPlaced;

    private long lastFrameNanos;
    /** Seconds since this screen was opened. */
    protected float elapsed;
    /** Seconds since the previous frame, clamped so alt-tab does not teleport animations. */
    protected float delta;
    /** Entrance fade, 0..1. */
    protected float fadeAlpha;
    /**
     * Where on the entrance curve this screen starts, in seconds.
     *
     * Zero for a screen opening cold. A screen taking over from another one starts
     * at whatever point of the curve produces the fade it inherited, so the fade
     * carries on from there instead of restarting — see {@link #initGui()}.
     */
    private float fadeOffset;

    /** Whether the next screen should reuse this screen's fade alpha. */
    private static boolean fadePreserved;
    /** The fade alpha captured from the previous screen. */
    private static float preservedFadeAlpha;

    /** Whether the next screen should reuse black hole camera/animation state. */
    private static boolean holePreserved;

    // ---- closing animation ----
    /**
     * What to show once the exit animation finishes, or null while the screen is
     * simply open. Screens that opt in call {@link #closeWith} instead of displaying
     * the next screen directly.
     */
    private Runnable pendingClose;
    private float closing;
    private static final float CLOSE_SECONDS = 0.16F;

    /**
     * Plays the entrance in reverse, then runs {@code action}.
     *
     * Screens animated in and then vanished on a frame boundary, which read as the
     * menu being torn away rather than dismissed — most obviously on the pause menu,
     * where the world is still visible behind it the whole time. Short on purpose:
     * this is in the way of what the player asked for, so it has to be felt rather
     * than waited for.
     */
    protected void closeWith(Runnable action) {
        if (this.pendingClose != null) {
            return;
        }
        this.pendingClose = action;
        this.closing = 0.0F;
    }

    /**
     * Instant seamless transition to another {@link MenuScreen}.
     * Preserves fade alpha and black hole camera — the next screen picks up exactly where this one left off.
     */
    protected void switchTo(MenuScreen next) {
        fadePreserved = true;
        preservedFadeAlpha = this.fadeAlpha;
        holePreserved = true;
        this.pendingClose = null; // cancel any pending close animation
        this.mc.displayGuiScreen(next);
    }

    /**
     * Instant seamless return to parent screen (if it's a MenuScreen).
     */
    protected void switchBack() {
        if (this.parent instanceof MenuScreen) {
            fadePreserved = true;
            preservedFadeAlpha = this.fadeAlpha;
            holePreserved = true;
            this.pendingClose = null;
            this.mc.displayGuiScreen(this.parent);
        } else {
            closeWith(new Runnable() {
                @Override
                public void run() {
                    mc.displayGuiScreen(parent);
                }
            });
        }
    }

    protected boolean isClosing() {
        return this.pendingClose != null;
    }

    /**
     * Drives the exit and fires the pending action when it is done.
     *
     * @return the opacity everything on this screen should be drawn at
     */
    private float advanceClose() {
        if (this.pendingClose == null) {
            return 1.0F;
        }
        this.closing += this.delta / CLOSE_SECONDS;
        if (this.closing >= 1.0F) {
            Runnable action = this.pendingClose;
            this.pendingClose = null;
            action.run();
            return 0.0F;
        }
        return 1.0F - Ease.inCubic(this.closing);
    }

    /** Parent to return to; may be null (then Escape closes to the world/main menu). */
    protected final GuiScreen parent;

    protected MenuScreen(GuiScreen parent) {
        this.parent = parent;
    }

    // ----------------------------------------------------------- own scaling --

    /**
     * Device pixels per unit of the space these screens lay out in, and the factor
     * that converts that space to the one the game's projection is set up for.
     */
    /**
     * Layout width the screens are designed against, in GUI units.
     *
     * Wide enough for two comfortable columns of translated labels, narrow enough
     * that text stays a readable physical size on a 1080p display.
     */
    private static final int TARGET_UNIT_WIDTH = 620;

    /**
     * Vertical floor, in GUI units — vanilla's own minimum, which every screen here
     * was laid out to clear. Setting it higher looks harmless and is not: a window
     * a few units under the floor drops a whole scale step, and on a short window
     * that step goes all the way to 1 and leaves the text half the size it should be.
     */
    private static final int MIN_UNIT_HEIGHT = 240;

    /** Guards against a nonsense factor if the display size is ever reported wrong. */
    private static final int MAX_SCALE_FACTOR = 8;

    private int uiScaleFactor = 1;
    // Per axis, because the two spaces round their dimensions up independently. A
    // single averaged factor left the last couple of pixels down one edge outside
    // everything this screen drew, showing whatever was behind the GUI.
    private float uiScaleX = 1.0F;
    private float uiScaleY = 1.0F;

    /**
     * Lays this screen out in its own coordinate space, independent of the GUI
     * Scale setting.
     *
     * That setting exists for the HUD and for players who want bigger text in game,
     * and it should keep working — but it hands a screen anywhere from 320 to 1920
     * units of width depending on where the slider is, and no single layout is
     * honest across that range. So these menus always use the size Auto would give,
     * which stays within roughly 320-570 by 240-270 on any monitor, and a matching
     * transform is applied at draw time. Mouse input needs no special handling:
     * {@code handleMouseInput} derives its coordinates from {@code width} and
     * {@code height}, which are now ours.
     */
    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        // The width and height handed in are deliberately ignored. During a resize
        // they can still describe the old window while mc.displayWidth already
        // describes the new one, and deriving the transform from a mismatched pair
        // drew the whole interface into one corner of the screen with last frame's
        // image left showing around it. Both spaces come off the framebuffer below,
        // which cannot disagree with itself.
        this.uiScaleFactor = autoScaleFactor(mc);
        int ownWidth = MathHelper.ceiling_double_int((double) mc.displayWidth / this.uiScaleFactor);
        int ownHeight = MathHelper.ceiling_double_int((double) mc.displayHeight / this.uiScaleFactor);
        super.setWorldAndResolution(mc, ownWidth, ownHeight);
    }

    /**
     * Recomputes the transform between our units and the game's.
     *
     * Done every frame rather than cached at layout time. The projection is set up
     * from a fresh {@code ScaledResolution} on the frame it is drawn, so anything
     * cached one resize earlier is a frame out of date — and one frame of a wrong
     * scale is exactly the glitch that shows.
     */
    private void syncScale() {
        Minecraft mc = this.mc;
        this.uiScaleFactor = autoScaleFactor(mc);
        ScaledResolution game = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        this.uiScaleX = this.width <= 0 ? 1.0F : (float) game.getScaledWidth() / this.width;
        this.uiScaleY = this.height <= 0 ? 1.0F : (float) game.getScaledHeight() / this.height;
    }

    /** The scale factor {@code ScaledResolution} picks when GUI Scale is Auto. */
    /**
     * Picks the GUI scale by how much layout room it leaves, not by how big the
     * window is.
     *
     * Vanilla's rule is "the largest factor that still leaves 320x240 units", which
     * means the unit count lurches around as the window changes: a 1270-wide window
     * lands on factor 2 and gets 635 units of width, while maximising to 1920 pushes
     * it to factor 4 and *drops* it to 480. Every screen here is laid out in those
     * units, so making the window bigger made the content narrower and the labels
     * started truncating — the exact opposite of what enlarging a window should do.
     *
     * Targeting a unit width instead pins the layout: 1280, 1920, 2560 and 3840 all
     * land within a few units of {@link #TARGET_UNIT_WIDTH}, so the interface looks
     * the same at every size and only the physical pixel size of the text changes.
     */
    private static int autoScaleFactor(Minecraft mc) {
        int factor = Math.round(mc.displayWidth / (float) TARGET_UNIT_WIDTH);
        factor = Math.max(1, Math.min(MAX_SCALE_FACTOR, factor));

        // Short windows get a smaller factor so the taller screens still have room
        // for their content; width is the target, height is the constraint.
        while (factor > 1 && mc.displayHeight / factor < MIN_UNIT_HEIGHT) {
            factor--;
        }
        return factor;
    }

    // ------------------------------------------------------------- lifecycle --

    /**
     * The size {@link #initGui()} last ran at, or -1 if it has never run.
     *
     * Forge lets any mod cancel {@code GuiScreenEvent.InitGuiEvent.Pre}, and
     * {@code GuiScreen.setWorldAndResolution} then sets the width, the height and the
     * font renderer and <em>skips {@code initGui} entirely</em>. Everything a screen
     * builds there — the widgets, the layout, the text fields — is simply never
     * built, and the first frame dereferences a null. It is not hypothetical: a pack
     * with a hundred mods in it has one that does this, and the crash it produces
     * names us, because we are what was drawing.
     *
     * <p>So the initialisation is no longer allowed to depend on being called. See
     * {@link #ensureInitialised()}.
     */
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    /**
     * Runs the initialisation if nothing else did.
     *
     * Cheap enough to sit at the top of the draw: two integer comparisons on a frame
     * where everything is in order, and on the frame where it is not, exactly the work
     * that was skipped. Keyed on the size rather than on a bare flag so that a resize
     * whose init was also cancelled is caught by the same test.
     */
    private void ensureInitialised() {
        if (this.laidOutWidth != this.width || this.laidOutHeight != this.height) {
            initGui();
        }
    }

    @Override
    public void initGui() {
        this.laidOutWidth = this.width;
        this.laidOutHeight = this.height;
        this.buttonList.clear();
        this.lastFrameNanos = System.nanoTime();
        this.elapsed = 0.0F;
        this.delta = 0.0F;
        if (fadePreserved) {
            this.fadeAlpha = preservedFadeAlpha;
            // Not just the alpha: the curve it came off has to be resumed too.
            // Setting the alpha alone did nothing visible, because the first frame
            // recomputed it from an elapsed of zero and threw it away — which is
            // exactly the blink that showed on every button press. Starting the
            // clock part-way along the curve is what makes the handover seamless.
            this.fadeOffset = FADE_IN_SECONDS * Ease.outCubicInverse(preservedFadeAlpha);
            fadePreserved = false;
        } else {
            this.fadeAlpha = 0.0F;
            this.fadeOffset = 0.0F;
        }
        boolean holeInherited = holePreserved;
        if (holeInherited) {
            holePreserved = false;
        } else {
            this.particles.resize(this.width, this.height);
            blackHole.resize(this.width, this.height);
        }
        buildLayout();

        // The very first menu of the session starts on its own angle rather than
        // swinging in from wherever the camera happened to be initialised.
        if (!cameraPlaced && !holeInherited) {
            cameraPlaced = true;
            blackHole.snapTo(blackHolePose());
        }
    }

    /** Register buttons and compute layout here; called from {@link #initGui()}. */
    protected abstract void buildLayout();

    /**
     * Rebuilds the controls in place, without restarting the entrance animation.
     *
     * For anything that changes the layout while the screen stays open — switching a
     * settings tab, filtering a list. Calling {@code initGui} for that reset the
     * fade to zero, so every tab click blinked the whole screen through black.
     */
    protected void relayout() {
        this.buttonList.clear();
        buildLayout();
    }

    /** Draw screen-specific content. Widgets are drawn afterwards. */
    protected abstract void drawContent(int mouseX, int mouseY);

    /**
     * Opacity applied to every widget. Defaults to the entrance fade; screens with
     * their own intro can hold the controls back until it finishes.
     */
    protected float widgetFade() {
        return this.fadeAlpha;
    }

    /**
     * Multiplied into everything this screen draws. 1 normally, easing to 0 while
     * closing — {@link #fadeAlpha} is the entrance and must not be rewound.
     */
    protected float closeFade() {
        return this.closeFade;
    }

    private float closeFade = 1.0F;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ensureInitialised();
        tickTiming();
        syncScale();

        // Folded into fadeAlpha so every existing call site dims on the way out
        // without each screen having to know about closing at all.
        this.closeFade = advanceClose();
        if (this.pendingClose == null && this.closeFade <= 0.0F) {
            // The action just ran and swapped the screen; nothing left to draw.
            return;
        }
        this.fadeAlpha *= this.closeFade;

        // These coordinates arrive in the game's units; everything below works in
        // ours. Scissor rects go straight to GL in device pixels, so Draw is told
        // the conversion too.
        int localX = (int) (mouseX / this.uiScaleX);
        int localY = (int) (mouseY / this.uiScaleY);
        Draw.setClipScale(this.uiScaleFactor);
        GL11.glPushMatrix();
        GL11.glScalef(this.uiScaleX, this.uiScaleY, 1.0F);
        try {
            drawBackdrop();
            drawContent(localX, localY);

            // Buttons animate off `delta`, so hand it to them before they draw.
            //
            // Deliberately not touching `visible` here. It used to be set from the fade
            // on this line, to stop a faded-out widget swallowing clicks — but a screen
            // that scrolls its rows sets the same flag from its scroll a moment earlier
            // in drawContent, and the assignment here landed on top of it and undid it
            // every frame. One flag cannot have two owners, so the fade now refuses
            // input in MenuButton itself and `visible` belongs to the screen alone.
            float widgetFade = widgetFade();
            List<?> buttons = this.buttonList;
            for (int i = 0; i < buttons.size(); i++) {
                Object o = buttons.get(i);
                if (o instanceof MenuButton) {
                    ((MenuButton) o).advance(this.delta, widgetFade);
                }
            }
            super.drawScreen(localX, localY, partialTicks);

            drawOverlay();
        } finally {
            GL11.glPopMatrix();
            Draw.clearClipScale();
        }
    }

    private void tickTiming() {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        // A stall (alt-tab, world load) must not fast-forward every animation.
        this.delta = Math.min(dt, 0.1F);
        this.elapsed += this.delta;
        Transitions.update(this.delta);
        this.fadeAlpha = Ease.outCubic((this.elapsed + this.fadeOffset) / FADE_IN_SECONDS);

        particles.update(this.delta);
    }

    /**
     * Suppresses vanilla's default background (black screen) because {@link #drawBackdrop()}
     * already draws a full-screen opaque backdrop. Without this, the first frame of a new
     * screen shows a black flash from {@link GuiScreen#drawDefaultBackground()}.
     */
    @Override
    public void drawDefaultBackground() {
    }

    // -------------------------------------------------------------- backdrop --

    /** Artwork + tint + particles. Override to change the artwork layer only. */
    protected void drawBackdrop() {
        // Deliberately overdrawn well past the screen. Our units and the game's do
        // not always land on exactly the same rectangle — rounding, or a resize
        // caught mid-frame — and a base fill that stops at our own edge leaves a
        // strip of whatever was in the buffer before. Overdrawing costs nothing and
        // cannot be got wrong.
        Draw.rect(-this.width, -this.height, this.width * 2, this.height * 2, Theme.background);
        if (isVoid()) {
            // Past the horizon. Nothing to draw but the dark.
            return;
        }
        // The overdrawn base fill above is opaque, so anything drawn over it is safe.
        drawBackgroundArt();
        drawBackgroundTint();
        // Dust drifting up from the floor makes sense in a room, not in space —
        // the black hole supplies its own moving matter.
        if (!isBlackHoleBackground()) {
            particles.render(this.fadeAlpha * 0.85F);
        }
    }

    protected void drawBackgroundArt() {
        if (isBlackHoleBackground()) {
            blackHole.update(this.delta);
            advanceCamera(blackHoleCenterX(), blackHoleCenterY(), blackHoleRadius());
            blackHole.lookFrom(blackHolePose());
            blackHole.render(cameraX, cameraY, cameraRadius,
                    this.fadeAlpha * blackHoleIntensity(), 1.0F);
            return;
        }
        // "image" mode needs artwork the pack supplies; nothing ships by default,
        // so a missing file degrades to the solid backdrop instead of the
        // missing-texture checkerboard.
        if ("solid".equals(UiConfig.background) || !hasBackgroundImage()) {
            return;
        }
        float zoom = 1.06F;
        float panX = 0.0F;
        float panY = 0.0F;
        if (Quality.backgroundDrift()) {
            // Two prime-ish periods keep the loop from feeling metronomic.
            zoom = 1.06F + (float) Math.sin(this.elapsed * 0.07F) * 0.035F;
            panX = (float) Math.sin(this.elapsed * 0.043F) * 0.6F;
            panY = (float) Math.cos(this.elapsed * 0.031F) * 0.4F;
        }
        Draw.textureCover(BACKGROUND, 0, 0, this.width, this.height,
                BACKGROUND_W, BACKGROUND_H, zoom, panX, panY,
                Draw.withAlpha(0xFFFFFF, this.fadeAlpha));
    }

    /**
     * Eases the hole toward this screen's framing.
     *
     * Opening the options screen therefore pulls the camera back and re-centres it
     * instead of cutting; going back pushes in again. On a resize the camera snaps,
     * because gliding across a window that just changed size looks like a glitch
     * rather than a move.
     */
    protected void advanceCamera(float targetX, float targetY, float targetRadius) {
        if (cameraWidth != this.width || cameraHeight != this.height || cameraRadius <= 0.0F) {
            cameraWidth = this.width;
            cameraHeight = this.height;
            cameraX = targetX;
            cameraY = targetY;
            cameraRadius = targetRadius;
            return;
        }
        cameraX = Ease.approach(cameraX, targetX, 0.20F, this.delta);
        cameraY = Ease.approach(cameraY, targetY, 0.20F, this.delta);
        cameraRadius = Ease.approach(cameraRadius, targetRadius, 0.20F, this.delta);
    }

    /**
     * Screens on the far side of the dive. They sit on plain black — the hole is
     * what was fallen into, so it is not also in the room.
     */
    protected boolean isVoid() {
        return false;
    }

    protected boolean isBlackHoleBackground() {
        // The preset has the last word over the background setting, not the other way
        // round: potato exists for hardware where the cheapest trace is still too much.
        return "blackhole".equals(UiConfig.background) && Quality.blackHole();
    }

    /** Cached across screens: probing the resource manager every frame is wasteful. */
    private static Boolean backgroundImagePresent;

    protected boolean hasBackgroundImage() {
        if (backgroundImagePresent == null) {
            try {
                this.mc.getResourceManager().getResource(BACKGROUND);
                backgroundImagePresent = Boolean.TRUE;
            } catch (java.io.IOException e) {
                backgroundImagePresent = Boolean.FALSE;
            }
        }
        return backgroundImagePresent.booleanValue();
    }

    protected float blackHoleCenterX() {
        return this.width * 0.5F;
    }

    protected float blackHoleCenterY() {
        return this.height * 0.42F;
    }

    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.085F;
    }

    /**
     * How present the hole is on this screen. The title screen shows it in full;
     * everywhere else it is set decoration behind a panel and must not compete
     * with the controls on top of it.
     */
    protected float blackHoleIntensity() {
        return 0.45F;
    }

    /**
     * Which rung of {@link LensLibrary}'s pose ladder this screen is seen from.
     * Different screens picking different rungs is what gives the hole something
     * to turn between; the renderer eases from wherever it currently is.
     */
    protected int blackHolePose() {
        return LensLibrary.POSE_EDGE_ON;
    }

    /** Darkens the artwork top and bottom so text stays legible over any image. */
    protected void drawBackgroundTint() {
        // The black hole is already mostly black and its own light is the subject —
        // washing it out with a scrim would defeat the point.
        float strength = isBlackHoleBackground() ? 0.35F : 1.0F;
        int top = Draw.withAlpha(Theme.background, 0.75F * strength * this.fadeAlpha);
        int mid = Draw.withAlpha(Theme.background, 0.25F * strength * this.fadeAlpha);
        int bottom = Draw.withAlpha(Theme.background, 0.85F * strength * this.fadeAlpha);
        Draw.gradientV(0, 0, this.width, this.height * 0.45F, top, mid);
        Draw.gradientV(0, this.height * 0.45F, this.width, this.height, mid, bottom);
    }

    // --------------------------------------------------------------- overlay --

    /** Post-processing drawn over everything, widgets included. */
    protected void drawOverlay() {
        if (Quality.vignette()) {
            Draw.vignette(this.width, this.height, 0.85F * this.fadeAlpha, 0xFF000000);
        }
        // Grain over the starfield just turns it to mush — the black hole already
        // supplies all the texture the backdrop needs.
        if (Quality.filmGrain() && !isBlackHoleBackground()) {
            drawFilmGrain();
        }
        if (Quality.scanlines()) {
            Draw.scanlines(this.width, this.height, 3.0F, Draw.withAlpha(0x000000, 0.10F * this.fadeAlpha));
        }

        // The dive covers everything, including the widgets.
        float blackout = Transitions.blackout();
        if (blackout > 0.002F) {
            Draw.rect(0, 0, this.width, this.height, Draw.withAlpha(0x000000, blackout));
        }
    }

    private void drawFilmGrain() {
        int specks = Math.max(40, (this.width * this.height) / 900);
        for (int i = 0; i < specks; i++) {
            int gx = grainRandom.nextInt(this.width);
            int gy = grainRandom.nextInt(this.height);
            float a = (0.02F + grainRandom.nextFloat() * 0.05F) * this.fadeAlpha;
            Draw.rect(gx, gy, gx + 1, gy + 1, Draw.withAlpha(0xFFFFFF, a));
        }
    }

    // ---------------------------------------------------------------- pieces --

    /** Section heading with an accent rule underneath. */
    protected void drawHeading(String title, int centerX, int y) {
        int color = Draw.withAlpha(Theme.text, this.fadeAlpha);
        this.drawCenteredString(this.fontRendererObj, title, centerX, y, color);

        float ruleWidth = Math.max(60, this.fontRendererObj.getStringWidth(title) * 0.7F);
        float ruleY = y + 13;
        Draw.gradientH(centerX - ruleWidth / 2, ruleY, centerX, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.8F * this.fadeAlpha));
        Draw.gradientH(centerX, ruleY, centerX + ruleWidth / 2, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.8F * this.fadeAlpha), Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** Small dim text, left-aligned. */
    protected void drawHint(String text, int x, int y) {
        this.fontRendererObj.drawString(text, x, y, Draw.withAlpha(Theme.textDim, 0.85F * this.fadeAlpha));
    }

    // ----------------------------------------------------------------- input --

    /**
     * Input reaches a screen before its first frame does, so the same guard is needed
     * here — a click landing on a screen that was never initialised would otherwise
     * be handled against a layout that does not exist yet.
     */
    @Override
    public void handleInput() {
        ensureInitialised();
        super.handleInput();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        onAction(button);
    }

    /** Handle a button press. */
    protected abstract void onAction(GuiButton button);

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    // ------------------------------------------------------------------ text --

    /**
     * Draws text left-aligned at {@code x}, cut to {@code maxWidth} with an ellipsis.
     *
     * Every string in this interface that comes from outside it — a translation, a
     * world name, a server MOTD, a resource pack description — can be any length at
     * all, while the box it goes in is fixed by the layout. Drawn plainly it simply
     * kept going, over the widget beside it and out of the panel. Anything variable
     * goes through here or {@link #drawFittedRight} instead.
     *
     * @return the width actually drawn
     */
    public int drawFitted(String text, int x, int y, int maxWidth, int colour) {
        if (text == null || maxWidth <= 0) {
            return 0;
        }
        String fitted = fit(text, maxWidth);
        this.fontRendererObj.drawString(fitted, x, y, colour);
        return this.fontRendererObj.getStringWidth(fitted);
    }

    /** As {@link #drawFitted}, but the text ends at {@code right}. */
    public int drawFittedRight(String text, int right, int y, int maxWidth, int colour) {
        if (text == null || maxWidth <= 0) {
            return 0;
        }
        String fitted = fit(text, maxWidth);
        int width = this.fontRendererObj.getStringWidth(fitted);
        this.fontRendererObj.drawString(fitted, right - width, y, colour);
        return width;
    }

    /** As {@link #drawFitted}, but centred on {@code cx}. */
    public int drawFittedCentred(String text, int cx, int y, int maxWidth, int colour) {
        if (text == null || maxWidth <= 0) {
            return 0;
        }
        String fitted = fit(text, maxWidth);
        int width = this.fontRendererObj.getStringWidth(fitted);
        this.fontRendererObj.drawString(fitted, cx - width / 2, y, colour);
        return width;
    }

    /**
     * Trims to fit, marking the cut with an ellipsis.
     *
     * The ellipsis matters: a silently chopped word reads as a typo, whereas a
     * trailing "…" reads as "there is more here", which is the truth.
     */
    public String fit(String text, int maxWidth) {
        FontRenderer font = this.fontRendererObj;
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsis = font.getStringWidth("...");
        if (maxWidth <= ellipsis) {
            return font.trimStringToWidth(text, maxWidth);
        }
        return font.trimStringToWidth(text, maxWidth - ellipsis).trim() + "...";
    }

}

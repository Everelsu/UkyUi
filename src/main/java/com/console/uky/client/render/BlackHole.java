package com.console.uky.client.render;

import com.console.uky.UkyUI;
import com.console.uky.config.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
import java.util.Random;

/**
 * Black hole rendered from a traced lensing table.
 *
 * The geometry comes from {@link LensMap}, computed once on a background thread:
 * for every texel it already knows whether that ray fell in, escaped, or landed
 * on the disk, and where. This class does the cheap half — shading the disk and
 * pushing the result to a texture — every frame, which is what makes the disk
 * actually turn instead of sitting there as a static ring.
 *
 * Stars are drawn separately as geometry rather than baked into the texture: at
 * the table's resolution they would be blurry smears, and the analytic point-mass
 * lens gives sharp doubled images and a proper Einstein ring for almost nothing.
 */
public final class BlackHole {

    // ---- starfield -----------------------------------------------------------

    private static final int STAR_COUNT = 420;
    /**
     * Stars visibly spiralling in. Deliberately few: this is the rare event that
     * catches the eye, and a constant stream of them reads as noise.
     */
    private static final int INFALL_COUNT = 14;
    private static final float SPAWN_RADIUS = 9.0F;
    private static final float CAPTURE_RADIUS = 1.02F;
    /** Matches the traced shadow so the overlays line up with the texture. */
    private static final float SHADOW_SCALE = 1.0F;
    private static final float INFALL_TILT = 0.22F;

    private float[] starX;
    private float[] starY;
    private float[] starMag;
    private float[] starHue;

    private float[] fallR;
    private float[] fallTheta;
    private float[] fallIncline;
    private float[] fallMag;
    private float[] fallSpin;

    private final Random random = new Random(0xB1ACC1E);

    private int width;
    private int height;

    // ---- lensing -------------------------------------------------------------


    /**
     * Scratch shared by every pose: the full-resolution one is the largest, so a
     * buffer sized for it serves all of them and nothing else needs allocating
     * while drawing.
     */
    private int[] argb;
    private IntBuffer staging;

    /**
     * Where the camera is on the pose ladder, and where it is heading. Fractional:
     * the two neighbouring poses are cross-faded so a turn is continuous rather
     * than a sequence of jumps between baked angles.
     */
    private float poseCurrent = LensLibrary.POSE_EDGE_ON;
    private float poseTarget = LensLibrary.POSE_EDGE_ON;

    /**
     * Tileable noise sampled bilinearly. Evaluating hash-based fbm per texel per
     * frame costs about ten times as much as reading a table, and at this scale
     * the difference is the whole frame budget.
     */
    private static final int NOISE_SIZE = 256;
    private static final int NOISE_MASK = NOISE_SIZE - 1;
    private static float[] noiseTable;
    /**
     * The resting pose is over a million texels, so re-shading all of it every
     * frame would cost more than the rest of the menu put together. Instead a
     * horizontal band is refreshed each frame; every texel still updates several
     * times a second, which is far more than slow-churning plasma needs. Poses
     * flicked past during a turn are small and get done in one go.
     */
    private static final int BANDS = 4;
    private int band;
    /** Ramps 0→1 once the resting pose lands, so the hole arrives in one piece. */
    private float readyFade;
    private float lastDelta;
    /** Latched after a rendering failure so it is reported once, not every frame. */
    private static boolean lensingDisabled;

    /**
     * Overall disk brightness. The Doppler boost alone tops out near 2, which is
     * physically right but reads as almost black on a menu; this lifts the whole
     * disk until the hot inner edge clips, which is what gives it the glare.
     */
    static final float DISK_GAIN = 3.6F;

    /**
     * How fast the disk turns, and how much structure it has.
     *
     * The rotation was always physically correct — every texel remembers the real
     * disk radius and azimuth its ray landed on, so the pattern shears exactly as
     * Kepler says. It just was not <em>visible</em>: a low-contrast wash has nothing
     * in it to track. Raising the contrast gives the disk filaments you can follow
     * as they sweep round and wind up against the slower outer material.
     */
    private static final float SPIN_RATE = 1.25F;
    static final float CONTRAST_FLOOR = 0.30F;
    static final float CONTRAST_RANGE = 1.45F;

    /**
     * Noise anisotropy. A low azimuthal frequency against a high radial one draws
     * the turbulence out into arcs that follow the flow, rather than the isotropic
     * blobs that read as static grain however fast they move.
     */
    static final float NOISE_AZIMUTH_FREQ = 1.9F;
    static final float NOISE_RADIAL_FREQ = 11.0F;

    /** Disk rotation phase and an independent turbulence phase. */
    /** Texture unit the baked turbulence is bound to. */
    private static final int NOISE_UNIT = 4;

    private float spin;
    private float churn;
    private float drift;

    // ---- lifecycle -----------------------------------------------------------

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        if (starX == null) {
            seedStars();
            seedInfall();
        }
        LensLibrary.warmUp();
    }

    /**
     * Starts the trace early, during mod loading, so it is finished long before the
     * title screen appears. Safe to call more than once.
     */
    public static void warmUp() {
        LensLibrary.warmUp();
    }

    /**
     * Aims the camera at a rung of the pose ladder. The move is eased, so a screen
     * change turns the hole rather than cutting to a new angle.
     */
    public void lookFrom(int poseIndex) {
        this.poseTarget = poseIndex;
    }

    /** Puts the camera on a pose immediately — for the first frame of a screen. */
    public void snapTo(int poseIndex) {
        this.poseTarget = poseIndex;
        this.poseCurrent = poseIndex;
    }

    private void seedStars() {
        starX = new float[STAR_COUNT];
        starY = new float[STAR_COUNT];
        starMag = new float[STAR_COUNT];
        starHue = new float[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = (random.nextFloat() - 0.5F) * 3.0F;
            starY[i] = (random.nextFloat() - 0.5F) * 2.4F;
            // Heavily skewed toward faint: a flatter distribution reads as confetti.
            float m = random.nextFloat();
            starMag[i] = 0.05F + m * m * m * m * 0.95F;
            starHue[i] = random.nextFloat();
        }
    }

    private void seedInfall() {
        fallR = new float[INFALL_COUNT];
        fallTheta = new float[INFALL_COUNT];
        fallIncline = new float[INFALL_COUNT];
        fallMag = new float[INFALL_COUNT];
        fallSpin = new float[INFALL_COUNT];
        for (int i = 0; i < INFALL_COUNT; i++) {
            respawnInfall(i);
            fallR[i] = 1.4F + random.nextFloat() * (SPAWN_RADIUS - 1.4F);
        }
    }

    private void respawnInfall(int i) {
        fallR[i] = SPAWN_RADIUS * (0.85F + random.nextFloat() * 0.5F);
        fallTheta[i] = random.nextFloat() * (float) Math.PI * 2.0F;
        float roll = random.nextFloat();
        fallIncline[i] = roll * roll;
        fallMag[i] = 0.30F + random.nextFloat() * 0.55F;
        fallSpin[i] = (0.7F + random.nextFloat() * 0.9F) * (random.nextFloat() < 0.85F ? 1.0F : -1.0F);
    }

    public void update(float deltaSeconds) {
        this.lastDelta = deltaSeconds;
        // Slower half-life than the position easing: the turn should read as the
        // camera swinging round, not snapping.
        this.poseCurrent = Ease.approach(this.poseCurrent, this.poseTarget, 0.16F, deltaSeconds);
        this.spin += deltaSeconds * SPIN_RATE;
        this.churn += deltaSeconds * 0.35F;
        this.drift += deltaSeconds * 0.006F;
        updateInfall(deltaSeconds);
    }

    /**
     * Advances the infalling stars.
     *
     * Not an orbit integration — angular speed is written straight from Kepler
     * (1/r^1.5) and the inward drift blows up as 1/r², which gives a wide slow arc
     * that whips into a tight spiral at the end. That is the shape the eye reads as
     * being pulled in.
     */
    private void updateInfall(float deltaSeconds) {
        for (int i = 0; i < INFALL_COUNT; i++) {
            float r = fallR[i];
            fallTheta[i] += fallSpin[i] * 1.9F / (float) Math.pow(Math.max(r, 0.6F), 1.5) * deltaSeconds;
            fallR[i] -= (0.8F + 7.0F / (r * r)) * deltaSeconds;
            // Orbits precess into the disk plane as they decay, like real accretion.
            fallIncline[i] -= fallIncline[i] * deltaSeconds * 0.55F;
            if (fallR[i] <= CAPTURE_RADIUS) {
                respawnInfall(i);
            }
        }
    }

    // ---- rendering -----------------------------------------------------------

    /**
     * @param cx        singularity position, screen space
     * @param cy        singularity position, screen space
     * @param radius    shadow radius in GUI pixels
     * @param intensity master fade, 0..1
     * @param warp      extra scale during the intro shock; 1 at rest
     */
    public void render(float cx, float cy, float radius, float intensity, float warp) {
        render(cx, cy, radius, intensity, warp, false);
    }

    /** @param mirrored kept for callers that want the image flipped outright */
    public void render(float cx, float cy, float radius, float intensity, float warp,
                       boolean mirrored) {
        if (intensity <= 0.01F || radius <= 0.5F) {
            return;
        }
        float shadow = radius * SHADOW_SCALE * warp;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawStars(cx, cy, shadow, shadow * 1.30F * warp, intensity);

        // Nothing at all until the resting pose lands, then the whole thing fades
        // up as a unit. Showing a stand-in silhouette first meant the core appeared
        // before the ring did, which read as a half-loaded image.
        if (!lensingDisabled) {
            try {
                if (useShader()) {
                    // Integrated per pixel: any resolution, any camera angle, no bake.
                    this.readyFade = 1.0F;
                    drawShaded(cx, cy, shadow, intensity);
                } else if (LensLibrary.isRestingPoseReady()) {
                    this.readyFade = Ease.approach(this.readyFade, 1.0F, 0.22F, lastDelta);
                    drawPoses(cx, cy, shadow, intensity * this.readyFade, mirrored);
                }
            } catch (Throwable t) {
                // A menu backdrop has no business taking the game down. Drop to the
                // starfield and carry on; the log has the reason.
                lensingDisabled = true;
                UkyUI.LOGGER.error("Black hole rendering failed; falling back to stars only", t);
            }
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawInfall(cx, cy, shadow, intensity);

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---- shader path ---------------------------------------------------------

    private static ShaderProgram shader;
    private static boolean shaderTried;
    /**
     * Reduced-size render target. Allocated once and only rebuilt when the size it
     * is asked for actually changes — re-creating it per frame, or on every window
     * focus change, is exactly the kind of thing that makes a menu hitch.
     */
    /**
     * The two most recent traces.
     *
     * {@code offscreen} always holds the newest and {@code previous} the one before
     * it, and the draw dissolves from one to the other over the gap between them.
     * That is what buys the low trace rate: without it a 7Hz disk is a slideshow,
     * and with it the motion is as smooth as the display, because what the eye
     * follows is the dissolve rather than the traces themselves.
     */
    private static OffscreenTarget offscreen = new OffscreenTarget();
    private static OffscreenTarget previous = new OffscreenTarget();
    /** How far the dissolve from {@link #previous} to {@link #offscreen} has run. */
    private static float blend = 1.0F;

    /**
     * Apparent shadow radius as a fraction of the field of view, in the shader's
     * units: the critical impact parameter over the camera distance.
     */
    private static final float SHADOW_ANGULAR = (float) (3.0 * Math.sqrt(3.0) / 85.0);
    /**
     * Quad half-extents in shadow radii.
     *
     * The disk reaches almost six shadow radii but is seen near edge-on, so it
     * needs far more room across than down. These match the framing the baked
     * tables had, so screens keep the sizes they were tuned with.
     */
    private static final float FRAME_HALF_H = 5.6F;
    private static final float FRAME_HALF_W = 10.0F;

    /**
     * The shader marches the disk in coarser sub-steps than the CPU table did, so
     * each sample carries more path length and the same gain comes out roughly
     * twice as bright. This puts it back.
     */
    /** The shader path needs less gain than the baked tables did. */
    private static final float SHADER_GAIN_TRIM = 0.45F;

    /**
     * Attempts left before the shader is given up on for good.
     *
     * The first attempt used to be the only one, which is fragile: it happens on the
     * first frame that draws the hole, and if the resource manager is mid-reload — or
     * another mod is still rearranging GL state at that moment — a single failure
     * demoted the whole session to the baked tables, whose photon ring is much fainter.
     * Retrying a few times costs nothing once it has succeeded, since the flag latches
     * on success.
     */
    private static int shaderAttemptsLeft = 3;

    /** Mixes the two most recent traces; see dissolve.fsh. */
    private static ShaderProgram dissolveShader;
    private static boolean dissolveTried;

    /**
     * The crossfade program, or null if it could not be built.
     *
     * Without it the draw shows the newest complete trace outright. That steps at
     * the trace rate, which is worse than a dissolve and better than the wrong one.
     */
    private static ShaderProgram dissolveProgram() {
        if (!dissolveTried) {
            dissolveTried = true;
            try {
                ShaderProgram program = new ShaderProgram(
                        new ResourceLocation("uky", "shaders/dissolve.vsh"),
                        new ResourceLocation("uky", "shaders/dissolve.fsh"));
                dissolveShader = program.isUsable() ? program : null;
            } catch (Throwable t) {
                dissolveShader = null;
            }
            if (dissolveShader == null) {
                UkyUI.LOGGER.info("Black hole: no crossfade shader; "
                        + "traces will be shown as they land");
            }
        }
        return dissolveShader;
    }

    private static boolean useShader() {
        if (!shaderTried && shaderAttemptsLeft > 0) {
            shaderAttemptsLeft--;
            if (ShaderProgram.isSupported()) {
                shader = new ShaderProgram(
                        new ResourceLocation("uky", "shaders/blackhole.vsh"),
                        new ResourceLocation("uky", "shaders/blackhole.fsh"));
                if (shader.isUsable()) {
                    shaderTried = true;
                    UkyUI.LOGGER.info("Black hole: rendering with GLSL");
                } else {
                    shader = null;
                    UkyUI.LOGGER.info("Black hole: shader load failed, {} attempt(s) left",
                            Integer.valueOf(shaderAttemptsLeft));
                    if (shaderAttemptsLeft == 0) {
                        shaderTried = true;
                        LensLibrary.forceTableFallback();
                    }
                }
            } else {
                // No GL2 at all; retrying cannot change that.
                shaderTried = true;
                LensLibrary.forceTableFallback();
            }
        }
        return shader != null && shader.isUsable();
    }

    /**
     * Draws the hole by integrating geodesics in the fragment shader.
     *
     * The camera pitch is a uniform, so the pose ladder stops being a flipbook of
     * baked stills and becomes what it always should have been: keyframes for a
     * continuously moving camera.
     */
    private void drawShaded(float cx, float cy, float shadow, float intensity) {
        float halfH = shadow * FRAME_HALF_H;
        float halfW = shadow * FRAME_HALF_W;

        int percent = Math.min(200, Math.max(50, UiConfig.blackHoleResolution));
        // Always through the buffer now, even at 100%. It costs one full-screen blit
        // and buys the ability to keep last frame's trace — see traceIsStale.
        if (offscreen.isUsable()
                && drawShadedOffscreen(cx, cy, halfW, halfH, intensity, percent)) {
            return;
        }
        drawShadedDirect(cx, cy, halfW, halfH, intensity);
    }

    // ---- trace rate ----------------------------------------------------------

    /**
     * Gap between traces on a settled menu.
     *
     * The trace is the whole cost of this effect — tens of milliseconds of it — so
     * what governs the load is how often it runs, not how fast it is. Seven times a
     * second is enough to carry the disk's drift once the dissolve is smoothing
     * between them, and it is roughly a seventh of the work of tracing every frame.
     */
    private static final long TRACE_INTERVAL_NANOS = 140_000_000L;

    /**
     * Gap while the camera is still easing to a new framing.
     *
     * A screen change swings the pose over a few hundred milliseconds. Dissolving
     * across that at the idle rate reads as the hole lagging behind the screen, so
     * the rate goes up for as long as the movement lasts and drops back after.
     */
    private static final long TRACE_INTERVAL_MOVING_NANOS = 50_000_000L;

    /** Pose change per trace above which the camera counts as still moving. */
    private static final float POSE_MOVING_EPSILON = 0.0015F;

    private static long lastTraceNanos;
    private static float lastTracePose = Float.NaN;
    /**
     * The interval the last trace was taken under.
     *
     * The dissolve has to run out over the gap it is actually covering. Dividing by
     * the idle interval while the camera was moving — and so tracing at the faster
     * rate — meant each new trace only ever reached a third of full strength before
     * the next replaced it, leaving the hole permanently smeared across two poses
     * for the whole of a screen change.
     */
    private static long lastTraceInterval = TRACE_INTERVAL_NANOS;

    /**
     * Whether the buffer needs re-tracing this frame.
     *
     * The picture depends only on the camera angle and the disk phase — not on where
     * the quad goes or how brightly it is drawn, both of which are applied on the way
     * out — so this is purely a question of how stale the image is allowed to get.
     *
     * <p>It used to also re-trace whenever the disk phase had moved more than a
     * hair, which sounded careful and meant the interval never applied at all: the
     * phase advances by {@code SPIN_RATE / fps} every frame, fourteen times that
     * threshold at 60fps, so the answer was always yes and the hole was traced on
     * every single frame. That is where the idle menu's GPU load came from. The
     * phase is now left to the clock, which is the only thing that was ever bounding
     * it, and the dissolve covers the difference.
     */
    private boolean traceIsStale() {
        long now = System.nanoTime();
        float pose = LensLibrary.elevationAt(this.poseCurrent);

        boolean moving = !Float.isNaN(lastTracePose)
                && Math.abs(pose - lastTracePose) > POSE_MOVING_EPSILON;
        long interval = moving ? TRACE_INTERVAL_MOVING_NANOS : TRACE_INTERVAL_NANOS;

        if (Float.isNaN(lastTracePose) || now - lastTraceNanos >= interval) {
            lastTraceNanos = now;
            lastTracePose = pose;
            lastTraceInterval = interval;
            return true;
        }
        return false;
    }

    /** 0 at the moment of a trace, 1 by the time the next one is due. */
    private static float blendProgress() {
        long elapsed = System.nanoTime() - lastTraceNanos;
        float t = elapsed / (float) lastTraceInterval;
        return t <= 0.0F ? 0.0F : (t >= 1.0F ? 1.0F : t);
    }


    /** Forces the next frame to re-trace, e.g. after the buffer was reallocated. */
    static void invalidateTrace() {
        lastTracePose = Float.NaN;
        // Nothing worth dissolving from: the next trace should simply appear.
        blend = 1.0F;
    }

    /**
     * Makes the current trace the previous one.
     *
     * The two targets exchange roles rather than one being copied into the other —
     * a full-resolution copy every trace would give back a good part of what the
     * lower trace rate just bought.
     */
    private static void swapBuffers() {
        OffscreenTarget spare = previous;
        previous = offscreen;
        offscreen = spare;
    }

    /**
     * Traces the hole into an off-screen buffer and resamples it onto the quad.
     *
     * Above 100% this is supersampling: the extra samples are averaged down by the
     * texture unit's linear filter, which is what takes the stair-steps off the
     * shadow rim and the photon ring. Below 100% it is the reverse trade, for
     * hardware that cannot afford the real thing.
     *
     * @return false if the buffer could not be prepared, so the caller falls back
     */
    private boolean drawShadedOffscreen(float cx, float cy, float halfW, float halfH,
                                        float intensity, int percent) {
        Minecraft mc = Minecraft.getMinecraft();

        // Sized from the window alone, never from the quad.
        //
        // The buffer's dimensions only set how densely the image is sampled — the
        // framing comes from uAspect and uFov — so any size renders the same
        // picture. Tracking the on-screen size looked like the thrifty choice and
        // was the opposite: the hole eases between framings whenever a screen
        // changes, so the requested size moved every frame, and every move tore
        // down a texture and a framebuffer and built new ones mid-frame. That is
        // what the hard flickering on opening the settings screen was.
        //
        // Off the window size it is allocated once and then left alone, and the
        // cost is fixed instead of scaling with whatever the animation is doing.
        int targetW = Math.max(64, mc.displayWidth * percent / 100);
        int targetH = Math.max(64, mc.displayHeight * percent / 100);

        // Only re-trace when the picture would actually differ. On an idle menu this
        // is the single biggest saving available: the blit below still runs every
        // frame, so nothing about the compositing changes.
        if (traceIsStale() || !offscreen.hasContent()) {
            // The trace that is about to be replaced becomes the one dissolved from,
            // so the two buffers alternate rather than one being copied to the other.
            if (offscreen.hasContent() && offscreen.matches(targetW, targetH)) {
                swapBuffers();
            }
            if (!offscreen.begin(targetW, targetH)) {
                return false;
            }

            // Inside the buffer the quad is the whole surface, so the projection is a
            // plain unit box rather than the GUI's ortho.
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, 1.0D, 1.0D, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Intensity is applied on the way out, so the buffer's contents stay valid
            // whatever the screen is fading through — which is also what lets a trace
            // be reused across frames that only differ in brightness or position.
            bindShaderUniforms(1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(0.0F, 1.0F);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(1.0F, 1.0F);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(1.0F, 0.0F);
            GL11.glEnd();
            ShaderProgram.unbind();

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            offscreen.end();
        } else if (!offscreen.matches(targetW, targetH)) {
            // The window changed under us; take the trace next frame rather than
            // stretching a buffer of the wrong size.
            invalidateTrace();
        }

        // Stretch it over the real rect. Still premultiplied, so the same blend.
        //
        // Two passes when a dissolve is in flight: the older trace at full strength,
        // then the newer one faded in on top of it. Drawn this way round there is no
        // dip in the middle of the crossing — the older image is never scaled down,
        // it is simply covered — and because consecutive traces are a seventh of a
        // second apart they differ by very little, so what covers what is not
        // something the eye can pick out.
        blend = blendProgress();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ShaderProgram dissolve = dissolveProgram();
        boolean canDissolve = dissolve != null && blend < 0.999F && previous.hasContent()
                && previous.matches(offscreen.getWidth(), offscreen.getHeight());

        if (canDissolve) {
            dissolve.bind();
            dissolve.set("uMix", blend);
            dissolve.set("uIntensity", intensity);
            dissolve.set("uPrevious", 0);
            dissolve.set("uCurrent", 1);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            offscreen.bindTexture();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            previous.bindTexture();

            blitQuad(cx, cy, halfW, halfH, 1.0F);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            ShaderProgram.unbind();
        } else {
            offscreen.bindTexture();
            blitQuad(cx, cy, halfW, halfH, intensity);
        }

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        return true;
    }

    /** Premultiplied quad covering the hole's rect. */
    private static void blitQuad(float cx, float cy, float halfW, float halfH, float alpha) {
        GL11.glColor4f(alpha, alpha, alpha, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(cx - halfW, cy - halfH);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(cx - halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(cx + halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(cx + halfW, cy - halfH);
        GL11.glEnd();
    }

    /** Sets every uniform the trace needs. Shared by both draw paths. */
    private void bindShaderUniforms(float intensity) {
        shader.bind();
        shader.set("uAspect", FRAME_HALF_W / FRAME_HALF_H);
        // Framing follows from the requested shadow size: the shadow subtends
        // SHADOW_ANGULAR, and it has to land at 1/FRAME_HALF_H of the half-height.
        shader.set("uFov", SHADOW_ANGULAR * FRAME_HALF_H);
        shader.set("uElevation", LensLibrary.elevationAt(this.poseCurrent));
        shader.set("uSpin", this.spin);
        shader.set("uIntensity", intensity);
        shader.set("uGain", DISK_GAIN * SHADER_GAIN_TRIM);
        shader.set("uSteps", UiConfig.blackHoleQuality);

        // The turbulence field is a constant, so it is baked once and sampled rather
        // than recomputed at every step of every ray. If the upload failed the shader
        // still has the analytic version to fall back on.
        int noise = NoiseVolume.ensureUploaded();
        shader.set("uHasNoise", noise != 0 ? 1 : 0);
        if (noise != 0) {
            shader.set("uNoisePeriod", NoiseVolume.PERIOD);
            shader.set("uNoise", NOISE_UNIT);
            NoiseVolume.bind(NOISE_UNIT);
        }
        setColour("uHot", 0xFFF3E4);
        setColour("uMid", Theme.accent);
        setColour("uCold", Theme.accentAlt);
    }

    private void drawShadedDirect(float cx, float cy, float halfW, float halfH, float intensity) {
        bindShaderUniforms(intensity);

        // Premultiplied: the shader returns emission already scaled by coverage, so
        // the shadow blocks the stars behind it while the disk adds light.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(cx - halfW, cy - halfH);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(cx - halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(cx + halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(cx + halfW, cy - halfH);
        GL11.glEnd();

        ShaderProgram.unbind();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private static void setColour(String name, int rgb) {
        shader.set(name, (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F);
    }

    /**
     * Draws the camera's current place on the pose ladder.
     *
     * Between two rungs both are drawn and cross-faded. A hard switch at the
     * halfway point would be a visible jump every time the angle crosses a baked
     * step; blending them turns thirteen stills into one continuous swing.
     */
    private void drawPoses(float cx, float cy, float shadow, float intensity, boolean mirrored) {
        int lower = (int) Math.floor(this.poseCurrent);
        float blend = this.poseCurrent - lower;
        int upper = lower + 1;

        BakedHole a = nearestAvailable(lower, -1);
        BakedHole b = blend > 0.001F ? nearestAvailable(upper, 1) : null;
        if (a == null) {
            a = b;
            b = null;
            blend = 0.0F;
        }
        if (a == null) {
            return;
        }
        if (b == null || b == a) {
            drawPose(a, cx, cy, shadow, intensity, mirrored, true);
            return;
        }
        drawPose(a, cx, cy, shadow, intensity * (1.0F - blend), mirrored, true);
        drawPose(b, cx, cy, shadow, intensity * blend, mirrored, false);
    }

    /**
     * The ladder fills in over several seconds, so a requested rung may not exist
     * yet; fall back toward the resting pose rather than dropping a frame.
     */
    private static BakedHole nearestAvailable(int index, int direction) {
        for (int i = index; i >= 0 && i < LensLibrary.poseCount(); i -= direction) {
            BakedHole pose = LensLibrary.pose(i);
            if (pose != null) {
                return pose;
            }
        }
        return LensLibrary.pose(LensLibrary.POSE_EDGE_ON);
    }

    private void drawPose(BakedHole pose, float cx, float cy, float shadow, float intensity,
                          boolean mirrored, boolean advanceBand) {
        // Poses only glimpsed during a turn are small enough to redo in one pass;
        // the resting pose is spread over several frames.
        boolean banded = isBanded(pose);
        ensureScratch(pose, banded);

        if (advanceBand) {
            this.band = (this.band + 1) % BANDS;
        }
        pose.upload(this.spin, this.argb, this.staging, banded ? BANDS : 1, this.band);

        // Scale so the traced shadow lands exactly on the requested radius.
        float scale = shadow / pose.getShadowTexels();
        float halfW = pose.getWidth() * 0.5F * scale;
        float halfH = pose.getHeight() * 0.5F * scale;

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Occlusion first, over the stars: this is what makes the shadow a hole
        // rather than a dark smudge painted on top of the sky.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        pose.bindOcclusion();
        GL11.glColor4f(0.0F, 0.0F, 0.0F, intensity);
        blit(cx, cy, halfW, halfH, mirrored);

        // Emission on top, additive. Intensity is applied here rather than baked
        // into the texels: only one band is re-shaded per frame, so folding a
        // changing intensity into the pixels left the bands at different
        // brightnesses and the disk visibly striped and flickered while fading.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        pose.bindEmission();
        GL11.glColor4f(intensity, intensity, intensity, 1.0F);
        blit(cx, cy, halfW, halfH, mirrored);
    }

    private static boolean isBanded(BakedHole pose) {
        return pose.getWidth() > 700;
    }

    /**
     * One buffer pair, grown to fit whichever pose needs the most, reused by all.
     *
     * The staging buffer has to hold a whole <em>upload</em>, not a whole pose:
     * a banded pose only ever sends one band, but a pose uploaded in a single pass
     * sends every row at once. Sizing it for a band regardless is what overflowed.
     */
    private void ensureScratch(BakedHole pose, boolean banded) {
        int needed = pose.getWidth() * pose.getHeight();
        if (this.argb == null || this.argb.length < needed) {
            this.argb = new int[needed];
        }

        int uploadRows = banded
                ? pose.getHeight() / BANDS + pose.getHeight() % BANDS + 1
                : pose.getHeight();
        int stagingNeeded = uploadRows * pose.getWidth();
        if (this.staging == null || this.staging.capacity() < stagingNeeded) {
            this.staging = BufferUtils.createIntBuffer(stagingNeeded);
        }
        buildNoiseTable();
    }

    private static void blit(float cx, float cy, float halfW, float halfH, boolean mirrored) {
        float vTop = mirrored ? 1.0F : 0.0F;
        float vBottom = mirrored ? 0.0F : 1.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, vTop);
        GL11.glVertex2f(cx - halfW, cy - halfH);
        GL11.glTexCoord2f(0.0F, vBottom);
        GL11.glVertex2f(cx - halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, vBottom);
        GL11.glVertex2f(cx + halfW, cy + halfH);
        GL11.glTexCoord2f(1.0F, vTop);
        GL11.glVertex2f(cx + halfW, cy - halfH);
        GL11.glEnd();
    }

    // ---- starfield overlay ---------------------------------------------------

    /**
     * Background sky, lensed analytically.
     *
     * For a point source a Schwarzschild lens has a closed form, so each star costs
     * one square root: with {@code b} its true offset and {@code Te} the Einstein
     * radius, the two images sit at {@code (b ± sqrt(b² + 4Te²)) / 2}, each
     * magnified by {@code |1 / (1 - (Te/T)⁴)|}. That yields the real behaviour —
     * stars sliding around the shadow, doubling, and flaring into a ring as they
     * pass behind.
     */
    private void drawStars(float cx, float cy, float shadow, float einstein, float intensity) {
        float scale = this.height;

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < STAR_COUNT; i++) {
            float sx = (starX[i] + drift) % 3.0F;
            if (sx > 1.5F) {
                sx -= 3.0F;
            }
            float bx = sx * scale + (this.width * 0.5F - cx);
            float by = starY[i] * scale + (this.height * 0.5F - cy);

            float b = (float) Math.sqrt(bx * bx + by * by);
            if (b < 0.0001F) {
                continue;
            }
            float ux = bx / b;
            float uy = by / b;

            float root = (float) Math.sqrt(b * b + 4.0F * einstein * einstein);
            emitStarImage(cx, cy, ux, uy, (b + root) * 0.5F, einstein, shadow, starMag[i], starHue[i], intensity);
            emitStarImage(cx, cy, ux, uy, (b - root) * 0.5F, einstein, shadow, starMag[i], starHue[i], intensity);
        }
        GL11.glEnd();
    }

    private void emitStarImage(float cx, float cy, float ux, float uy, float image,
                               float einstein, float shadow, float mag, float hue, float intensity) {
        float r = Math.abs(image);
        if (r <= shadow) {
            return; // swallowed
        }
        float ratio = einstein / r;
        float denom = 1.0F - ratio * ratio * ratio * ratio;
        float amp = Math.abs(denom) < 0.02F ? 50.0F : Math.abs(1.0F / denom);
        if (amp > 50.0F) {
            amp = 50.0F;
        }

        float x = cx + ux * image;
        float y = cy + uy * image;
        if (x < -8 || x > this.width + 8 || y < -8 || y > this.height + 8) {
            return;
        }

        float brightness = mag * amp * intensity;
        if (brightness <= 0.02F) {
            return;
        }
        if (brightness > 1.0F) {
            brightness = 1.0F;
        }

        // Images near the ring stretch tangentially, like real arcs.
        float stretch = 1.0F + Math.min(2.5F, amp * 0.2F);
        float size = 0.35F + mag * 0.55F;
        float tx = -uy * size * stretch;
        float ty = ux * size * stretch;
        float nx = ux * size;
        float ny = uy * size;

        GL11.glColor4f(0.72F + hue * 0.28F, 0.78F + hue * 0.20F, 1.0F, brightness);
        GL11.glVertex2f(x - tx - nx, y - ty - ny);
        GL11.glVertex2f(x + tx - nx, y + ty - ny);
        GL11.glVertex2f(x + tx + nx, y + ty + ny);
        GL11.glVertex2f(x - tx + nx, y - ty + ny);
    }

    // ---- infall overlay ------------------------------------------------------

    private void drawInfall(float cx, float cy, float shadow, float intensity) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < INFALL_COUNT; i++) {
            float r = fallR[i];
            float tilt = INFALL_TILT + (1.0F - INFALL_TILT) * fallIncline[i];
            float cos = (float) Math.cos(fallTheta[i]);
            float sin = (float) Math.sin(fallTheta[i]);

            float rr = r * shadow;
            float x = cx + cos * rr;
            float y = cy + sin * rr * tilt;
            if (x < -30 || x > this.width + 30 || y < -30 || y > this.height + 30) {
                continue;
            }

            float death = r < 1.5F ? clamp01((r - CAPTURE_RADIUS) / (1.5F - CAPTURE_RADIUS)) : 1.0F;
            float lum = fallMag[i] * 0.85F * death * intensity;
            if (lum <= 0.02F) {
                continue;
            }

            // A point until it is genuinely close, then a short tangential smear.
            //
            // Sized against the hole rather than in flat GUI units. At a fixed size
            // these stayed the same width while the hole shrank — on the settings
            // screen, where it is drawn small, they came out as solid white blobs
            // wider than the photon ring. The floor keeps them visible when the hole
            // is small enough that a proportional size would round to nothing.
            float speedStretch = r < 6.0F ? 1.0F + (6.0F - r) * 0.55F : 1.0F;
            float unit = Math.max(0.3F, shadow * 0.02F);
            float size = unit * (0.7F + fallMag[i] * 0.8F);
            float length = size * speedStretch;

            float tx = -sin * length;
            float ty = cos * length * tilt;
            float nx = cos * size;
            float ny = sin * size * tilt;

            // Same blue-white as the sky; only the last stretch warms up.
            int rgb = Draw.mix(0xFFDCEBFF, Theme.accent, clamp01((3.0F - r) / 2.0F) * 0.7F);
            GL11.glColor4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F, Math.min(1.0F, lum));
            GL11.glVertex2f(x - tx - nx, y - ty - ny);
            GL11.glVertex2f(x + tx - nx, y + ty - ny);
            GL11.glVertex2f(x + tx + nx, y + ty + ny);
            GL11.glVertex2f(x - tx + nx, y - ty + ny);
        }
        GL11.glEnd();
    }

    // ---- noise ---------------------------------------------------------------

    /** Bilinear read of the tileable noise table. */
    static float sampleNoise(float x, float y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;

        int x0 = xi & NOISE_MASK;
        int y0 = (yi & NOISE_MASK) << 8;
        int x1 = (xi + 1) & NOISE_MASK;
        int y1 = ((yi + 1) & NOISE_MASK) << 8;

        float a = noiseTable[y0 | x0];
        float b = noiseTable[y0 | x1];
        float c = noiseTable[y1 | x0];
        float d = noiseTable[y1 | x1];

        float top = a + (b - a) * xf;
        float bottom = c + (d - c) * xf;
        return top + (bottom - top) * yf;
    }

    static void buildNoiseTable() {
        if (noiseTable != null) {
            return;
        }
        float[] table = new float[NOISE_SIZE * NOISE_SIZE];
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                table[(y << 8) | x] = fbm(x * 0.06F, y * 0.06F);
            }
        }
        noiseTable = table;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float valueNoise(float x, float y) {
        int xi = floor(x);
        int yi = floor(y);
        float xf = x - xi;
        float yf = y - yi;
        // Smoothstep or the cells show up as visible squares.
        float u = xf * xf * (3.0F - 2.0F * xf);
        float v = yf * yf * (3.0F - 2.0F * yf);

        float a = hash(xi, yi);
        float b = hash(xi + 1, yi);
        float c = hash(xi, yi + 1);
        float d = hash(xi + 1, yi + 1);

        float top = a + (b - a) * u;
        float bottom = c + (d - c) * u;
        return top + (bottom - top) * v;
    }

    private static float fbm(float x, float y) {
        float sum = 0.0F;
        float amp = 0.5F;
        float freq = 1.0F;
        for (int i = 0; i < 3; i++) {
            sum += valueNoise(x * freq, y * freq) * amp;
            freq *= 2.13F;
            amp *= 0.5F;
        }
        return sum;
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}

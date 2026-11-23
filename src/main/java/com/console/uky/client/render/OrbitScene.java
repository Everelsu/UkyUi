package com.console.uky.client.render;

import com.console.uky.compat.GL11;
import com.console.uky.compat.Tessellator;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * The 1.21.1 menu backdrop: low orbit over a blocky planet, with a station coming in
 * and a full orbital day/night illumination cycle.
 *
 * <p>The planet is drawn the way a Minecraft world looks once the camera is so far out
 * that the chunks have collapsed into one colour each — the flat, map-coloured squares
 * HBM's space dimension swaps in for distant terrain. It is a sphere of such squares,
 * each the colour of a biome picked from noise, turning under the camera so the ground
 * keeps streaming towards the viewer the way the floor grid does on a synthwave title.
 *
 * <p>As we orbit the planet, our position relative to the sun changes continuously:
 * we pass through bright orbital daylight, cross the sunset terminator with a golden-orange
 * atmospheric rim, enter the planet's night shadow where city/biome settlement lights
 * glow on the dark surface below and the station's floodlights and solar reflections
 * stand out against deep starry space, and then witness an orbital sunrise cresting
 * the horizon ahead.
 */
public final class OrbitScene {

    // ---- the planet ----
    private static final double R = 1.0;
    private static final double ALTITUDE = 0.06;
    /** Angular size of one terrain square. Large enough to read as pixels up close. */
    private static final double TILE = 0.0085;
    /**
     * Radians of orbit per second (~0.0085 rad/s: about one terrain tile per second,
     * calm and majestic low-Earth-orbit pace; a full 360° orbit takes ~12 minutes).
     */
    private static final double ORBIT_SPEED = 0.0085;
    /** Clouds sit a little above the ground and drift gently against it. */
    private static final double CLOUD_LIFT = 0.0032;
    private static final double CLOUD_DRIFT = 0.0009;

    // ---- the camera ----
    private static final double FOV_Y = Math.toRadians(68.0);
    /** Where the horizon falls on the screen, as a fraction of its height. */
    private static final float HORIZON_AT = 0.64F;
    private static final double NEAR = 0.0006;

    // ---- the station ----
    /** Slow, weighty orbital rendezvous (~28 seconds to rise over the limb and glide in). */
    private static final float APPROACH_SECONDS = 28.0F;
    /** Station units to world units. The truss is about 9 units across. */
    private static final double STATION_SCALE = 0.0155;
    private static final double PARK_DEPTH = 0.14;
    private static final double START_DEPTH = 0.62;

    private static final int ATMOSPHERE_DAY = 0x7AB2FF;
    private static final int ATMOSPHERE_DEEP = 0x2F68D8;
    private static final int ATMOSPHERE_SUNSET = 0xFF7B3A;
    private static final int ATMOSPHERE_NIGHT = 0x16284C;

    /** Vanilla Minecraft & HBM Space celestial textures. */
    private static final ResourceLocation MC_SUN =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation HBM_KERBOL =
            ResourceLocation.fromNamespaceAndPath("uky", "textures/gui/space/kerbol.png");
    private static final ResourceLocation HBM_MUN_PHASES =
            ResourceLocation.fromNamespaceAndPath("uky", "textures/gui/space/mun_phases.png");
    private static final ResourceLocation HBM_FLARE =
            ResourceLocation.fromNamespaceAndPath("uky", "textures/gui/space/flare.png");

    private double phase;
    private double time;
    private float stationTime;
    /** Smoothed pointer look, owned by the shared scene so Alt-Tab/resize never jerks the planet. */
    private float smoothLookX;
    private float smoothLookY;
    private boolean lookInitialised;
    private long lastUpdateNanos;

    private float anchorX;
    private float anchorY;
    private float cameraYawOffset;
    private float cameraPitchOffset;
    private float stationScaleFactor = 1.0F;
    private int anchorWidth = -1;
    private int anchorHeight = -1;

    // Current frame's sun direction unit vector in world space.
    private final double[] sun = new double[3];

    // Camera of the frame being drawn.
    private double camX;
    private double camY;
    private double camZ;
    private final double[] fwd = new double[3];
    private final double[] right = new double[3];
    private final double[] up = new double[3];
    private double focal;
    private float centreX;
    private float centreY;
    private int width;
    private int height;

    // Result of the last project() call.
    private float projX;
    private float projY;
    private double projDepth;

    private final float[] stars;
    private static final int STAR_COUNT = 780;

    /** Biome colour per square, keyed on the square's indices; see {@link #ground}. */
    private static final int CACHE_I = 1024;
    private static final int CACHE_J = 128;
    private final int[] cacheKey = new int[CACHE_I * CACHE_J];
    private final int[] cacheColour = new int[CACHE_I * CACHE_J];

    public OrbitScene() {
        Random random = new Random(0x5EEDL);
        this.stars = new float[STAR_COUNT * 6];
        for (int s = 0; s < STAR_COUNT; s++) {
            double z = random.nextDouble() * 2.0 - 1.0;
            double t = random.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(1.0 - z * z);
            int o = s * 6;
            this.stars[o] = (float) (rr * Math.cos(t));
            this.stars[o + 1] = (float) z;
            this.stars[o + 2] = (float) (rr * Math.sin(t));
            float pick = random.nextFloat();
            this.stars[o + 3] = pick > 0.96F ? 1.7F : (pick > 0.78F ? 1.1F : 0.65F);
            this.stars[o + 4] = 0.28F + random.nextFloat() * 0.72F;
            this.stars[o + 5] = random.nextFloat() * 6.283F;
        }
        java.util.Arrays.fill(this.cacheKey, Integer.MIN_VALUE);
    }

    public void update(float delta) {
        long now = System.nanoTime();
        // If the window was unfocused/alt-tabbed or a long pause happened, ignore the
        // spike frame so the planet does not lurch forward or wobble.
        if (this.lastUpdateNanos != 0L && (now - this.lastUpdateNanos) > 250_000_000L) {
            this.lastUpdateNanos = now;
            return;
        }
        this.lastUpdateNanos = now;
        float dt = Math.max(0.0F, Math.min(delta, 0.05F));
        this.time += dt;
        this.phase += ORBIT_SPEED * dt;
        this.stationTime += dt;
    }

    /** Puts the station back below the horizon, to fly in again. */
    public void restartApproach() {
        this.stationTime = 0.0F;
    }

    /**
     * Where on the screen the station should come to rest, and how the camera is angled
     * for the current screen. Eased, so moving between the title screen and settings
     * glides the station into the open pane and turns the view smoothly.
     */
    public void anchor(float x, float y, float yawOffset, float pitchOffset, float scaleFactor,
                       int screenWidth, int screenHeight, float delta) {
        if (screenWidth != this.anchorWidth || screenHeight != this.anchorHeight) {
            this.anchorWidth = screenWidth;
            this.anchorHeight = screenHeight;
            this.anchorX = x;
            this.anchorY = y;
            this.cameraYawOffset = yawOffset;
            this.cameraPitchOffset = pitchOffset;
            this.stationScaleFactor = scaleFactor;
            return;
        }
        float step = Math.max(0.0F, Math.min(delta, 0.05F));
        this.anchorX = Ease.approach(this.anchorX, x, 0.22F, step);
        this.anchorY = Ease.approach(this.anchorY, y, 0.22F, step);
        this.cameraYawOffset = Ease.approach(this.cameraYawOffset, yawOffset, 0.25F, step);
        this.cameraPitchOffset = Ease.approach(this.cameraPitchOffset, pitchOffset, 0.25F, step);
        this.stationScaleFactor = Ease.approach(this.stationScaleFactor, scaleFactor, 0.25F, step);
    }

    /**
     * Rotates a fixed celestial direction {@code (x, y, z)} by our orbital angle
     * {@link #phase} about the X axis, plus a slight orbital inclination tilt so the
     * sky dome wheeling overhead feels three-dimensional.
     *
     * <p>Because our ship is pitching down at {@code ORBIT_SPEED} to keep the Earth's
     * horizon level as we circle the planet, every distant body in space — stars, the
     * Sun, the Moon, and the outer planets — naturally rises from behind the Earth's
     * horizon ahead of us and climbs up the sky at the exact same physical angular rate!
     */
    private void rotateSky(double x0, double y0, double z0, double extraRate, double[] out) {
        double angle = this.phase * (1.0 + extraRate);
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        // Slightly inclined orbital plane (~12 degrees) so celestial bodies arc gently sideways too.
        double tilt = 0.21;
        double ct = Math.cos(tilt);
        double st = Math.sin(tilt);
        double xt = x0 * ct - y0 * st;
        double yt = x0 * st + y0 * ct;
        // As we orbit forward (ground moves from -Z toward +Y), distant sky objects move
        // from behind the horizon (-Y, -Z) up into the sky (+Y, -Z).
        double yr = yt * ca - z0 * sa;
        double zr = yt * sa + z0 * ca;
        out[0] = xt * ct + yr * st;
        out[1] = -xt * st + yr * ct;
        out[2] = zr;
    }

    /**
     * Computes the sun unit vector for the current orbital position {@link #phase}.
     *
     * Locked to {@link #phase} so the Sun moves at the exact physical speed of our
     * orbit around the Earth rather than racing ahead artificially.
     */
    private void updateSunDirection() {
        rotateSky(0.32, 0.28, -0.905, 0.0, this.sun);
        double len = Math.sqrt(this.sun[0] * this.sun[0] + this.sun[1] * this.sun[1] + this.sun[2] * this.sun[2]);
        this.sun[0] /= len;
        this.sun[1] /= len;
        this.sun[2] /= len;
    }

    /**
     * Draws the whole scene over the given rectangle.
     *
     * @param lookX  pointer parallax, -1..1
     * @param lookY  pointer parallax, -1..1
     * @param alpha  how much of the scene there is; the rest is the backdrop colour
     * @param dive   0..1, how far into the fall towards the ground the camera is
     */
    public void render(int w, int h, float lookX, float lookY, float alpha, float dive, int backdrop) {
        if (w <= 0 || h <= 0) {
            return;
        }
        this.width = w;
        this.height = h;
        this.centreX = w * 0.5F;
        this.centreY = h * 0.5F;
        double baseFocal = h * 0.5 / Math.tan(FOV_Y * 0.5);

        updateSunDirection();

        float clampedLookX = clamp(lookX, -1.0F, 1.0F);
        float clampedLookY = clamp(lookY, -1.0F, 1.0F);
        if (!this.lookInitialised) {
            this.smoothLookX = clampedLookX;
            this.smoothLookY = clampedLookY;
            this.lookInitialised = true;
        } else {
            // Frame-clamped smoothing so returning from Alt-Tab with the cursor on the
            // other side of the monitor glides smoothly instead of whipping the planet.
            this.smoothLookX = Ease.approach(this.smoothLookX, clampedLookX, 0.35F, 0.016F);
            this.smoothLookY = Ease.approach(this.smoothLookY, clampedLookY, 0.35F, 0.016F);
        }

        // The station is placed against the camera's base pose so pointer parallax
        // moves the view subtly past it rather than carrying it locked to the cursor.
        double restHeight = R + ALTITUDE;
        double restPitch = pitchFor(restHeight, baseFocal) + this.cameraPitchOffset;
        double[] sFwd = new double[3];
        double[] sRight = new double[3];
        double[] sUp = new double[3];
        basis(this.cameraYawOffset, restPitch, 0.0, sFwd, sRight, sUp);
        double[] station = stationPosition(restHeight, baseFocal, sFwd, sRight, sUp);

        // The camera as drawn.
        double camHeight = R + ALTITUDE * (1.0 - 0.6 * dive);
        this.focal = baseFocal * (1.0 + 0.5 * dive);
        double pitch = pitchFor(camHeight, this.focal) + this.cameraPitchOffset
                + this.smoothLookY * 0.014 + dive * 0.55;
        double yaw = this.cameraYawOffset + this.smoothLookX * 0.022
                + Math.sin(this.time * 0.024) * 0.008;
        double roll = Math.sin(this.time * 0.065) * 0.010 - this.smoothLookX * 0.006
                - this.cameraYawOffset * 0.18;
        this.camX = 0.0;
        this.camY = camHeight;
        this.camZ = 0.0;
        basis(yaw, pitch, roll, this.fwd, this.right, this.up);

        double horizonDistance = Math.sqrt(camHeight * camHeight - R * R);
        double dx = station[0] - this.camX;
        double dy = station[1] - this.camY;
        double dz = station[2] - this.camZ;
        boolean stationBehindLimb = Math.sqrt(dx * dx + dy * dy + dz * dz) > horizonDistance;

        // Sun elevation at the sub-camera point (+Y): +1 is noon, 0 is terminator, -1 is midnight.
        float subSolar = (float) this.sun[1];
        float dayFactor = Ease.clamp01((subSolar + 0.22F) / 0.65F);

        drawSpace(dayFactor);
        drawStars(dayFactor);
        drawSolarSystemBodies(dayFactor);
        drawSun(camHeight);
        drawMoon();
        if (stationBehindLimb) {
            // Further than the horizon: whatever of it is below the limb is behind the
            // planet, and drawing the planet over it is exactly that.
            drawStation(station, sFwd, sRight, sUp);
        }
        float[] limb = limbPoints(camHeight);
        drawPlanetFill(limb, dayFactor);
        drawGround(camHeight);
        drawClouds(camHeight);
        drawAtmosphere(limb);
        if (!stationBehindLimb) {
            drawStation(station, sFwd, sRight, sUp);
        }

        if (alpha < 1.0F) {
            Draw.rect(-w, -h, w * 2, h * 2, Draw.withAlpha(backdrop, 1.0F - alpha));
        }
    }

    // ---------------------------------------------------------------- camera --

    /** Pitch that puts the horizon at {@link #HORIZON_AT} for a camera this high. */
    private double pitchFor(double cameraHeight, double focalLength) {
        double dip = Math.acos(R / cameraHeight);
        return dip - Math.atan((HORIZON_AT - 0.5) * this.height / focalLength);
    }

    /** Camera axes for a yaw (right positive), downward pitch and roll. */
    private static void basis(double yaw, double pitch, double roll,
                              double[] f, double[] r, double[] u) {
        double sp = Math.sin(pitch);
        double cp = Math.cos(pitch);
        // Pitched down about the right axis.
        double fx = 0.0, fy = -sp, fz = -cp;
        double ux = 0.0, uy = cp, uz = -sp;
        double rx = 1.0, ry = 0.0, rz = 0.0;
        // Yawed about the world's up.
        double sy = Math.sin(yaw);
        double cy = Math.cos(yaw);
        double t;
        t = fx * cy - fz * sy; fz = fx * sy + fz * cy; fx = t;
        t = ux * cy - uz * sy; uz = ux * sy + uz * cy; ux = t;
        t = rx * cy - rz * sy; rz = rx * sy + rz * cy; rx = t;
        // Rolled about the view direction.
        double sr = Math.sin(roll);
        double cr = Math.cos(roll);
        f[0] = fx; f[1] = fy; f[2] = fz;
        r[0] = rx * cr + ux * sr; r[1] = ry * cr + uy * sr; r[2] = rz * cr + uz * sr;
        u[0] = ux * cr - rx * sr; u[1] = uy * cr - ry * sr; u[2] = uz * cr - rz * sr;
    }

    /** Projects a world point; false when it is behind the camera. */
    private boolean project(double x, double y, double z) {
        double vx = x - this.camX;
        double vy = y - this.camY;
        double vz = z - this.camZ;
        double depth = vx * this.fwd[0] + vy * this.fwd[1] + vz * this.fwd[2];
        this.projDepth = depth;
        if (depth < NEAR) {
            return false;
        }
        double xc = vx * this.right[0] + vy * this.right[1] + vz * this.right[2];
        double yc = vx * this.up[0] + vy * this.up[1] + vz * this.up[2];
        float limit = Math.max(this.width, this.height) * 6.0F;
        this.projX = clamp((float) (this.centreX + this.focal * xc / depth), -limit, limit);
        this.projY = clamp((float) (this.centreY - this.focal * yc / depth), -limit, limit);
        return true;
    }

    // ----------------------------------------------------------------- space --

    /**
     * Renders the procedural 3D orbital scene without external textures (space gradient,
     * wheeling 3D starfield, curved blocky biome-tiled Earth, drifting cloud layer, and
     * atmospheric limb glow), used by the loading splash screen before resource packs
     * have finished loading and during its seamless hand-off to the title screen.
     *
     * @param w      screen width in GUI units
     * @param h      screen height in GUI units
     * @param ascent 0..1, how far the camera has ascended/pitched into its resting orbital view
     * @param alpha  overall opacity (0..1)
     */
    public void renderSplash(int w, int h, float ascent, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.005F) {
            return;
        }
        this.width = w;
        this.height = h;
        this.centreX = w * 0.5F;
        this.centreY = h * 0.5F;
        double baseFocal = h * 0.5 / Math.tan(FOV_Y * 0.5);

        updateSunDirection();

        float settle = Ease.outCubic(Ease.clamp01(ascent));
        double camHeight = R + ALTITUDE * (0.45 + 0.55 * settle);
        this.focal = baseFocal;
        // As the splash progresses, the camera pitches smoothly from looking slightly higher
        // above the limb down into the exact resting horizon angle of GuiTitleScreen.
        double pitch = pitchFor(camHeight, this.focal) - (1.0 - settle) * 0.14;
        double yaw = Math.sin(this.time * 0.024) * 0.008;
        double roll = Math.sin(this.time * 0.065) * 0.010;
        this.camX = 0.0;
        this.camY = camHeight;
        this.camZ = 0.0;
        basis(yaw, pitch, roll, this.fwd, this.right, this.up);

        float subSolar = (float) this.sun[1];
        float dayFactor = Ease.clamp01((subSolar + 0.22F) / 0.65F);

        drawSpace(dayFactor);
        drawStars(dayFactor);
        drawSolarSystemBodies(dayFactor);
        float[] limb = limbPoints(camHeight);
        drawPlanetFill(limb, dayFactor);
        drawGround(camHeight);
        drawClouds(camHeight);
        drawAtmosphere(limb);
    }

    /**
     * Real space is pitch-black overhead, with only a faint scatter near the planet's
     * sunlit atmosphere. At orbital night even that scatter drops to near-black.
     */
    private void drawSpace(float dayFactor) {
        float horizon = this.height * HORIZON_AT;
        int zenith = 0xFF010206;
        int midSky = Draw.mix(0xFF02030A, 0xFF040814, dayFactor);
        int horizonGlow = Draw.mix(0xFF050A18, 0xFF0A1938, dayFactor);
        Draw.gradientV(-this.width, -this.height, this.width * 2, horizon * 0.55F, zenith, midSky);
        Draw.gradientV(-this.width, horizon * 0.55F, this.width * 2, horizon + 12.0F, midSky, horizonGlow);
        Draw.rect(-this.width, horizon + 12.0F, this.width * 2, this.height * 2, horizonGlow);
    }

    /**
     * Stars rotating overhead in sync with our orbital pitch around the Earth.
     * Stars shine slightly brighter when we pass into the planet's night shadow.
     */
    private void drawStars(float dayFactor) {
        float nightBoost = 1.18F - 0.32F * dayFactor;
        double[] dir = new double[3];
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int s = 0; s < STAR_COUNT; s++) {
            int o = s * 6;
            rotateSky(this.stars[o], this.stars[o + 1], this.stars[o + 2], 0.0, dir);
            double x = dir[0];
            double y = dir[1];
            double z = dir[2];
            double depth = x * this.fwd[0] + y * this.fwd[1] + z * this.fwd[2];
            if (depth <= 0.05) {
                continue;
            }
            float sx = this.centreX + (float) (this.focal
                    * (x * this.right[0] + y * this.right[1] + z * this.right[2]) / depth);
            float sy = this.centreY - (float) (this.focal
                    * (x * this.up[0] + y * this.up[1] + z * this.up[2]) / depth);
            if (sx < -2 || sy < -2 || sx > this.width + 2 || sy > this.height + 2) {
                continue;
            }
            float twinkle = 0.82F + 0.18F * (float) Math.sin(this.time * 0.9 + this.stars[o + 5]);
            float a = Math.min(1.0F, this.stars[o + 4] * twinkle * nightBoost);
            float size = this.stars[o + 3];
            int colour = s % 7 == 0 ? 0xFFE6C2 : (s % 5 == 0 ? 0xBFD6FF : (s % 11 == 0 ? 0xFFD2B8 : 0xFFFFFF));
            quad(sx, sy, sx + size, sy, sx + size, sy + size, sx, sy + size, Draw.withAlpha(colour, a));
        }
        GL11.glEnd();
        end();
    }

    /**
     * Draws the sun disc, solar corona, and orbital sunrise/sunset flare when the sun
     * is above or grazing the planet's limb.
     *
     * Because the planet is drawn after the sun, when the sun dips behind the planet's
     * horizon the planet naturally eclipses the solar disc while its atmospheric halo
     * still glows above the limb!
     */
    private void drawSun(double cameraHeight) {
        double depth = this.sun[0] * this.fwd[0] + this.sun[1] * this.fwd[1] + this.sun[2] * this.fwd[2];
        if (depth <= 0.05) {
            return;
        }
        float sx = this.centreX + (float) (this.focal
                * (this.sun[0] * this.right[0] + this.sun[1] * this.right[1] + this.sun[2] * this.sun[2]) / depth);
        float sy = this.centreY - (float) (this.focal
                * (this.sun[0] * this.up[0] + this.sun[1] * this.up[1] + this.sun[2] * this.up[2]) / depth);

        // How close the sun ray is to the planet's limb: cos(limbAngle) from camera.
        // Ray from camera (0, cameraHeight, 0) in direction `sun` has dot with -Y equal to -sun[1].
        double sinLimb = R / cameraHeight;
        double cosLimb = Math.sqrt(1.0 - sinLimb * sinLimb);
        // Angle above limb: positive when sun is in open sky, 0 at limb eclipse, negative behind planet.
        double limbClearance = this.sun[1] + cosLimb;
        float visible = Ease.clamp01((float) ((limbClearance + 0.14) / 0.24));
        if (visible <= 0.005F) {
            return;
        }
        // Golden-orange shift when the sun is grazing the planet's atmosphere at sunrise/sunset.
        float grazing = 1.0F - Ease.clamp01((float) (Math.abs(limbClearance) / 0.22));
        int flareTint = Draw.mix(0xFFE8C8, 0xFF8238, grazing * 0.78F);
        int sunTint = Draw.mix(0xFFFFFF, 0xFFB870, grazing * 0.65F);

        float sunHalf = Math.max(18.0F, this.height * 0.085F);
        float flareHalf = sunHalf * 2.1F;

        // 1. HBM Space solar flare corona behind the sun (additive).
        drawAdditiveQuad(HBM_FLARE, sx, sy, flareHalf, flareHalf, 0.0F, 0.0F, 1.0F, 1.0F,
                Draw.withAlpha(flareTint, 0.50F * visible));
        // 2. Vanilla Minecraft sun texture (additive, so its black background is transparent!).
        drawAdditiveQuad(MC_SUN, sx, sy, sunHalf, sunHalf, 0.0F, 0.0F, 1.0F, 1.0F,
                Draw.withAlpha(sunTint, 0.95F * visible));
        // 3. HBM Kerbol crisp core layer on top.
        drawAdditiveQuad(HBM_KERBOL, sx, sy, sunHalf * 0.78F, sunHalf * 0.78F, 0.0F, 0.0F, 1.0F, 1.0F,
                Draw.withAlpha(0xFFFFFF, 0.85F * visible));
    }

    /**
     * Draws the Moon using HBM Space's actual `mun_phases.png` texture (4x2 lunar phase
     * sprite sheet in authentic Minecraft pixel-art style) with additive blending, moving
     * across the sky at the physical orbital rate as we circle the Earth.
     */
    private void drawMoon() {
        double[] dir = new double[3];
        // The Moon rises and wheels across the sky with our orbital rotation, plus its own
        // slight monthly orbital motion relative to the background stars.
        rotateSky(-0.34, 0.21, -0.916, 0.04, dir);
        double dist = 4.3;
        double mx = this.camX + dir[0] * dist;
        double my = this.camY + dir[1] * dist;
        double mz = this.camZ + dir[2] * dist;

        if (!project(mx, my, mz)) {
            return;
        }
        float screenX = this.projX;
        float screenY = this.projY;
        double centerDepth = this.projDepth;
        float moonHalf = (float) Math.max(24.0, this.focal * 0.52 / centerDepth);
        if (screenX < -moonHalf * 2 || screenX > this.width + moonHalf * 2
                || screenY < -moonHalf * 2 || screenY > this.height + moonHalf * 2) {
            return;
        }

        // Compute lunar phase index (0..7) from the orbital angle between Moon and Sun,
        // avoiding index 4 (new moon, which is blank in mun_phases.png) so the Moon is always visible.
        double moonAngle = Math.atan2(mx, -mz);
        double sunAngle = Math.atan2(this.sun[0], -this.sun[2]);
        double rel = ((moonAngle - sunAngle) / (2.0 * Math.PI)) % 1.0;
        if (rel < 0.0) {
            rel += 1.0;
        }
        int phaseIndex = ((int) Math.floor(rel * 8.0)) & 7;
        if (phaseIndex == 4) {
            phaseIndex = 3;
        }
        if (phaseIndex == 2 || phaseIndex == 6) {
            // Prefer full / gibbous / crescent cells so the lunar crater texture is well-lit.
            phaseIndex = (phaseIndex == 2) ? 1 : 7;
        }
        int col = phaseIndex % 4;
        int row = phaseIndex / 4;
        float u0 = col / 4.0F;
        float v0 = row / 2.0F;
        float u1 = (col + 1) / 4.0F;
        float v1 = (row + 1) / 2.0F;

        drawAdditiveQuad(HBM_MUN_PHASES, screenX, screenY, moonHalf, moonHalf, u0, v0, u1, v1,
                Draw.withAlpha(0xFFFFFF, 0.96F));
    }

    /**
     * Draws a textured quad with additive blending ({@code GL_SRC_ALPHA, GL_ONE}), the way
     * Minecraft and HBM Space render `sun.png`, `moon_phases.png`, and `mun_phases.png`.
     */
    private static void drawAdditiveQuad(ResourceLocation tex, float cx, float cy,
                                         float halfW, float halfH,
                                         float u0, float v0, float u1, float v1, int argb) {
        GL11.bindTexture(tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(
                (argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                (argb >>> 24) / 255.0F);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(cx - halfW, cy + halfH, 0.0D, u0, v1);
        t.addVertexWithUV(cx + halfW, cy + halfH, 0.0D, u1, v1);
        t.addVertexWithUV(cx + halfW, cy - halfH, 0.0D, u1, v0);
        t.addVertexWithUV(cx - halfW, cy - halfH, 0.0D, u0, v0);
        t.draw();

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Other bodies of the Solar System visible from low Earth orbit, wheeling across
     * the sky with our orbital rotation around the Earth (and slowly drifting along the
     * ecliptic at their own Keplerian rates):
     * - Saturn: blocky gold cube with tilted square rings
     * - Jupiter: banded cream/ochre gas giant square with Great Red Spot
     * - Mars: rusty-red planet square with polar ice cap
     */
    private void drawSolarSystemBodies(float dayFactor) {
        float visibility = 1.05F - 0.25F * dayFactor;
        double[] dir = new double[3];

        // 1. Saturn (rotates with the sky dome as we orbit Earth)
        rotateSky(-0.56, 0.20, -0.804, 0.005, dir);
        drawSaturn(dir[0], dir[1], dir[2], visibility);

        // 2. Jupiter (rotates with the sky dome as we orbit Earth)
        rotateSky(0.55, 0.23, -0.803, 0.012, dir);
        drawJupiter(dir[0], dir[1], dir[2], visibility);

        // 3. Mars (rotates with the sky dome as we orbit Earth)
        rotateSky(0.36, 0.11, -0.926, 0.022, dir);
        drawMars(dir[0], dir[1], dir[2], visibility);
    }

    private void drawSaturn(double dx, double dy, double dz, float visibility) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!project(this.camX + dx / len * 12.0, this.camY + dy / len * 12.0, this.camZ + dz / len * 12.0)) {
            return;
        }
        float sx = this.projX;
        float sy = this.projY;
        float r = Math.max(3.2F, this.height * 0.0095F);

        Draw.rect(sx - r * 2.6F, sy - r * 1.8F, sx + r * 2.6F, sy + r * 1.8F,
                Draw.withAlpha(0xE8CFA0, 0.11F * visibility));

        // Blocky rectangular 4-sided ring frame (tilted square ring, not a smooth circle!).
        float tilt = -0.28F;
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float rxOut = r * 2.55F, ryOut = r * 0.82F;
        float rxIn = r * 1.50F, ryIn = r * 0.46F;
        int ringBack = Draw.withAlpha(0xB8A074, 0.82F * visibility);
        int ringFront = Draw.withAlpha(0xE6D0A4, 0.94F * visibility);
        int ringSide = Draw.withAlpha(0xD4BC8E, 0.90F * visibility);

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        boxRingBar(sx, sy, ct, st, -rxOut, -ryOut, rxOut, -ryIn, ringBack);
        boxRingBar(sx, sy, ct, st, -rxOut, -ryIn, -rxIn, ryIn, ringSide);
        boxRingBar(sx, sy, ct, st, rxIn, -ryIn, rxOut, ryIn, ringSide);

        float p = (r * 2.0F) / 3.0F;
        int[] saturnRows = {0xDFC48E, 0xC4A46C, 0xEAD3A2};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float x0 = sx - r + col * p;
                float y0 = sy - r + row * p;
                float shade = (col == 0 ? 1.06F : (col == 2 ? 0.86F : 0.98F));
                int cell = Draw.withAlpha(shade(saturnRows[row], shade), 0.98F * visibility);
                quad(x0, y0, x0 + p, y0, x0 + p, y0 + p, x0, y0 + p, cell);
            }
        }

        boxRingBar(sx, sy, ct, st, -rxOut, ryIn, rxOut, ryOut, ringFront);
        GL11.glEnd();
        end();
    }

    private static void boxRingBar(float cx, float cy, float ct, float st,
                                   float x0, float y0, float x1, float y1, int colour) {
        float ax = cx + x0 * ct - y0 * st, ay = cy + x0 * st + y0 * ct;
        float bx = cx + x1 * ct - y0 * st, by = cy + x1 * st + y0 * ct;
        float dx = cx + x1 * ct - y1 * st, dy = cy + x1 * st + y1 * ct;
        float ex = cx + x0 * ct - y1 * st, ey = cy + x0 * st + y1 * ct;
        quad(ax, ay, bx, by, dx, dy, ex, ey, colour);
    }

    private void drawJupiter(double dx, double dy, double dz, float visibility) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!project(this.camX + dx / len * 12.0, this.camY + dy / len * 12.0, this.camZ + dz / len * 12.0)) {
            return;
        }
        float sx = this.projX;
        float sy = this.projY;
        float r = Math.max(3.8F, this.height * 0.011F);
        Draw.rect(sx - r * 1.6F, sy - r * 1.6F, sx + r * 1.6F, sy + r * 1.6F,
                Draw.withAlpha(0xE8C89E, 0.13F * visibility));
        int[][] jupiterPixels = {
                {0xD6BC96, 0xE2C8A2, 0xD6BC96, 0xC4A882},
                {0xB58054, 0xBF8A5E, 0xB58054, 0x9E6C42},
                {0xE5CEAA, 0xEAD4B2, 0xC95638, 0xCFA074},
                {0xC29268, 0xCCA076, 0xBE8E64, 0xA87A52}
        };
        float cell = (r * 2.0F) / 4.0F;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float x0 = sx - r + col * cell;
                float y0 = sy - r + row * cell;
                Draw.rect(x0, y0, x0 + cell, y0 + cell,
                        Draw.withAlpha(jupiterPixels[row][col], 0.98F * visibility));
            }
        }
    }

    private void drawMars(double dx, double dy, double dz, float visibility) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!project(this.camX + dx / len * 12.0, this.camY + dy / len * 12.0, this.camZ + dz / len * 12.0)) {
            return;
        }
        float sx = this.projX;
        float sy = this.projY;
        float r = Math.max(2.7F, this.height * 0.0075F);
        Draw.rect(sx - r * 1.7F, sy - r * 1.7F, sx + r * 1.7F, sy + r * 1.7F,
                Draw.withAlpha(0xE06A42, 0.14F * visibility));
        int[][] marsPixels = {
                {0xD86842, 0xF4ECE6, 0xC85A36},
                {0xE0724C, 0xB84E2C, 0xD2623E},
                {0xC45634, 0xD46440, 0xA84424}
        };
        float cell = (r * 2.0F) / 3.0F;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float x0 = sx - r + col * cell;
                float y0 = sy - r + row * cell;
                Draw.rect(x0, y0, x0 + cell, y0 + cell,
                        Draw.withAlpha(marsPixels[row][col], 0.98F * visibility));
            }
        }
    }

    // ---------------------------------------------------------------- planet --

    /** The planet's visible edge, projected: x, y pairs from left to right. */
    private float[] limbPoints(double cameraHeight) {
        double planeY = R * R / cameraHeight;
        double radius = R * Math.sqrt(1.0 - (R / cameraHeight) * (R / cameraHeight));
        int segments = 240;
        float[] out = new float[(segments + 1) * 2];
        int n = 0;
        for (int i = 0; i <= segments; i++) {
            double t = -Math.PI + i * 2.0 * Math.PI / segments;
            if (!project(radius * Math.sin(t), planeY, -radius * Math.cos(t))) {
                continue;
            }
            out[n++] = this.projX;
            out[n++] = this.projY;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Solid planet under the limb, so no star or sun disc shows between the edge squares. */
    private void drawPlanetFill(float[] limb, float dayFactor) {
        if (limb.length < 4) {
            return;
        }
        float bottom = this.height * 4.0F;
        int colour = 0xFF000000 | (Draw.mix(0x050914, 0x1D3968, dayFactor) & 0xFFFFFF);
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i + 3 < limb.length; i += 2) {
            quad(limb[i], limb[i + 1], limb[i + 2], limb[i + 3],
                    limb[i + 2], bottom, limb[i], bottom, colour);
        }
        GL11.glEnd();
        end();
    }

    private void drawGround(double cameraHeight) {
        drawShell(cameraHeight, R, this.phase, false);
    }

    private void drawClouds(double cameraHeight) {
        drawShell(cameraHeight, R + CLOUD_LIFT, this.phase + this.time * CLOUD_DRIFT, true);
    }

    /**
     * One layer of squares: the ground, or the cloud deck above it.
     *
     * Each square is lit from the real 3D sun vector:
     * - On the sunlit hemisphere: crisp biome colours with atmospheric Rayleigh scatter near the limb.
     * - Along the terminator (dot(N, sun) near 0): warm golden-amber twilight tint on clouds and terrain.
     * - On the night hemisphere (dot(N, sun) < 0): deep space-shadowed terrain with warm glowing
     *   settlement/city light clusters on dry land tiles.
     */
    private void drawShell(double cameraHeight, double radius, double rotation, boolean clouds) {
        double cosHorizon = radius / cameraHeight;
        if (cosHorizon >= 1.0) {
            return;
        }
        double reach = Math.acos(R / cameraHeight) + (clouds ? 0.08 : 0.02);
        int i0 = (int) Math.floor((rotation - 0.03) / TILE);
        int i1 = (int) Math.ceil((rotation + reach) / TILE);
        int jMax = Math.min(CACHE_J / 2 - 2, (int) Math.ceil(reach / TILE) + 1);
        int ni = i1 - i0 + 1;
        int nj = 2 * jMax + 1;
        if (ni <= 1 || nj <= 1 || ni > 600) {
            return;
        }

        float[] px = new float[ni * nj];
        float[] py = new float[ni * nj];
        boolean[] ok = new boolean[ni * nj];
        for (int a = 0; a < ni; a++) {
            double angle = (i0 + a) * TILE - rotation;
            double ca = Math.cos(angle);
            double sa = Math.sin(angle);
            for (int b = 0; b < nj; b++) {
                double lat = (b - jMax) * TILE;
                double rr = radius * Math.cos(lat);
                int k = a * nj + b;
                ok[k] = project(radius * Math.sin(lat), rr * ca, -rr * sa);
                px[k] = this.projX;
                py[k] = this.projY;
            }
        }

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int a = 0; a < ni - 1; a++) {
            int i = i0 + a;
            double angle = (i + 0.5) * TILE - rotation;
            double ca = Math.cos(angle);
            double sa = Math.sin(angle);
            for (int b = 0; b < nj - 1; b++) {
                int j = b - jMax;
                double lat = (j + 0.5) * TILE;
                double cl = Math.cos(lat);
                // Unit normal at the square's centre.
                double nx = Math.sin(lat);
                double ny = cl * ca;
                double nz = -cl * sa;
                // Facing the camera at all? (Past the horizon otherwise.)
                double facing = cameraHeight * ny - radius;
                if (facing <= 0.0) {
                    continue;
                }
                int k00 = a * nj + b;
                int k10 = k00 + nj;
                if (!ok[k00] || !ok[k00 + 1] || !ok[k10] || !ok[k10 + 1]) {
                    continue;
                }
                double ex = -radius * nx;
                double ey = cameraHeight - radius * ny;
                double ez = -radius * nz;
                double distance = Math.sqrt(ex * ex + ey * ey + ez * ez);
                float grazing = (float) (1.0 - facing / distance);
                float haze = (float) Math.pow(grazing, clouds ? 3.0 : 2.4);

                // Raw solar dot product at this tile: >0 day, ~0 twilight terminator, <0 night.
                float sunDot = (float) (nx * this.sun[0] + ny * this.sun[1] + nz * this.sun[2]);
                float dayLight = Ease.clamp01((sunDot + 0.08F) / 0.72F);
                // Twilight band peaks right along the terminator where sunDot is near 0.
                float twilight = Math.max(0.0F, 1.0F - Math.abs(sunDot - 0.02F) / 0.22F);
                float nightness = Ease.clamp01((-sunDot + 0.05F) / 0.30F);

                int localAtmo = Draw.mix(
                        Draw.mix(ATMOSPHERE_NIGHT, ATMOSPHERE_DAY, dayLight),
                        ATMOSPHERE_SUNSET, twilight * 0.65F);

                int colour;
                if (clouds) {
                    float density = cloud(i, j);
                    if (density <= 0.02F) {
                        continue;
                    }
                    int cloudBase = Draw.mix(0xF4F7FF, 0xFF9E64, twilight * 0.55F);
                    int lit = shade(cloudBase, 0.10F + 0.98F * dayLight + 0.18F * twilight);
                    colour = Draw.withAlpha(Draw.mix(lit, localAtmo, haze * 0.75F),
                            density * (1.0F - haze * 0.6F) * (0.28F + 0.72F * dayLight));
                } else {
                    int base = ground(i, j);
                    int lit = shade(base, 0.07F + 0.98F * dayLight);
                    if (twilight > 0.01F) {
                        lit = add(lit, 0xD96528, twilight * 0.18F);
                    }
                    // On the night side, inhabited land tiles glow with warm city lights.
                    if (nightness > 0.05F && cityLight(i, j)) {
                        float cityGlow = nightness * (1.0F - haze * 0.7F)
                                * (0.55F + 0.45F * (float) hash(i, j, 313));
                        lit = add(lit, 0xFFB856, cityGlow * 0.75F);
                    }
                    colour = 0xFF000000 | (Draw.mix(lit, localAtmo, haze * (0.45F + 0.40F * dayLight)) & 0xFFFFFF);
                }
                quad(px[k00], py[k00], px[k10], py[k10],
                        px[k10 + 1], py[k10 + 1], px[k00 + 1], py[k00 + 1], colour);
            }
        }
        GL11.glEnd();
        end();
    }

    /**
     * Glow of the air along the limb, locally lit by the 3D sun direction:
     * bright cyan-blue on the day limb, blazing amber-orange where the terminator meets
     * the horizon, and deep indigo-blue on the night limb.
     */
    private void drawAtmosphere(float[] limb) {
        int n = limb.length / 2;
        if (n < 2) {
            return;
        }
        float[] nx = new float[n];
        float[] ny = new float[n];
        float[] sunSide = new float[n];
        float[] twilightSide = new float[n];
        for (int i = 0; i < n; i++) {
            int prev = Math.max(0, i - 1) * 2;
            int next = Math.min(n - 1, i + 1) * 2;
            float tx = limb[next] - limb[prev];
            float ty = limb[next + 1] - limb[prev + 1];
            float len = (float) Math.sqrt(tx * tx + ty * ty);
            if (len < 1.0E-4F) {
                nx[i] = 0.0F;
                ny[i] = -1.0F;
            } else {
                nx[i] = ty / len;
                ny[i] = -tx / len;
                if (ny[i] > 0.0F) {
                    nx[i] = -nx[i];
                    ny[i] = -ny[i];
                }
            }
            // Approximate limb normal direction across the visible horizon arc.
            float arc = (i / (float) Math.max(1, n - 1)) * 2.0F - 1.0F;
            double lnx = Math.sin(arc * 0.85);
            double lny = 0.94;
            double lnz = -Math.cos(arc * 0.85) * 0.34;
            float dot = (float) (lnx * this.sun[0] + lny * this.sun[1] + lnz * this.sun[2]);
            sunSide[i] = 0.16F + 0.84F * Ease.clamp01((dot + 0.25F) / 0.85F);
            twilightSide[i] = Math.max(0.0F, 1.0F - Math.abs(dot - 0.02F) / 0.28F);
        }

        float h = this.height;
        band(limb, nx, ny, sunSide, twilightSide, h * 0.14F, ATMOSPHERE_DEEP, ATMOSPHERE_SUNSET, 0.34F, 1.0F);
        band(limb, nx, ny, sunSide, twilightSide, h * 0.032F, 0x9FCBFF, 0xFF9848, 0.58F, 1.0F);
        band(limb, nx, ny, sunSide, twilightSide, h * 0.008F, 0xE8F5FF, 0xFFD29A, 0.92F, 1.0F);
        band(limb, nx, ny, sunSide, twilightSide, h * 0.052F, ATMOSPHERE_DAY, ATMOSPHERE_SUNSET, 0.52F, -1.0F);
    }

    private void band(float[] limb, float[] nx, float[] ny, float[] gain, float[] twilight,
                      float thickness, int dayRgb, int sunsetRgb, float alpha, float direction) {
        begin();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i < nx.length; i++) {
            float x = limb[i * 2];
            float y = limb[i * 2 + 1];
            int rgb = Draw.mix(dayRgb, sunsetRgb, twilight[i] * 0.72F);
            GL11.vertexColour(Draw.withAlpha(rgb, alpha * gain[i]));
            GL11.glVertex2f(x, y);
            GL11.vertexColour(Draw.withAlpha(rgb, 0.0F));
            GL11.glVertex2f(x + nx[i] * thickness * direction, y + ny[i] * thickness * direction);
        }
        GL11.glEnd();
        end();
    }

    // ---------------------------------------------------------------- biomes --

    private static final int DEEP_OCEAN = 0x1B2A55;
    private static final int OCEAN = 0x23406E;
    private static final int SHALLOWS = 0x2E5C86;
    private static final int BEACH = 0xC2B282;
    private static final int PLAINS = 0x6E9147;
    private static final int FOREST = 0x4A7232;
    private static final int DARK_FOREST = 0x36572A;
    private static final int SWAMP = 0x4B5A37;
    private static final int JUNGLE = 0x3D7C25;
    private static final int TAIGA = 0x4E6A55;
    private static final int TUNDRA = 0xB9C6C8;
    private static final int SNOW = 0xE6EDF0;
    private static final int DESERT = 0xD1BE88;
    private static final int SAVANNA = 0x9E9A55;
    private static final int BADLANDS = 0xA9653B;
    private static final int MOUNTAIN = 0x7E7F80;

    /** The square's biome colour, with a little per-square variation, cached. */
    private int ground(int i, int j) {
        int slot = Math.floorMod(i, CACHE_I) * CACHE_J + (j + CACHE_J / 2);
        if (this.cacheKey[slot] == i) {
            return this.cacheColour[slot];
        }
        int colour = shade(biome(i, j), 0.93F + 0.14F * (float) hash(i, j, 99));
        this.cacheKey[slot] = i;
        this.cacheColour[slot] = colour;
        return colour;
    }

    /** Whether a square carries a cluster of settlement lights visible on the night side. */
    private static boolean cityLight(int i, int j) {
        double elevation = fbm(i * 0.045, j * 0.045, 1);
        if (elevation < 0.505 || elevation > 0.68) {
            return false; // no cities in oceans or high peaks
        }
        double settlement = valueNoise(i * 0.16 + 19.3, j * 0.16 - 41.7, 17);
        return settlement > 0.72 && hash(i, j, 211) > 0.52;
    }

    private static int biome(int i, int j) {
        double elevation = fbm(i * 0.045, j * 0.045, 1);
        double moisture = fbm(i * 0.06 + 31.7, j * 0.06 - 12.3, 2);
        double temperature = fbm(i * 0.028 - 70.1, j * 0.028 + 40.9, 3);

        if (elevation < 0.42) {
            return DEEP_OCEAN;
        }
        if (elevation < 0.48) {
            return OCEAN;
        }
        if (elevation < 0.50) {
            return SHALLOWS;
        }
        if (elevation < 0.515 && temperature > 0.40) {
            return BEACH;
        }
        if (elevation > 0.70) {
            return elevation > 0.735 || temperature < 0.45 ? SNOW : MOUNTAIN;
        }
        if (temperature < 0.38) {
            return moisture > 0.5 ? TAIGA : (temperature < 0.33 ? SNOW : TUNDRA);
        }
        if (temperature > 0.60) {
            if (moisture < 0.44) {
                return elevation > 0.62 ? BADLANDS : DESERT;
            }
            if (moisture > 0.58) {
                return JUNGLE;
            }
            return SAVANNA;
        }
        if (moisture > 0.62) {
            return elevation < 0.56 ? SWAMP : DARK_FOREST;
        }
        if (moisture > 0.52) {
            return FOREST;
        }
        return PLAINS;
    }

    /** Cloud cover over a square, 0..1. Cells, like the game's own clouds. */
    private static float cloud(int i, int j) {
        double n = fbm(i * 0.11 + 5.0, j * 0.11 - 9.0, 7);
        return (float) Math.max(0.0, Math.min(1.0, (n - 0.56) / 0.09)) * 0.82F;
    }

    private static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    private static double valueNoise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fy = fy * fy * (3.0 - 2.0 * fy);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static double fbm(double x, double y, int seed) {
        double sum = 0.0;
        double amp = 0.5;
        double norm = 0.0;
        for (int o = 0; o < 4; o++) {
            sum += amp * valueNoise(x, y, seed * 31 + o);
            norm += amp;
            x *= 2.03;
            y *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    // --------------------------------------------------------------- station --

    /**
     * Placeholder station: a pressurised core, a node, a truss and four solar wings.
     * Each box is x0, y0, z0, x1, y1, z1 in station units, then its colour. Local +Z
     * faces the camera — the docking port leads.
     */
    private static final double[][] BOXES = {
            {-0.75, -0.75, -0.40, 0.75, 0.75, 0.40, 0xB9B5AA},
            {-0.50, -0.50, 0.40, 0.50, 0.50, 1.90, 0xDCD8CD},
            {-0.50, -0.50, -1.90, 0.50, 0.50, -0.40, 0xD2CEC2},
            {-0.28, -0.28, 1.90, 0.28, 0.28, 2.35, 0xA6A298},
            {0.75, -0.13, -0.13, 4.50, 0.13, 0.13, 0x8C8C8A},
            {-4.50, -0.13, -0.13, -0.75, 0.13, 0.13, 0x8C8C8A},
            {1.50, -0.035, 0.22, 4.30, 0.035, 1.95, 0x24397A},
            {1.50, -0.035, -1.95, 4.30, 0.035, -0.22, 0x24397A},
            {-4.30, -0.035, 0.22, -1.50, 0.035, 1.95, 0x24397A},
            {-4.30, -0.035, -1.95, -1.50, 0.035, -0.22, 0x24397A},
            {-0.05, 0.75, -0.05, 0.05, 1.70, 0.05, 0xC8C8C8},
            {-0.60, -1.25, -0.90, 0.60, -0.75, 0.90, 0xE8E8E4},
    };

    /** Corner indices of each face, wound outward, and the face's axis: 0..5 = -x +x -y +y -z +z. */
    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5},
    };

    /**
     * Where the station is this frame, in world space.
     *
     * <p>We are orbiting the planet and coming up on the station ahead of us: it starts
     * truly tucked behind the planet's curvature (below the horizon line, so the planet
     * occludes it at first), crests over the limb as we round the orbit, and rises into
     * view in front of us.
     */
    private double[] stationPosition(double cameraHeight, double focalLength,
                                     double[] f, double[] r, double[] u) {
        float t = Ease.clamp01(this.stationTime / APPROACH_SECONDS);
        // Depth goes as 1/z so that its size on screen grows evenly, but we hold the
        // station beyond the horizon distance while it crests the limb so the planet
        // genuinely occludes its lower half as it rises out from behind the curve.
        float travel = Ease.inCubic(Math.max(0.0F, (t - 0.12F) / 0.88F));
        double parkDepth = PARK_DEPTH / Math.max(0.45F, this.stationScaleFactor);
        double inverse = 1.0 / START_DEPTH + (1.0 / parkDepth - 1.0 / START_DEPTH) * travel;
        double depth = 1.0 / inverse;

        double horizonRatio = Math.tan(Math.acos(R / cameraHeight)
                - pitchFor(cameraHeight, focalLength));
        double parkX = (this.anchorX - this.centreX) / focalLength;
        double parkY = -(this.anchorY - this.centreY) / focalLength;
        // Start directly behind the planet's horizon line so the station is completely
        // hidden behind the Earth at t=0 and crests the limb large enough to see clearly.
        double startX = parkX * 0.25;
        double startY = -horizonRatio - 0.065;

        float rise = Ease.inOutCubic(t);
        double ratioX = startX + (parkX - startX) * rise;
        double ratioY = startY + (parkY - startY) * Ease.inOutCubic(t);
        // Once in place it floats gently on orbit.
        float bob = Ease.clamp01(t * 3.0F - 2.0F);
        ratioY += Math.sin(this.time * 0.25) * 0.007 * bob;
        ratioX += Math.sin(this.time * 0.16) * 0.004 * bob;

        double xs = ratioX * depth;
        double ys = ratioY * depth;
        return new double[] {
                this.camX + r[0] * xs + u[0] * ys + f[0] * depth,
                cameraHeight + r[1] * xs + u[1] * ys + f[1] * depth,
                this.camZ + r[2] * xs + u[2] * ys + f[2] * depth,
        };
    }

    private void drawStation(double[] position, double[] f, double[] r, double[] u) {
        // Orientation: local axes laid on the resting camera's, then turned so that the
        // station is seen three-quarters on, and rolled slowly about its long axis.
        double yaw = 0.55 + Math.sin(this.time * 0.045) * 0.14;
        double pitch = 0.32 + Math.sin(this.time * 0.035) * 0.05;
        double spin = this.time * 0.022;
        double[][] axes = stationAxes(f, r, u, yaw, pitch, spin);
        double s = STATION_SCALE;

        int boxes = BOXES.length;
        float[] fx = new float[boxes * 6 * 4];
        float[] fy = new float[boxes * 6 * 4];
        double[] depth = new double[boxes * 6];
        int[] colour = new int[boxes * 6];
        int faces = 0;

        double[] cx = new double[8];
        double[] cy = new double[8];
        double[] cz = new double[8];
        float[] sx = new float[8];
        float[] sy = new float[8];
        boolean[] ok = new boolean[8];
        double earthX = -position[0];
        double earthY = -position[1];
        double earthZ = -position[2];
        double earthLen = Math.sqrt(earthX * earthX + earthY * earthY + earthZ * earthZ);
        earthX /= earthLen;
        earthY /= earthLen;
        earthZ /= earthLen;

        float dayBounce = Ease.clamp01((float) ((this.sun[1] + 0.25) / 0.75));

        for (double[] box : BOXES) {
            for (int c = 0; c < 8; c++) {
                double lx = ((c & 1) == 0 ? box[0] : box[3]) * s;
                double ly = ((c & 2) == 0 ? box[1] : box[4]) * s;
                double lz = ((c & 4) == 0 ? box[2] : box[5]) * s;
                cx[c] = position[0] + axes[0][0] * lx + axes[1][0] * ly + axes[2][0] * lz;
                cy[c] = position[1] + axes[0][1] * lx + axes[1][1] * ly + axes[2][1] * lz;
                cz[c] = position[2] + axes[0][2] * lx + axes[1][2] * ly + axes[2][2] * lz;
                ok[c] = project(cx[c], cy[c], cz[c]);
                sx[c] = this.projX;
                sy[c] = this.projY;
            }
            for (int face = 0; face < 6; face++) {
                int[] idx = FACES[face];
                if (!ok[idx[0]] || !ok[idx[1]] || !ok[idx[2]] || !ok[idx[3]]) {
                    continue;
                }
                double[] axis = axes[face / 2];
                double sign = (face & 1) == 0 ? -1.0 : 1.0;
                double nx = axis[0] * sign;
                double ny = axis[1] * sign;
                double nz = axis[2] * sign;
                double mx = (cx[idx[0]] + cx[idx[2]]) * 0.5;
                double my = (cy[idx[0]] + cy[idx[2]]) * 0.5;
                double mz = (cz[idx[0]] + cz[idx[2]]) * 0.5;
                if (nx * (this.camX - mx) + ny * (this.camY - my) + nz * (this.camZ - mz) <= 0.0) {
                    continue;
                }
                double sunDot = Math.max(0.0, nx * this.sun[0] + ny * this.sun[1] + nz * this.sun[2]);
                double earth = Math.max(0.0, nx * earthX + ny * earthY + nz * earthZ);
                int base = (int) box[6];
                int lit = shade(base, (float) (0.14 + 1.02 * sunDot * (0.35 + 0.65 * dayBounce)));
                lit = add(lit, ATMOSPHERE_DAY, (float) (0.24 * earth * dayBounce));
                for (int k = 0; k < 4; k++) {
                    fx[faces * 4 + k] = sx[idx[k]];
                    fy[faces * 4 + k] = sy[idx[k]];
                }
                double vx = mx - this.camX;
                double vy = my - this.camY;
                double vz = mz - this.camZ;
                depth[faces] = vx * vx + vy * vy + vz * vz;
                colour[faces] = 0xFF000000 | (lit & 0xFFFFFF);
                faces++;
            }
        }

        // Far faces first.
        Integer[] order = new Integer[faces];
        for (int i = 0; i < faces; i++) {
            order[i] = Integer.valueOf(i);
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(depth[b.intValue()], depth[a.intValue()]));

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int n = 0; n < faces; n++) {
            int i = order[n].intValue() * 4;
            quad(fx[i], fy[i], fx[i + 1], fy[i + 1], fx[i + 2], fy[i + 2], fx[i + 3], fy[i + 3],
                    colour[order[n].intValue()]);
        }
        GL11.glEnd();
        end();

        // Navigation lights on the truss tips: red to port, green to starboard.
        float blink = (float) ((this.time % 2.4) / 2.4);
        if (blink < 0.10F) {
            float a = 1.0F - blink / 0.10F;
            navLight(position, axes, -4.5 * s, 0xFF4040, a);
            navLight(position, axes, 4.5 * s, 0x50FF70, a);
        }
    }

    private void navLight(double[] position, double[][] axes, double offset, int rgb, float alpha) {
        if (!project(position[0] + axes[0][0] * offset,
                position[1] + axes[0][1] * offset,
                position[2] + axes[0][2] * offset)) {
            return;
        }
        float s = (float) Math.max(2.0, this.focal * 0.002 / this.projDepth);
        Draw.rect(this.projX - s * 2.0F, this.projY - s * 2.0F, this.projX + s * 2.0F, this.projY + s * 2.0F,
                Draw.withAlpha(rgb, 0.24F * alpha));
        Draw.rect(this.projX - s * 1.0F, this.projY - s * 1.0F, this.projX + s * 1.0F, this.projY + s * 1.0F,
                Draw.withAlpha(rgb, 0.88F * alpha));
    }

    /** The station's local x, y, z axes in world space. */
    private static double[][] stationAxes(double[] f, double[] r, double[] u,
                                          double yaw, double pitch, double spin) {
        // Start with local axes on the camera's: x right, y up, z towards the viewer.
        double[] x = {r[0], r[1], r[2]};
        double[] y = {u[0], u[1], u[2]};
        double[] z = {-f[0], -f[1], -f[2]};
        // Roll about its own long axis.
        rotate(x, y, spin);
        // Turned about the vertical, then tipped towards the camera.
        rotate(z, x, yaw);
        rotate(y, z, pitch);
        return new double[][] {x, y, z};
    }

    /** Rotates the pair (a, b) in their own plane by {@code angle}. */
    private static void rotate(double[] a, double[] b, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        for (int k = 0; k < 3; k++) {
            double ak = a[k];
            double bk = b[k];
            a[k] = ak * c + bk * s;
            b[k] = bk * c - ak * s;
        }
    }

    // ---------------------------------------------------------------- drawing --

    private static void begin() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private static void end() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void quad(float x1, float y1, float x2, float y2,
                             float x3, float y3, float x4, float y4, int argb) {
        GL11.vertexColour(argb);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glVertex2f(x4, y4);
    }

    private static int shade(int rgb, float k) {
        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * k));
        int g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int add(int rgb, int tint, float k) {
        int r = Math.min(255, (rgb >> 16 & 0xFF) + (int) ((tint >> 16 & 0xFF) * k));
        int g = Math.min(255, (rgb >> 8 & 0xFF) + (int) ((tint >> 8 & 0xFF) * k));
        int b = Math.min(255, (rgb & 0xFF) + (int) ((tint & 0xFF) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}

package com.console.uky.config;

/**
 * One knob for how much work the interface is allowed to do.
 *
 * Everything expensive in this mod already had a setting, and that was the problem:
 * eight of them, in a config file, each phrased in terms of what it does rather than
 * what it costs. Somebody on a card that cannot hold sixty frames on the title screen
 * does not want to learn what supersampling the photon ring means — they want the
 * menu to stop stuttering. So the individual settings stay exactly as they are, and
 * this sits on top of them as a ceiling.
 *
 * <p><b>A preset only ever caps.</b> {@link #MAXIMUM} caps nothing, so it is the
 * behaviour this mod has always had and is what a config carrying no preset gets.
 * Below that, a value the player set by hand is still honoured whenever it is already
 * cheaper than the cap — turning particles off at maximum turns them off, and choosing
 * minimal never turns anything back on. That is the direction that cannot surprise
 * anyone: lowering the preset can only ever make the menu cheaper.
 *
 * <p>What each level actually costs is dominated by one thing. The black hole is
 * traced per pixel in a fragment shader, so its price is (resolution)² × (steps) ×
 * (traces per second), and the three of them are what these levels mostly move. The
 * rest — particles, grain, stars — is rounding error by comparison, and is cut at
 * minimal because on hardware that needs minimal, rounding errors have started to
 * matter too.
 */
public final class Quality {

    /** Config values, as written in {@code uky.cfg}. */
    public static final String POTATO = "potato";
    public static final String MINIMAL = "minimal";
    public static final String BALANCED = "balanced";
    public static final String MAXIMUM = "maximum";

    /** In the order the settings screen cycles them: cheapest first. */
    public static final String[] LEVELS = { POTATO, MINIMAL, BALANCED, MAXIMUM };

    private static final int L_POTATO = 0;
    private static final int L_MINIMAL = 1;
    private static final int L_BALANCED = 2;
    private static final int L_MAXIMUM = 3;

    /**
     * Last string parsed and what it came out as.
     *
     * These are read several times a frame — once per effect that has to decide
     * whether to draw — and the value only changes when somebody opens the settings.
     */
    private static String cachedRaw;
    private static int cachedLevel = L_MAXIMUM;

    private Quality() {
    }

    private static int level() {
        String raw = UiConfig.graphics;
        if (raw == null) {
            return L_MAXIMUM;
        }
        if (raw.equals(cachedRaw)) {
            return cachedLevel;
        }
        String value = raw.trim().toLowerCase();
        int level;
        if (POTATO.equals(value)) {
            level = L_POTATO;
        } else if (MINIMAL.equals(value)) {
            level = L_MINIMAL;
        } else if (BALANCED.equals(value)) {
            level = L_BALANCED;
        } else {
            // Anything unrecognised means the file was hand-edited to something this
            // build does not know. Full quality is the safe reading: it is what the
            // mod does without a preset at all.
            level = L_MAXIMUM;
        }
        cachedRaw = raw;
        cachedLevel = level;
        return level;
    }

    /** The current preset, normalised to one of {@link #LEVELS}. */
    public static String current() {
        return LEVELS[level()];
    }

    /** Translation key naming the current preset, for the settings screen. */
    public static String label() {
        return "uky.graphics." + current();
    }

    /** Moves to the next preset and writes it to the config file. */
    public static void cycle() {
        UiConfig.setGraphics(LEVELS[(level() + 1) % LEVELS.length]);
    }

    // ------------------------------------------------------------ black hole --

    /**
     * Whether the black hole is drawn at all.
     *
     * The one thing {@link #POTATO} takes away outright rather than turning down.
     * Every other preset still traces it — that is the whole cost of this menu, a
     * light path integrated per pixel per frame — and on hardware where even the
     * cheapest trace is too much, the honest answer is not to draw it. The menus keep
     * their layout, their panels and their type; the backdrop becomes the flat colour
     * the {@code solid} background already offers, which costs one quad.
     */
    public static boolean blackHole() {
        return level() > L_POTATO;
    }

    /**
     * Trace resolution, as a percentage of the window, after the cap.
     *
     * The single most expensive number in the mod: the shader runs once per pixel of
     * this buffer, so 150 costs nine times what 50 does. Minimal traces at half the
     * window and lets the texture unit stretch it back up, which is soft on the
     * shadow rim and is the difference between a menu that runs and one that does not.
     */
    public static int blackHoleResolution() {
        int configured = Math.min(200, Math.max(50, UiConfig.blackHoleResolution));
        switch (level()) {
            case L_POTATO:
            case L_MINIMAL: return Math.min(configured, 50);
            case L_BALANCED: return Math.min(configured, 100);
            default: return configured;
        }
    }

    /** Light-path steps per pixel, after the cap. Linear in cost, unlike resolution. */
    public static int blackHoleQuality() {
        int configured = UiConfig.blackHoleQuality;
        switch (level()) {
            // 200 is the floor the config itself enforces — below it the photon ring
            // never closes and the hole reads as a dark blob rather than a cheap one.
            case L_POTATO:
            case L_MINIMAL: return Math.min(configured, 200);
            case L_BALANCED: return Math.min(configured, 260);
            default: return configured;
        }
    }

    /**
     * Nanoseconds between traces on a settled menu.
     *
     * The third factor in the black hole's cost and the cheapest one to spend: the
     * dissolve between consecutive traces is what the eye actually follows, so
     * tracing four times a second instead of seven is nearly invisible and saves
     * nearly half the work.
     *
     * @param moving whether the camera is still easing to a new framing, where a
     *               slow rate reads as the hole lagging behind the screen
     */
    public static long traceIntervalNanos(boolean moving) {
        switch (level()) {
            case L_POTATO:
            case L_MINIMAL: return moving ? 110_000_000L : 260_000_000L;
            case L_BALANCED: return moving ? 70_000_000L : 190_000_000L;
            default: return moving ? 50_000_000L : 140_000_000L;
        }
    }

    /**
     * How many background stars to lens, out of the {@code max} that were seeded.
     *
     * Each one is two quads and a square root on the CPU, in immediate mode. That is
     * genuinely cheap and it is also four hundred and twenty of them every frame,
     * which on the cards this level exists for is no longer nothing.
     */
    public static int starCount(int max) {
        switch (level()) {
            case L_POTATO: return 0;
            case L_MINIMAL: return Math.min(max, 140);
            case L_BALANCED: return Math.min(max, 280);
            default: return max;
        }
    }

    /** Infalling stars drawn, out of the {@code max} seeded. None at minimal. */
    public static int infallCount(int max) {
        switch (level()) {
            case L_POTATO:
            case L_MINIMAL: return 0;
            case L_BALANCED: return Math.min(max, 8);
            default: return max;
        }
    }

    // ---------------------------------------------------------------- overlay --

    public static boolean ambientParticles() {
        return UiConfig.ambientParticles && level() > L_MINIMAL;
    }

    public static int particleBudget() {
        if (!ambientParticles()) {
            return 0;
        }
        int configured = UiConfig.ambientParticleBudget;
        return level() == L_BALANCED ? Math.min(configured, 18) : configured;
    }

    /**
     * Film grain, which is a few hundred one-pixel quads a frame in immediate mode
     * and the most expensive thing here per unit of anybody noticing it.
     */
    public static boolean filmGrain() {
        return UiConfig.filmGrain && level() > L_BALANCED;
    }

    /** Kept at every level: two gradients, and it is what keeps text readable. */
    public static boolean vignette() {
        return UiConfig.vignette;
    }

    public static boolean scanlines() {
        return UiConfig.scanlines && level() > L_MINIMAL;
    }

    /** Slow zoom and pan on the background image. Sampling cost, not geometry. */
    public static boolean backgroundDrift() {
        return UiConfig.backgroundDrift && level() > L_MINIMAL;
    }

    /**
     * The opening shot.
     *
     * Off at minimal, and not only for the cost: it is three and a half seconds of
     * the heaviest frame in the mod — full-screen shockwave over a hole being traced
     * at a warped scale — and on hardware that struggles it is the first thing the
     * player sees struggling.
     */
    public static boolean intro() {
        return UiConfig.introEnabled && level() > L_MINIMAL;
    }

    /**
     * Multiplier on the death scene's damage — scanlines, tape band, dropouts, grain.
     *
     * Scaled rather than switched off. The scene is the one place in the mod where
     * the effects are the content, so it degrades instead of disappearing.
     */
    public static float deathIntensity() {
        switch (level()) {
            case L_POTATO: return 0.25F;
            case L_MINIMAL: return 0.45F;
            case L_BALANCED: return 0.80F;
            default: return 1.0F;
        }
    }
}
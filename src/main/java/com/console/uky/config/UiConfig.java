package com.console.uky.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Single source of truth for everything a pack author would want to tweak
 * without recompiling: which vanilla screens get replaced, how heavy the
 * ambient effects are, and the colour palette.
 *
 * Loaded once in pre-init. Colours are stored as {@code 0xRRGGBB} hex strings in
 * the config file (alpha is applied per-use in code, so authors never have to
 * reason about 8-digit values).
 */
public final class UiConfig {

    private static final String CAT_SCREENS = "screens";
    private static final String CAT_EFFECTS = "effects";
    private static final String CAT_THEME = "theme";
    private static final String CAT_MENU = "mainmenu";
    private static final String CAT_SPLASH = "splash";

    /**
     * Path the splash uses to find the config before FML hands us a pre-init
     * event; must match the file Forge derives from the mod id.
     */
    private static final String CONFIG_PATH = "config/uky.cfg";

    private static Configuration config;

    // ---- screens ----
    public static boolean replaceMainMenu = true;
    public static boolean replaceOptions = true;
    public static boolean replacePauseMenu = true;
    public static boolean replaceWorldList = true;
    public static boolean replaceLoadingScreen = true;

    // ---- effects ----
    public static boolean ambientParticles = true;
    public static int ambientParticleBudget = 40;
    public static boolean vignette = true;
    public static boolean filmGrain = true;
    public static boolean scanlines = false;
    /** Slow zoom/drift on the background image. */
    public static boolean backgroundDrift = true;
    public static boolean buttonSounds = true;
    /** One of {@code blackhole}, {@code image}, {@code solid}. */
    public static String background = "blackhole";
    /**
     * Integration steps per pixel for the black hole. This is the single knob that
     * decides what the menu costs to draw — the shader traces a light path for
     * every pixel it covers.
     */
    public static int blackHoleQuality = 200;
    /**
     * Resolution the black hole is traced at, as a percentage of the window.
     *
     * 100 draws it straight to the screen at native resolution. Above that it is
     * traced into a larger buffer and averaged down, which is real anti-aliasing on
     * the shadow rim and the photon ring — at the square of the cost. Below 100 is
     * the cheap way out for weak hardware, and it looks it: the edges are the
     * sharpest thing in the image, so they are the first thing to go soft.
     */
    public static int blackHoleResolution = 150;

    // ---- intro ----
    public static boolean introEnabled = true;
    public static boolean menuMusic = true;
    public static double menuMusicVolume = 0.55D;

    // ---- main menu ----
    public static boolean showSingleplayer = true;
    public static boolean showMultiplayer = true;
    public static boolean showModList = true;
    /** Title-screen composition: {@code center} or {@code left}. */
    public static String layout = "left";
    /** Link buttons under the menu, each entry {@code Label|https://...}. */
    public static String[] links = new String[0];
    /** Wordmark shown under the logo, or on its own when no logo image is present. */
    public static String title = "ULTRAKILL YOURSELF";
    /** Free-form line under the title; empty hides it. */
    public static String tagline = "";
    /** Bottom-left credit line; empty hides it. */
    public static String footer = "";

    // ---- splash ----
    public static boolean customSplash = true;
    public static String[] splashTips = new String[0];

    // ---- theme (0xRRGGBB) ----
    public static int colorBackground = 0x0B0B0E;
    public static int colorSurface = 0x14141A;
    public static int colorAccent = 0xC8A24B;
    public static int colorAccentAlt = 0x8C5A2B;
    public static int colorText = 0xE6E2D8;
    public static int colorTextDim = 0x8A8578;
    public static int colorDanger = 0xB4472F;

    private UiConfig() {
    }

    /** Loads the config file, unless something already did (see {@link #loadEarly}). */
    public static void load(File file) {
        if (config != null) {
            return;
        }
        config = new Configuration(file);
        config.load();
        read();
        save();
    }

    /**
     * Loads the config from its conventional path.
     *
     * The splash screen runs long before pre-init, so it cannot wait for FML to
     * hand over a config file. Resolving the path from the game directory gives
     * the same file the mod later receives, and {@link #load} then no-ops.
     */
    public static void loadEarly(File gameDir) {
        load(new File(gameDir, CONFIG_PATH));
    }

    /** Re-reads every value from the in-memory config object. */
    private static void read() {
        replaceMainMenu = bool(CAT_SCREENS, "replaceMainMenu", true,
                "Replace the vanilla title screen with the UKY main menu.");
        replaceOptions = bool(CAT_SCREENS, "replaceOptions", true,
                "Replace the vanilla options screen. Sub-screens (video, controls, ...) stay vanilla.");
        replacePauseMenu = bool(CAT_SCREENS, "replacePauseMenu", true,
                "Replace the in-game pause menu.");
        replaceWorldList = bool(CAT_SCREENS, "replaceWorldList", true,
                "Show singleplayer worlds as tiles with a picture of where you left off. "
                        + "The picture is taken automatically on the way out and stored "
                        + "inside the world's own save folder.");
        replaceLoadingScreen = bool(CAT_SCREENS, "replaceLoadingScreen", true,
                "Use that same picture as the backdrop while the world loads.");

        ambientParticles = bool(CAT_EFFECTS, "ambientParticles", true,
                "Drifting dust/ember particles behind the menus.");
        ambientParticleBudget = clampInt(CAT_EFFECTS, "ambientParticleBudget", 40, 0, 400,
                "Maximum simultaneous ambient particles. Lower this on weak machines.");
        vignette = bool(CAT_EFFECTS, "vignette", true, "Darkened screen edges.");
        filmGrain = bool(CAT_EFFECTS, "filmGrain", true, "Subtle animated noise over the background.");
        scanlines = bool(CAT_EFFECTS, "scanlines", false, "CRT-style horizontal scanlines.");
        backgroundDrift = bool(CAT_EFFECTS, "backgroundDrift", true,
                "Slow zoom and pan on the menu background image.");
        buttonSounds = bool(CAT_EFFECTS, "buttonSounds", true, "Play a click sound on button press.");
        background = str(CAT_EFFECTS, "background", "blackhole",
                "Menu backdrop: 'blackhole' (rendered in code), 'image' "
                        + "(assets/uky/textures/gui/background.png) or 'solid'.");
        // The floor is 200 and not lower on purpose. Near the photon sphere a step is
        // about 0.11 long and the circuit about 19, so one turn costs some 165 steps,
        // and the photon ring is made of light that went most of the way round and
        // came back out across the disk. Below roughly 200 those rays are cut off
        // mid-turn and the ring does not form at all — the hole comes out as a plain
        // dark blob with no rim, which is what a config carrying 140 looked like. It
        // reads as a broken render rather than a cheaper one, so the setting no longer
        // offers it. Existing configs below the floor are raised to it on load.
        blackHoleQuality = clampInt(CAT_EFFECTS, "blackHoleQuality", 200, 200, 420,
                "Light-path steps per pixel for the black hole. This is what it costs "
                        + "to draw. 200 is the least that still completes the orbits "
                        + "the photon ring is made of; below that the ring disappears "
                        + "entirely, so the range starts there. 320 is reference "
                        + "quality. If the menu runs badly, lower blackHoleResolution "
                        + "instead — it costs roughly the square of its value.");
        blackHoleResolution = clampInt(CAT_EFFECTS, "blackHoleResolution", 150, 50, 200,
                "Resolution the black hole is traced at, as a percentage of the "
                        + "window. 100 is native. 150-200 supersamples and averages "
                        + "down, which smooths the shadow rim and the photon ring at "
                        + "roughly the square of the cost. Below 100 is cheaper but "
                        + "visibly soft, because the edges are the sharpest thing in "
                        + "the picture.");

        introEnabled = bool(CAT_MENU, "intro", true,
                "Play the impact-and-black-hole intro the first time the title screen "
                        + "opens each launch. Click or press a key to skip.");
        menuMusic = bool(CAT_MENU, "music", true, "Loop the menu track on the title screen.");
        menuMusicVolume = dbl(CAT_MENU, "musicVolume", 0.55D, 0.0D, 1.0D,
                "Volume of the menu track, on top of the game's music slider.");

        showSingleplayer = bool(CAT_MENU, "showSingleplayer", true, "Show the singleplayer entry.");
        showMultiplayer = bool(CAT_MENU, "showMultiplayer", true, "Show the multiplayer entry.");
        showModList = bool(CAT_MENU, "showModList", true, "Show the mod list entry.");
        layout = str(CAT_MENU, "layout", "left",
                "Title screen composition: 'left' puts the menu in a column on the left with "
                        + "the black hole off-centre, 'center' stacks everything down the middle.");
        links = strList(CAT_MENU, "links", new String[0],
                "Link buttons under the menu. One entry per line, formatted 'Label|https://example.com'.");
        title = str(CAT_MENU, "title", "ULTRAKILL YOURSELF",
                "Wordmark drawn on the title screen. Leave empty when the logo image already contains one.");
        tagline = str(CAT_MENU, "tagline", "", "Line shown under the title. Leave empty to hide.");
        footer = str(CAT_MENU, "footer", "", "Line shown in the bottom-left corner. Leave empty to hide.");

        customSplash = bool(CAT_SPLASH, "customSplash", true,
                "Replace FML's mod-loading screen. Turn off if the loading screen misbehaves "
                        + "on your GPU; the game falls back to Forge's own splash.");
        splashTips = strList(CAT_SPLASH, "tips", new String[0],
                "Lines cycled at the bottom of the loading screen. Leave empty to show none.");

        colorBackground = hex(CAT_THEME, "background", 0x0B0B0E, "Base backdrop colour.");
        colorSurface = hex(CAT_THEME, "surface", 0x14141A, "Panel and button fill colour.");
        colorAccent = hex(CAT_THEME, "accent", 0xC8A24B, "Primary accent (hover, focus, progress).");
        colorAccentAlt = hex(CAT_THEME, "accentAlt", 0x8C5A2B, "Secondary accent for gradients and embers.");
        colorText = hex(CAT_THEME, "text", 0xE6E2D8, "Primary text colour.");
        colorTextDim = hex(CAT_THEME, "textDim", 0x8A8578, "Muted text colour.");
        colorDanger = hex(CAT_THEME, "danger", 0xB4472F, "Destructive action colour (quit, disconnect).");
    }

    /** Re-reads the in-memory config after the in-game editor changed it. */
    public static void reload() {
        if (config == null) {
            return;
        }
        read();
        save();
    }

    public static void save() {
        if (config != null && config.hasChanged()) {
            config.save();
        }
    }

    public static Configuration raw() {
        return config;
    }

    // ------------------------------------------------------------- accessors --

    private static boolean bool(String cat, String key, boolean def, String comment) {
        return config.getBoolean(key, cat, def, comment);
    }

    private static String str(String cat, String key, String def, String comment) {
        return config.getString(key, cat, def, comment);
    }

    private static String[] strList(String cat, String key, String[] def, String comment) {
        return config.getStringList(key, cat, def, comment);
    }

    private static int clampInt(String cat, String key, int def, int min, int max, String comment) {
        int value = config.getInt(key, cat, def, min, max, comment);

        // getInt clamps what it hands back but leaves the stored property alone, so a
        // file carrying an out-of-range value goes on displaying it while the game
        // quietly uses something else. Anyone who then went looking for why the black
        // hole ignored their setting would find the old number sitting there. Write
        // the effective value back instead.
        Property property = config.get(cat, key, def);
        if (property.getInt(def) != value) {
            property.set(value);
        }
        return value;
    }

    private static double dbl(String cat, String key, double def, double min, double max, String comment) {
        return config.get(cat, key, def, comment, min, max).getDouble(def);
    }

    private static int hex(String cat, String key, int def, String comment) {
        String raw = config.getString(key, cat, String.format("0x%06X", def),
                comment + " Format: 0xRRGGBB.");
        try {
            return Integer.decode(raw.trim()) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            // A typo in the config should not black out the menu — fall back silently.
            return def;
        }
    }
}

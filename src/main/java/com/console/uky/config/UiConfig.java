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
 *
 * <p><b>Defaults live in the field initialisers below, and only there.</b> Each
 * read in {@link #read} passes its own field in as the default, so changing a
 * value here changes what a fresh config is written with, what the in-game
 * "reset" restores, and what the game uses before the file has been read —
 * all at once. There used to be a second copy of every default inside
 * {@code read()}, and the two disagreeing was invisible until someone edited the
 * wrong one and nothing happened.
 */
public final class UiConfig {

    private static final String CAT_SCREENS = "screens";
    private static final String CAT_EFFECTS = "effects";
    private static final String CAT_THEME = "theme";
    private static final String CAT_MENU = "mainmenu";
    private static final String CAT_SPLASH = "splash";
    private static final String CAT_DEATH = "death";

    /**
     * Where the config lives, relative to the game directory.
     *
     * In its own folder rather than loose in {@code config/}: the mod already owns
     * {@code config/uky/icons} for link artwork, and having the settings for those
     * icons sit in a different directory from the icons themselves is the kind of
     * thing nobody finds twice. The splash screen resolves this path directly —
     * it runs long before FML offers a config file — so it has to be spelled out
     * here rather than derived from the mod id.
     */
    private static final String CONFIG_PATH = "config/uky/uky.cfg";


    private static Configuration config;

    // ---- screens ----
    public static boolean replaceMainMenu = true;
    public static boolean replaceOptions = true;
    public static boolean replacePauseMenu = true;
    public static boolean replaceWorldList = true;
    public static boolean replaceLoadingScreen = true;
    public static boolean replaceDeathScreen = true;
    public static boolean replacePlayerList = true;
    /**
     * Open the settings once, the first time the title screen is reached.
     *
     * Both the switch and the marker: it is turned off the moment it fires, and
     * turning it back on by hand makes it happen again. One key rather than a
     * preference plus a hidden "already done" flag, because the second one is a piece
     * of state nobody can guess the meaning of in a file meant to be read.
     */
    public static boolean showSettingsOnFirstRun = true;

    // ---- death ----
    public static double deathSceneSeconds = 3.0D;
    public static boolean deathAutoRespawn = true;
    public static double deathAutoRespawnSeconds = 6.5D;
    public static double deathIntensity = 1.0D;
    public static boolean deathSounds = true;
    public static double deathVolume = 0.85D;


    // ---- effects ----
    /**
     * Ceiling on everything below it: {@code minimal}, {@code balanced} or
     * {@code maximum}. See {@link Quality}, which is what actually reads it.
     *
     * Defaults to maximum because that is what this mod did before the setting
     * existed, and a config that silently downgrades an existing install is a bug
     * report about the menu looking worse after an update.
     */
    public static String graphics = Quality.MAXIMUM;
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
    public static String linkIconFolder = "config/uky/icons";
    /** Wordmark shown under the logo, or on its own when no logo image is present. */
    public static String title = "ULTRAKILL YOURSELF";
    /** Free-form line under the title; empty hides it. */
    public static String tagline = "";
    /** Bottom-left credit line; empty hides it. */
    public static String footer = "";
    public static String footerRight = "UKYUI %version%";

    // ---- splash ----
    /**
     * What the loading screen says out of the box.
     *
     * Shipping lines rather than an empty list on purpose: an option whose default
     * shows nothing is an option nobody discovers, and "tips" gives no clue where
     * they would even appear. Seeing them once is the whole explanation.
     */
    private static final String[] DEFAULT_TIPS = {
        "Loading a few hundred mods. This takes a moment.",
        "Everything in this menu is configurable — config/uky/uky.cfg.",
        "F3 + G draws chunk borders.",
        "Sleeping through the night skips the mobs, not the danger.",
        "Do not dig straight down.",
    };

    public static boolean customSplash = true;
    public static boolean showTips = false;
    public static String[] splashTips = DEFAULT_TIPS;

    // ---- theme (0xRRGGBB) ----
    public static int colorBackground = 0x0B0B0E;
    public static int colorSurface = 0x14141A;
    public static int colorAccent = 0xC8A24B;
    public static int colorAccentAlt = 0x8C5A2B;
    public static int colorText = 0xE6E2D8;
    public static int colorTextDim = 0x8A8578;
    public static int colorDanger = 0xB4472F;
    /** Black hole disk colours: inner (hot), middle, outer (cold). */
    public static int colorBlackHoleHot = 0xFFF3E4;
    public static int colorBlackHoleMid = 0xC8A24B;
    public static int colorBlackHoleCold = 0x8C5A2B;

    private UiConfig() {
    }

    /** Loads the config file, unless something already did (see {@link #loadEarly}). */
    public static void load(File file) {
        if (config != null) {
            return;
        }
        migrate(file);
        config = new Configuration(file);
        config.load();
        read();
        // Written every launch rather than only when a value changed, because the
        // comments are half of what this file is for. Forge marks the config dirty
        // when a *value* is added or edited, never when a comment is — so a build
        // that only reworded an explanation left every existing config still
        // carrying the old one, which is the version anyone would go and read.
        // Values are preserved either way; only the prose is refreshed.
        config.save();
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

    /**
     * A setting with everything the loading screen cannot draw taken out of it.
     *
     * The loading screen runs before the resource system exists, so the only glyphs
     * it can reach are the ones in the default font sheet — the printable ASCII range
     * and nothing else. That is not a cosmetic limit. Under a renderer replacement
     * such as Angelica, asking for a character outside the sheet sends the request to
     * a unicode font provider whose class initialiser reads {@code glyph_sizes.bin}
     * from a resource manager that is still empty. It throws, and a failed static
     * initialiser marks that class unusable for the rest of the JVM's life: every
     * piece of non-Latin text drawn later in the session then dies with
     * {@code NoClassDefFoundError}, starting with the language screen, with nothing
     * in the crash naming the loading screen that caused it.
     *
     * <p>So one Cyrillic {@code title} in the config costs the whole session. This is
     * the guard against that, and it is deliberately applied at the loading screen
     * rather than when the file is read: the same {@code title} is the main menu's
     * wordmark, and there — after the resources are up — any script at all is fine.
     *
     * @return the text, with anything outside printable ASCII replaced by "?"
     */
    public static String splashSafe(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder safe = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c <= '~') {
                if (safe != null) {
                    safe.append(c);
                }
                continue;
            }
            if (safe == null) {
                safe = new StringBuilder(text.length());
                safe.append(text, 0, i);
            }
            safe.append('?');
        }
        if (safe == null) {
            return text;
        }
        // System.err rather than the logger: this runs while mods are still loading.
        System.err.println("[UKY] the loading screen cannot draw \"" + text
                + "\" — it has no font for anything outside plain ASCII this early,"
                + " so it is showing \"" + safe + "\" instead. Menus are unaffected.");
        return safe.toString();
    }

    /**
     * Moves a config left over from when this file lived in {@code config/}.
     *
     * Silent and best-effort. A pack that has already been set up should not lose
     * its palette because the file moved, and if the move cannot be made — the old
     * file is locked, the folder is read-only — the worst case is a fresh config
     * with default values, which is what a new install gets anyway.
     */
    private static void migrate(File target) {
        File folder = target.getParentFile();
        if (target.exists() || folder == null || folder.getParentFile() == null) {
            return;
        }
        // The old file is a sibling of this folder — config/uky.cfg next to
        // config/uky/ — which holds wherever the game directory happens to be.
        File legacy = new File(folder.getParentFile(), target.getName());
        if (!legacy.isFile()) {
            return;
        }
        if ((folder.isDirectory() || folder.mkdirs()) && legacy.renameTo(target)) {
            System.out.println("[UKY] Config moved to " + target.getPath());
        }
    }

    /**
     * A line at the top of each section saying what the section is for.
     *
     * Forge writes these into the file above the block, and they are the first thing
     * anyone opening it reads. The per-key comments explain a setting; these explain
     * why the block exists at all, which is the part a pack author actually has to
     * guess at otherwise.
     */
    private static void describeCategories() {
        config.setCategoryComment(CAT_SCREENS,
                "Which vanilla screens this mod takes over. Turn one off and that "
                        + "screen goes back to vanilla; everything else keeps working.");
        config.setCategoryComment(CAT_MENU,
                "The main menu: wordmark, which entries are shown, the music, and the "
                        + "link buttons underneath. See 'links' for how to add YouTube, "
                        + "Discord, Boosty and anything else, with your own icons.");
        config.setCategoryComment(CAT_DEATH,
                "The death scene. It has no buttons: the picture cuts out, the scene "
                        + "plays, then any key or click comes back — and if the player "
                        + "does nothing, it comes back on its own.");
        config.setCategoryComment(CAT_SPLASH,
                "The mod-loading screen at startup — the one with the progress bar, "
                        + "before the main menu. Not the world-loading screen.");
        config.setCategoryComment(CAT_EFFECTS,
                "The backdrop and everything drawn over it. If the menu runs badly, "
                        + "lower blackHoleResolution first: it costs roughly the square "
                        + "of its value, while blackHoleQuality costs a straight line.");
        config.setCategoryComment(CAT_THEME,
                "The palette, as 0xRRGGBB. Only these are stored; every hover, border "
                        + "and shadow in the interface is derived from them, so changing "
                        + "'accent' alone restyles the whole thing consistently.");
    }

    /** Re-reads every value from the in-memory config object. */
    private static void read() {
        describeCategories();

        replaceMainMenu = bool(CAT_SCREENS, "replaceMainMenu", replaceMainMenu,
                "Replace the vanilla title screen with the UKY main menu.");
        replaceOptions = bool(CAT_SCREENS, "replaceOptions", replaceOptions,
                "Replace the vanilla options screen. Sub-screens (video, controls, ...) stay vanilla.");
        replacePauseMenu = bool(CAT_SCREENS, "replacePauseMenu", replacePauseMenu,
                "Replace the in-game pause menu.");
        replaceWorldList = bool(CAT_SCREENS, "replaceWorldList", replaceWorldList,
                "Show singleplayer worlds as tiles with a picture of where you left off. "
                        + "The picture is taken automatically on the way out and stored "
                        + "inside the world's own save folder.");
        replaceLoadingScreen = bool(CAT_SCREENS, "replaceLoadingScreen", replaceLoadingScreen,
                "Use that same picture as the backdrop while the world loads.");
        replaceDeathScreen = bool(CAT_SCREENS, "replaceDeathScreen", replaceDeathScreen,
                "Replace the death screen with the death scene: the picture cuts out, "
                        + "the tape fails, a heart winds down. No buttons — any key or "
                        + "click comes back once it has played.");
        replacePlayerList = bool(CAT_SCREENS, "replacePlayerList", replacePlayerList,
                "Replace the Tab player list. Vanilla sizes its box by the server's "
                        + "player cap rather than by who is actually online, so one "
                        + "player on a sixty-slot server gets a screen-high panel of "
                        + "empty rows. This one is as tall as the names in it, sorts "
                        + "them, shows the ping as a number and marks your own row.");
        showSettingsOnFirstRun = bool(CAT_SCREENS, "showSettingsOnFirstRun", showSettingsOnFirstRun,
                "Open the settings screen by itself the first time you reach the "
                        + "title screen, so the graphics preset and the rest are found "
                        + "rather than looked for. Turns itself off once it has "
                        + "happened; set it back to true to see it again.");

        deathSceneSeconds = dbl(CAT_DEATH, "sceneSeconds", deathSceneSeconds, 0.0D, 30.0D,
                "Seconds of scene before a key or a click will bring the player back. "
                        + "Nothing is listening before this.");
        deathAutoRespawn = bool(CAT_DEATH, "autoRespawn", deathAutoRespawn,
                "Come back on our own if the player does nothing. Never in hardcore, "
                        + "where there is nothing to come back to.");
        deathAutoRespawnSeconds = dbl(CAT_DEATH, "autoRespawnSeconds", deathAutoRespawnSeconds, 0.5D, 120.0D,
                "Seconds from the death to that automatic return. Values below "
                        + "sceneSeconds simply return as soon as the scene ends.");
        deathIntensity = dbl(CAT_DEATH, "intensity", deathIntensity, 0.0D, 2.0D,
                "How damaged the picture gets: scanlines, tape band, dropouts, grain. "
                        + "Zero leaves the fade and the text and nothing else.");
        deathSounds = bool(CAT_DEATH, "sounds", deathSounds,
                "Play the heartbeat and breathing under the death scene.");
        deathVolume = dbl(CAT_DEATH, "volume", deathVolume, 0.0D, 1.0D,
                "Volume of those two, on top of the game's master slider.");

        graphics = str(CAT_EFFECTS, "graphics", graphics,
                "One ceiling over everything else in this section, for people who "
                        + "would rather pick a word than tune eight numbers:\n"
                        + "    maximum  - no ceiling. Every setting below is used as "
                        + "written, which is what this mod did before this option "
                        + "existed. The default.\n"
                        + "    balanced - the black hole is traced at native "
                        + "resolution rather than supersampled, a little less often, "
                        + "and the film grain goes. Roughly half the GPU cost of "
                        + "maximum, and hard to tell apart in motion.\n"
                        + "    minimal  - for weak or old cards. The hole is traced "
                        + "at half resolution and four times a second, the intro, the "
                        + "particles, the grain and the background drift are off, and "
                        + "the death scene is toned down. Roughly a fifth of the cost.\n"
                        + "    potato   - no black hole at all. Every other preset "
                        + "still traces it, and that trace is the entire cost of this "
                        + "menu; where even the cheapest one is too much the backdrop "
                        + "becomes a flat colour and the menus keep everything else.\n"
                        + "This can only ever lower a setting, never raise one: "
                        + "anything you have already turned off stays off at every "
                        + "level, and 'maximum' does not undo your own choices. It is "
                        + "also on the Video tab of the in-game settings, so it can "
                        + "be changed without editing this file.");
        ambientParticles = bool(CAT_EFFECTS, "ambientParticles", ambientParticles,
                "Drifting dust/ember particles behind the menus. Off entirely at "
                        + "graphics=minimal.");
        ambientParticleBudget = clampInt(CAT_EFFECTS, "ambientParticleBudget", ambientParticleBudget, 0, 400,
                "Maximum simultaneous ambient particles. Lower this on weak machines.");
        vignette = bool(CAT_EFFECTS, "vignette", vignette, "Darkened screen edges.");
        filmGrain = bool(CAT_EFFECTS, "filmGrain", filmGrain, "Subtle animated noise over the background.");
        scanlines = bool(CAT_EFFECTS, "scanlines", scanlines, "CRT-style horizontal scanlines.");
        backgroundDrift = bool(CAT_EFFECTS, "backgroundDrift", backgroundDrift,
                "Slow zoom and pan on the menu background image.");
        buttonSounds = bool(CAT_EFFECTS, "buttonSounds", buttonSounds, "Play a click sound on button press.");
        background = str(CAT_EFFECTS, "background", background,
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
        blackHoleQuality = clampInt(CAT_EFFECTS, "blackHoleQuality", blackHoleQuality, 200, 420,
                "Light-path steps per pixel for the black hole. This is what it costs "
                        + "to draw. 200 is the least that still completes the orbits "
                        + "the photon ring is made of; below that the ring disappears "
                        + "entirely, so the range starts there. 320 is reference "
                        + "quality. If the menu runs badly, lower blackHoleResolution "
                        + "instead — it costs roughly the square of its value.");
        blackHoleResolution = clampInt(CAT_EFFECTS, "blackHoleResolution", blackHoleResolution, 50, 200,
                "Resolution the black hole is traced at, as a percentage of the "
                        + "window. 100 is native. 150-200 supersamples and averages "
                        + "down, which smooths the shadow rim and the photon ring at "
                        + "roughly the square of the cost. Below 100 is cheaper but "
                        + "visibly soft, because the edges are the sharpest thing in "
                        + "the picture.");

        introEnabled = bool(CAT_MENU, "intro", introEnabled,
                "Play the impact-and-black-hole intro the first time the title screen "
                        + "opens each launch. Click or press a key to skip.");
        menuMusic = bool(CAT_MENU, "music", menuMusic, "Loop the menu track on the title screen.");
        menuMusicVolume = dbl(CAT_MENU, "musicVolume", menuMusicVolume, 0.0D, 1.0D,
                "Volume of the menu track, on top of the game's music slider.");

        showSingleplayer = bool(CAT_MENU, "showSingleplayer", showSingleplayer, "Show the singleplayer entry.");
        showMultiplayer = bool(CAT_MENU, "showMultiplayer", showMultiplayer, "Show the multiplayer entry.");
        showModList = bool(CAT_MENU, "showModList", showModList, "Show the mod list entry.");
        layout = str(CAT_MENU, "layout", layout,
                "Title screen composition: 'left' puts the menu in a column on the left with "
                        + "the black hole off-centre, 'center' stacks everything down the middle.");
        links = strList(CAT_MENU, "links", links,
                "Link buttons under the main menu. One per line:\n"
                        + "    Label|https://example.com\n"
                        + "    Label|https://example.com|icon\n"
                        + "The third field is optional. Leave it out and an icon is "
                        + "picked from the address (YouTube and Twitch get a play "
                        + "triangle, Discord and Telegram a speech bubble, Boosty and "
                        + "Patreon a heart, GitHub and CurseForge a star, anything "
                        + "else a globe).\n"
                        + "To choose one yourself, write: play, chat, heart, star, "
                        + "globe, spark or skull.\n"
                        + "To use your own picture, write the name of a PNG in the "
                        + "icon folder below, e.g. 'youtube.png'. Square images work "
                        + "best; 32x32 or 64x64 is plenty.\n"
                        + "Example:\n"
                        + "    YouTube|https://youtube.com/@channel\n"
                        + "    Discord|https://discord.gg/invite\n"
                        + "    Boosty|https://boosty.to/page|boosty.png");
        linkIconFolder = str(CAT_MENU, "linkIconFolder", linkIconFolder,
                "Folder the link icons above are read from, relative to the game "
                        + "directory. Created on first launch — drop PNGs in it and "
                        + "name them in 'links'.");
        // Where each of the four pieces of text lands. Written out because "tagline"
        // and "footer" say nothing about position, and the only way anyone found out
        // was by typing something in and relaunching.
        //
        //   +-------------------------------------------------+
        //   |                 [ logo image ]                  |
        //   |                  T I T L E                      |  <- title
        //   |                   tagline                       |  <- tagline
        //   |                                                 |
        //   |   01 Singleplayer                               |
        //   |   02 Multiplayer         (black hole)           |
        //   |   03 Options                                    |
        //   |   [>] YouTube  [O] Discord                      |  <- links
        //   |                                                 |
        //   |  Minecraft 1.7.10                               |
        //   |  footer                          footerRight    |
        //   +-------------------------------------------------+
        title = str(CAT_MENU, "title", title,
                "Big wordmark across the top of the title screen, drawn letter by "
                        + "letter with a rule under it. Leave empty when your logo "
                        + "image already has the name in it.");
        tagline = str(CAT_MENU, "tagline", tagline,
                "One small line directly under the wordmark — a subtitle for the "
                        + "pack. Empty means no line is drawn and everything below "
                        + "moves up. Example: tagline=season 3");
        footer = str(CAT_MENU, "footer", footer,
                "One small line in the bottom-LEFT corner, under the 'Minecraft "
                        + "1.7.10' line. Empty hides it. Example: footer=build 12");
        footerRight = str(CAT_MENU, "footerRight", footerRight,
                "One small line in the bottom-RIGHT corner. %version% is replaced "
                        + "with this mod's version. Empty hides it. Put your pack's "
                        + "name and version here. Example: footerRight=My Pack 1.4.2");

        customSplash = bool(CAT_SPLASH, "customSplash", customSplash,
                "Replace FML's mod-loading screen. Turn off if the loading screen misbehaves "
                        + "on your GPU; the game falls back to Forge's own splash. Ignored when "
                        + "Angelica is installed, which manages GL state in a way this screen "
                        + "cannot be made to share.");
        showTips = bool(CAT_SPLASH, "showTips", showTips,
                "Show the lines below at the bottom of the mod-loading screen — the "
                        + "one with the progress bar, while the game is starting up. "
                        + "Turn this off to leave that area empty without deleting "
                        + "the list.");
        splashTips = strList(CAT_SPLASH, "tips", splashTips,
                "The lines themselves. One is shown at a time, at the bottom of the "
                        + "mod-loading screen, swapping every few seconds in the "
                        + "order written here.\n"
                        + "This is the startup screen, not the one that appears while "
                        + "a world loads.\n"
                        + "One line per entry, plain text — no colour codes. Write "
                        + "whatever the pack wants people to read while they wait: "
                        + "controls, warnings, where the wiki is, a joke.\n"
                        + "Delete every line (or set showTips=false) to show nothing.");

        colorBackground = hex(CAT_THEME, "background", colorBackground, "Base backdrop colour.");
        colorSurface = hex(CAT_THEME, "surface", colorSurface, "Panel and button fill colour.");
        colorAccent = hex(CAT_THEME, "accent", colorAccent, "Primary accent (hover, focus, progress).");
        colorAccentAlt = hex(CAT_THEME, "accentAlt", colorAccentAlt, "Secondary accent for gradients and embers.");
        colorText = hex(CAT_THEME, "text", colorText, "Primary text colour.");
        colorTextDim = hex(CAT_THEME, "textDim", colorTextDim, "Muted text colour.");
        colorDanger = hex(CAT_THEME, "danger", colorDanger, "Destructive action colour (quit, disconnect).");
        colorBlackHoleHot = hex(CAT_THEME, "blackHoleHot", colorBlackHoleHot, "Black hole inner disk colour (hot region).");
        colorBlackHoleMid = hex(CAT_THEME, "blackHoleMid", colorBlackHoleMid, "Black hole middle disk colour.");
        colorBlackHoleCold = hex(CAT_THEME, "blackHoleCold", colorBlackHoleCold, "Black hole outer disk colour (cold region).");

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
    /**
     * Writes the graphics preset, from the in-game settings screen.
     *
     * Straight to the file rather than only to the field. This is the one setting in
     * here with a control on a vanilla-style settings screen, and every other control
     * on that screen persists the moment it is changed — a preset that reverted on the
     * next launch would read as it not having worked at all.
     */
    public static void setGraphics(String value) {
        graphics = value;
        if (config != null) {
            config.get(CAT_EFFECTS, "graphics", value).set(value);
            config.save();
        }
    }

    /**
     * Records that the one-off settings screen has been shown.
     *
     * Written through to the file straight away rather than at shutdown, so a crash
     * — or a player who alt-F4s out of the settings screen they were just handed —
     * does not get shown it again on the next launch.
     */
    public static void setShowSettingsOnFirstRun(boolean value) {
        showSettingsOnFirstRun = value;
        if (config != null) {
            config.get(CAT_SCREENS, "showSettingsOnFirstRun", value).set(value);
            config.save();
        }
    }

    public static Configuration raw() {
        return config;
    }

    // ------------------------------------------------------------- accessors --

    /**
     * The value each setting had before any config was read, by category and key.
     *
     * Defaults are declared exactly once — in the field initialisers at the top of
     * this class — and every read below passes its own field in as the default.
     * That works the first time and would quietly rot on the second: {@link #read}
     * runs again on {@link #reload}, and by then the fields hold whatever the file
     * said, so a setting the user had changed would become its own default and the
     * shipped value would be gone. Remembering the first one seen fixes that, and
     * costs one map lookup per setting per load.
     */
    private static final java.util.Map<String, Object> SHIPPED =
            new java.util.HashMap<String, Object>();

    /** The default for this setting: whatever it was before the first read. */
    private static Object shipped(String cat, String key, Object current) {
        String id = cat + '.' + key;
        Object known = SHIPPED.get(id);
        if (known == null) {
            SHIPPED.put(id, current);
            return current;
        }
        return known;
    }

    private static boolean bool(String cat, String key, boolean def, String comment) {
        def = ((Boolean) shipped(cat, key, Boolean.valueOf(def))).booleanValue();
        return config.getBoolean(key, cat, def, comment);
    }

    private static String str(String cat, String key, String def, String comment) {
        def = (String) shipped(cat, key, def);
        return config.getString(key, cat, def, comment);
    }

    private static String[] strList(String cat, String key, String[] def, String comment) {
        def = (String[]) shipped(cat, key, def);
        return config.getStringList(key, cat, def, comment);
    }

    private static int clampInt(String cat, String key, int def, int min, int max, String comment) {
        def = ((Integer) shipped(cat, key, Integer.valueOf(def))).intValue();
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
        def = ((Double) shipped(cat, key, Double.valueOf(def))).doubleValue();
        return config.get(cat, key, def, comment, min, max).getDouble(def);
    }

    private static int hex(String cat, String key, int def, String comment) {
        def = ((Integer) shipped(cat, key, Integer.valueOf(def))).intValue();
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

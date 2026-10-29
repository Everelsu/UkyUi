package com.console.uky.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String CAT_HUD = "hud";
    private static final String CAT_MODS = "mods";
    private static final String CAT_EFFECTS = "effects";
    private static final String CAT_THEME = "theme";
    private static final String CAT_MENU = "mainmenu";
    private static final String CAT_SPLASH = "splash";
    private static final String CAT_DEATH = "death";

    /**
     * Where the config lives, relative to the config directory.
     *
     * In its own folder rather than loose in {@code config/}: the mod already owns
     * {@code config/uky/icons} for link artwork, and having the settings for those
     * icons sit in a different directory from the icons themselves is the kind of
     * thing nobody finds twice.
     */
    public static final String FILE_NAME = "uky/uky.toml";

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

    // ---- hud ----
    /**
     * Replace the achievement popup with ours.
     *
     * The vanilla one is a 160x32 slice of a texture that slides down from the top
     * edge and back up again, and it has looked like that since 2011. Ours is the
     * same idea drawn in the palette, with the arrival worth watching — see
     * {@code AchievementToast}.
     */
    public static boolean achievementToast = true;
    /** Play {@code achievement} the moment a toast arrives. */
    public static boolean achievementSound = true;
    public static double achievementVolume = 0.9D;
    /**
     * Rewrite the chat line an achievement produces.
     *
     * Vanilla's is a full sentence naming the player and the achievement in
     * brackets; this cuts it to the achievement, marked, and makes it a link into
     * the achievements list. Multiplayer keeps the name, because there it is the
     * part that matters.
     */
    public static boolean achievementChatLink = true;
    /**
     * Draw the chat in this mod's own style instead of vanilla's grey boxes.
     *
     * Off by default and deliberately so: the chat is the one HUD element people
     * read rather than glance at, and a pack author should opt into changing it.
     */
    public static boolean redesignChat = false;
    /**
     * Draw item tooltips as one of our panels, and make them fit the screen.
     *
     * The look is the smaller half of this. The rest is the part a modded pack needs:
     * a line too long to fit is wrapped, a box too tall for the window is scaled to
     * it, and one that is still too tall after that is cut and counted rather than
     * drawn off the edge.
     */
    public static boolean restyleTooltips = true;
    /**
     * Width, in interface units, past which a tooltip line is re-flowed. 0 never
     * wraps, which is what vanilla does.
     */
    public static int tooltipWidth = 220;

    // ---- other mods ----
    /**
     * The blocks a tooltip is worth least on, out of the box.
     *
     * Vanilla terrain only, and deliberately so: these are the blocks every pack has,
     * every player already knows by sight, and every landscape is made of. Nothing
     * modded is guessed at from here — a pack that wants its own filler quiet says so
     * in the config, which is what the list is for.
     */
    private static final String[] DEFAULT_WAILA_HIDDEN = {
        "minecraft:stone",
        "minecraft:grass",
        "minecraft:dirt",
        "minecraft:sand",
        "minecraft:gravel",
        "minecraft:cobblestone",
        "minecraft:sandstone",
        "minecraft:netherrack",
        "minecraft:end_stone",
        "minecraft:bedrock",
        "minecraft:water",
        "minecraft:flowing_water",
        "minecraft:lava",
        "minecraft:flowing_lava",
        "minecraft:tallgrass",
        "minecraft:snow_layer",
    };

    /**
     * Draw Waila's block tooltip as one of our panels.
     *
     * Only the box and the text colour: what goes inside it is Waila's, down to the
     * last provider a pack has registered. Nothing here knows what a tooltip says.
     */
    public static boolean restyleWaila = true;
    /**
     * Blocks Waila should say nothing about at all.
     *
     * The tooltip is worth having over a machine and worth nothing over the ground: a
     * player who is walking across a hillside spends the whole walk with a box in the
     * corner of the screen naming the stone they are standing on. Which blocks those
     * are is a per-pack question — one pack's filler is another pack's ore — so it is a
     * list rather than a rule, and {@link #wailaHideListed} is the single switch that
     * turns the whole thing off without anyone having to empty it.
     *
     * <p>Entries are registry names: {@code minecraft:grass}, optionally with a
     * metadata value after it ({@code minecraft:stone:1} for granite, leaving ordinary
     * stone alone). The domain may be left off — {@code grass} matches
     * {@code minecraft:grass} — which is what most people will type.
     */
    public static String[] wailaHiddenBlocks = DEFAULT_WAILA_HIDDEN;
    /**
     * Whether {@link #wailaHiddenBlocks} is obeyed.
     *
     * Separate from the list so that turning the feature off for an evening does not
     * cost the list, and so the answer to "why is Waila not showing" is one line rather
     * than a diff.
     */
    public static boolean wailaHideListed = true;
    /**
     * Draw Xaero's minimap frame in this mod's style.
     *
     * The minimap is on screen the whole time somebody plays, and its own frame is a
     * beige bevel that has nothing to do with the rest of the HUD. This replaces the
     * box and only the box: the map, the entities, the waypoints and the coordinates
     * under it are Xaero's and stay untouched.
     *
     * <p>Xaero's own frame is switched off through its own setting rather than by
     * cutting its drawing out, and the value it had is put back the moment this is
     * turned off. See {@code XaeroFrame}.
     */
    public static boolean restyleXaeroFrame = true;

    /**
     * Hand BetterQuesting a theme built from the palette below — once.
     *
     * The theme is registered whether or not this is on; it shows up in the quest
     * book's own theme list either way. This decides whether it is also *selected*, and
     * it turns itself off the moment it has been, so the selection is a default rather
     * than something the player has to fight. See {@code QuestBookTheme.select}.
     */
    public static boolean restyleQuestBook = true;
    /**
     * Show BetterQuesting's "quest complete" notice as this mod's own panel.
     *
     * The quest book announces a finished quest with a title across the middle of the
     * screen; this mod announces an earned achievement with a panel that cuts in from
     * the right. They are the same event, and showing them in two shapes at once is what
     * makes a pack look assembled rather than made. See {@code QuestToast}.
     */
    public static boolean restyleQuestToast = true;
    /**
     * A scrim under the quest book, and the beat it takes to arrive.
     *
     * Separate from the theme because it is a different thing: the theme is what the
     * book is drawn with, this is what happens around it, and either is worth having
     * without the other.
     */
    public static boolean questBookTransition = true;
    /** How dark the world goes behind the quest book; 0 leaves it alone. */
    public static double questBookDim = 0.80D;

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
    /**
     * Let a comet cross the backdrop, and light the star that sends one.
     *
     * Roughly one every forty seconds, several seconds to cross, drawn large enough to
     * be noticed — the first version was astronomically modest and read as a star that
     * had come loose. The backdrop is otherwise a still image that moves: nothing in it
     * ever happens, and this is the one thing that does.
     */
    public static boolean comets = true;
    /**
     * Lay a shadow under the text in this mod's screens.
     *
     * On, because off is what they were doing and it is what looked wrong: the interface
     * drew flat while every vanilla screen, every other mod and the chat around it did
     * not. A switch rather than a hundred and twenty edits, so the two looks can be
     * compared — see {@code UkyFontRenderer}.
     */
    public static boolean textShadow = true;
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
    public static double introVolume = 0.45D;
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
        "Everything in this menu is configurable — config/uky/uky.toml.",
        "F3 + G draws chunk borders.",
        "Sleeping through the night skips the mobs, not the danger.",
        "Do not dig straight down.",
    };

    public static boolean customSplash = true;
    public static boolean showPercent = true;
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
        if (shouldWarnAboutSplashText(text)) {
            // System.err rather than the logger: this runs while mods are still loading.
            System.err.println("[UKY] the loading screen cannot draw \"" + text
                    + "\" — it has no font for anything outside plain ASCII this early,"
                    + " so it is showing \"" + safe + "\" instead. Menus are unaffected.");
        }
        return safe.toString();
    }

    /**
     * Text {@link #splashSafe} has already complained about.
     *
     * The warning is worth printing once per string and is ruinous printed every
     * frame. Its callers are the loading screen's own draw path — the wordmark, the
     * current step, the tip — so the same handful of strings pass through here sixty
     * times a second for the whole of mod loading, and {@code System.err} on a
     * running game is not a cheap call: FML redirects it into log4j, so each one is a
     * synchronised append to latest.log made from the splash thread while the main
     * thread is loading mods through the same appender.
     *
     * <p>Measured on a 116-mod pack before this guard: 5,582 lines from one config
     * tip, 69% of every line logged during start-up.
     */
    private static final Set<String> splashWarned = new HashSet<String>();

    /**
     * Whether {@code text} is worth a warning, and remembers that it was.
     *
     * <p>Deliberately capped. The wordmark and the tips are the strings an author can
     * actually act on, and there are a handful of those; the loading step is whatever
     * mod is being loaded at the time, and a pack with non-Latin mod names would
     * otherwise put an unbounded number of one-off lines through here — the same
     * flood, spread over more strings, about something the author cannot fix anyway.
     * Past the cap the substitution still happens, silently.
     *
     * <p>Not synchronised: the loading screen draws from one thread. The cost of
     * being wrong about that is a duplicated line.
     */
    private static boolean shouldWarnAboutSplashText(String text) {
        return splashWarned.size() < 16 && splashWarned.add(text);
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
        category(CAT_SCREENS,
                "Which vanilla screens this mod takes over. Turn one off and that "
                        + "screen goes back to vanilla; everything else keeps working.");
        category(CAT_HUD,
                "What this mod draws over the game rather than instead of it: the "
                        + "achievement popup, the chat line an achievement produces, "
                        + "and the chat itself. Nothing here changes what the game "
                        + "does — only how it is drawn and worded.");
        category(CAT_MODS,
                "Interfaces belonging to other mods that this one restyles when they "
                        + "are installed. Each entry does nothing at all when its mod "
                        + "is absent, and none of them change what those mods do — "
                        + "only what they look like.");
        category(CAT_MENU,
                "The main menu: wordmark, which entries are shown, the music, and the "
                        + "link buttons underneath. See 'links' for how to add YouTube, "
                        + "Discord, Boosty and anything else, with your own icons.");
        category(CAT_DEATH,
                "The death scene. It has no buttons: the picture cuts out, the scene "
                        + "plays, then any key or click comes back — and if the player "
                        + "does nothing, it comes back on its own.");
        category(CAT_SPLASH,
                "The mod-loading screen at startup — the one with the progress bar, "
                        + "before the main menu. Not the world-loading screen.");
        category(CAT_EFFECTS,
                "The backdrop and everything drawn over it. If the menu runs badly, "
                        + "lower blackHoleResolution first: it costs roughly the square "
                        + "of its value, while blackHoleQuality costs a straight line.");
        category(CAT_THEME,
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

        achievementToast = bool(CAT_HUD, "achievementToast", achievementToast,
                "Replace the achievement popup — the box that drops in from the top "
                        + "of the screen when you earn one. Ours is a slanted panel "
                        + "that cuts in from the right: the item lands in its frame, "
                        + "the name types itself out, and a hairline along the bottom "
                        + "counts the time it has left. Earn several at once and they "
                        + "queue rather than replacing each other.\n"
                        + "Off puts vanilla's own box back.");
        achievementSound = bool(CAT_HUD, "achievementSound", achievementSound,
                "Play a sound when that panel arrives. Silence is the vanilla "
                        + "behaviour; this is not.");
        achievementVolume = dbl(CAT_HUD, "achievementVolume", achievementVolume, 0.0D, 1.0D,
                "Volume of that sound, on top of the game's master slider.");
        achievementChatLink = bool(CAT_HUD, "achievementChatLink", achievementChatLink,
                "Shorten the chat line an achievement produces, and make it a link.\n"
                        + "Vanilla writes a whole sentence — 'Player has just earned "
                        + "the achievement [Taking Inventory]'. This cuts it to the "
                        + "achievement itself, keeping the player's name only when "
                        + "somebody else earned it. Clicking it opens the achievements "
                        + "list scrolled to that entry with it picked out; hovering "
                        + "still shows what it was for.");
        restyleTooltips = bool(CAT_HUD, "restyleTooltips", restyleTooltips,
                "Draw the box that appears over an item — its name, what it does, "
                        + "everything every mod in the pack has added to it — as one "
                        + "of our panels instead of vanilla's purple-bordered one.\n"
                        + "The look is the smaller half. Vanilla's box is laid out on "
                        + "the assumption that nothing will ever be very wide or very "
                        + "tall, which is true of vanilla and not of a modded pack: a "
                        + "machine listing its energy, its fluids and three lines of "
                        + "lore produces a box taller than the window and drawn off "
                        + "the end of it. This one wraps long lines to tooltipWidth "
                        + "below, scales a box that is still too tall until it fits, "
                        + "and only then cuts what is left over — saying how many "
                        + "lines it cut.\n"
                        + "What the lines say is untouched, including everything other "
                        + "mods put in them.");
        tooltipWidth = clampInt(CAT_HUD, "tooltipWidth", tooltipWidth, 0, 640,
                "Width, in interface units, past which a tooltip line is re-flowed "
                        + "onto the next. Around 220 is a comfortable paragraph and "
                        + "roughly a third of the screen. 0 turns wrapping off and "
                        + "leaves long lines to run as far as they like, which is "
                        + "what vanilla does.\n"
                        + "Needs restyleTooltips on.");
        redesignChat = bool(CAT_HUD, "redesignChat", redesignChat,
                "Draw the chat in this mod's style: each line on its own dark panel "
                        + "with a rail down the left, new lines sliding in from the "
                        + "left, and the input box below drawn as one of our fields.\n"
                        + "Off by default. The chat is read rather than glanced at, "
                        + "and every setting the game already has for it — scale, "
                        + "width, height, opacity, visibility — is still obeyed either "
                        + "way.");

        restyleWaila = bool(CAT_MODS, "restyleWaila", restyleWaila,
                "Draw Waila's block tooltip — the box naming whatever you are looking "
                        + "at — as one of our panels: dark fill, hairline border, the "
                        + "gold rail down the left, and the palette's text colour "
                        + "instead of Waila's grey. The panel draws itself in from the "
                        + "left over a sixth of a second, contents and all, whenever "
                        + "you look at something new.\n"
                        + "Progress bars inside it (a furnace burning, a machine "
                        + "working) are ours too: they fill left to right from the "
                        + "second accent to the first, with a highlight running along "
                        + "the filled part while there is still work to do.\n"
                        + "What the box says is untouched, including everything other "
                        + "mods add to it.");
        wailaHideListed = bool(CAT_MODS, "wailaHideListed", wailaHideListed,
                "Obey 'wailaHiddenBlocks' below. Off shows Waila's tooltip over "
                        + "everything again, without anyone having to empty the list "
                        + "to get there.\n"
                        + "Independent of restyleWaila: that is what the tooltip looks "
                        + "like, this is whether there is one at all.");
        wailaHiddenBlocks = strList(CAT_MODS, "wailaHiddenBlocks", wailaHiddenBlocks,
                "Blocks Waila says nothing about. Look at one of these and no tooltip "
                        + "appears at all.\n"
                        + "The tooltip earns its place over a machine and earns nothing "
                        + "over the ground: naming the stone underfoot for the whole of "
                        + "a walk across a hillside is a box in the corner of the screen "
                        + "that is never once read. Which blocks those are is a question "
                        + "about the pack rather than about Waila, so it is a list.\n"
                        + "One registry name per line: 'minecraft:grass'. Add a metadata "
                        + "value to name one variant only — 'minecraft:stone:1' hides "
                        + "granite and leaves ordinary stone alone. The domain may be "
                        + "left off, so 'grass' works as well as 'minecraft:grass'.\n"
                        + "The default is vanilla terrain and nothing else. Modded "
                        + "filler is not guessed at: it is the same name the block goes "
                        + "by in commands and recipes, which NEI shows under an item "
                        + "once item ids are turned on in its options.\n"
                        + "Emptying the list has the same effect as wailaHideListed=false.");
        restyleXaeroFrame = bool(CAT_MODS, "restyleXaeroFrame", restyleXaeroFrame,
                "Draw the frame around Xaero's minimap in this mod's style: a hairline "
                        + "border with the gold rail down its left edge, or a ring when "
                        + "the map is set to round.\n"
                        + "Only the frame. What the map draws inside it — terrain, "
                        + "entities, waypoints, the coordinates under it — is Xaero's "
                        + "and is not touched.\n"
                        + "Xaero's own frame is turned off through its own setting "
                        + "(Minimap settings, 'Frame'), so it is visible there rather "
                        + "than being a mystery, and the value it had is put back the "
                        + "moment this is switched off.");
        restyleQuestBook = bool(CAT_MODS, "restyleQuestBook", restyleQuestBook,
                "Hand BetterQuesting's quest book the UKY theme, once. The theme is "
                        + "built from the palette in [theme] below, so it follows the "
                        + "rest of the interface rather than sitting beside it.\n"
                        + "This turns itself off the first time a quest book is opened "
                        + "with it on, and from then on the book's own Themes screen "
                        + "decides — pick anything else there and it stays picked. It "
                        + "used to re-select ours every time the book opened, which "
                        + "meant another theme could be chosen and lasted exactly until "
                        + "the book was next opened.\n"
                        + "Set it back to true to hand the book our theme again — after "
                        + "changing the palette, for instance. The theme is registered "
                        + "whether this is on or off, so it is always in that list.");
        restyleQuestToast = bool(CAT_MODS, "restyleQuestToast", restyleQuestToast,
                "Show the quest book's 'quest complete' notice as one of our panels — "
                        + "the same one an achievement uses, cutting in from the right "
                        + "with the quest's own icon in its frame.\n"
                        + "Finishing a quest and earning an achievement at the same "
                        + "moment otherwise puts two announcements of the same kind of "
                        + "thing on screen in two different shapes, in two different "
                        + "places, for two different lengths of time.\n"
                        + "Off leaves BetterQuesting's own title, including whatever its "
                        + "own notification settings say about style and duration. Note "
                        + "that with this on those settings no longer apply, because the "
                        + "notice is no longer theirs to draw.");
        questBookTransition = bool(CAT_MODS, "questBookTransition", questBookTransition,
                "Darken the world behind the quest book, and let the book arrive over "
                        + "a fifth of a second instead of appearing between two frames. "
                        + "The scrim lifts again after the book is closed.\n"
                        + "Independent of restyleQuestBook: this is what happens around "
                        + "the book rather than what it is drawn with, so it applies "
                        + "whichever theme the book is using.");
        questBookDim = dbl(CAT_MODS, "questBookDim", questBookDim, 0.0D, 1.0D,
                "How dark the world goes behind the quest book. 1 is the backdrop "
                        + "colour at full strength — the world is gone; 0 leaves it "
                        + "untouched and the book floats on the landscape the way "
                        + "BetterQuesting draws it by itself. Around 0.8 is dark "
                        + "enough for a dark theme to keep its contrast without "
                        + "pretending the world stopped existing.\n"
                        + "Needs questBookTransition on: it is the same scrim that "
                        + "fades in and out.");

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
        comets = bool(CAT_EFFECTS, "comets", comets,
                "Let a comet cross the backdrop: roughly one every forty seconds, with "
                        + "a head, a halo and a tail a third of the screen long, taking "
                        + "several seconds to cross.\n"
                        + "The backdrop is otherwise a still image that moves — the "
                        + "stars drift, the disk turns, and nothing in it ever happens. "
                        + "This is the one thing that does, so it is drawn to be seen "
                        + "rather than to be astronomically modest.\n"
                        + "They cross every screen that draws our own sky, the title "
                        + "screen included; in a world there is no sky to cross.\n"
                        + "This also turns off the star that answers: one star high on "
                        + "the left twinkles on its own, lights up under the pointer, "
                        + "and sends a comet when it is clicked. It is on the screens "
                        + "inside rather than on the title screen, which has enough "
                        + "things on it to press already.");
        textShadow = bool(CAT_EFFECTS, "textShadow", textShadow,
                "Draw a shadow under the text in this mod's screens, the way Minecraft "
                        + "draws its own.\n"
                        + "These screens were flat everywhere while the game around them "
                        + "— vanilla screens, other mods, the chat, item counts — was "
                        + "not, which is what made the font look wrong when nothing was "
                        + "wrong with it. Turn it off to compare.\n"
                        + "Only this mod's own screens are affected, and only while they "
                        + "are being drawn. It also needs this mod's font renderer, so a "
                        + "pack whose renderer has already been replaced by something "
                        + "else — OptiFine installs its own — will not see a difference.");
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
        introVolume = dbl(CAT_MENU, "introVolume", introVolume, 0.0D, 1.0D,
                "Volume of the intro's build-up and impact, on top of the game's master "
                        + "slider. 0 plays it silent.");
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
        //   |  Minecraft 1.21.1                               |
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
                        + "version' line. Empty hides it. Example: footer=build 12");
        footerRight = str(CAT_MENU, "footerRight", footerRight,
                "One small line in the bottom-RIGHT corner. %version% is replaced "
                        + "with this mod's version. Empty hides it. Put your pack's "
                        + "name and version here. Example: footerRight=My Pack 1.4.2");

        customSplash = bool(CAT_SPLASH, "customSplash", customSplash,
                "Replace the loading screen — start-up after NeoForge's early window, and "
                        + "every resource reload after it. Turn off if it misbehaves on "
                        + "your GPU; the game falls back to NeoForge's own.");
        showPercent = bool(CAT_SPLASH, "showPercent", showPercent,
                "Show a percentage beside each bar on the mod-loading screen. Bars "
                        + "that report no total have no percentage to show and are "
                        + "drawn as a moving chunk instead, whatever this is set to.");
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

    /**
     * Flips one of the HUD switches from the settings screen, file and all.
     *
     * Same reasoning as {@link #setGraphics}: these sit among vanilla options that
     * persist the moment they are clicked, and one that reverted on the next launch
     * would read as not having worked.
     */
    public static void setChatRedesign(boolean value) {
        redesignChat = value;
        write(CAT_HUD, "redesignChat", value);
    }

    public static void setAchievementToast(boolean value) {
        achievementToast = value;
        write(CAT_HUD, "achievementToast", value);
    }

    public static void setRestyleTooltips(boolean value) {
        restyleTooltips = value;
        write(CAT_HUD, "restyleTooltips", value);
    }

    public static void setAchievementChatLink(boolean value) {
        achievementChatLink = value;
        write(CAT_HUD, "achievementChatLink", value);
    }

    /**
     * The Waila filter, from the settings screen.
     *
     * Only the switch has a control. What is on the list is a per-pack decision made
     * once, in a file, with a registry name that nobody is going to type on a screen
     * with no keyboard focus — so the screen offers the half of it that is worth
     * changing mid-game and the config keeps the half that is not.
     */
    public static void setWailaHideListed(boolean value) {
        wailaHideListed = value;
        write(CAT_MODS, "wailaHideListed", value);
    }


    // ------------------------------------------------------------- accessors --
    //
    // read() runs more than once. The first time, while the class initialises,
    // BUILDER is set and every helper defines its setting, with the field's own
    // initialiser as the default — so defaults are declared exactly once, in the
    // fields. Every later run (ModConfigEvent.Loading / Reloading) reads the value
    // the spec now holds back into the field. ModConfigSpec corrects out-of-range
    // and unknown values itself and drops keys it no longer defines, which is the
    // whole of what the 1.7.10 build's prune() and clamp write-back were for.

    private static ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES =
            new HashMap<String, ModConfigSpec.ConfigValue<?>>();

    public static final ModConfigSpec SPEC;

    static {
        read();
        SPEC = BUILDER.build();
        BUILDER = null;
    }

    /** Copies the spec's values into the fields. Called on load and on every reload. */
    public static void reload() {
        if (SPEC.isLoaded()) {
            read();
        }
    }

    private static void category(String cat, String comment) {
        if (BUILDER != null) {
            BUILDER.comment(comment).push(cat);
            BUILDER.pop();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(String cat, String key, T def) {
        if (BUILDER != null) {
            return def;
        }
        if (!SPEC.isLoaded()) {
            return early(cat, key, def);
        }
        return ((ModConfigSpec.ConfigValue<T>) VALUES.get(cat + '.' + key)).get();
    }

    /**
     * The file as read before NeoForge loads it; see {@link #loadEarly}. Null until then,
     * and after it every value comes from the spec.
     */
    private static com.electronwill.nightconfig.core.Config earlyFile;

    /**
     * Reads the file straight off disk, for the loading screen.
     *
     * That screen is up from the moment the game window is, which is before any mod is
     * constructed and so before NeoForge has loaded this config — but it is also the one
     * place a pack's title and tips are first seen. A missing or broken file simply
     * leaves the defaults.
     */
    public static void loadEarly() {
        if (SPEC.isLoaded() || earlyFile != null) {
            return;
        }
        java.nio.file.Path file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        try (com.electronwill.nightconfig.core.file.CommentedFileConfig config =
                     com.electronwill.nightconfig.core.file.CommentedFileConfig.of(file)) {
            config.load();
            earlyFile = config;
            read();
        } catch (Throwable t) {
            earlyFile = null;
            System.err.println("[UKY] could not read " + file + " early: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T early(String cat, String key, T def) {
        if (earlyFile == null) {
            return def;
        }
        Object raw = earlyFile.get(List.of(cat, key));
        if (raw == null) {
            return def;
        }
        // TOML hands numbers back as whatever width it parsed; the fields want theirs.
        if (def instanceof Integer && raw instanceof Number) {
            return (T) Integer.valueOf(((Number) raw).intValue());
        }
        if (def instanceof Double && raw instanceof Number) {
            return (T) Double.valueOf(((Number) raw).doubleValue());
        }
        if (def == null || def.getClass().isInstance(raw) || raw instanceof List) {
            return (T) raw;
        }
        return def;
    }

    private static boolean bool(String cat, String key, boolean def, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment).define(List.of(cat, key), def));
        }
        return value(cat, key, def);
    }

    private static String str(String cat, String key, String def, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment).define(List.of(cat, key), def));
        }
        return value(cat, key, def);
    }

    private static String[] strList(String cat, String key, String[] def, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment).defineListAllowEmpty(
                    List.of(cat, key), Arrays.asList(def), () -> "", o -> o instanceof String));
            return def;
        }
        List<?> list = value(cat, key, null);
        if (list == null) {
            return def;
        }
        String[] out = new String[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = String.valueOf(list.get(i));
        }
        return out;
    }

    private static int clampInt(String cat, String key, int def, int min, int max, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment).defineInRange(List.of(cat, key), def, min, max));
        }
        return value(cat, key, def);
    }

    private static double dbl(String cat, String key, double def, double min, double max, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment).defineInRange(List.of(cat, key), def, min, max));
        }
        return value(cat, key, def);
    }

    private static int hex(String cat, String key, int def, String comment) {
        if (BUILDER != null) {
            VALUES.put(cat + '.' + key, BUILDER.comment(comment + " Format: 0xRRGGBB.")
                    .define(List.of(cat, key), String.format("0x%06X", def), o -> o instanceof String));
            return def;
        }
        String raw = value(cat, key, "");
        try {
            return Integer.decode(raw.trim()) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            // A typo in the config should not black out the menu — fall back silently.
            return def;
        }
    }

    /**
     * Writes one setting through to the file straight away.
     *
     * These sit among vanilla options that persist the moment they are clicked, and
     * one that reverted on the next launch would read as not having worked.
     */
    @SuppressWarnings("unchecked")
    private static <T> void write(String cat, String key, T value) {
        ModConfigSpec.ConfigValue<T> v = (ModConfigSpec.ConfigValue<T>) VALUES.get(cat + '.' + key);
        if (v != null && SPEC.isLoaded()) {
            v.set(value);
            v.save();
        }
    }

    /** Writes the graphics preset, from the in-game settings screen. */
    public static void setGraphics(String value) {
        graphics = value;
        write(CAT_EFFECTS, "graphics", value);
    }

    /** Records that the quest book has been handed our theme, so it is not handed it again. */
    public static void setRestyleQuestBook(boolean value) {
        if (restyleQuestBook == value) {
            return;
        }
        restyleQuestBook = value;
        write(CAT_MODS, "restyleQuestBook", value);
    }

    /**
     * Records that the one-off settings screen has been shown, straight away, so a
     * crash — or a player who alt-F4s out of it — does not get shown it again.
     */
    public static void setShowSettingsOnFirstRun(boolean value) {
        showSettingsOnFirstRun = value;
        write(CAT_SCREENS, "showSettingsOnFirstRun", value);
    }
}

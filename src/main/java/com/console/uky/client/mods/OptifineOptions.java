package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OptiFine's settings screens, as a model this interface can draw itself.
 *
 * OptiFine is an easier guest than a Sodium-derived renderer, and for one reason:
 * it does not invent an option model. It adds its settings straight to vanilla's
 * {@code GameSettings.Options} enum and patches {@code getKeyBinding},
 * {@code setOptionValue} and the float pair to understand them — so an OptiFine
 * option is a vanilla option as far as anything drawing one is concerned, and the
 * widgets this mod already uses for the Graphics tab drive them with no adapter at
 * all. Compare {@link AngelicaOptions}, which has to mirror a whole second model.
 *
 * <p>What OptiFine does not hand out is the <em>grouping</em>. Which setting belongs
 * on Quality and which on Details exists only as a static array inside each of its
 * five sub-screens, so that is what is read here: the arrays, not the screens. The
 * sections then carry OptiFine's own names in OptiFine's own order, and a player who
 * knows its menus finds every setting where they left it.
 *
 * <p>All reflection, because OptiFine is not a compile dependency and must not become
 * one — the pack is expected to run with or without it. The arrays are located by
 * <em>type</em> rather than by field name, the same way this mod locates vanilla's
 * private fields: OptiFine ships obfuscated and renames freely between releases, but
 * a {@code GameSettings.Options[]} sitting statically on a settings screen is what it
 * is. Nothing here throws outward; a failure costs the section, and the settings
 * screen falls back to the door to OptiFine's own menus.
 */
public final class OptifineOptions {

    /**
     * The class every OptiFine build has, and the cheapest thing to ask for.
     *
     * Deliberately not {@code Loader.isModLoaded}: OptiFine is a tweaker first and a
     * Forge mod second, and which id it registers under — or whether it registers at
     * all — has changed across builds. The class either loaded or it did not.
     */
    private static final String[] MARKERS = {"net.optifine.Config", "Config"};

    private static final String SHADERS = "net.optifine.shaders.Shaders";
    private static final String SHADERS_SCREEN = "net.optifine.shaders.gui.GuiShaders";

    /**
     * OptiFine's five option screens, in the order its video settings lists them.
     *
     * Each row is: the class holding the option array, the translation key OptiFine
     * titles that screen with, and ours to fall back on. The 1.12.2 builds put these
     * under {@code net.optifine.gui}; older ones left them in the default package, and
     * both are tried because the cost of the second lookup is one failed
     * {@code Class.forName} on a version that does not need it.
     */
    private static final String[][] PAGES = {
        {"GuiQualitySettingsOF", "of.options.qualityTitle", "uky.settings.optifine.quality"},
        {"GuiDetailSettingsOF", "of.options.detailsTitle", "uky.settings.optifine.details"},
        {"GuiPerformanceSettingsOF", "of.options.performanceTitle",
                "uky.settings.optifine.performance"},
        {"GuiAnimationSettingsOF", "of.options.animationsTitle",
                "uky.settings.optifine.animations"},
        {"GuiOtherSettingsOF", "of.options.otherTitle", "uky.settings.optifine.other"},
    };

    private static final String[] PACKAGES = {"net.optifine.gui.", ""};

    private OptifineOptions() {
    }

    // ------------------------------------------------------------------ model --

    /** One of OptiFine's screens, which becomes one headed section of the settings list. */
    public static final class Section {

        private final String name;
        private final List<GameSettings.Options> options;

        Section(String name, List<GameSettings.Options> options) {
            this.name = name;
            this.options = options;
        }

        public String name() {
            return this.name;
        }

        /**
         * The settings on this page, as vanilla options.
         *
         * Which is the whole point: these go straight into the same
         * {@code MenuOptionButton} and {@code MenuSlider} the vanilla rows use.
         */
        public List<GameSettings.Options> options() {
            return this.options;
        }
    }

    // ---------------------------------------------------------------- reading --

    /** Whether OptiFine is on the classpath at all. */
    public static boolean isInstalled() {
        for (String marker : MARKERS) {
            if (find(marker) != null) {
                return true;
            }
        }
        return false;
    }

    /** Cached across screens: the arrays are static and cannot change while running. */
    private static List<Section> cached;

    /**
     * Reads OptiFine's screens into sections.
     *
     * Cached, unlike Angelica's read. There the options carry pending edits and must
     * survive a relayout; here an option is a vanilla enum constant that writes
     * straight through on click, so there is nothing to lose and no reason to walk the
     * reflection again on every tab switch.
     *
     * @return the sections, or an empty list if OptiFine is absent or unreadable
     */
    public static List<Section> read() {
        if (cached != null) {
            return cached;
        }
        List<Section> sections = new ArrayList<Section>();
        if (!isInstalled()) {
            cached = sections;
            return sections;
        }

        for (String[] page : PAGES) {
            Class<?> screen = findPage(page[0]);
            if (screen == null) {
                // A page OptiFine does not have in this build. Its sub-screens have come
                // and gone across versions, so a missing one is a fact and not a fault.
                continue;
            }
            List<GameSettings.Options> options = optionsOf(screen);
            if (options.isEmpty()) {
                continue;
            }
            sections.add(new Section(title(page[1], page[2]), options));
        }

        if (sections.isEmpty()) {
            UkyUI.LOGGER.warn("OptiFine is installed but its settings pages could not be "
                    + "read; its own screens will be offered instead");
        } else {
            UkyUI.LOGGER.info("OptiFine's settings will be drawn in the Graphics tab "
                    + "({} sections)", Integer.valueOf(sections.size()));
        }
        cached = sections;
        return sections;
    }

    /**
     * Every option OptiFine claims, for the Graphics tab to skip.
     *
     * Exact enum identity, not the label match {@link AngelicaOptions} needs. Both
     * sides are the same enum here, so a setting that appears in one of OptiFine's
     * pages and in our own vanilla list is recognisable as one setting rather than as
     * two rows that happen to read alike.
     */
    private static Set<GameSettings.Options> owned;

    public static boolean owns(GameSettings.Options option) {
        if (option == null) {
            return false;
        }
        if (owned == null) {
            Set<GameSettings.Options> set = new LinkedHashSet<GameSettings.Options>();
            for (Section section : read()) {
                set.addAll(section.options());
            }
            owned = set;
        }
        return owned.contains(option);
    }

    /**
     * The option array a settings screen lays itself out from.
     *
     * Located by type: exactly one static {@code GameSettings.Options[]} sits on each of
     * these classes, and unlike its name that is not something OptiFine's obfuscation
     * or its next release can quietly change.
     */
    private static List<GameSettings.Options> optionsOf(Class<?> screen) {
        List<GameSettings.Options> found = new ArrayList<GameSettings.Options>();
        try {
            for (Field field : screen.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || field.getType() != GameSettings.Options[].class) {
                    continue;
                }
                field.setAccessible(true);
                GameSettings.Options[] options = (GameSettings.Options[]) field.get(null);
                if (options == null) {
                    continue;
                }
                for (GameSettings.Options option : options) {
                    // OptiFine pads some of its arrays to keep its own two-column
                    // layout even; a hole there is spacing, not a setting.
                    if (option != null && !found.contains(option)) {
                        found.add(option);
                    }
                }
                break;
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read OptiFine's options from {}",
                    screen.getName(), t);
            return Collections.<GameSettings.Options>emptyList();
        }
        return found;
    }

    /** OptiFine's own name for the page, falling back to ours if it has none. */
    private static String title(String theirs, String ours) {
        String translated = I18n.format(theirs, new Object[0]);
        return theirs.equals(translated) ? I18n.format(ours, new Object[0]) : translated;
    }

    // ---------------------------------------------------------------- shaders --

    /** Whether this OptiFine build has the shader engine, which not all of them do. */
    public static boolean hasShaders() {
        return find(SHADERS) != null && find(SHADERS_SCREEN) != null;
    }

    /**
     * OptiFine's shader pack screen, which is a screen and not a model.
     *
     * The one part of its settings that genuinely cannot be redrawn here: the pack list
     * is a file browser with its own scrolling and its own per-pack option pages, none
     * of which is described anywhere a caller could read. So it stays a link to what its
     * author wrote, exactly as Iris's does.
     *
     * @return the screen, or null if it could not be built
     */
    public static GuiScreen shadersScreen(GuiScreen parent, GameSettings settings) {
        try {
            return (GuiScreen) find(SHADERS_SCREEN)
                    .getConstructor(GuiScreen.class, GameSettings.class)
                    .newInstance(parent, settings);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open OptiFine's shader pack screen", t);
            return null;
        }
    }

    // --------------------------------------------------------------- plumbing --

    private static Class<?> findPage(String simpleName) {
        for (String prefix : PACKAGES) {
            Class<?> found = find(prefix + simpleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }
}

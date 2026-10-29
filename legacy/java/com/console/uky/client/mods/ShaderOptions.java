package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import net.minecraft.client.resources.I18n;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A shader pack's own settings, as a model this interface can draw itself.
 *
 * <p>A pack author writes {@code #define}s in the shader source and describes them in
 * {@code shaders.properties}: which ones appear on the menu, in what order, on which
 * page, and under what name. Iris parses all of that into an
 * {@code OptionMenuContainer} — a main screen and any number of sub-screens, each a
 * list of elements — and then builds a widget per element. The container is the model
 * and the widgets are one rendering of it, so this reads the container and
 * {@code GuiShaderOptionsScreen} draws the other.
 *
 * <p>Doing it this way rather than listing every option in the pack matters: a pack
 * with two hundred defines shows the thirty its author put on the menu, on the pages
 * they grouped them into, in their order, with their names. A flat list of everything
 * would technically be the same settings and would be unusable.
 *
 * <h2>How a value is changed</h2>
 *
 * <p>Not by writing it anywhere. {@code Iris.getShaderPackOptionQueue()} is a map of
 * option name to value that the next reload consumes, and every one of Iris's own
 * widgets edits exactly that map. The <em>pending</em> values on an element already
 * account for it, so a row re-reads its own value after a click and sees what it just
 * queued. Nothing is written to disk and nothing is recompiled until the reload —
 * which is what makes Apply a real button rather than a formality.
 *
 * <p>Labels come out of the game's own translation table, because Iris installs the
 * pack's language files into it: {@code option.NAME} for a row, {@code value.NAME.X}
 * for one of its values, {@code screen.ID} for a page, {@code option.NAME.comment} for
 * the description. A key with no translation falls back to the raw name, which is what
 * Iris does and what a pack shipping no lang file gets either way.
 */
public final class ShaderOptions {

    private static final String IRIS = "net.coderbot.iris.Iris";

    /** What a row turned out to be. */
    public enum Kind {
        /** A define that is either on or off. */
        TOGGLE,
        /** A define cycling through the values its author allowed. */
        CYCLE,
        /** A link to another page of the pack's menu. */
        LINK,
        /** The pack's profile picker: a named set of values for everything else. */
        PROFILE
    }

    /**
     * One row of one page.
     *
     * <p>Carries everything a click on it needs — the values its author allowed, and
     * for the profile row the element it came off. Reading a page is a walk over
     * another mod's object graph, and doing that again on every click to answer a
     * question the walk already answered is how the two come to disagree.
     */
    public static final class Entry {

        private final Kind kind;
        private final String id;
        private final String label;
        private final String value;
        private final String comment;
        private final boolean modified;
        /** Values a cycle may take, in the author's order. Null for anything else. */
        private final List<String> allowed;
        /** The menu element this was read from; only the profile row needs it back. */
        private final Object source;

        Entry(Kind kind, String id, String label, String value, String comment,
              boolean modified, List<String> allowed, Object source) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.value = value;
            this.comment = comment;
            this.modified = modified;
            this.allowed = allowed;
            this.source = source;
        }

        public Kind kind() {
            return this.kind;
        }

        /** The option's own name, or for a link the page it leads to. */
        public String id() {
            return this.id;
        }

        public String label() {
            return this.label;
        }

        /** What it is set to now, already translated. Empty for a link. */
        public String value() {
            return this.value;
        }

        /** The author's description, or null. */
        public String comment() {
            return this.comment;
        }

        /** Whether this differs from what the pack ships with. */
        public boolean modified() {
            return this.modified;
        }

        public boolean on() {
            return "true".equalsIgnoreCase(this.value);
        }
    }

    private static boolean resolved;
    private static boolean usable;

    private static Method getCurrentPack;
    private static Method getMenuContainer;
    private static Method getOptionQueue;
    private static Method queueFromProfile;
    private static Method queueDefaults;

    private static Field containerMainScreen;
    private static Field containerSubScreens;
    private static Field screenElements;

    private static Class<?> booleanElement;
    private static Class<?> stringElement;
    private static Class<?> linkElement;
    private static Class<?> profileElement;

    private static Field booleanOption;
    private static Field stringOption;
    private static Field linkTarget;
    private static Field profileProfiles;
    private static Field profileOptions;

    private static Method elementPending;
    private static Method elementApplied;
    private static Method profilePending;

    private static Method optionName;
    private static Method optionComment;
    private static Method booleanDefault;
    private static Method stringDefault;
    private static Method stringAllowed;

    private static Method valuesBoolean;
    private static Method valuesString;

    private static Method profileScan;
    private static Field profileCurrent;
    private static Field profileNext;
    private static Field profileName;

    private ShaderOptions() {
    }

    /** Whether the loaded pack has a settings menu that can be read. */
    public static boolean available() {
        return !page(null).isEmpty();
    }

    /**
     * One page of the pack's menu.
     *
     * @param screenId the sub-screen to read, or null for the pack's main page
     * @return its rows, in the author's order; empty when there is nothing to read
     */
    public static List<Entry> page(String screenId) {
        if (!resolve()) {
            return Collections.emptyList();
        }
        try {
            Object container = container();
            if (container == null) {
                return Collections.emptyList();
            }
            Object screen = screenId == null
                    ? containerMainScreen.get(container)
                    : ((Map<?, ?>) containerSubScreens.get(container)).get(screenId);
            if (screen == null) {
                return Collections.emptyList();
            }
            List<?> elements = (List<?>) screenElements.get(screen);
            List<Entry> rows = new ArrayList<Entry>();
            for (int i = 0; i < elements.size(); i++) {
                Entry row = read(elements.get(i));
                if (row != null) {
                    rows.add(row);
                }
            }
            return rows;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read the shader pack's settings", t);
            return Collections.emptyList();
        }
    }

    /** The title of a sub-screen, translated. */
    public static String pageTitle(String screenId) {
        return screenId == null ? "" : translate("screen." + screenId, screenId);
    }

    /**
     * Queues the next value for a row.
     *
     * <p>A toggle flips; a cycle moves one along its author's list and wraps; a profile
     * moves to the next profile, which queues a value for every option the profile
     * names at once. Nothing takes effect until {@link ShaderPacks#apply}.
     */
    public static void cycle(Entry entry) {
        if (entry == null || !resolve()) {
            return;
        }
        try {
            if (entry.kind == Kind.TOGGLE) {
                queue().put(entry.id, entry.on() ? "false" : "true");
                return;
            }
            if (entry.kind == Kind.CYCLE) {
                queue().put(entry.id, next(entry));
                return;
            }
            if (entry.kind == Kind.PROFILE) {
                cycleProfile(entry.source);
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not change shader option {}", entry.id, t);
        }
    }

    /** The value after this one, wrapping at the end of the author's list. */
    private static String next(Entry entry) {
        List<String> allowed = entry.allowed;
        if (allowed == null || allowed.isEmpty()) {
            return entry.value;
        }
        int at = allowed.indexOf(entry.value);
        return allowed.get((at + 1) % allowed.size());
    }

    /**
     * Puts every option back to what the pack ships with.
     *
     * Iris's own method, which queues the pack's defaults rather than emptying the
     * queue: a cleared queue means "change nothing", and what is wanted here is
     * "change everything back".
     */
    public static void resetAll() {
        if (!resolve()) {
            return;
        }
        try {
            queueDefaults.invoke(null);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not reset the shader pack's settings", t);
        }
    }

    /** Whether anything has been queued and is waiting for a reload. */
    public static boolean pending() {
        if (!resolve()) {
            return false;
        }
        try {
            return !queue().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Throws away everything queued but not applied. */
    public static void discard() {
        if (!resolve()) {
            return;
        }
        try {
            queue().clear();
        } catch (Throwable t) {
            // Nothing useful to do: the queue is consumed by the next reload either
            // way, and a failure here at worst applies a change the player cancelled.
            UkyUI.LOGGER.warn("Could not drop the queued shader options", t);
        }
    }

    // ------------------------------------------------------------------ reading --

    /** One element, or null for a spacer and for anything unrecognised. */
    private static Entry read(Object element) throws Exception {
        if (element == null) {
            return null;
        }
        if (booleanElement.isInstance(element)) {
            Object option = booleanOption.get(element);
            String name = (String) optionName.invoke(option);
            boolean value = ((Boolean) valuesBoolean.invoke(
                    elementPending.invoke(element), name)).booleanValue();
            boolean shipped = ((Boolean) booleanDefault.invoke(option)).booleanValue();
            return new Entry(Kind.TOGGLE, name, label(name), Boolean.toString(value),
                    comment(name, option), value != shipped, null, element);
        }
        if (stringElement.isInstance(element)) {
            Object option = stringOption.get(element);
            String name = (String) optionName.invoke(option);
            String value = (String) valuesString.invoke(
                    elementPending.invoke(element), name);
            String shipped = (String) stringDefault.invoke(option);
            List<String> allowed = new ArrayList<String>();
            List<?> authors = (List<?>) stringAllowed.invoke(option);
            if (authors != null) {
                for (int i = 0; i < authors.size(); i++) {
                    allowed.add(String.valueOf(authors.get(i)));
                }
            }
            return new Entry(Kind.CYCLE, name, label(name), value, comment(name, option),
                    value != null && !value.equals(shipped), allowed, element);
        }
        if (linkElement.isInstance(element)) {
            String target = (String) linkTarget.get(element);
            return new Entry(Kind.LINK, target, translate("screen." + target, target), "",
                    optional("screen." + target + ".comment"), false, null, element);
        }
        if (profileElement.isInstance(element)) {
            return profileEntry(element);
        }
        // OptionMenuElement.EMPTY, and anything a newer Iris has added: a row we
        // cannot drive is worse than no row, since it would look like one that does
        // nothing when clicked.
        return null;
    }

    /**
     * The profile row: which named set of values the current ones add up to.
     *
     * A profile is not stored anywhere — it is derived. {@code scan} compares every
     * option's value against each profile's, and reports the one that matches, or
     * nothing when the values are somebody's own mixture. That is why the value here
     * can read as custom while every option below it looks ordinary.
     */
    private static Entry profileEntry(Object element) throws Exception {
        Object profiles = profileProfiles.get(element);
        Object options = profileOptions.get(element);
        Object values = profilePending.invoke(element);
        Object result = profileScan.invoke(profiles, options, values);

        Object current = profileCurrent.get(result);
        String name = null;
        if (current instanceof java.util.Optional) {
            Object profile = ((java.util.Optional<?>) current).orElse(null);
            if (profile != null) {
                name = String.valueOf(profileName.get(profile));
            }
        }
        String value = name == null
                ? I18n.format("uky.shaderPacks.profile.custom", new Object[0])
                : translate("profile." + name, name);
        return new Entry(Kind.PROFILE, "", I18n.format("uky.shaderPacks.profile",
                new Object[0]), value, null, name == null, null, element);
    }

    /**
     * Moves the pack to its next profile.
     *
     * A profile is a named set of values for many options at once, so this queues all
     * of them — which is why the rows under it change together when it is clicked.
     */
    private static void cycleProfile(Object element) throws Exception {
        if (element == null) {
            return;
        }
        Object result = profileScan.invoke(profileProfiles.get(element),
                profileOptions.get(element), profilePending.invoke(element));
        Object next = profileNext.get(result);
        if (next != null) {
            queueFromProfile.invoke(null, next);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> queue() throws Exception {
        return (Map<String, String>) getOptionQueue.invoke(null);
    }

    /** The loaded pack's menu, or null when no pack is loaded. */
    private static Object container() throws Exception {
        Object pack = getCurrentPack.invoke(null);
        if (pack instanceof java.util.Optional) {
            pack = ((java.util.Optional<?>) pack).orElse(null);
        }
        return pack == null ? null : getMenuContainer.invoke(pack);
    }

    // ------------------------------------------------------------------- labels --

    private static String label(String name) {
        return translate("option." + name, prettify(name));
    }

    private static String comment(String name, Object option) throws Exception {
        String translated = optional("option." + name + ".comment");
        if (translated != null) {
            return translated;
        }
        // The pack's own comment out of the shader source, which is what Iris falls
        // back to and is often the only description a pack has.
        Object own = optionComment.invoke(option);
        if (own instanceof java.util.Optional) {
            Object text = ((java.util.Optional<?>) own).orElse(null);
            return text == null ? null : text.toString();
        }
        return null;
    }

    /** A translation, or {@code fallback} when the key is not in the table. */
    private static String translate(String key, String fallback) {
        String out = I18n.format(key, new Object[0]);
        return key.equals(out) ? fallback : out;
    }

    private static String optional(String key) {
        String out = I18n.format(key, new Object[0]);
        return key.equals(out) ? null : out;
    }

    /**
     * A define's name made readable, for a pack that shipped no translation.
     *
     * {@code SHADOW_QUALITY} is what the author wrote and what Iris shows; "Shadow
     * quality" is what it says. Only the shape is changed — every word is the
     * author's — so a player comparing this screen with the pack's documentation is
     * still looking at the same option.
     */
    private static String prettify(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        boolean start = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                out.append(' ');
                start = false;
                continue;
            }
            if (start) {
                out.append(Character.toUpperCase(c));
                start = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ resolve --

    private static boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;
        try {
            Class<?> iris = Class.forName(IRIS);
            getCurrentPack = iris.getMethod("getCurrentPack");
            getOptionQueue = iris.getMethod("getShaderPackOptionQueue");
            queueDefaults = iris.getMethod("queueDefaultShaderPackOptionValues");

            Class<?> pack = Class.forName("net.coderbot.iris.shaderpack.ShaderPack");
            getMenuContainer = pack.getMethod("getMenuContainer");

            String menu = "net.coderbot.iris.shaderpack.option.menu.";
            Class<?> container = Class.forName(menu + "OptionMenuContainer");
            containerMainScreen = container.getField("mainScreen");
            containerSubScreens = container.getField("subScreens");
            screenElements = Class.forName(menu + "OptionMenuElementScreen")
                    .getField("elements");

            Class<?> option = Class.forName(menu + "OptionMenuOptionElement");
            elementPending = option.getMethod("getPendingOptionValues");
            elementApplied = option.getMethod("getAppliedOptionValues");

            booleanElement = Class.forName(menu + "OptionMenuBooleanOptionElement");
            stringElement = Class.forName(menu + "OptionMenuStringOptionElement");
            linkElement = Class.forName(menu + "OptionMenuLinkElement");
            profileElement = Class.forName(menu + "OptionMenuProfileElement");
            booleanOption = booleanElement.getField("option");
            stringOption = stringElement.getField("option");
            linkTarget = linkElement.getField("targetScreenId");
            profileProfiles = profileElement.getField("profiles");
            profileOptions = profileElement.getField("options");
            profilePending = profileElement.getMethod("getPendingOptionValues");

            String options = "net.coderbot.iris.shaderpack.option.";
            Class<?> base = Class.forName(options + "BaseOption");
            optionName = base.getMethod("getName");
            optionComment = base.getMethod("getComment");
            booleanDefault = Class.forName(options + "BooleanOption")
                    .getMethod("getDefaultValue");
            Class<?> string = Class.forName(options + "StringOption");
            stringDefault = string.getMethod("getDefaultValue");
            stringAllowed = string.getMethod("getAllowedValues");

            Class<?> values = Class.forName(options + "values.OptionValues");
            valuesBoolean = values.getMethod("getBooleanValueOrDefault", String.class);
            valuesString = values.getMethod("getStringValueOrDefault", String.class);

            Class<?> profileSet = Class.forName(options + "ProfileSet");
            profileScan = profileSet.getMethod("scan",
                    Class.forName(options + "OptionSet"), values);
            Class<?> result = Class.forName(options + "ProfileSet$ProfileResult");
            profileCurrent = result.getField("current");
            profileNext = result.getField("next");
            profileName = Class.forName(options + "Profile").getField("name");
            queueFromProfile = iris.getMethod("queueShaderPackOptionsFromProfile",
                    Class.forName(options + "Profile"));

            usable = true;
        } catch (Throwable t) {
            usable = false;
            UkyUI.LOGGER.info("Shader pack settings will not be shown: {}", t.toString());
        }
        return usable;
    }
}

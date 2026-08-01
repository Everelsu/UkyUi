package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Angelica's renderer settings, as a model this interface can draw itself.
 *
 * Angelica adds no settings screen for anyone to find. It listens for Forge's
 * {@code InitGuiEvent.Pre}, watches for vanilla's video screen being opened, and swaps
 * Sodium's options in as it appears — so a mod like this one, which replaces the
 * options screen outright and never opens the vanilla video screen, cuts the renderer's
 * settings off from the player entirely.
 *
 * <p>Behind that screen is a model, not a drawing: Sodium describes each setting as an
 * {@code Option} — name, tooltip, current value, and a {@code Control} saying whether it
 * is a tick box, a cycle through named values, or a slider over a range — grouped into
 * {@code OptionPage}s. A model can be rendered by anyone, so the pages are read here and
 * laid out as sections of our own Graphics tab rather than opening a second settings
 * screen drawn in a second style.
 *
 * <p>All of it is reflection, because Angelica is not a compile dependency and must not
 * become one: the pack is expected to run with or without it, and a hard reference would
 * mean a missing class at load time rather than a missing section. It also means every
 * call here is against another mod's private shape, which can change under us — so
 * nothing throws outward. A failure costs the section, and {@link #isPresentButUnreadable}
 * is what lets the screen offer the old door to Angelica's own screen instead.
 *
 * <p>Editing and saving follow Sodium's own contract exactly, which is not the obvious
 * one: {@code setValue} only stages a value on the option. {@code applyChanges} is what
 * writes it through to the mod's config object, and {@code OptionStorage.save} is what
 * puts it on disk. Nothing here touches Angelica's config file itself.
 */
public final class AngelicaOptions {

    private static final String MOD_ID = "angelica";

    private static final String OPTIONS = "me.jellysquid.mods.sodium.client.gui.options.";
    private static final String CONTROL = OPTIONS + "control.";
    private static final String PAGES = "me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages";
    private static final String GAME_OPTIONS = "me.jellysquid.mods.sodium.client.gui.SodiumGameOptions";

    private static final String DYNAMIC_LIGHTS = "com.gtnewhorizons.angelica.dynamiclights.DynamicLights";
    private static final String DYNAMIC_LIGHTS_PAGE =
            "com.gtnewhorizons.angelica.client.gui.DynamicLightsOptionPages";
    private static final String IRIS = "net.coderbot.iris.Iris";
    private static final String SHADER_PACK_SCREEN = "net.coderbot.iris.gui.screen.ShaderPackScreen";

    /**
     * The pages Sodium's own screen builds, in the order it builds them.
     *
     * Taken from that screen rather than invented, so the sections here carry the same
     * names in the same order as the ones a player will have seen anywhere else.
     */
    private static final String[] SODIUM_PAGES = {
        "general", "quality", "advanced", "performance", "appearance", "text"
    };

    /** What a control turned out to be, once its class was recognised. */
    public enum Kind {
        /** A tick box: on or off. */
        TOGGLE,
        /** A cycle through named values. */
        CYCLE,
        /** A slider over a range, stepping by a fixed interval. */
        SLIDER
    }

    // ---- reflected handles, resolved once ----

    private static boolean resolved;
    private static boolean usable;

    private static Method optionName;
    private static Method optionTooltip;
    private static Method optionControl;
    private static Method optionGetValue;
    private static Method optionSetValue;
    private static Method optionIsAvailable;
    private static Method optionHasChanged;
    private static Method optionApplyChanges;
    private static Method optionGetStorage;
    private static Method optionGetFlags;

    private static Method pageName;
    private static Method pageOptions;
    private static Method storageSave;
    private static Method applyAtlasSettings;

    private static Class<?> tickBoxClass;
    private static Class<?> cyclingClass;
    private static Class<?> sliderClass;

    private static Field cyclingValues;
    private static Field cyclingNames;
    private static Field sliderMin;
    private static Field sliderMax;
    private static Field sliderInterval;
    private static Field sliderMode;
    private static Method formatterFormat;

    private AngelicaOptions() {
    }

    // ------------------------------------------------------------------ model --

    /** One {@code OptionPage}, which becomes one headed section of the settings list. */
    public static final class Section {

        private final String name;
        private final List<Entry> options;

        Section(String name, List<Entry> options) {
            this.name = name;
            this.options = options;
        }

        public String name() {
            return this.name;
        }

        public List<Entry> options() {
            return this.options;
        }
    }

    /**
     * One setting, wrapping the mod's live {@code Option}.
     *
     * Everything is read through on demand rather than copied: an option's availability
     * and its displayed value both depend on what the others are set to — several of
     * Sodium's grey out until a related setting is turned on — so a snapshot would be
     * stale the moment anything above it was touched.
     */
    public static final class Entry {

        private final Object option;
        private final Kind kind;

        /** Cycling: the values this option may take, and their names by enum ordinal. */
        private final Object[] choices;
        private final String[] choiceNames;

        /** Slider: the range and the formatter that turns a raw value into text. */
        private final int min;
        private final int max;
        private final int interval;
        private final Object formatter;

        /**
         * Set the first time anything about this option throws.
         *
         * A broken row is then drawn disabled and left alone, rather than throwing once
         * a frame for as long as the screen is open. The log line is worth exactly one
         * appearance, which is what this flag buys.
         */
        private boolean broken;

        Entry(Object option, Kind kind, Object[] choices, String[] choiceNames,
              int min, int max, int interval, Object formatter) {
            this.option = option;
            this.kind = kind;
            this.choices = choices;
            this.choiceNames = choiceNames;
            this.min = min;
            this.max = max;
            this.interval = interval;
            this.formatter = formatter;
        }

        public Kind kind() {
            return this.kind;
        }

        public String name() {
            String name = (String) call(optionName);
            return name == null ? "" : name;
        }

        /** The mod's own explanation of the setting, or null if it gave none. */
        public String tooltip() {
            String tooltip = (String) call(optionTooltip);
            return tooltip == null || tooltip.isEmpty() ? null : tooltip;
        }

        /** False greys the row out: the setting exists but cannot be changed yet. */
        public boolean isEnabled() {
            if (this.broken) {
                return false;
            }
            Object available = call(optionIsAvailable);
            return available instanceof Boolean && ((Boolean) available).booleanValue();
        }

        // ---- toggle and cycle ----

        public boolean isOn() {
            Object value = call(optionGetValue);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        }

        /** The current value's display name, already translated by the mod. */
        public String valueLabel() {
            Object value = call(optionGetValue);
            if (value == null) {
                return "";
            }
            if (this.kind == Kind.TOGGLE) {
                return String.valueOf(value);
            }
            // Sodium names its values by the *enum's* ordinal, not by position in the
            // allowed list — the two differ whenever an option offers only a subset of
            // its enum — so this is indexed the same way its own screen indexes it.
            if (this.choiceNames != null && value instanceof Enum) {
                int ordinal = ((Enum<?>) value).ordinal();
                if (ordinal >= 0 && ordinal < this.choiceNames.length) {
                    return this.choiceNames[ordinal];
                }
            }
            return String.valueOf(value);
        }

        /** Advances to the next value, wrapping at the end. */
        public void cycle() {
            if (this.broken) {
                return;
            }
            if (this.kind == Kind.TOGGLE) {
                set(Boolean.valueOf(!isOn()));
                return;
            }
            if (this.choices == null || this.choices.length == 0) {
                return;
            }
            Object current = call(optionGetValue);
            int at = 0;
            for (int i = 0; i < this.choices.length; i++) {
                if (this.choices[i] == current) {
                    at = i;
                    break;
                }
            }
            // A value outside the allowed set lands on index 0, which is what Sodium's
            // own control does: the next click then moves to the first legal value.
            set(this.choices[(at + 1) % this.choices.length]);
        }

        // ---- slider ----

        public float normalized() {
            Object value = call(optionGetValue);
            if (!(value instanceof Number) || this.max == this.min) {
                return 0.0F;
            }
            float t = (((Number) value).intValue() - this.min) / (float) (this.max - this.min);
            return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        }

        /** Snaps to the option's own step, so the label never shows a value it cannot hold. */
        public void setNormalized(float t) {
            if (this.broken || this.interval <= 0) {
                return;
            }
            float clamped = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
            float raw = this.min + clamped * (this.max - this.min);
            int stepped = this.min
                    + Math.round((raw - this.min) / this.interval) * this.interval;
            if (stepped < this.min) {
                stepped = this.min;
            } else if (stepped > this.max) {
                stepped = this.max;
            }
            set(Integer.valueOf(stepped));
        }

        /** The value as the mod would write it — "8 chunks", "60 fps", "Off". */
        public String formattedValue() {
            Object value = call(optionGetValue);
            if (this.formatter == null || !(value instanceof Number)) {
                return value == null ? "" : String.valueOf(value);
            }
            try {
                Object text = formatterFormat.invoke(this.formatter,
                        Integer.valueOf(((Number) value).intValue()));
                return text == null ? "" : String.valueOf(text);
            } catch (Throwable t) {
                fail(t);
                return "";
            }
        }

        // ---- saving ----

        boolean hasChanged() {
            Object changed = call(optionHasChanged);
            return changed instanceof Boolean && ((Boolean) changed).booleanValue();
        }

        void applyChanges() {
            call(optionApplyChanges);
        }

        Object storage() {
            return call(optionGetStorage);
        }

        /** The option's flags by name, so no enum of the mod's has to be mirrored here. */
        void collectFlags(Set<String> into) {
            Object flags = call(optionGetFlags);
            if (!(flags instanceof Collection)) {
                return;
            }
            for (Object flag : (Collection<?>) flags) {
                if (flag instanceof Enum) {
                    into.add(((Enum<?>) flag).name());
                }
            }
        }

        private void set(Object value) {
            try {
                optionSetValue.invoke(this.option, value);
            } catch (Throwable t) {
                fail(t);
            }
        }

        private Object call(Method method) {
            if (this.broken || method == null) {
                return null;
            }
            try {
                return method.invoke(this.option);
            } catch (Throwable t) {
                fail(t);
                return null;
            }
        }

        private void fail(Throwable t) {
            if (this.broken) {
                return;
            }
            this.broken = true;
            UkyUI.LOGGER.warn("Angelica option {} could not be read; leaving it alone",
                    this.option == null ? "?" : this.option.getClass().getName(), t);
        }
    }

    // ----------------------------------------------------------------- reading --

    /**
     * Whether Angelica is installed at all.
     *
     * The first gate, and the cheap one: without it there is nothing to look up and no
     * reason to touch reflection. Callers outside ask {@link #read} instead and get an
     * empty list, which is the same answer with no second thing to keep in step.
     */
    private static boolean isInstalled() {
        return Loader.isModLoaded(MOD_ID);
    }

    /**
     * Reads Angelica's pages into sections.
     *
     * Called once per settings screen, not once per layout: the options carry the
     * pending edits, so rebuilding them on a tab switch or a window resize would throw
     * away whatever the player had changed but not yet applied.
     *
     * @return the sections, or an empty list if Angelica is absent or unreadable
     */
    public static List<Section> read() {
        List<Section> sections = new ArrayList<Section>();
        if (!resolve()) {
            return sections;
        }

        Class<?> pages = find(PAGES);
        if (pages != null) {
            for (String factory : SODIUM_PAGES) {
                addPage(sections, build(pages, factory));
            }
        }

        if (dynamicLightsEnabled()) {
            Class<?> lights = find(DYNAMIC_LIGHTS_PAGE);
            if (lights != null) {
                addPage(sections, build(lights, "dynamicLights"));
            }
        }

        return sections;
    }

    /**
     * Builds one page, caught on its own.
     *
     * Per page rather than around the loop, so a page that will not build costs its own
     * section and not every section after it.
     *
     * @return the page, or null — which a mod may also return in its own right, as
     *         Angelica does for dynamic lights when there is nothing to configure
     */
    private static Object build(Class<?> owner, String factory) {
        try {
            return owner.getMethod(factory).invoke(null);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Angelica's {} settings page could not be built", factory, t);
            return null;
        }
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether Iris is running, and so has a shader pack screen worth linking to. */
    public static boolean hasShaderPacks() {
        try {
            return Class.forName(IRIS).getField("enabled").getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Iris's shader pack screen, which is a screen and not a model.
     *
     * Sodium lists it as a page with no options, purely so its tab strip has somewhere
     * to put the link; that is the one part of its settings that genuinely cannot be
     * redrawn here, so it stays a link to what its author wrote.
     *
     * @return the screen, or null if it could not be built
     */
    public static GuiScreen shaderPackScreen(GuiScreen parent) {
        try {
            return (GuiScreen) Class.forName(SHADER_PACK_SCREEN)
                    .getConstructor(GuiScreen.class).newInstance(parent);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the shader pack screen", t);
            return null;
        }
    }

    private static boolean dynamicLightsEnabled() {
        try {
            return Class.forName(DYNAMIC_LIGHTS).getField("configEnabled").getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Turns one {@code OptionPage} into a section, dropping it if nothing came back. */
    private static void addPage(List<Section> into, Object page) {
        if (page == null) {
            return;
        }
        try {
            Object raw = pageOptions.invoke(page);
            if (!(raw instanceof Collection)) {
                return;
            }
            List<Entry> options = new ArrayList<Entry>();
            for (Object option : (Collection<?>) raw) {
                Entry entry = wrap(option);
                if (entry != null) {
                    options.add(entry);
                }
            }
            if (options.isEmpty()) {
                // An empty page is Sodium's way of writing a link — the shader pack
                // entry is one — and there is nothing here to draw for it.
                return;
            }
            into.add(new Section(String.valueOf(pageName.invoke(page)), options));
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read one of Angelica's settings pages", t);
        }
    }

    /**
     * Wraps one option, reading whatever its control needs to be drawn.
     *
     * @return null for a control this interface has no widget for, which is better than
     *         a row that looks editable and is not
     */
    private static Entry wrap(Object option) {
        if (option == null) {
            return null;
        }
        try {
            Object control = optionControl.invoke(option);
            if (control == null) {
                return null;
            }

            if (tickBoxClass.isInstance(control)) {
                return new Entry(option, Kind.TOGGLE, null, null, 0, 0, 0, null);
            }

            if (cyclingClass.isInstance(control)) {
                Object[] choices = (Object[]) cyclingValues.get(control);
                String[] names = (String[]) cyclingNames.get(control);
                if (choices == null || choices.length == 0) {
                    return null;
                }
                return new Entry(option, Kind.CYCLE, choices, names, 0, 0, 0, null);
            }

            if (sliderClass.isInstance(control)) {
                return new Entry(option, Kind.SLIDER, null, null,
                        sliderMin.getInt(control), sliderMax.getInt(control),
                        sliderInterval.getInt(control), sliderMode.get(control));
            }

            return null;
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read one of Angelica's settings", t);
            return null;
        }
    }

    // ----------------------------------------------------------------- saving --

    /**
     * Commits every pending edit, the way Angelica's own screen does.
     *
     * The order matters and is not obvious. Each changed option is applied, which is
     * what moves the staged value onto the mod's config object; the flags they carry
     * decide whether the atlas or the chunk renderer has to be rebuilt for the change
     * to be visible; and only then is each touched storage told to write itself out.
     * Doing any of it per-edit instead would rebuild the world's renderers on every
     * pixel of a slider drag.
     */
    public static void apply(List<Section> sections) {
        if (sections == null || sections.isEmpty() || !resolve()) {
            return;
        }
        try {
            Set<String> flags = new LinkedHashSet<String>();
            Set<Object> touched = new LinkedHashSet<Object>();

            for (Section section : sections) {
                for (Entry entry : section.options()) {
                    if (!entry.hasChanged()) {
                        continue;
                    }
                    entry.applyChanges();
                    entry.collectFlags(flags);
                    Object storage = entry.storage();
                    if (storage != null) {
                        touched.add(storage);
                    }
                }
            }

            if (touched.isEmpty()) {
                return;
            }

            reload(flags);

            for (Object storage : touched) {
                try {
                    storageSave.invoke(storage);
                } catch (Throwable t) {
                    UkyUI.LOGGER.warn("Angelica would not save {}",
                            storage.getClass().getName(), t);
                }
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not save Angelica's settings", t);
        }
    }

    /**
     * Rebuilds whatever the applied changes invalidated.
     *
     * An asset reload covers a renderer reload, which is why these are a chain and not
     * two independent checks — reloading resources rebuilds the chunks on its way
     * through, and doing both would rebuild them twice.
     */
    private static void reload(Set<String> flags) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (flags.contains("REQUIRES_ASSET_RELOAD")) {
                applyAtlasSettings.invoke(null);
                mc.refreshResources();
            } else if (flags.contains("REQUIRES_RENDERER_RELOAD") && mc.renderGlobal != null) {
                mc.renderGlobal.loadRenderers();
            }
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Angelica threw while applying its settings", t);
        }
        // REQUIRES_GAME_RESTART has nothing to do here, and Angelica's own screen does
        // nothing for it either — the value is saved and takes effect next launch.
    }

    // --------------------------------------------------------------- resolving --

    /** Looks up everything once. False means these settings cannot be drawn at all. */
    private static synchronized boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;

        if (!isInstalled()) {
            return false;
        }

        try {
            Class<?> option = Class.forName(OPTIONS + "Option");
            optionName = option.getMethod("getName");
            optionTooltip = option.getMethod("getTooltip");
            optionControl = option.getMethod("getControl");
            optionGetValue = option.getMethod("getValue");
            optionSetValue = option.getMethod("setValue", Object.class);
            optionIsAvailable = option.getMethod("isAvailable");
            optionHasChanged = option.getMethod("hasChanged");
            optionApplyChanges = option.getMethod("applyChanges");
            optionGetStorage = option.getMethod("getStorage");
            optionGetFlags = option.getMethod("getFlags");

            Class<?> page = Class.forName(OPTIONS + "OptionPage");
            pageName = page.getMethod("getName");
            // The flattened list, rather than walking the groups: the groups are spacing
            // in Sodium's own layout, and the page is the division the player sees.
            pageOptions = page.getMethod("getOptions");

            storageSave = Class.forName(OPTIONS + "storage.OptionStorage").getMethod("save");
            applyAtlasSettings = Class.forName(GAME_OPTIONS).getMethod("applyAtlasSettings");

            tickBoxClass = Class.forName(CONTROL + "TickBoxControl");
            cyclingClass = Class.forName(CONTROL + "CyclingControl");
            sliderClass = Class.forName(CONTROL + "SliderControl");

            // These describe the control and are private, because nothing was ever meant
            // to render one but Sodium. They are read and never written.
            cyclingValues = open(cyclingClass, "allowedValues");
            cyclingNames = open(cyclingClass, "names");
            sliderMin = open(sliderClass, "min");
            sliderMax = open(sliderClass, "max");
            sliderInterval = open(sliderClass, "interval");
            sliderMode = open(sliderClass, "mode");

            formatterFormat = Class.forName(CONTROL + "ControlValueFormatter")
                    .getMethod("format", int.class);

            usable = true;
            UkyUI.LOGGER.info("Angelica's renderer settings will be drawn in the Graphics tab");
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Angelica is installed but its settings could not be read; "
                    + "its own screen will be offered instead", t);
            usable = false;
        }
        return usable;
    }

    private static Field open(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.mods.ModConfigCatalog;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.console.uky.compat.GuiButton;
import com.console.uky.compat.GuiTextField;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Another mod's settings, drawn in our widgets.
 *
 * Most mods do not draw their settings screen at all — they hand Forge a list of
 * {@link IConfigElement}, each of which knows its name, type, value, default and
 * permitted values, and Forge renders it. That list is a model, and a model can be
 * rendered by anyone, so a modpack's settings need not be a patchwork of every
 * screen style of the last decade.
 *
 * <p>The elements point at the mod's live properties, so editing here is editing the
 * real config. Saving follows Forge's own contract exactly: values are set on the
 * elements, then {@code OnConfigChangedEvent} is posted for the owning mod, which is
 * what every Forge mod listens for in order to write its file. Nothing here writes
 * another mod's config itself — that is the mod's own business and it knows where its
 * file is.
 */
public class GuiModConfigScreen extends MenuScreen {

    /** What kind of value a row edits; the names 1.7.10's FML gave them. */
    enum ConfigGuiType { BOOLEAN, INTEGER, DOUBLE, STRING }

    /**
     * One row: a section to open or a value to edit. The questions are the ones FML's
     * IConfigElement answered on 1.7.10, so the screen below is unchanged by where the
     * answers come from — now a {@link ModConfigSpec}, which describes every value it
     * holds: its type, its range, its default, its comment and whether it needs a restart.
     */
    interface IConfigElement {
        boolean isProperty();

        List<IConfigElement> getChildElements();

        String getName();

        String getLanguageKey();

        default boolean showInGui() {
            return true;
        }

        default ConfigGuiType getType() {
            return ConfigGuiType.STRING;
        }

        default boolean isList() {
            return false;
        }

        default Object get() {
            return null;
        }

        default void set(Object value) {
        }

        default String[] getValidValues() {
            return null;
        }

        default Object getList() {
            return null;
        }

        default Object getDefaults() {
            return null;
        }

        default boolean isListLengthFixed() {
            return false;
        }

        default boolean isDefault() {
            return true;
        }

        default void setToDefault() {
        }

        default boolean requiresMcRestart() {
            return false;
        }
    }

    /** A section of a config: the tree under one key, or a whole config file. */
    private static final class Section implements IConfigElement {
        private final String name;
        private final String key;
        private final List<IConfigElement> children;

        Section(String name, String key, List<IConfigElement> children) {
            this.name = name;
            this.key = key;
            this.children = children;
        }

        @Override
        public boolean isProperty() {
            return false;
        }

        @Override
        public List<IConfigElement> getChildElements() {
            return this.children;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getLanguageKey() {
            return this.key;
        }
    }

    /** One value of a spec. */
    private static final class Value implements IConfigElement {
        private final ModConfigSpec owner;
        private final ModConfigSpec.ConfigValue<Object> value;
        private final ModConfigSpec.ValueSpec spec;
        private final Set<ModConfigSpec> touched;

        @SuppressWarnings("unchecked")
        Value(ModConfigSpec owner, ModConfigSpec.ConfigValue<?> value, Set<ModConfigSpec> touched) {
            this.owner = owner;
            this.value = (ModConfigSpec.ConfigValue<Object>) value;
            this.spec = value.getSpec();
            this.touched = touched;
        }

        @Override
        public boolean isProperty() {
            return true;
        }

        @Override
        public List<IConfigElement> getChildElements() {
            return new ArrayList<IConfigElement>();
        }

        @Override
        public String getName() {
            List<String> path = this.value.getPath();
            return path.get(path.size() - 1);
        }

        @Override
        public String getLanguageKey() {
            return this.spec.getTranslationKey();
        }

        @Override
        public ConfigGuiType getType() {
            Object current = this.value.get();
            if (current instanceof Boolean) {
                return ConfigGuiType.BOOLEAN;
            }
            if (current instanceof Integer || current instanceof Long) {
                return ConfigGuiType.INTEGER;
            }
            if (current instanceof Number) {
                return ConfigGuiType.DOUBLE;
            }
            return ConfigGuiType.STRING;
        }

        @Override
        public boolean isList() {
            return this.value.get() instanceof List;
        }

        @Override
        public Object get() {
            Object current = this.value.get();
            return current instanceof Enum ? ((Enum<?>) current).name() : current;
        }

        @Override
        public String[] getValidValues() {
            Object current = this.value.get();
            if (!(current instanceof Enum)) {
                return null;
            }
            Object[] constants = ((Enum<?>) current).getDeclaringClass().getEnumConstants();
            List<String> valid = new ArrayList<String>();
            for (Object constant : constants) {
                if (this.spec.test(constant)) {
                    valid.add(((Enum<?>) constant).name());
                }
            }
            return valid.toArray(new String[0]);
        }

        @Override
        public Object getList() {
            return this.value.get();
        }

        @Override
        public Object getDefaults() {
            return this.value.getDefault();
        }

        @Override
        public boolean isDefault() {
            Object current = this.value.get();
            Object fallback = this.value.getDefault();
            return current == null ? fallback == null : current.equals(fallback);
        }

        @Override
        public void setToDefault() {
            store(this.value.getDefault());
        }

        @Override
        public boolean requiresMcRestart() {
            return this.spec.restartType() != ModConfigSpec.RestartType.NONE;
        }

        /** Converts what the screen hands over to the type the value holds, then stores it. */
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void set(Object raw) {
            Object current = this.value.get();
            Object converted = raw;
            if (current instanceof Enum && raw instanceof String) {
                converted = Enum.valueOf(((Enum) current).getDeclaringClass(), (String) raw);
            } else if (current instanceof Long && raw instanceof Number) {
                converted = Long.valueOf(((Number) raw).longValue());
            } else if (current instanceof Integer && raw instanceof Number) {
                converted = Integer.valueOf(((Number) raw).intValue());
            } else if (current instanceof Double && raw instanceof Number) {
                converted = Double.valueOf(((Number) raw).doubleValue());
            } else if (current instanceof List && raw instanceof Object[]) {
                converted = new ArrayList<Object>(java.util.Arrays.asList((Object[]) raw));
            }
            store(converted);
        }

        private void store(Object converted) {
            // The spec says what it accepts — a range, a pattern, an element check — and a
            // value it would correct on load is not written in the first place.
            if (converted != null && this.spec.test(converted)) {
                this.value.set(converted);
                this.touched.add(this.owner);
            }
        }
    }

    /** The rows under one level of a spec, sections first as NeoForge's own screen has them. */
    @SuppressWarnings("unchecked")
    private static List<IConfigElement> level(ModConfigSpec owner, UnmodifiableConfig values,
                                              String prefix, Set<ModConfigSpec> touched) {
        List<IConfigElement> sections = new ArrayList<IConfigElement>();
        List<IConfigElement> rows = new ArrayList<IConfigElement>();
        for (Map.Entry<String, Object> entry : values.valueMap().entrySet()) {
            Object child = entry.getValue();
            if (child instanceof ModConfigSpec.ConfigValue) {
                rows.add(new Value(owner, (ModConfigSpec.ConfigValue<?>) child, touched));
            } else if (child instanceof UnmodifiableConfig) {
                List<String> path = new ArrayList<String>(java.util.Arrays.asList(
                        prefix.isEmpty() ? new String[0] : prefix.split("\\.")));
                path.add(entry.getKey());
                sections.add(new Section(entry.getKey(), owner.getLevelTranslationKey(path),
                        level(owner, (UnmodifiableConfig) child, String.join(".", path), touched)));
            }
        }
        sections.addAll(rows);
        return sections;
    }


    private static final int ID_DONE = 200;
    private static final int ID_DEFAULTS = 201;

    private final String modId;
    private final String heading;
    private final List<IConfigElement> elements;
    /** Specs edited on this screen or any section opened from it; saved on the way out. */
    private final Set<ModConfigSpec> touched;

    /** Set once anything is edited, so an untouched visit posts no event. */
    private boolean edited;
    private boolean requiresMcRestart;

    private GuiTextField editor;
    private int editing = -1;

    private ElementList list;
    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private GuiModConfigScreen(net.minecraft.client.gui.screens.Screen parent, String modId, String heading,
                               List<IConfigElement> elements, Set<ModConfigSpec> touched) {
        super(parent);
        this.modId = modId;
        this.heading = heading;
        this.elements = elements;
        this.touched = touched;
    }

    /**
     * Builds the screen for a mod, or returns null if its settings cannot be redrawn.
     *
     * A null here is not a failure — it means the mod drew its own screen instead of
     * describing one, and the caller should open that.
     */
    /**
     * This mod's settings in this screen, or null if it has none to redraw.
     *
     * Every config file the mod registered with a loaded spec — client, common, and the
     * server one only in a world of the player's own, as vanilla allows. One file opens
     * straight into its values; several become a section each.
     */
    public static GuiModConfigScreen forEntry(ModConfigCatalog.Entry entry,
                                              net.minecraft.client.gui.screens.Screen parent) {
        try {
            Set<ModConfigSpec> touched = new LinkedHashSet<ModConfigSpec>();
            List<IConfigElement> files = new ArrayList<IConfigElement>();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            for (ModConfig.Type type : ModConfig.Type.values()) {
                if (type == ModConfig.Type.STARTUP
                        || (type == ModConfig.Type.SERVER && !mc.hasSingleplayerServer())) {
                    continue;
                }
                for (ModConfig config : ModConfigs.getConfigSet(type)) {
                    if (!config.getModId().equals(entry.modId())
                            || !(config.getSpec() instanceof ModConfigSpec)) {
                        continue;
                    }
                    ModConfigSpec spec = (ModConfigSpec) config.getSpec();
                    if (!spec.isLoaded()) {
                        continue;
                    }
                    files.add(new Section(type.name().toLowerCase(java.util.Locale.ROOT),
                            "neoforge.configuration.uitext.type." + type.name().toLowerCase(java.util.Locale.ROOT),
                            level(spec, spec.getValues(), "", touched)));
                }
            }
            if (files.isEmpty()) {
                return null;
            }
            List<IConfigElement> top = files.size() == 1 ? files.get(0).getChildElements() : files;
            return new GuiModConfigScreen(parent, entry.modId(), entry.displayName(), visible(top), touched);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read the settings of mod {}", entry.modId(), t);
            return null;
        }
    }

    private GuiModConfigScreen child(IConfigElement category) {
        String name = label(category);
        return new GuiModConfigScreen(this, this.modId, this.heading + "  ·  " + name,
                visible(category.getChildElements()), this.touched);
    }

    @SuppressWarnings("unchecked")
    private static List<IConfigElement> visible(List<IConfigElement> source) {
        List<IConfigElement> kept = new ArrayList<IConfigElement>();
        if (source != null) {
            for (IConfigElement element : source) {
                // Mods mark internal entries as hidden and Forge honours it; a screen
                // that showed them would be showing more than the mod's own does.
                if (element != null && element.showInGui()) {
                    kept.add(element);
                }
            }
        }
        return kept;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.70F), 500);
        this.panelX1 = Math.max(10, (int) (this.width * 0.06F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        boolean cramped = this.height < 300;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 20) - buttonHeight;

        if (this.list == null) {
            this.list = new ElementList();
        }
        int listTop = this.panelY1 + 44;
        this.list.setBounds(this.panelX1 + 15, listTop, panelWidth - 30,
                Math.max(40, buttonY - 12 - listTop), 22);

        int gap = 8;
        int half = (panelWidth - 30 - gap) / 2;
        MenuButton defaults = new MenuButton(ID_DEFAULTS, this.panelX1 + 15, buttonY,
                half, buttonHeight, I18n.get("uky.modSettings.defaults", new Object[0]),
                MenuButton.Style.NORMAL);
        defaults.entrance(0.05F);
        this.buttonList.add(defaults);

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15 + half + gap, buttonY,
                half, buttonHeight, I18n.get("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.88F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.24F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.10F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.5F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_IN_PLANE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        float a = this.fadeAlpha;

        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.55F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));

        drawFitted(this.heading.toUpperCase(), this.panelX1 + 15, this.panelY1 + 14,
                this.panelX2 - this.panelX1 - 30, Draw.withAlpha(Theme.text, a));
        Draw.rect(this.panelX1 + 15, this.panelY1 + 32, this.panelX2 - 15, this.panelY1 + 33,
                Draw.fade(Theme.separator, a));

        this.list.update(this.delta);
        this.list.draw(mouseX, mouseY, a);

        if (this.editor != null) {
            this.editor.drawTextBox();
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        if (button.id == ID_DONE) {
            commitEditor();
            applyAndClose();
        } else if (button.id == ID_DEFAULTS) {
            for (IConfigElement element : this.elements) {
                if (element.isProperty()) {
                    element.setToDefault();
                    this.edited = true;
                    this.requiresMcRestart |= element.requiresMcRestart();
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.editor != null) {
            if (keyCode == 1 || keyCode == 28) {
                commitEditor();
                return;
            }
            this.editor.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == 1) {
            applyAndClose();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        if (this.editor != null) {
            commitEditor();
            return;
        }
        if (this.list.mouseClicked(mouseX, mouseY)) {
            int index = this.list.rowIndexAt(mouseX, mouseY);
            if (index >= 0 && index < this.elements.size()) {
                activate(index, mouseX);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = com.console.uky.compat.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.list.mouseWheel(wheel > 0 ? 1 : -1);
        }
    }

    /** Click behaviour depends entirely on what kind of thing the row is. */
    private void activate(int index, int mouseX) {
        IConfigElement element = this.elements.get(index);

        if (!element.isProperty()) {
            this.mc.setScreen(child(element));
            return;
        }

        // The reset arrow sits at the right end and takes precedence over the row.
        if (mouseX > this.list.rowRight() - 14 && !element.isDefault()) {
            element.setToDefault();
            this.edited = true;
            this.requiresMcRestart |= element.requiresMcRestart();
            return;
        }

        if (element.getType() == ConfigGuiType.BOOLEAN && !element.isList()) {
            set(element, Boolean.valueOf(!isTrue(element.get())));
            return;
        }

        String[] valid = element.getValidValues();
        if (valid != null && valid.length > 0 && !element.isList()) {
            String current = String.valueOf(element.get());
            int at = 0;
            for (int i = 0; i < valid.length; i++) {
                if (valid[i].equals(current)) {
                    at = i;
                    break;
                }
            }
            set(element, valid[(at + 1) % valid.length]);
            return;
        }

        if (element.isList()) {
            editList(element);
            return;
        }

        beginEditing(index, element);
    }

    /**
     * Opens a config list in the shared editor.
     *
     * <p>Ours and every other mod's alike: this screen is drawn from
     * {@link IConfigElement}, and a list is one of the kinds an element can be. It is
     * the one place in the interface where lists are edited — the settings tabs are for
     * what a player changes while playing, and a list is a decision made once.
     *
     * <p>Every value goes to the editor as text and comes back as text, and is then
     * parsed into whatever the element said it was. That is exactly what Forge's own
     * list screen does, and for the same reason: a list of doubles and a list of
     * strings are the same rows on screen, and only the mod knows which it wanted.
     *
     * <p>A value that will not parse is dropped rather than guessed at — writing
     * {@code 0} into somebody's config because "seven" was typed into a number list is
     * worse than the row not being there. The length is left to the element too: a mod
     * that fixed it gets an editor with no Add and no bin.
     */
    @SuppressWarnings("unchecked")
    private void editList(final IConfigElement element) {
        final ConfigGuiType type = element.getType();
        switchTo(new GuiListEditScreen(this, label(element), new GuiListEditScreen.Source() {
            @Override
            public String[] load() {
                return asStrings(element.getList());
            }

            @Override
            public String[] defaults() {
                return asStrings(element.getDefaults());
            }

            @Override
            public boolean resizable() {
                return !element.isListLengthFixed();
            }

            @Override
            public void save(String[] values) {
                List<Object> parsed = new ArrayList<Object>(values.length);
                for (int i = 0; i < values.length; i++) {
                    Object value = parse(type, values[i]);
                    if (value != null) {
                        parsed.add(value);
                    }
                }
                // The array overload, spelled out. IConfigElement has both set(Object)
                // and set(Object[]), and an argument typed as Object binds to the
                // first — which stores the whole array as one scalar value.
                Object[] array = parsed.toArray();
                element.set(array);
                GuiModConfigScreen.this.edited = true;
                GuiModConfigScreen.this.requiresMcRestart |= element.requiresMcRestart();
            }
        }));
    }

    /** A list of anything, as the lines a text editor can show. */
    private static String[] asStrings(Object source) {
        if (source == null) {
            return new String[0];
        }
        List<String> out = new ArrayList<String>();
        if (source instanceof Object[]) {
            Object[] values = (Object[]) source;
            for (int i = 0; i < values.length; i++) {
                out.add(String.valueOf(values[i]));
            }
        } else if (source instanceof List) {
            List<?> values = (List<?>) source;
            for (int i = 0; i < values.size(); i++) {
                out.add(String.valueOf(values.get(i)));
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** One line back into the type the owning mod asked for, or null if it will not go. */
    private static Object parse(ConfigGuiType type, String text) {
        try {
            if (type == ConfigGuiType.INTEGER) {
                return Integer.valueOf(Integer.parseInt(text.trim()));
            }
            if (type == ConfigGuiType.DOUBLE) {
                return Double.valueOf(Double.parseDouble(text.trim()));
            }
            if (type == ConfigGuiType.BOOLEAN) {
                return Boolean.valueOf(Boolean.parseBoolean(text.trim()));
            }
            return text;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private void beginEditing(int index, IConfigElement element) {
        int rowY = this.list.rowTop(index);
        int width = 120;
        this.editor = new GuiTextField(this.fontRendererObj,
                this.list.rowRight() - width - 16, rowY + 5, width, 14);
        this.editor.setMaxStringLength(256);
        this.editor.setText(String.valueOf(element.get()));
        this.editor.setFocused(true);
        this.editing = index;
    }

    /**
     * Parses what was typed and keeps it only if it fits the element's type.
     *
     * A number field that silently accepted "seven" would write nonsense into someone
     * else's config file, so a value that will not parse is simply dropped.
     */
    private void commitEditor() {
        if (this.editor == null) {
            return;
        }
        String text = this.editor.getText().trim();
        IConfigElement element = this.elements.get(this.editing);
        this.editor = null;
        this.editing = -1;

        try {
            if (element.getType() == ConfigGuiType.INTEGER) {
                set(element, Integer.valueOf(Integer.parseInt(text)));
            } else if (element.getType() == ConfigGuiType.DOUBLE) {
                set(element, Double.valueOf(Double.parseDouble(text)));
            } else {
                set(element, text);
            }
        } catch (NumberFormatException ignored) {
            // Left as it was.
        }
    }

    @SuppressWarnings("unchecked")
    private void set(IConfigElement element, Object value) {
        element.set(value);
        this.edited = true;
        this.requiresMcRestart |= element.requiresMcRestart();
    }

    /**
     * Hands the change back to the mod that owns it.
     *
     * This is what Forge's own screen does on the way out, and it is the only correct
     * way to do it from outside: the mod holds its {@code Configuration} and is the
     * only thing that knows when and where to write it.
     */
    private void applyAndClose() {
        // Only the outermost screen saves: a section is part of the same files, and
        // saving is what tells the mod to reload, which it should hear once.
        if (!(this.parent instanceof GuiModConfigScreen)) {
            for (ModConfigSpec spec : this.touched) {
                try {
                    spec.save();
                } catch (Throwable t) {
                    UkyUI.LOGGER.warn("Mod {} threw while saving its config", this.modId, t);
                }
            }
            this.touched.clear();
        }
        switchBack();
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue()
                : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String label(IConfigElement element) {
        String key = element.getLanguageKey();
        if (key != null && !key.isEmpty()) {
            String translated = I18n.get(key, new Object[0]);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return element.getName();
    }

    // ------------------------------------------------------------------ list --

    private final class ElementList extends ScrollList {

        @Override
        public int rowCount() {
            return GuiModConfigScreen.this.elements.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            IConfigElement element = GuiModConfigScreen.this.elements.get(index);
            boolean category = !element.isProperty();

            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }
            if (category) {
                Draw.rect(rowX, rowY + 2, rowX + 2, rowY + rowHeight - 2,
                        Draw.withAlpha(Theme.accent, 0.55F * alpha));
            }

            int textY = rowY + (rowHeight - 8) / 2;
            int changed = element.isProperty() && !element.isDefault() ? 14 : 0;

            if (category) {
                drawFitted(label(element), rowX + 10, textY, rowWidth - 30,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
                Icons.forward(rowX + rowWidth - 8, rowY + rowHeight / 2.0F, 7,
                        Draw.withAlpha(Theme.textDim, (isHovered ? 0.9F : 0.45F) * alpha));
                return;
            }

            if (element.getType() == ConfigGuiType.BOOLEAN && !element.isList()) {
                drawToggle(rowX + rowWidth - 24 - changed, rowY + rowHeight / 2.0F,
                        isTrue(element.get()), alpha);
                drawFitted(label(element), rowX + 10, textY,
                        rowWidth - 40 - changed - 10,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
            } else {
                String value = GuiModConfigScreen.this.editing == index ? ""
                        : String.valueOf(element.isList()
                                ? I18n.get("uky.modSettings.list", new Object[0])
                                : element.get());
                int valueWidth = drawFittedRight(value, rowX + rowWidth - 8 - changed, textY,
                        rowWidth / 2, Draw.withAlpha(Theme.textDim, 0.85F * alpha));
                drawFitted(label(element), rowX + 10, textY,
                        rowWidth - 18 - changed - valueWidth - 10,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
            }

            // Only where there is something to undo, so the arrow means "this differs
            // from what the mod shipped" as much as it means "click to reset".
            if (changed > 0) {
                Icons.refresh(rowX + rowWidth - 7, rowY + rowHeight / 2.0F, 9,
                        Draw.withAlpha(Theme.textDim, 0.8F * alpha));
            }
        }

        /** The same square switch the rest of the interface uses. */
        private void drawToggle(float x, float cy, boolean on, float alpha) {
            float w = 20.0F;
            float h = 9.0F;
            Draw.rect(x, cy - h / 2.0F, x + w, cy + h / 2.0F,
                    Draw.fade(on ? Draw.withAlpha(Theme.accent, 0.45F)
                            : Draw.withAlpha(Theme.background, 0.8F), alpha));
            Draw.border(x, cy - h / 2.0F, x + w, cy + h / 2.0F, 1.0F,
                    Draw.fade(on ? Theme.accent : Theme.panelBorder, alpha));

            float inset = 1.5F;
            float knobW = (w - inset * 2.0F) * 0.42F;
            float knobX = x + inset + (on ? (w - inset * 2.0F - knobW) : 0.0F);
            Draw.rect(knobX, cy - h / 2.0F + inset, knobX + knobW, cy + h / 2.0F - inset,
                    Draw.fade(on ? Theme.accent : Theme.textDim, alpha));
        }
    }
}

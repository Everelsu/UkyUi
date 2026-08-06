package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.mods.AngelicaOptions;
import com.console.uky.client.mods.ModConfigCatalog;
import com.console.uky.client.mods.VideoSettingsTakeover;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.MenuOptionButton;
import com.console.uky.client.gui.widget.MenuSlider;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSnooper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.storage.WorldInfo;

/**
 * The full settings screen, organised into tabs.
 *
 * Vanilla scatters its options across half a dozen screens reached by buttons on
 * buttons; here everything that can be edited in place is, grouped by what the
 * player is actually trying to change. Only the screens with genuinely different
 * interaction models — key bindings, resource packs — stay as links.
 *
 * The panel sits on the left so the black hole stays visible on the right rather
 * than being buried behind a full-width card.
 */
public class GuiSettingsScreen extends MenuScreen {

    private static final int ID_TAB_BASE = 300;

    private static final int ID_CONTROLS = 100;
    private static final int ID_SOUNDS = 106;
    private static final int ID_CHAT = 103;
    private static final int ID_SNOOPER = 104;
    private static final int ID_RESOURCE_PACKS = 105;
    private static final int ID_LANGUAGE = 102;
    private static final int ID_DIFFICULTY = 111;
    private static final int ID_DONE = 200;

    private static final String[] TABS = {
        "uky.settings.general", "uky.settings.video", "uky.settings.audio", "uky.settings.other"
    };

    private static final int PADDING = 20;
    /**
     * How far apart the two columns sit. Wide on purpose: at the old spacing the
     * eye read the whole grid as one block instead of two lists.
     */
    private static final int COLUMN_GAP = 22;

    /** Rows each tab lays out, used to size the whole grid so it always fits. */
    private static final int[] TAB_ROWS = {5, 6, 4, 3};

    private final GameSettings settings;
    /** Remembered across openings: coming back to the tab you left is the least surprising. */
    private static int activeTab;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    /**
     * Rows belonging to the current tab, as opposed to the tabs and Done.
     *
     * Kept apart from {@code buttonList} because only these scroll. The chrome has to
     * stay where it is or the header would slide away with the content.
     */
    private final java.util.List<MenuButton> contentRows =
            new java.util.ArrayList<MenuButton>();

    /** Each row's laid-out y, so the offset never compounds across frames. */
    private int[] rowNominalY = new int[0];

    /**
     * A heading laid out among the rows, naming the group of settings under it.
     *
     * Not a widget: it is text and a rule, with nothing to click. It still has to scroll
     * and clip exactly as the rows do, though, or a heading would sit still while its
     * own settings slid out from under it.
     */
    private static final class Heading {

        final String label;
        /** Where the layout put it, so scrolling never compounds. */
        final int nominalY;
        /** Whether clicking it folds the section away. Only the renderer's are. */
        final boolean collapsible;
        final boolean collapsed;
        int y;
        boolean visible;

        Heading(String label, int nominalY, boolean collapsible, boolean collapsed) {
            this.label = label;
            this.nominalY = nominalY;
            this.collapsible = collapsible;
            this.collapsed = collapsed;
            this.y = nominalY;
        }
    }

    /** Height of a heading's own line, excluding the air the layout leaves above it. */
    private static final int HEADING_HEIGHT = 10;

    private final java.util.List<Heading> headings = new java.util.ArrayList<Heading>();

    /**
     * Angelica's settings, read once for the life of this screen.
     *
     * Once, because the options carry the player's pending edits until they are applied
     * — rebuilding them on a tab switch or a window resize would quietly throw those
     * away. Null until the Graphics tab is first laid out, so a session that never opens
     * it never touches Angelica at all.
     */
    private java.util.List<AngelicaOptions.Section> angelica;

    /**
     * Renderer sections the player has opened, by name.
     *
     * Opened rather than folded, so that folded is what a section is by default: there
     * are seven of them and fifty-odd rows between them, and a tab that arrives with
     * all of it unrolled is a wall to scroll past rather than a list to read. Closed,
     * the whole renderer is seven lines and you open the one you came for.
     *
     * <p>Static, so it survives leaving and reopening the screen the way the active tab
     * does — a section opened once should still be open when you come back to it.
     */
    private static final java.util.Set<String> expanded = new java.util.HashSet<String>();

    /** Every renderer option's name, for spotting the vanilla rows it duplicates. */
    private java.util.Set<String> rendererLabels;

    /**
     * Columns the current tab lays its rows out in.
     *
     * Two for a tab of short vanilla labels, one where the renderer's are involved:
     * "Использовать отсечение граней блоков" and its value do not go in half a panel,
     * and at that width every second row was an ellipsis.
     */
    private int columns = 2;

    /** Position in the entrance stagger, so no caller has to count rows itself. */
    private int rowIndex;

    /** Pointer position, kept for hit-testing the section headings. */
    private int mouseX;
    private int mouseY;

    private float scroll;
    private float scrollTarget;
    /** Height the rows actually need, which may exceed the space available. */
    private int contentExtent;

    private int contentTop;
    private int columnWidth;
    private int leftColumn;
    private int rightColumn;
    /** Y of the rule that separates the settings from the Done button. */
    private int footerRuleY;
    // Sized from the space actually available rather than fixed, so the screen
    // holds together from GUI scale 1 on a large window to scale 4 on a small one.
    private int rowHeight;
    private int rowGap;

    public GuiSettingsScreen(GuiScreen parent, GameSettings settings) {
        super(parent);
        this.settings = settings;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        // Wider than before to pay for the bigger column gap without squeezing the
        // controls themselves.
        int panelWidth = Math.min((int) (this.width * 0.62F), 440);
        this.panelX1 = Math.max(10, (int) (this.width * 0.05F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        this.columnWidth = (panelWidth - PADDING * 2 - COLUMN_GAP) / 2;
        this.leftColumn = this.panelX1 + PADDING;
        this.rightColumn = this.leftColumn + this.columnWidth + COLUMN_GAP;
        this.contentTop = this.panelY1 + TAB_RULE_OFFSET + 16;

        // The footer is reserved first, then the rows share what is left. Sizing in
        // that order is what stops a tall tab from running over the Done button.
        // At GUI scale 4 on a small window there are barely 270 units of height to
        // work with, so the footer gives up some of its own breathing room first.
        boolean cramped = this.height < 300;
        int doneHeight = cramped ? 20 : 24;
        int doneY = this.panelY2 - (cramped ? 10 : PADDING) - doneHeight;
        this.footerRuleY = doneY - (cramped ? 8 : 16);

        this.contentRows.clear();
        this.headings.clear();

        // Rows are sized for comfort now rather than squeezed to fit the tab that has
        // the most of them. Anything that does not fit scrolls, which is the whole
        // point of the change: a tab is free to be longer than the panel.
        int rows = TAB_ROWS[Math.min(activeTab, TAB_ROWS.length - 1)];
        int available = this.footerRuleY - (cramped ? 8 : 12) - this.contentTop;
        int slot = Math.max(22, available / Math.min(rows, 6));
        // Roughly a 3:2 split between the control and the air under it. The upper
        // clamps stop a tall window producing absurdly fat rows; the lower ones are
        // the smallest that still reads, and the gap yields before the row does.
        this.rowHeight = clamp(slot * 3 / 5, 14, 26);
        this.rowGap = clamp(slot - this.rowHeight, 3, 20);

        buildTabs();
        buildContent();
        measureContent();

        // Full content width and centred. On the right under one column it sat
        // outside the reading path entirely and simply went unnoticed.
        MenuButton done = new MenuButton(ID_DONE, this.leftColumn, doneY,
                this.panelX2 - PADDING - this.leftColumn, doneHeight,
                I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Distance from the panel's top edge down to the rule beneath the tabs. */
    /**
     * Distance from the panel's top to the rule under the tabs.
     *
     * Was 56, which left a band of empty panel above the first row of options taller
     * than the options themselves. The title sits at 14, the tabs at 26, and a tab is
     * 18 tall, so the rule only needs to clear 44.
     */
    private static final int TAB_RULE_OFFSET = 48;

    /** Left edge and width of each tab, in screen units. Filled by {@link #buildTabs}. */
    private final int[] tabX = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    /**
     * Lays the tabs out to their labels rather than into equal slots.
     *
     * Equal slots looked orderly in the code and wrong on screen: "Звук и чат" all
     * but touched its neighbour while "Общие" sat in a puddle of space, because a
     * left-aligned label in a fixed slot puts the gap after the text instead of
     * between the tabs. Measuring each label and using one padding between them
     * gives an even rhythm in any language.
     */
    private void buildTabs() {
        int usable = this.panelX2 - this.panelX1 - PADDING * 2;
        int between = 18;
        int labelPad = 12;

        int total = 0;
        for (int i = 0; i < TABS.length; i++) {
            String label = I18n.format(TABS[i], new Object[0]);
            // The ghost style letter-spaces its label by a pixel a glyph, so the
            // plain string width is an under-measure.
            this.tabWidth[i] = this.fontRenderer.getStringWidth(label) + label.length()
                    + labelPad * 2;
            total += this.tabWidth[i];
        }
        total += between * (TABS.length - 1);

        // Long translations can overflow the panel. Fall back to sharing the space
        // out proportionally rather than letting a tab run off the edge.
        if (total > usable) {
            int slack = usable - between * (TABS.length - 1);
            int sum = 0;
            for (int i = 0; i < TABS.length; i++) {
                sum += this.tabWidth[i];
            }
            for (int i = 0; i < TABS.length; i++) {
                this.tabWidth[i] = Math.max(20, this.tabWidth[i] * slack / Math.max(1, sum));
            }
        }

        int x = this.panelX1 + PADDING;
        for (int i = 0; i < TABS.length; i++) {
            this.tabX[i] = x;
            MenuButton tab = new MenuButton(ID_TAB_BASE + i, x, this.panelY1 + 26,
                    this.tabWidth[i], 18,
                    I18n.format(TABS[i], new Object[0]), MenuButton.Style.GHOST);
            // The rule below the row and the marker on it already say which tab is
            // active; the ghost style's own connector and underline only added
            // clutter to every single one.
            tab.align(MenuButton.Align.LEFT).plain().steady();
            tab.entrance(0.02F + i * 0.03F);
            if (i == activeTab) {
                tab.selected();
            }
            this.buttonList.add(tab);
            x += this.tabWidth[i] + between;
        }
    }

    private void buildContent() {
        int y = this.contentTop;
        this.rowIndex = 0;
        // One column wherever the renderer's settings are in play; its labels are
        // sentences, not words, and half a panel truncated most of them.
        this.columns = activeTab == 1 && !angelica().isEmpty() ? 1 : 2;

        switch (activeTab) {
            case 0:
                // GUI Scale is back, and it now only affects the game's own HUD and
                // vanilla screens — these menus lay themselves out independently of
                // it. See MenuScreen.setWorldAndResolution.
                // SHOW_CAPE went in 1.8, replaced by the per-part skin customisation
                // screen; AUTO_JUMP takes its place as the general-tab row a 1.12.2
                // player expects to find. Difficulty is no longer a GameSettings option
                // at all — see DIFFICULTY_SOURCE.
                y = flow(y,
                        GameSettings.Options.FOV, DIFFICULTY_SOURCE,
                        GameSettings.Options.SENSITIVITY, GameSettings.Options.GUI_SCALE,
                        GameSettings.Options.INVERT_MOUSE, GameSettings.Options.VIEW_BOBBING,
                        GameSettings.Options.TOUCHSCREEN, GameSettings.Options.AUTO_JUMP);
                addLink(ID_CONTROLS, this.leftColumn, y, "options.controls");
                addLink(ID_LANGUAGE, this.rightColumn, y, "options.language");
                break;
            case 1:
                y = flow(y,
                        GameSettings.Options.GRAPHICS, GameSettings.Options.RENDER_DISTANCE,
                        GameSettings.Options.FRAMERATE_LIMIT, GameSettings.Options.AMBIENT_OCCLUSION,
                        GameSettings.Options.RENDER_CLOUDS, GameSettings.Options.PARTICLES,
                        GameSettings.Options.USE_FULLSCREEN, GameSettings.Options.ENABLE_VSYNC,
                        GameSettings.Options.GAMMA, GameSettings.Options.MIPMAP_LEVELS,
                        // ANISOTROPIC_FILTERING went in 1.9; ENTITY_SHADOWS is the row
                        // vanilla's own video settings put in that part of the list.
                        GameSettings.Options.ENTITY_SHADOWS);
                // FBO_ENABLE is deliberately absent. Everything this mod draws goes
                // through the framebuffer — the menus, the loading screen, the world
                // preview that is read back out of it — so turning it off does not read
                // as a rendering option being switched, it reads as the interface
                // breaking. A player would find the setting, try it, and reasonably
                // conclude the UI was at fault. It is still reachable in options.txt
                // for anyone who genuinely needs it.
                buildRendererContent(y);
                break;
            case 2:
                addLink(ID_SOUNDS, this.leftColumn, y, "options.sounds");
                addLink(ID_CHAT, this.rightColumn, y, "options.chat.title");
                y += this.rowHeight + this.rowGap;
                flow(y,
                        GameSettings.Options.CHAT_SCALE, GameSettings.Options.CHAT_OPACITY,
                        GameSettings.Options.CHAT_VISIBILITY, GameSettings.Options.CHAT_COLOR,
                        GameSettings.Options.CHAT_LINKS, GameSettings.Options.CHAT_WIDTH);
                break;
            default:
                addLink(ID_RESOURCE_PACKS, this.leftColumn, y, "options.resourcepack");
                addLink(ID_SNOOPER, this.rightColumn, y, "options.snooper.view");
                y += this.rowHeight + this.rowGap;
                addLink(ID_MODS, this.leftColumn, y, "uky.menu.mods");
                // Only offered when something in the pack actually has settings, which
                // on a bare install is nothing at all.
                if (!ModConfigCatalog.entries().isEmpty()) {
                    addLink(ID_MOD_SETTINGS, this.rightColumn, y, "uky.modSettings.title");
                }
                y += this.rowHeight + this.rowGap;
                flow(y, GameSettings.Options.FORCE_UNICODE_FONT,
                        GameSettings.Options.SNOOPER_ENABLED);
                break;
        }
    }

    /**
     * Lays options out across the tab's columns, and returns the next free y.
     *
     * A flow rather than fixed pairs, because rows can now drop out: an option the
     * renderer already offers is not drawn twice, and with pairs its partner would have
     * been left sitting beside a hole.
     */
    private int flow(int y, Object... rows) {
        int column = 0;
        for (Object row : rows) {
            // Rows are typed loosely on purpose. Nearly all of them are vanilla
            // options, but 1.12 took difficulty out of that enum and gave it a screen
            // of its own, and a setting that reads and writes somewhere else is still
            // the same row to the player. MenuOptionButton.Source is what that looks
            // like, so the flow takes either and the columns keep packing.
            if (row instanceof GameSettings.Options) {
                GameSettings.Options option = (GameSettings.Options) row;
                if (isRendererOwned(option)) {
                    continue;
                }
                addOption(option, columnX(column), y);
            } else if (row instanceof MenuOptionButton.Source) {
                addSourceRow((MenuOptionButton.Source) row, columnX(column), y);
            } else {
                continue;
            }
            if (++column == this.columns) {
                column = 0;
                y += this.rowHeight + this.rowGap;
            }
        }
        return column == 0 ? y : y + this.rowHeight + this.rowGap;
    }

    /**
     * The difficulty row.
     *
     * 1.7.10 had this as {@code GameSettings.Options.DIFFICULTY} and it worked from the
     * main menu. 1.12 moved it onto the world: it lives in the loaded save's
     * {@code WorldInfo}, which is why vanilla's own options screen greys the button out
     * when no world is open and when the save is hardcore or has its difficulty locked.
     * The same three conditions are what {@link MenuOptionButton.Source#available}
     * reports here, so the row is present everywhere it used to be and simply says it
     * cannot be changed when it cannot.
     */
    private static final MenuOptionButton.Source DIFFICULTY_SOURCE = new MenuOptionButton.Source() {

        private WorldInfo info() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.world == null ? null : mc.world.getWorldInfo();
        }

        @Override
        public String label() {
            return I18n.format("options.difficulty", new Object[0]);
        }

        @Override
        public String value() {
            WorldInfo info = info();
            EnumDifficulty difficulty = info == null
                    ? Minecraft.getMinecraft().gameSettings.difficulty
                    : info.getDifficulty();
            return difficulty == null
                    ? ""
                    : I18n.format(difficulty.getTranslationKey(), new Object[0]);
        }

        @Override
        public boolean toggle() {
            return false;
        }

        @Override
        public boolean on() {
            return false;
        }

        @Override
        public void cycle() {
            WorldInfo info = info();
            if (info == null) {
                return;
            }
            // Wraps through the four in order, exactly as vanilla's button does.
            info.setDifficulty(EnumDifficulty.byId(info.getDifficulty().getId() + 1));
        }

        @Override
        public boolean available() {
            WorldInfo info = info();
            return info != null && !info.isHardcoreModeEnabled() && !info.isDifficultyLocked();
        }
    };

    /** A row driven by something other than {@link GameSettings.Options}. */
    private void addSourceRow(MenuOptionButton.Source source, int x, int y) {
        MenuButton widget = new MenuOptionButton(ID_DIFFICULTY, x, y, rowWidth(),
                this.rowHeight, source);
        widget.entrance(stagger());
        this.buttonList.add(widget);
        this.contentRows.add(widget);
    }

    private int columnX(int column) {
        return column == 0 ? this.leftColumn : this.rightColumn;
    }

    /** Width of one row in the current column count. */
    private int rowWidth() {
        return this.columns == 1
                ? this.panelX2 - PADDING - this.leftColumn
                : this.columnWidth;
    }

    /**
     * Whether the renderer already offers this vanilla option, and so we should not.
     *
     * Most of Sodium's settings are its own, but a dozen of them read and write
     * vanilla's — render distance, brightness, v-sync — and it names those with
     * vanilla's own translation key. So the two labels are the same string in every
     * language, and matching on the label is enough to spot the pair. That is
     * deliberately not a list of which options Angelica happens to cover: such a list
     * is a second thing to keep in step with somebody else's mod, and when it fell
     * behind it would hide a setting that no longer exists anywhere. Matching can only
     * fail the other way, leaving a visible duplicate, which is the harmless direction.
     */
    private boolean isRendererOwned(GameSettings.Options option) {
        if (option == null) {
            return true;
        }
        if (angelica().isEmpty()) {
            return false;
        }
        // 1.7.10 needed an exception here for anisotropic filtering, which Sodium named
        // with a key of its own so the label match below could never see the pair.
        // 1.9 removed that option from vanilla, so the exception went with it.
        if (this.rendererLabels == null) {
            this.rendererLabels = new java.util.HashSet<String>();
            for (AngelicaOptions.Section section : angelica()) {
                for (AngelicaOptions.Entry entry : section.options()) {
                    this.rendererLabels.add(normalise(entry.name()));
                }
            }
        }
        return this.rendererLabels.contains(
                normalise(I18n.format(option.getTranslation(), new Object[0])));
    }

    /** Case and punctuation carry no meaning here and the two mods differ on both. */
    private static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final int ID_MODS = 107;
    private static final int ID_MOD_SETTINGS = 108;
    private static final int ID_VIDEO = 109;
    private static final int ID_SHADER_PACKS = 110;
    /**
     * Every renderer option shares one id.
     *
     * They are told apart by their type, not their number: a cycling one is handled by
     * the {@code MenuOptionButton} branch above the switch, and a slider acts on its own
     * drag and wants nothing from the switch at all. Numbering them would mean carrying
     * a parallel list solely to look up what a number meant.
     */
    private static final int ID_RENDERER_OPTION = 400;

    /**
     * The renderer's own settings, below the vanilla ones they belong with.
     *
     * Angelica replaces most of what the video settings mean, and it describes its
     * options rather than only drawing them — so its pages become headed sections of
     * this list instead of a second settings screen in a second style. See
     * {@link AngelicaOptions}.
     */
    private void buildRendererContent(int y) {
        java.util.List<AngelicaOptions.Section> sections = angelica();

        if (!sections.isEmpty() && AngelicaOptions.hasShaderPacks()) {
            // First, above the sections. Shader packs are the one thing here that
            // changes how the game looks all at once, and it is what anyone opening
            // this part of the settings is most often after; behind seven folded
            // sections it was the last thing they would find. It is also the one part
            // that is a screen and not a model — Sodium itself lists it as a page with
            // no options, purely to have somewhere to put the link — so it stays a link
            // to the one its author wrote.
            y = heading(shaderPacksLabel(), y, false, false);
            addLink(ID_SHADER_PACKS, this.leftColumn, y, "uky.settings.shaderPacks.open");
            y += this.rowHeight + this.rowGap;
        }

        for (AngelicaOptions.Section section : sections) {
            boolean open = expanded.contains(section.name());
            y = heading(section.name(), y, true, !open);
            if (!open) {
                continue;
            }
            // One to a row, in the order the mod lists them, so a player who knows
            // Angelica's own screen finds its settings where they left them.
            for (AngelicaOptions.Entry entry : section.options()) {
                addRendererOption(entry, this.leftColumn, y);
                y += this.rowHeight + this.rowGap;
            }
        }

        if (!sections.isEmpty()) {
            return;
        }

        // Nothing was read, yet something out there owns the video settings. Rather than
        // leave the player with no way at all to reach the renderer's options — which is
        // the state this whole section exists to fix — hand back the door to its own
        // screen. Covers Angelica changing shape under us, and equally a claimant that
        // was never one of Sodium's to begin with.
        if (VideoSettingsTakeover.isClaimed()) {
            y = heading(I18n.format("uky.settings.renderer", new Object[0]), y, false, false);
            addLink(ID_VIDEO, this.leftColumn, y, "uky.settings.videoTakeover");
        }
    }

    /** Iris names this section itself; our own wording is only the fallback. */
    private static String shaderPacksLabel() {
        String translated = I18n.format("options.iris.shaderPackSelection", new Object[0]);
        return "options.iris.shaderPackSelection".equals(translated)
                ? I18n.format("uky.settings.shaderPacks", new Object[0])
                : translated;
    }

    /**
     * Places a section heading and returns the y the rows under it start at.
     *
     * The air above is doubled and the air below is not: a heading belongs to what
     * follows it, and spacing it evenly between the two made it read as though it
     * belonged to neither.
     */
    private int heading(String label, int y, boolean collapsible, boolean folded) {
        int top = this.headings.isEmpty() && this.contentRows.isEmpty()
                ? y
                : y + this.rowGap * 2;
        this.headings.add(new Heading(label, top, collapsible, folded));
        return top + HEADING_HEIGHT + this.rowGap;
    }

    private void addRendererOption(final AngelicaOptions.Entry entry, int x, int y) {
        MenuButton widget;
        if (entry.kind() == AngelicaOptions.Kind.SLIDER) {
            widget = new MenuSlider(ID_RENDERER_OPTION, x, y, rowWidth(), this.rowHeight,
                    new MenuSlider.Source() {
                        @Override
                        public String caption() {
                            // The game captions its own sliders this way, and these sit
                            // in the same column as those.
                            return entry.name() + ": " + entry.formattedValue();
                        }

                        @Override
                        public float normalized() {
                            return entry.normalized();
                        }

                        @Override
                        public void setNormalized(float t) {
                            entry.setNormalized(t);
                        }

                        @Override
                        public boolean available() {
                            return entry.isEnabled();
                        }
                    });
        } else {
            widget = new MenuOptionButton(ID_RENDERER_OPTION, x, y, rowWidth(),
                    this.rowHeight, new MenuOptionButton.Source() {
                        @Override
                        public String label() {
                            return entry.name();
                        }

                        @Override
                        public String value() {
                            return entry.valueLabel();
                        }

                        @Override
                        public boolean toggle() {
                            return entry.kind() == AngelicaOptions.Kind.TOGGLE;
                        }

                        @Override
                        public boolean on() {
                            return entry.isOn();
                        }

                        @Override
                        public void cycle() {
                            entry.cycle();
                        }

                        @Override
                        public boolean available() {
                            return entry.isEnabled();
                        }
                    });
        }
        widget.entrance(stagger());
        this.buttonList.add(widget);
        this.contentRows.add(widget);
    }

    /**
     * Entrance delay for the next row, advancing the stagger as it goes.
     *
     * Capped, because the renderer contributes upwards of fifty rows and an uncapped
     * stagger turned opening the tab into a second and a half of things still arriving.
     */
    private float stagger() {
        return 0.06F + Math.min(this.rowIndex++, 14) * 0.02F;
    }

    private void addOption(GameSettings.Options option, int x, int y) {
        int width = rowWidth();
        MenuButton widget = option.isFloat()
                ? new MenuSlider(option.getOrdinal(), x, y, width, this.rowHeight, option)
                : new MenuOptionButton(option.getOrdinal(), x, y, width, this.rowHeight, option);
        widget.entrance(stagger());
        this.buttonList.add(widget);
        this.contentRows.add(widget);
    }

    /**
     * Angelica's pages, read on the first layout of this screen and kept after.
     *
     * Read whichever tab is open, not only the Graphics one, because the General tab
     * has to know what the renderer already offers in order not to offer it twice.
     */
    private java.util.List<AngelicaOptions.Section> angelica() {
        if (this.angelica == null) {
            this.angelica = AngelicaOptions.read();
        }
        return this.angelica;
    }

    /**
     * How far the rows reach past the bottom of the visible area.
     *
     * Taken from the rows themselves rather than from a count, because the tabs lay
     * their content out differently and a formula would have to be kept in step with
     * every one of them.
     */
    private void measureContent() {
        int bottom = this.contentTop;
        for (MenuButton row : this.contentRows) {
            bottom = Math.max(bottom, row.y + row.height);
        }
        // Headings count too. A tab could end on one — an empty section would — and
        // measuring only the rows would then let the last heading scroll out of reach.
        for (Heading heading : this.headings) {
            bottom = Math.max(bottom, heading.nominalY + HEADING_HEIGHT);
        }
        this.contentExtent = bottom - this.contentTop;

        this.rowNominalY = new int[this.contentRows.size()];
        for (int i = 0; i < this.contentRows.size(); i++) {
            this.rowNominalY[i] = this.contentRows.get(i).y;
        }

        int visible = viewportHeight();
        float max = Math.max(0.0F, this.contentExtent - visible);
        if (this.scrollTarget > max) {
            this.scrollTarget = max;
        }
        if (this.scroll > max) {
            this.scroll = max;
        }
    }

    private int viewportHeight() {
        return Math.max(20, this.footerRuleY - 6 - this.contentTop);
    }

    private float maxScroll() {
        return Math.max(0.0F, this.contentExtent - viewportHeight());
    }

    /**
     * Moves the rows to where the scroll says they are.
     *
     * The buttons are genuinely moved rather than drawn offset, so hit testing,
     * hovering and the marquee all follow without any of them needing to know that
     * scrolling exists. The nominal position is remembered on the first pass so the
     * offset is always applied to the layout rather than compounding.
     */
    private void applyScroll() {
        this.scroll = Ease.approach(this.scroll, this.scrollTarget, 0.05F, this.delta);
        int offset = Math.round(this.scroll);
        for (int i = 0; i < this.contentRows.size(); i++) {
            MenuButton row = this.contentRows.get(i);
            int nominal = this.rowNominalY[i];
            row.y = nominal - offset;
            // Shown only when the row is *entirely* inside the viewport. Allowing a
            // partial one meant its visible half was still drawn, and with no clip
            // around the button pass it landed on top of the tab strip and the title.
            // Whole rows only is the version that needs no clipping to be correct.
            row.visible = row.y >= this.contentTop
                    && row.y + row.height <= this.footerRuleY - 4;
        }
        for (Heading heading : this.headings) {
            heading.y = heading.nominalY - offset;
            heading.visible = heading.y >= this.contentTop
                    && heading.y + HEADING_HEIGHT <= this.footerRuleY - 4;
        }
    }

    /** The scrollbar, shown only when there is something to scroll. */
    private void drawScrollbar(float alpha) {
        float max = maxScroll();
        if (max <= 0.5F) {
            return;
        }
        int visible = viewportHeight();
        float trackX = this.panelX2 - PADDING + 6;
        float thumbHeight = Math.max(18.0F, visible * visible / (float) this.contentExtent);
        float travel = visible - thumbHeight;
        float thumbY = this.contentTop + travel * (this.scroll / max);

        Draw.rect(trackX, this.contentTop, trackX + 2, this.contentTop + visible,
                Draw.withAlpha(Theme.separator, 0.35F * alpha));
        Draw.rect(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
                Draw.withAlpha(Theme.accent, 0.8F * alpha));
    }

    private void addLink(int id, int x, int y, String langKey) {
        MenuButton link = new MenuButton(id, x, y, rowWidth(), this.rowHeight,
                I18n.format(langKey, new Object[0]), MenuButton.Style.NORMAL);
        // Left-aligned like every option row. Centred, they broke the line the
        // accent rails set up down the column.
        link.align(MenuButton.Align.LEFT);
        link.entrance(stagger());
        this.buttonList.add(link);
        this.contentRows.add(link);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        // Pushed well clear of the panel. With the glass letting it through, a hole
        // sitting directly behind the second column cost more readability than it
        // was worth; out here it still frames the screen.
        return this.width * 0.87F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.55F;
    }

    @Override
    protected float blackHoleRadius() {
        // Smaller than the title screen's on purpose. The bright inner disk runs to
        // roughly three times this, and at the old size it reached back under the
        // right-hand column of controls.
        return Math.min(this.width, this.height) * 0.13F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.72F;
    }

    @Override
    protected int blackHolePose() {
        // Looking down on the disk: the settings screen should not read as the
        // title screen with a card dropped on it.
        return LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        drawGlassPanel();
        applyScroll();

        this.fontRenderer.drawString(I18n.format("options.title", new Object[0]),
                panelX1 + PADDING, panelY1 + 14, Draw.withAlpha(Theme.text, this.fadeAlpha));

        // Rule under the tab row, with the active tab's span picked out on it.
        int ruleY = panelY1 + TAB_RULE_OFFSET;
        Draw.rect(panelX1 + PADDING, ruleY, panelX2 - PADDING, ruleY + 1,
                Draw.fade(Theme.separator, this.fadeAlpha));

        int active = Math.min(activeTab, TABS.length - 1);
        Draw.rect(this.tabX[active], ruleY, this.tabX[active] + this.tabWidth[active], ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));

        // The only other rule on the screen, and it earns its place: it is what
        // makes Done read as the end of the form rather than one more control.
        Draw.gradientH(panelX1 + PADDING, this.footerRuleY, panelX2 - PADDING,
                this.footerRuleY + 1,
                Draw.withAlpha(Theme.accent, 0.35F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        drawHeadings();
        drawScrollbar(this.fadeAlpha);
    }

    /**
     * The section headings, drawn the way the key bindings screen draws its categories.
     *
     * Same mark for the same idea: these rows belong together and here is what they are.
     * The rule runs from the end of the label out to the right edge, which groups without
     * putting a heavy band behind every heading.
     */
    private void drawHeadings() {
        for (Heading heading : this.headings) {
            if (!heading.visible) {
                continue;
            }
            boolean hot = heading.collapsible && isOverHeading(heading);
            int right = this.panelX2 - PADDING;
            // A collapsible heading gives up the last of its width to the marker, so a
            // long section name can never run underneath it.
            int markerRoom = heading.collapsible ? 14 : 0;

            String label = heading.label.toUpperCase();
            int width = drawFitted(label, this.leftColumn, heading.y,
                    right - markerRoom - this.leftColumn,
                    Draw.withAlpha(hot ? Theme.textHover : Theme.accent,
                            (hot ? 1.0F : 0.85F) * this.fadeAlpha));

            // Rule running from the label to the right edge, so the grouping reads
            // without a heavy band behind it.
            int ruleFrom = this.leftColumn + width + 6;
            int ruleTo = right - markerRoom;
            if (ruleTo > ruleFrom) {
                Draw.gradientH(ruleFrom, heading.y + 3, ruleTo, heading.y + 4,
                        Draw.withAlpha(Theme.accent, 0.35F * this.fadeAlpha),
                        Draw.withAlpha(Theme.accent, 0.0F));
            }

            if (heading.collapsible) {
                // Pointing down when the section is open and along when it is folded,
                // which is the one convention every disclosure control shares.
                float cx = right - 5.0F;
                float cy = heading.y + 4.0F;
                int colour = Draw.withAlpha(hot ? Theme.textHover : Theme.accent,
                        (hot ? 1.0F : 0.7F) * this.fadeAlpha);
                if (heading.collapsed) {
                    Icons.forward(cx, cy, 7, colour);
                } else {
                    Icons.arrowDown(cx, cy, 7, colour);
                }
            }
        }
    }

    /** Hit box of a heading: the whole line, so the target is not a seven-unit arrow. */
    private boolean isOverHeading(Heading heading) {
        return this.mouseX >= this.leftColumn && this.mouseX <= this.panelX2 - PADDING
                && this.mouseY >= heading.y - 2 && this.mouseY <= heading.y + HEADING_HEIGHT;
    }

    /**
     * Folds a section away, or opens it again.
     *
     * @return true if the click landed on a heading and nothing else should see it
     */
    private boolean toggleHeadingAt(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        for (Heading heading : this.headings) {
            if (!heading.visible || !heading.collapsible || !isOverHeading(heading)) {
                continue;
            }
            if (!expanded.remove(heading.label)) {
                expanded.add(heading.label);
            }
            // The rows below have just appeared or gone; everything under them moves.
            relayout();
            return true;
        }
        return false;
    }

    /**
     * The panel as a sheet of dark glass rather than a black card.
     *
     * At near-full opacity the backdrop stopped being part of the scene and became
     * wallpaper behind a box. Letting the disk show through faintly keeps the two
     * connected; the controls carry their own fills, so text still has local
     * contrast wherever it lands.
     */
    private void drawGlassPanel() {
        float a = this.fadeAlpha;

        // Slightly denser at the top, where the title and tabs sit, thinning toward
        // the bottom. A flat wash reads as a rectangle; a gradient reads as glass.
        Draw.gradientV(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.52F * a),
                Draw.withAlpha(Theme.background, 0.30F * a));

        // Light catching the top edge, and the accent rail down the left.
        Draw.rect(panelX1, panelY1, panelX2, panelY1 + 1, Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(panelX1, panelY1, panelX1 + 2, panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a),
                Draw.withAlpha(Theme.accent, 0.0F));

        // Falloff past the right edge so the glass has a thickness to it and the
        // disk behind does not run straight into the controls. Long, because
        // against something as bright as the disk a short one reads as a seam.
        Draw.gradientH(panelX2, panelY1, panelX2 + 64, panelY2,
                Draw.withAlpha(Theme.background, 0.45F * a),
                Draw.withAlpha(Theme.background, 0.0F));
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        // Headings are drawn, not widgets, so they have to claim the click before the
        // button list gets it — and a fold rebuilds the rows the list is about to test.
        if (toggleHeadingAt(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0 && maxScroll() > 0.0F) {
            this.scrollTarget -= (wheel > 0 ? 1 : -1) * (this.rowHeight + this.rowGap) * 1.5F;
            this.scrollTarget = Math.max(0.0F, Math.min(maxScroll(), this.scrollTarget));
        }
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button.id >= ID_TAB_BASE && button.id < ID_TAB_BASE + TABS.length) {
            activeTab = button.id - ID_TAB_BASE;
            // Not initGui: that restarts the entrance fade, so switching tabs
            // blinked the whole screen through black.
            relayout();
            return;
        }
        if (button instanceof MenuOptionButton) {
            ((MenuOptionButton) button).cycle();
            return;
        }

        switch (button.id) {
            case ID_CONTROLS:
                openSub(new GuiControlsScreen(this, this.settings));
                break;
            case ID_SOUNDS:
                openSub(new GuiAudioScreen(this, this.settings, GuiAudioScreen.soundTab()));
                break;
            case ID_CHAT:
                openSub(new GuiAudioScreen(this, this.settings, GuiAudioScreen.chatTab()));
                break;
            case ID_LANGUAGE:
                openSub(new GuiLanguageScreen(this));
                break;
            case ID_RESOURCE_PACKS:
                openSub(new GuiResourcePacksScreen(this));
                break;
            case ID_SNOOPER:
                openSub(new GuiSnooper(this, this.settings));
                break;
            case ID_MODS:
                openSub(new GuiModsScreen(this));
                break;
            case ID_MOD_SETTINGS:
                openSub(new GuiModSettingsScreen(this));
                break;
            case ID_VIDEO:
                openSub(VideoSettingsTakeover.open(this, this.settings));
                break;
            case ID_SHADER_PACKS:
                GuiScreen shaders = AngelicaOptions.shaderPackScreen(this);
                if (shaders != null) {
                    openSub(shaders);
                }
                break;
            case ID_DONE:
                save();
                switchBack();
                break;
            default:
                break;
        }
    }

    private void openSub(GuiScreen screen) {
        // Vanilla saves before every sub-screen; matching that keeps behaviour
        // identical if the player alt-F4s from inside one.
        save();
        if (screen instanceof MenuScreen) {
            switchTo((MenuScreen) screen);
        } else {
            closeWith(new Runnable() {
                @Override
                public void run() {
                    mc.displayGuiScreen(screen);
                }
            });
        }
    }

    /**
     * Writes out everything this screen edits, wherever it lives.
     *
     * Vanilla's options write through as they are changed and only need flushing; the
     * renderer's are staged on their own option objects until applied, which is that
     * mod's contract rather than a choice made here. Both are settled at the same
     * moments, so leaving by any route leaves nothing behind.
     */
    private void save() {
        this.settings.saveOptions();
        if (this.angelica != null) {
            AngelicaOptions.apply(this.angelica);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) { // Escape
            save();
            switchBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.mods.ModConfigCatalog;
import com.console.uky.client.mods.VideoSettingsTakeover;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.MenuOptionButton;
import com.console.uky.client.gui.widget.MenuSlider;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSnooper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

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

        int rows = TAB_ROWS[Math.min(activeTab, TAB_ROWS.length - 1)];
        int slot = Math.max(17, (this.footerRuleY - (cramped ? 8 : 12) - this.contentTop) / rows);
        // Roughly a 3:2 split between the control and the air under it. The upper
        // clamps stop a tall window producing absurdly fat rows; the lower ones are
        // the smallest that still reads, and the gap yields before the row does.
        this.rowHeight = clamp(slot * 3 / 5, 14, 26);
        this.rowGap = clamp(slot - this.rowHeight, 3, 20);

        buildTabs();
        buildContent();

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
            this.tabWidth[i] = this.fontRendererObj.getStringWidth(label) + label.length()
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
        int index = 0;
        switch (activeTab) {
            case 0:
                // GUI Scale is back, and it now only affects the game's own HUD and
                // vanilla screens — these menus lay themselves out independently of
                // it. See MenuScreen.setWorldAndResolution.
                y = pair(y, index, GameSettings.Options.FOV, GameSettings.Options.DIFFICULTY);
                index += 2;
                y = pair(y, index, GameSettings.Options.SENSITIVITY, GameSettings.Options.GUI_SCALE);
                index += 2;
                y = pair(y, index, GameSettings.Options.INVERT_MOUSE, GameSettings.Options.VIEW_BOBBING);
                index += 2;
                y = pair(y, index, GameSettings.Options.TOUCHSCREEN, GameSettings.Options.SHOW_CAPE);
                index += 2;
                addLink(ID_CONTROLS, this.leftColumn, y, "options.controls", index++);
                addLink(ID_LANGUAGE, this.rightColumn, y, "options.language", index);
                break;
            case 1:
                y = pair(y, index, GameSettings.Options.GRAPHICS, GameSettings.Options.RENDER_DISTANCE);
                index += 2;
                y = pair(y, index, GameSettings.Options.FRAMERATE_LIMIT, GameSettings.Options.AMBIENT_OCCLUSION);
                index += 2;
                y = pair(y, index, GameSettings.Options.RENDER_CLOUDS, GameSettings.Options.PARTICLES);
                index += 2;
                y = pair(y, index, GameSettings.Options.USE_FULLSCREEN, GameSettings.Options.ENABLE_VSYNC);
                index += 2;
                y = pair(y, index, GameSettings.Options.GAMMA, GameSettings.Options.MIPMAP_LEVELS);
                index += 2;
                y = pair(y, index, GameSettings.Options.ANISOTROPIC_FILTERING, GameSettings.Options.FBO_ENABLE);
                if (VideoSettingsTakeover.isClaimed()) {
                    y += this.rowHeight + this.rowGap;
                    addLink(ID_VIDEO, this.leftColumn, y, "uky.settings.videoTakeover", index++);
                }
                break;
            case 2:
                addLink(ID_SOUNDS, this.leftColumn, y, "options.sounds", index++);
                addLink(ID_CHAT, this.rightColumn, y, "options.chat.title", index++);
                y += this.rowHeight + this.rowGap;
                y = pair(y, index, GameSettings.Options.CHAT_SCALE, GameSettings.Options.CHAT_OPACITY);
                index += 2;
                y = pair(y, index, GameSettings.Options.CHAT_VISIBILITY, GameSettings.Options.CHAT_COLOR);
                index += 2;
                pair(y, index, GameSettings.Options.CHAT_LINKS, GameSettings.Options.CHAT_WIDTH);
                break;
            default:
                addLink(ID_RESOURCE_PACKS, this.leftColumn, y, "options.resourcepack", index++);
                addLink(ID_SNOOPER, this.rightColumn, y, "options.snooper.view", index++);
                y += this.rowHeight + this.rowGap;
                addLink(ID_MODS, this.leftColumn, y, "uky.menu.mods", index++);
                // Only offered when something in the pack actually has settings, which
                // on a bare install is nothing at all.
                if (!ModConfigCatalog.entries().isEmpty()) {
                    addLink(ID_MOD_SETTINGS, this.rightColumn, y, "uky.modSettings.title",
                            index++);
                }
                y += this.rowHeight + this.rowGap;
                y = pair(y, index, GameSettings.Options.FORCE_UNICODE_FONT, GameSettings.Options.SNOOPER_ENABLED);
                break;
        }
    }

    private static final int ID_MODS = 107;
    private static final int ID_MOD_SETTINGS = 108;
    private static final int ID_VIDEO = 109;

    /** Lays two options side by side and returns the next row's y. */
    private int pair(int y, int index, GameSettings.Options left, GameSettings.Options right) {
        addOption(left, this.leftColumn, y, index);
        if (right != null) {
            addOption(right, this.rightColumn, y, index + 1);
        }
        return y + this.rowHeight + this.rowGap;
    }

    private void addOption(GameSettings.Options option, int x, int y, int index) {
        MenuButton widget = option.getEnumFloat()
                ? new MenuSlider(option.returnEnumOrdinal(), x, y, this.columnWidth, this.rowHeight, option)
                : new MenuOptionButton(option.returnEnumOrdinal(), x, y, this.columnWidth, this.rowHeight, option);
        widget.entrance(0.06F + index * 0.02F);
        this.buttonList.add(widget);
    }

    private void addLink(int id, int x, int y, String langKey, int index) {
        MenuButton link = new MenuButton(id, x, y, this.columnWidth, this.rowHeight,
                I18n.format(langKey, new Object[0]), MenuButton.Style.NORMAL);
        // Left-aligned like every option row. Centred, they broke the line the
        // accent rails set up down the column.
        link.align(MenuButton.Align.LEFT);
        link.entrance(0.06F + index * 0.02F);
        this.buttonList.add(link);
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
        drawGlassPanel();

        this.fontRendererObj.drawString(I18n.format("options.title", new Object[0]),
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
            case ID_DONE:
                this.settings.saveOptions();
                this.mc.displayGuiScreen(this.parent);
                break;
            default:
                break;
        }
    }

    private void openSub(GuiScreen screen) {
        // Vanilla saves before every sub-screen; matching that keeps behaviour
        // identical if the player alt-F4s from inside one.
        this.settings.saveOptions();
        this.mc.displayGuiScreen(screen);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // Escape
            this.settings.saveOptions();
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

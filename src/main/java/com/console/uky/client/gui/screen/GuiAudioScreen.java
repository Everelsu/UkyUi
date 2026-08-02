package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.MenuOptionButton;
import com.console.uky.client.gui.widget.MenuSlider;
import com.console.uky.client.gui.widget.SoundSlider;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Sound levels and chat options.
 *
 * Both were links out to vanilla screens — a column of grey sliders on the dirt
 * background and a two-column grid of grey buttons — which was jarring given
 * everything they were opened from. They are the same kind of screen, so they share
 * one here with a tab each, using the same sliders and toggles as the settings.
 *
 * <p>Master is separated from the rest: it scales all of them, so listing it inline
 * with the categories it governs invites you to read it as a peer.
 */
public class GuiAudioScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_TAB_BASE = 300;

    private static final int TAB_SOUND = 0;
    private static final int TAB_CHAT = 1;
    private static final String[] TABS = {"options.sounds", "options.chat.title"};

    /** Everything except MASTER, which gets its own row above these. */
    private static final SoundCategory[] CATEGORIES = {
        SoundCategory.MUSIC, SoundCategory.RECORDS, SoundCategory.WEATHER,
        SoundCategory.BLOCKS, SoundCategory.MOBS, SoundCategory.ANIMALS,
        SoundCategory.PLAYERS,
    };

    private static final GameSettings.Options[] CHAT_OPTIONS = {
        GameSettings.Options.CHAT_VISIBILITY, GameSettings.Options.CHAT_COLOR,
        GameSettings.Options.CHAT_LINKS, GameSettings.Options.CHAT_LINKS_PROMPT,
        GameSettings.Options.CHAT_OPACITY, GameSettings.Options.CHAT_SCALE,
        GameSettings.Options.CHAT_HEIGHT_FOCUSED, GameSettings.Options.CHAT_HEIGHT_UNFOCUSED,
        GameSettings.Options.CHAT_WIDTH,
    };

    private static final int PADDING = 20;
    private static final int COLUMN_GAP = 22;

    private final GameSettings settings;
    private static int activeTab = TAB_SOUND;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int columnWidth;
    private int leftColumn;
    private int rightColumn;
    private int rowHeight;
    private int rowGap;
    private int footerRuleY;

    private final int[] tabX = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    public GuiAudioScreen(GuiScreen parent, GameSettings settings) {
        super(parent);
        this.settings = settings;
    }

    public GuiAudioScreen(GuiScreen parent, GameSettings settings, int tab) {
        this(parent, settings);
        activeTab = Math.max(0, Math.min(tab, TABS.length - 1));
    }

    public static int soundTab() {
        return TAB_SOUND;
    }

    public static int chatTab() {
        return TAB_CHAT;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.62F), 440);
        this.panelX1 = Math.max(10, (int) (this.width * 0.05F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        this.columnWidth = (panelWidth - PADDING * 2 - COLUMN_GAP) / 2;
        this.leftColumn = this.panelX1 + PADDING;
        this.rightColumn = this.leftColumn + this.columnWidth + COLUMN_GAP;

        // Footer reserved first, then the rows share what is left — the same order the
        // settings screen uses, and for the same reason.
        boolean cramped = this.height < 300;
        int doneHeight = cramped ? 20 : 24;
        int doneY = this.panelY2 - (cramped ? 10 : PADDING) - doneHeight;
        this.footerRuleY = doneY - (cramped ? 8 : 16);

        int contentTop = this.panelY1 + 76;
        int rows = activeTab == TAB_SOUND
                ? 1 + (CATEGORIES.length + 1) / 2
                : (CHAT_OPTIONS.length + 1) / 2;
        int slot = Math.max(17, (this.footerRuleY - (cramped ? 8 : 12) - contentTop) / rows);
        this.rowHeight = clamp(slot * 3 / 5, 14, 26);
        this.rowGap = clamp(slot - this.rowHeight, 3, 20);

        buildTabs();

        if (activeTab == TAB_SOUND) {
            buildSoundRows(contentTop);
        } else {
            buildChatRows(contentTop);
        }

        MenuButton done = new MenuButton(ID_DONE, this.leftColumn, doneY,
                this.panelX2 - PADDING - this.leftColumn, doneHeight,
                I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void buildTabs() {
        int between = 18;
        int labelPad = 12;
        int x = this.panelX1 + PADDING;
        for (int i = 0; i < TABS.length; i++) {
            String label = I18n.format(TABS[i], new Object[0]);
            this.tabWidth[i] = this.fontRendererObj.getStringWidth(label) + label.length()
                    + labelPad * 2;
            this.tabX[i] = x;

            MenuButton tab = new MenuButton(ID_TAB_BASE + i, x, this.panelY1 + 32,
                    this.tabWidth[i], 18, label, MenuButton.Style.GHOST);
            tab.align(MenuButton.Align.LEFT).plain();
            tab.entrance(0.02F + i * 0.03F);
            if (i == activeTab) {
                tab.selected();
            }
            this.buttonList.add(tab);
            x += this.tabWidth[i] + between;
        }
    }

    private void buildSoundRows(int y) {
        // Master spans both columns: it multiplies every slider under it, so it reads
        // as the thing they all hang off rather than one of them.
        SoundSlider master = new SoundSlider(500, this.leftColumn, y,
                this.panelX2 - PADDING - this.leftColumn, this.rowHeight,
                SoundCategory.MASTER, this.settings);
        master.entrance(0.06F);
        this.buttonList.add(master);
        y += this.rowHeight + this.rowGap + 4;

        for (int i = 0; i < CATEGORIES.length; i++) {
            int x = (i % 2 == 0) ? this.leftColumn : this.rightColumn;
            SoundSlider slider = new SoundSlider(510 + i, x, y, this.columnWidth,
                    this.rowHeight, CATEGORIES[i], this.settings);
            slider.entrance(0.08F + i * 0.02F);
            this.buttonList.add(slider);
            if (i % 2 == 1) {
                y += this.rowHeight + this.rowGap;
            }
        }
    }

    private void buildChatRows(int y) {
        for (int i = 0; i < CHAT_OPTIONS.length; i++) {
            GameSettings.Options option = CHAT_OPTIONS[i];
            int x = (i % 2 == 0) ? this.leftColumn : this.rightColumn;
            MenuButton widget = option.getEnumFloat()
                    ? new MenuSlider(option.returnEnumOrdinal(), x, y,
                            this.columnWidth, this.rowHeight, option)
                    : new MenuOptionButton(option.returnEnumOrdinal(), x, y,
                            this.columnWidth, this.rowHeight, option);
            widget.entrance(0.06F + i * 0.02F);
            this.buttonList.add(widget);
            if (i % 2 == 1) {
                y += this.rowHeight + this.rowGap;
            }
        }
    }

    // --------------------------------------------------------------- drawing --

    /**
     * Pulled back and high, unlike the settings screen's low close framing — these
     * are opened from there, so a different distance and angle makes the move
     * between them read as the camera travelling rather than the panel swapping.
     */
    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.84F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.24F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.085F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.62F;
    }

    @Override
    protected int blackHolePose() {
        // Seen from below for the sound tab and above for chat, so even switching tabs
        // turns it a little.
        return activeTab == TAB_SOUND ? LensLibrary.POSE_BELOW : LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.52F * a),
                Draw.withAlpha(Theme.background, 0.30F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.gradientH(this.panelX2, this.panelY1, this.panelX2 + 64, this.panelY2,
                Draw.withAlpha(Theme.background, 0.45F * a),
                Draw.withAlpha(Theme.background, 0.0F));

        this.fontRendererObj.drawString(
                I18n.format("options.sounds.title", new Object[0]),
                this.panelX1 + PADDING, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, a));

        int ruleY = this.panelY1 + 56;
        Draw.rect(this.panelX1 + PADDING, ruleY, this.panelX2 - PADDING, ruleY + 1,
                Draw.fade(Theme.separator, a));
        int active = Math.min(activeTab, TABS.length - 1);
        Draw.rect(this.tabX[active], ruleY, this.tabX[active] + this.tabWidth[active], ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.9F * a));

        Draw.gradientH(this.panelX1 + PADDING, this.footerRuleY, this.panelX2 - PADDING,
                this.footerRuleY + 1,
                Draw.withAlpha(Theme.accent, 0.35F * a), Draw.withAlpha(Theme.accent, 0.0F));
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        if (button.id >= ID_TAB_BASE && button.id < ID_TAB_BASE + TABS.length) {
            activeTab = button.id - ID_TAB_BASE;
            relayout();
            return;
        }
        if (button instanceof MenuOptionButton) {
            ((MenuOptionButton) button).cycle();
            return;
        }
        if (button.id == ID_DONE) {
            this.settings.saveOptions();
            switchBack();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.settings.saveOptions();
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

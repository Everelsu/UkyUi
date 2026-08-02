package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatList;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Achievements and statistics, in this pack's language.
 *
 * Vanilla gives these two very different screens: achievements are a pannable tree
 * of icons you have to drag around to read, and statistics are three paged lists.
 * Both are the same kind of thing — a record of what you have done — so they share
 * one screen here with a tab each, and both are searchable, which the tree in
 * particular never was.
 *
 * <p>The achievement tree is deliberately not reproduced. Its layout carries no
 * information the parent link does not, and dragging a canvas to find out whether
 * you have tamed a wolf is worse than reading a list. Locked entries stay listed but
 * dimmed, so the list doubles as something to aim at.
 */
public class GuiProgressScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_TAB_BASE = 300;

    private static final int TAB_ACHIEVEMENTS = 0;
    private static final int TAB_STATS = 1;
    private static final String[] TABS = {"gui.achievements", "gui.stats"};

    private final StatFileWriter stats;
    /** Remembered across openings, like the settings tabs. */
    private static int activeTab = TAB_ACHIEVEMENTS;

    private final List<Achievement> achievements = new ArrayList<Achievement>();
    private final List<StatBase> statRows = new ArrayList<StatBase>();

    private GuiTextField search;
    private ProgressList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private final int[] tabX = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    private int unlocked;

    public GuiProgressScreen(GuiScreen parent, StatFileWriter stats) {
        super(parent);
        this.stats = stats;
    }

    /** Opens straight onto one tab, so the two pause-menu buttons still differ. */
    public GuiProgressScreen(GuiScreen parent, StatFileWriter stats, int tab) {
        this(parent, stats);
        activeTab = Math.max(0, Math.min(tab, TABS.length - 1));
    }

    public static int achievementsTab() {
        return TAB_ACHIEVEMENTS;
    }

    public static int statsTab() {
        return TAB_STATS;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.62F), 440);
        this.panelX1 = Math.max(10, (int) (this.width * 0.05F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        buildTabs();

        String previous = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(this.fontRendererObj,
                this.panelX1 + 15, this.panelY1 + 64, panelWidth - 30, 16);
        this.search.setMaxStringLength(48);
        this.search.setEnableBackgroundDrawing(false);
        this.search.setText(previous);
        this.search.setFocused(true);

        boolean cramped = this.height < 300;
        int doneHeight = cramped ? 20 : 24;
        int doneY = this.panelY2 - (cramped ? 10 : 20) - doneHeight;

        if (this.list == null) {
            this.list = new ProgressList();
        }
        int listTop = this.panelY1 + 86;
        this.list.setBounds(this.panelX1 + 15, listTop, panelWidth - 30,
                Math.max(40, doneY - 12 - listTop), 22);

        applyFilter();

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15, doneY,
                panelWidth - 30, doneHeight, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /** Same label-width layout as the settings tabs, for the same reason. */
    private void buildTabs() {
        int between = 18;
        int labelPad = 12;
        int x = this.panelX1 + 15;
        for (int i = 0; i < TABS.length; i++) {
            String label = I18n.format(TABS[i], new Object[0]);
            this.tabWidth[i] = this.fontRendererObj.getStringWidth(label) + label.length()
                    + labelPad * 2;
            this.tabX[i] = x;

            MenuButton tab = new MenuButton(ID_TAB_BASE + i, x, this.panelY1 + 30,
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

    @SuppressWarnings("unchecked")
    private void applyFilter() {
        String query = this.search.getText().trim().toLowerCase();
        this.achievements.clear();
        this.statRows.clear();
        this.unlocked = 0;

        if (activeTab == TAB_ACHIEVEMENTS) {
            List<Achievement> all = AchievementList.achievementList;
            for (int i = 0; i < all.size(); i++) {
                Achievement achievement = all.get(i);
                if (this.stats.hasAchievementUnlocked(achievement)) {
                    this.unlocked++;
                }
                if (query.isEmpty() || nameOf(achievement).toLowerCase().contains(query)
                        || descriptionOf(achievement).toLowerCase().contains(query)) {
                    this.achievements.add(achievement);
                }
            }
        } else {
            // General stats first, then the mined/crafted object stats. Item stats are
            // left out: there is one per item per action, which is thousands of rows
            // of mostly zeroes, and the search box is the better way to reach those.
            addStats(StatList.generalStats, query);
            addStats(StatList.objectMineStats, query);
        }
        this.list.setSelected(-1);
    }

    @SuppressWarnings("unchecked")
    private void addStats(List source, String query) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            Object entry = source.get(i);
            if (!(entry instanceof StatBase)) {
                continue;
            }
            StatBase stat = (StatBase) entry;
            if (query.isEmpty() || nameOf(stat).toLowerCase().contains(query)) {
                this.statRows.add(stat);
            }
        }
    }

    private static String nameOf(StatBase stat) {
        try {
            return stat.func_150951_e().getUnformattedText();
        } catch (Throwable t) {
            // A modded stat with a broken name must not take the screen down.
            return stat.statId;
        }
    }

    private static String descriptionOf(Achievement achievement) {
        try {
            String description = achievement.getDescription();
            return description == null ? "" : description;
        } catch (Throwable t) {
            return "";
        }
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.87F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.55F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.13F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.72F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.list.update(this.delta);
        drawGlassPanel();

        this.fontRendererObj.drawString(
                I18n.format(TABS[activeTab], new Object[0]).toUpperCase(),
                this.panelX1 + 15, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        // Achievement count, right-aligned against the panel edge.
        if (activeTab == TAB_ACHIEVEMENTS) {
            String tally = this.unlocked + " / " + AchievementList.achievementList.size();
            this.fontRendererObj.drawString(tally,
                    this.panelX2 - 15 - this.fontRendererObj.getStringWidth(tally),
                    this.panelY1 + 14, Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));
        }

        int ruleY = this.panelY1 + 54;
        Draw.rect(this.panelX1 + 15, ruleY, this.panelX2 - 15, ruleY + 1,
                Draw.fade(Theme.separator, this.fadeAlpha));
        int active = Math.min(activeTab, TABS.length - 1);
        Draw.rect(this.tabX[active], ruleY, this.tabX[active] + this.tabWidth[active], ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));

        drawSearchBox();
        this.list.draw(mouseX, mouseY, this.fadeAlpha);
    }

    /** Same glass treatment as the settings panel. */
    private void drawGlassPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.52F * a),
                Draw.withAlpha(Theme.background, 0.30F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a),
                Draw.withAlpha(Theme.accent, 0.0F));
        Draw.gradientH(this.panelX2, this.panelY1, this.panelX2 + 64, this.panelY2,
                Draw.withAlpha(Theme.background, 0.45F * a),
                Draw.withAlpha(Theme.background, 0.0F));
    }

    private void drawSearchBox() {
        int x1 = this.search.xPosition - 5;
        int x2 = this.search.xPosition + this.search.width + 5;
        int y1 = this.search.yPosition - 4;
        int y2 = this.search.yPosition + 12;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(Theme.surface, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2,
                Draw.withAlpha(this.search.isFocused() ? Theme.accent : Theme.separator,
                        this.fadeAlpha));
        if (this.search.getText().isEmpty()) {
            this.fontRendererObj.drawString(
                    I18n.format("uky.progress.search", new Object[0]),
                    this.search.xPosition, this.search.yPosition,
                    Draw.withAlpha(Theme.textDim, 0.5F * this.fadeAlpha));
        }
        this.search.drawTextBox();
    }

    private int rowTotal() {
        return activeTab == TAB_ACHIEVEMENTS ? this.achievements.size() : this.statRows.size();
    }

    private final class ProgressList extends ScrollList {
        @Override
        public int rowCount() {
            return rowTotal();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }
            if (activeTab == TAB_ACHIEVEMENTS) {
                drawAchievementRow(index, rowX, rowY, rowWidth, rowHeight, alpha);
            } else {
                drawStatRow(index, rowX, rowY, rowWidth, rowHeight, alpha);
            }
        }

        @Override
        protected void onRowClicked(int index) {
            // Nothing to open; these rows are a record, not a control.
        }
    }

    private void drawAchievementRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                                    float alpha) {
        Achievement achievement = this.achievements.get(index);
        boolean got = this.stats.hasAchievementUnlocked(achievement);
        boolean reachable = this.stats.canUnlockAchievement(achievement);

        if (got) {
            Draw.rect(rowX, rowY, rowX + 2, rowY + rowHeight,
                    Draw.withAlpha(Theme.accent, 0.9F * alpha));
        }

        drawItemIcon(achievement, rowX + 5, rowY + 3, got ? 1.0F : 0.35F);

        // Unlocked reads normally, reachable-but-not-yet is dimmer, and everything
        // still gated behind a parent is dimmest — so the list shows a frontier.
        int nameColour = got ? Theme.textHover : (reachable ? Theme.text : Theme.textDisabled);
        String name = fit(nameOf(achievement), rowWidth - 34);
        this.fontRendererObj.drawString(name, rowX + 26, rowY + 3,
                Draw.withAlpha(nameColour, alpha));

        String description = fit(
                descriptionOf(achievement), rowWidth - 34);
        this.fontRendererObj.drawString(description, rowX + 26, rowY + 13,
                Draw.withAlpha(Theme.textDim, (got ? 0.8F : 0.5F) * alpha));

        if (got) {
            Icons.check(rowX + rowWidth - 9, rowY + rowHeight / 2.0F, 8.0F,
                    Draw.withAlpha(Theme.accent, 0.95F * alpha));
        }
    }

    /**
     * The achievement's item, drawn with the game's own item renderer.
     *
     * Wrapped in lighting setup and a try/catch: a modded achievement can carry an
     * item whose renderer throws, and one bad entry must not take the list with it.
     */
    private void drawItemIcon(Achievement achievement, int x, int y, float brightness) {
        if (achievement.theItemStack == null) {
            return;
        }
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(brightness, brightness, brightness, 1.0F);
            this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj,
                    this.mc.getTextureManager(), achievement.theItemStack, x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable t) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawStatRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                             float alpha) {
        StatBase stat = this.statRows.get(index);
        int raw = this.stats.writeStat(stat);
        String value;
        try {
            value = stat.func_75968_a(raw);
        } catch (Throwable t) {
            value = String.valueOf(raw);
        }

        int valueWidth = this.fontRendererObj.getStringWidth(value);
        String name = fit(nameOf(stat),
                rowWidth - valueWidth - 24);
        int textY = rowY + (rowHeight - 8) / 2;

        // Untouched stats sit back so the ones with a number carry the list.
        boolean touched = raw > 0;
        this.fontRendererObj.drawString(name, rowX + 8, textY,
                Draw.withAlpha(touched ? Theme.text : Theme.textDisabled, alpha));
        this.fontRendererObj.drawString(value, rowX + rowWidth - 8 - valueWidth, textY,
                Draw.withAlpha(touched ? Theme.accent : Theme.textDim,
                        (touched ? 0.95F : 0.5F) * alpha));
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.search.mouseClicked(mouseX, mouseY, button);
        if (this.list.mouseClicked(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
        this.list.mouseDragged(mouseY);
        super.mouseClickMove(mouseX, mouseY, button, heldTime);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        this.list.mouseReleased();
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.list.mouseWheel(wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            switchBack();
            return;
        }
        if (keyCode == 200) {
            this.list.moveSelection(-1);
            return;
        }
        if (keyCode == 208) {
            this.list.moveSelection(1);
            return;
        }
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            applyFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.search.updateCursorCounter();
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button.id >= ID_TAB_BASE && button.id < ID_TAB_BASE + TABS.length) {
            activeTab = button.id - ID_TAB_BASE;
            // relayout, not initGui: the entrance fade must not restart.
            relayout();
            return;
        }
        if (button.id == ID_DONE) {
            switchBack();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

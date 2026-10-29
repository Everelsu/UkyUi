package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.ItemIcon;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import com.console.uky.compat.GuiButton;
import com.console.uky.compat.GuiTextField;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * dimmed, so the list doubles as something to aim at — and the ones still gated
 * behind another achievement say which one, which is the single piece the tree's
 * arrows were actually carrying.
 *
 * <p>Beside the search box are three filters, because "what have I not done yet" is
 * the question this screen exists to answer and searching cannot express it.
 *
 * <p>It is also the destination of an achievement link in chat: see
 * {@link #focusOn}, which is what makes a click there land on the right row rather
 * than merely on the right screen.
 */
public class GuiProgressScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_TAB_BASE = 300;
    private static final int ID_FILTER_BASE = 400;

    private static final int TAB_ACHIEVEMENTS = 0;
    private static final int TAB_STATS = 1;
    private static final String[] TABS = {"gui.advancements", "gui.stats"};

    /** Everything, only what has been earned, only what has not. */
    private static final int FILTER_ALL = 0;
    private static final int FILTER_DONE = 1;
    private static final int FILTER_LEFT = 2;
    private static final String[] FILTERS = {
        "uky.progress.filter.all", "uky.progress.filter.done", "uky.progress.filter.left"
    };

    /** Content width below which the filters cannot share a row with the search box. */
    private static final int FILTER_ROW_MIN_WIDTH = 330;

    /** How long the row a link landed on keeps announcing itself. */
    private static final float FOCUS_SECONDS = 2.4F;

    private final StatsCounter stats;
    private final ClientAdvancements advancements;
    /**
     * Progress by advancement, as the server last reported it. The client keeps its own
     * copy private and hands it out only to a listener, so this screen listens.
     */
    private final Map<AdvancementHolder, AdvancementProgress> progress =
            new HashMap<AdvancementHolder, AdvancementProgress>();
    /** Remembered across openings, like the settings tabs. */
    private static int activeTab = TAB_ACHIEVEMENTS;
    private static int activeFilter = FILTER_ALL;

    private final List<Entry> achievements = new ArrayList<Entry>();
    private final List<Stat<?>> statRows = new ArrayList<Stat<?>>();

    /**
     * Indentation per level of the chain, and how many levels of it are drawn.
     *
     * Capped because the depth is not bounded — a mod is free to hang an achievement
     * off an achievement off an achievement — and past three or four levels the rows
     * would be all margin and no name. Everything deeper simply sits at the last
     * level; the words on the second line still say what it needs.
     */
    private static final int INDENT = 9;
    private static final int MAX_INDENT_LEVELS = 3;

    /** Whether the current view is the tree or a flat result list. */
    private boolean showTree = true;

    private GuiTextField search;
    private ProgressList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private final int[] tabX = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    private int unlocked;
    private int total;

    /**
     * The achievement a link asked for, and when it was arrived at.
     *
     * Kept until the player does something of their own — types in the search box or
     * picks another row — because the layout is rebuilt on a tab switch and a resize,
     * and losing the highlight to a window drag would be its own small mystery.
     */
    private AdvancementNode focus;
    private float focusAt = -1.0F;

    public GuiProgressScreen(net.minecraft.client.gui.screens.Screen parent, int tab) {
        super(parent);
        this.stats = this.mc.player.getStats();
        this.advancements = this.mc.player.connection.getAdvancements();
        activeTab = Math.max(0, Math.min(tab, TABS.length - 1));
    }

    public static int achievementsTab() {
        return TAB_ACHIEVEMENTS;
    }

    public static int statsTab() {
        return TAB_STATS;
    }

    /**
     * Opens on this achievement: scrolled to it, picked out, and lit for a moment.
     *
     * The filter and the search box are cleared first. A link that landed on an empty
     * list because the last thing searched for was something else would be a link
     * that does not work, and the player did not choose either of those settings on
     * this trip.
     */
    public void focusOn(AdvancementNode achievement) {
        this.focus = achievement;
        activeTab = TAB_ACHIEVEMENTS;
        activeFilter = FILTER_ALL;
        if (this.search != null) {
            this.search.setText("");
        }
    }

    // ---------------------------------------------------------------- layout --

    /** Keeps {@link #progress} current and the tree rebuilt as the server changes it. */
    private final ClientAdvancements.Listener listener = new ClientAdvancements.Listener() {
        @Override
        public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress value) {
            GuiProgressScreen.this.progress.put(node.holder(), value);
            changed();
        }

        @Override
        public void onSelectedTabChanged(AdvancementHolder tab) {
        }

        @Override
        public void onAddAdvancementRoot(AdvancementNode node) {
            changed();
        }

        @Override
        public void onRemoveAdvancementRoot(AdvancementNode node) {
            changed();
        }

        @Override
        public void onAddAdvancementTask(AdvancementNode node) {
            changed();
        }

        @Override
        public void onRemoveAdvancementTask(AdvancementNode node) {
            changed();
        }

        @Override
        public void onAdvancementsCleared() {
            GuiProgressScreen.this.progress.clear();
            changed();
        }
    };

    private void changed() {
        if (this.list != null && this.search != null) {
            applyFilter();
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.advancements.setListener(this.listener);
        // Stats are the server's; vanilla's screen asks for them on opening, and so does this.
        this.mc.getConnection().send(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.REQUEST_STATS));
        com.console.uky.compat.Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        this.advancements.setListener(null);
        com.console.uky.compat.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    /** Y of the search box's text, which everything below it is measured from. */
    private int searchY;
    /** Y of the filter row, whether or not it shares a line with the search box. */
    private int filterY;

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.62F), 440);
        this.panelX1 = Math.max(10, (int) (this.width * 0.05F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        buildTabs();

        int contentWidth = panelWidth - 30;
        boolean showFilters = activeTab == TAB_ACHIEVEMENTS;
        // Beside the search box where there is room for both, under it where there is
        // not. A row of chips squeezed to nothing is worse than one extra line.
        boolean filtersBeside = showFilters && contentWidth >= FILTER_ROW_MIN_WIDTH;

        this.searchY = this.panelY1 + 64;
        this.filterY = filtersBeside ? this.searchY - 4 : this.searchY + 18;

        int filtersWidth = showFilters ? buildFilters(filtersBeside, contentWidth) : 0;
        int searchWidth = filtersBeside ? contentWidth - filtersWidth - 12 : contentWidth;

        String previous = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(this.fontRendererObj,
                this.panelX1 + 15, this.searchY, searchWidth, 16);
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
        int listTop = (showFilters && !filtersBeside ? this.filterY + 18 : this.searchY + 22) + 4;
        this.list.setBounds(this.panelX1 + 15, listTop, contentWidth,
                Math.max(40, doneY - 12 - listTop), 22);

        applyFilter();

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15, doneY,
                contentWidth, doneHeight, I18n.get("gui.done", new Object[0]),
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
            String label = I18n.get(TABS[i], new Object[0]);
            this.tabWidth[i] = this.fontRendererObj.getStringWidth(label) + label.length()
                    + labelPad * 2;
            this.tabX[i] = x;

            MenuButton tab = new MenuButton(ID_TAB_BASE + i, x, this.panelY1 + 30,
                    this.tabWidth[i], 18, label, MenuButton.Style.GHOST);
            tab.align(MenuButton.Align.LEFT).plain().steady();
            tab.entrance(0.02F + i * 0.03F);
            if (i == activeTab) {
                tab.selected();
            }
            this.buttonList.add(tab);
            x += this.tabWidth[i] + between;
        }
    }

    /**
     * The three filter chips, laid out to their own labels.
     *
     * @return the width the row takes, so the search box can have the rest
     */
    private int buildFilters(boolean beside, int contentWidth) {
        int gap = 6;
        int[] widths = new int[FILTERS.length];
        int total = 0;
        for (int i = 0; i < FILTERS.length; i++) {
            String label = I18n.get(FILTERS[i], new Object[0]);
            widths[i] = this.fontRendererObj.getStringWidth(label) + 16;
            total += widths[i];
        }
        total += gap * (FILTERS.length - 1);

        // Right-aligned beside the search box, left-aligned on a row of its own —
        // each following whatever it is sharing the line with.
        int x = beside ? this.panelX2 - 15 - total : this.panelX1 + 15;
        for (int i = 0; i < FILTERS.length; i++) {
            MenuButton chip = new MenuButton(ID_FILTER_BASE + i, x, this.filterY,
                    widths[i], 16, I18n.get(FILTERS[i], new Object[0]),
                    MenuButton.Style.GHOST);
            chip.plain().steady();
            chip.entrance(0.04F + i * 0.02F);
            if (i == activeFilter) {
                chip.selected();
            }
            this.buttonList.add(chip);
            x += widths[i] + gap;
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private void applyFilter() {
        String query = this.search == null ? "" : this.search.getText().trim().toLowerCase();
        this.achievements.clear();
        this.statRows.clear();
        this.unlocked = 0;

        if (activeTab == TAB_ACHIEVEMENTS) {
            List<Entry> tree = tree();
            this.total = tree.size();
            // The tree only means anything while the whole of it is on screen. A
            // search or a filter takes rows out of the middle of it, and lines drawn
            // between what is left would join achievements that have nothing to do
            // with each other — so those views are flat, and every row carries the
            // name of what it needs in words instead.
            this.showTree = query.isEmpty() && activeFilter == FILTER_ALL;
            for (int i = 0; i < tree.size(); i++) {
                Entry entry = tree.get(i);
                if (entry.got(this.progress)) {
                    this.unlocked++;
                }
                if (activeFilter == FILTER_DONE && !entry.got(this.progress)) {
                    continue;
                }
                if (activeFilter == FILTER_LEFT && entry.got(this.progress)) {
                    continue;
                }
                if (query.isEmpty() || nameOf(entry.achievement).toLowerCase().contains(query)
                        || descriptionOf(entry.achievement).toLowerCase().contains(query)) {
                    this.achievements.add(entry);
                }
            }
        } else {
            // General stats first, then the mined/crafted object stats. Item stats are
            // left out: there is one per item per action, which is thousands of rows
            // of mostly zeroes, and the search box is the better way to reach those.
            addStats(query);
        }
        this.list.setSelected(-1);
        applyFocus();
    }

    /**
     * Puts the list on the row a link asked for.
     *
     * Runs after every filter pass rather than once on opening: the pass is what
     * builds the row indices, and they move whenever the tab, the filter or the
     * search changes.
     */
    private void applyFocus() {
        if (this.focus == null || activeTab != TAB_ACHIEVEMENTS) {
            return;
        }
        int index = -1;
        for (int i = 0; i < this.achievements.size(); i++) {
            if (this.achievements.get(i).achievement == this.focus) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        this.list.setSelected(index);
        this.list.scrollToCenter(index);
        if (this.focusAt < 0.0F) {
            this.focusAt = this.elapsed;
        }
    }

    /** Anything the player does themselves ends the highlight. */
    private void clearHighlight() {
        this.focus = null;
        this.focusAt = -1.0F;
    }

    /**
     * The general stats, every one, then each block mined at least once. 1.7.10 listed
     * every block there was; with a modded registry that is thousands of zeroes.
     */
    private void addStats(String query) {
        for (ResourceLocation id : BuiltInRegistries.CUSTOM_STAT) {
            addStat(Stats.CUSTOM.get(id), query);
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            Stat<Block> stat = Stats.BLOCK_MINED.get(block);
            if (this.stats.getValue(stat) > 0) {
                addStat(stat, query);
            }
        }
    }

    private void addStat(Stat<?> stat, String query) {
        if (query.isEmpty() || nameOf(stat).toLowerCase().contains(query)) {
            this.statRows.add(stat);
        }
    }

    private static final class Entry {

        final AdvancementNode achievement;
        /** How many achievements have to be earned before this one is reachable. */
        final int depth;
        /** True when nothing else in the tree shares this parent below it. */
        final boolean last;
        /** For each ancestor level, whether its branch continues past this row. */
        final int continuing;
        /** How many achievements this one directly opens up. */
        int children;

        Entry(AdvancementNode achievement, int depth, boolean last, int continuing) {
            this.achievement = achievement;
            this.depth = depth;
            this.last = last;
            this.continuing = continuing;
        }

        boolean got(Map<AdvancementHolder, AdvancementProgress> progress) {
            return isDone(progress, this.achievement);
        }
    }

    /**
     * Every achievement, in the order its dependencies imply.
     *
     * Registration order is what {@code AchievementList} keeps, and it is nearly but
     * not quite the tree: mods add their own pages at the end, and nothing says a
     * child follows its parent. So the parents are walked instead — depth first, each
     * child immediately after the achievement it needs, which is the order that makes
     * indentation readable.
     *
     * <p>Rebuilt per filter pass rather than cached. It is a few hundred entries and
     * two passes over them, against a screen that is already drawing item models; and
     * a cache would have to notice a mod registering an achievement late, which some
     * do.
     */
    /**
     * Every advancement with something to show, as a tree: each tab's root, and under
     * it what it leads to. Nodes with no display — recipe unlocks, invisible triggers —
     * are skipped, and their children hang from whatever is above them.
     */
    private List<Entry> tree() {
        List<Entry> out = new ArrayList<Entry>();
        java.util.Set<AdvancementNode> visited = new java.util.HashSet<AdvancementNode>();
        List<AdvancementNode> roots = new ArrayList<AdvancementNode>();
        for (AdvancementNode root : this.advancements.getTree().roots()) {
            if (shown(root)) {
                roots.add(root);
            }
        }
        for (int i = 0; i < roots.size(); i++) {
            walk(roots.get(i), 0, i == roots.size() - 1, 0, visited, out);
        }
        return out;
    }

    private static boolean shown(AdvancementNode node) {
        return node.advancement().display().isPresent();
    }

    /** The node's children, with displayless ones replaced by their own children. */
    private static List<AdvancementNode> shownChildren(AdvancementNode node) {
        List<AdvancementNode> out = new ArrayList<AdvancementNode>();
        for (AdvancementNode child : node.children()) {
            if (shown(child)) {
                out.add(child);
            } else {
                out.addAll(shownChildren(child));
            }
        }
        return out;
    }

    /** The nearest ancestor with a display, or null. */
    private static AdvancementNode shownParent(AdvancementNode node) {
        AdvancementNode parent = node.parent();
        while (parent != null && !shown(parent)) {
            parent = parent.parent();
        }
        return parent;
    }

    private static boolean isDone(Map<AdvancementHolder, AdvancementProgress> progress,
                                  AdvancementNode node) {
        AdvancementProgress value = progress.get(node.holder());
        return value != null && value.isDone();
    }

    private boolean isDone(AdvancementNode node) {
        return isDone(this.progress, node);
    }

    private void walk(AdvancementNode achievement, int depth, boolean last, int continuing,
                      java.util.Set<AdvancementNode> visited, List<Entry> out) {
        if (!visited.add(achievement)) {
            return;
        }
        Entry entry = new Entry(achievement, depth, last, continuing);
        out.add(entry);

        List<AdvancementNode> mine = shownChildren(achievement);
        if (mine == null || mine.isEmpty()) {
            return;
        }
        entry.children = mine.size();
        // A branch that is not the last of its siblings keeps a line running down the
        // rows underneath it; that is what this bit records for everything below.
        int nested = last ? continuing : continuing | (1 << depth);
        for (int i = 0; i < mine.size(); i++) {
            walk(mine.get(i), depth + 1, i == mine.size() - 1, nested, visited, out);
        }
    }

    private static DisplayInfo display(AdvancementNode node) {
        return node.advancement().display().orElse(null);
    }

    private static String nameOf(AdvancementNode node) {
        DisplayInfo display = display(node);
        return display == null ? node.holder().id().toString() : display.getTitle().getString();
    }

    private static String nameOf(Stat<?> stat) {
        Object value = stat.getValue();
        if (value instanceof ResourceLocation) {
            ResourceLocation id = (ResourceLocation) value;
            return Component.translatable("stat." + id.toString().replace(':', '.')).getString();
        }
        if (value instanceof Block) {
            return ((Block) value).getName().getString();
        }
        return stat.getName();
    }

    private static String descriptionOf(AdvancementNode node) {
        DisplayInfo display = display(node);
        return display == null ? "" : display.getDescription().getString();
    }

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
                I18n.get(TABS[activeTab], new Object[0]).toUpperCase(),
                this.panelX1 + 15, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        int ruleY = this.panelY1 + 54;
        Draw.rect(this.panelX1 + 15, ruleY, this.panelX2 - 15, ruleY + 1,
                Draw.fade(Theme.separator, this.fadeAlpha));
        int active = Math.min(activeTab, TABS.length - 1);
        Draw.rect(this.tabX[active], ruleY, this.tabX[active] + this.tabWidth[active], ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));

        if (activeTab == TAB_ACHIEVEMENTS) {
            drawTally(ruleY);
        }

        drawSearchBox();
        this.list.draw(mouseX, mouseY, this.fadeAlpha);
    }

    /**
     * How much of the whole thing is done, as a number and as a line.
     *
     * The line is drawn on the rule under the tabs rather than on a bar of its own:
     * the rule is already the full width of the content, it already means "everything
     * above this belongs to everything below it", and a completion bar is the same
     * statement about the same width.
     */
    private void drawTally(int ruleY) {
        String tally = this.unlocked + " / " + this.total;
        int tallyWidth = this.fontRendererObj.getStringWidth(tally);
        this.fontRendererObj.drawString(tally, this.panelX2 - 15 - tallyWidth,
                this.panelY1 + 14, Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));

        if (this.total <= 0) {
            return;
        }
        // Eased in with the screen, so it fills rather than simply being at a value.
        float done = this.unlocked / (float) this.total
                * Ease.outCubic(this.elapsed / 0.7F);
        int left = this.panelX1 + 15;
        int right = this.panelX2 - 15;
        float end = left + (right - left) * done;
        Draw.gradientH(left, ruleY, end, ruleY + 1,
                Draw.withAlpha(Theme.accentAlt, 0.85F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.95F * this.fadeAlpha));
        if (done > 0.002F) {
            Draw.rect(end - 1.0F, ruleY - 1.0F, end, ruleY + 2.0F,
                    Draw.withAlpha(Theme.textHover, 0.7F * this.fadeAlpha));
        }
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
                    I18n.get("uky.progress.search", new Object[0]),
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
            // Nothing to open; these rows are a record, not a control. Clicking one
            // is still the player taking over from the link that brought them here.
            clearHighlight();
        }
    }

    private void drawAchievementRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                                    float alpha) {
        Entry entry = this.achievements.get(index);
        AdvancementNode achievement = entry.achievement;
        AdvancementNode parent = shownParent(achievement);
        boolean got = isDone(achievement);
        boolean reachable = got || parent == null || isDone(parent);

        drawFocusHighlight(achievement, rowX, rowY, rowWidth, rowHeight, alpha);

        if (got) {
            Draw.rect(rowX, rowY, rowX + 2, rowY + rowHeight,
                    Draw.withAlpha(Theme.accent, 0.9F * alpha));
        }

        int indent = this.showTree ? Math.min(entry.depth, MAX_INDENT_LEVELS) * INDENT : 0;
        if (indent > 0 || (this.showTree && entry.depth > 0)) {
            drawTreeLines(entry, rowX, rowY, rowHeight, alpha);
        }

        drawItemIcon(achievement, rowX + 5 + indent, rowY + 3, got ? 1.0F : 0.35F);

        // Unlocked reads normally, reachable-but-not-yet is dimmer, and everything
        // still gated behind a parent is dimmest — so the list shows a frontier.
        int nameColour = got ? Theme.textHover : (reachable ? Theme.text : Theme.textDisabled);
        int textX = rowX + 26 + indent;
        int textRight = rowWidth - 34 - indent;
        String name = fit(nameOf(achievement), textRight);
        int nameWidth = this.fontRendererObj.getStringWidth(name);
        this.fontRendererObj.drawString(name, textX, rowY + 3,
                Draw.withAlpha(nameColour, alpha));

        int badgeX = textX + nameWidth + 7;

        // The hardest ones are marked, which is the whole of what vanilla's spiked
        // frame said and the only thing its tree drew that this list did not.
        if (isSpecial(achievement)) {
            Icons.star(badgeX, rowY + 7.0F, 7.0F,
                    Draw.withAlpha(Theme.accentAlt, (got ? 0.95F : 0.5F) * alpha));
            badgeX += 11;
        }

        // How many more this one is holding up. It is the other half of the same
        // question — the row above says what you need, this says what needing it is
        // worth — and it is the reason to go and do a locked one first.
        if (entry.children > 0) {
            String badge = "+" + entry.children;
            this.fontRendererObj.drawString(badge, badgeX, rowY + 3,
                    Draw.withAlpha(got ? Theme.accent : Theme.textDim, 0.55F * alpha));
        }

        // What it was for, or — while it is still locked behind another one — which
        // one. Said in words as well as drawn, because the search and the filters flatten
        // the tree and the words are all that is left there.
        String second;
        float secondAlpha;
        if (!reachable) {
            second = I18n.get("uky.progress.requires", new Object[0])
                    + " " + nameOf(parent);
            secondAlpha = 0.45F;
        } else {
            second = descriptionOf(achievement);
            secondAlpha = got ? 0.8F : 0.5F;
        }
        this.fontRendererObj.drawString(fit(second, textRight), textX, rowY + 13,
                Draw.withAlpha(Theme.textDim, secondAlpha * alpha));

        if (got) {
            Icons.check(rowX + rowWidth - 9, rowY + rowHeight / 2.0F, 8.0F,
                    Draw.withAlpha(Theme.accent, 0.95F * alpha));
        }
    }

    /**
     * The elbow into this row, and the branches passing it on their way further down.
     *
     * Drawn rather than written because it has to be read at a glance across a whole
     * screen of rows: the line says "this one is under that one" without anybody
     * having to compare two names. Gold where the achievement above it is already
     * earned — so an open path is visibly open, and the point where the tree stops
     * being gold is exactly where there is work to do.
     */
    private void drawTreeLines(Entry entry, int rowX, int rowY, int rowHeight, float alpha) {
        float middle = rowY + rowHeight * 0.5F;
        AdvancementNode parent = shownParent(entry.achievement);
        boolean parentDone = parent != null && isDone(parent);
        int colour = Draw.withAlpha(parentDone ? Theme.accent : Theme.textDim,
                (parentDone ? 0.45F : 0.30F) * alpha);

        // The branches of everything this row sits under, still running past it.
        for (int level = 0; level < Math.min(entry.depth, MAX_INDENT_LEVELS); level++) {
            if ((entry.continuing & (1 << level)) == 0) {
                continue;
            }
            float x = rowX + 5 + level * INDENT + 6;
            Draw.rect(x, rowY, x + 1, rowY + rowHeight,
                    Draw.withAlpha(Theme.textDim, 0.18F * alpha));
        }

        int level = Math.min(entry.depth, MAX_INDENT_LEVELS) - 1;
        if (level < 0) {
            return;
        }
        float x = rowX + 5 + level * INDENT + 6;
        // Down to the middle and no further when this is the last child, all the way
        // through when it is not: the difference between a corner and a tee.
        Draw.rect(x, rowY, x + 1, entry.last ? middle : rowY + rowHeight, colour);
        Draw.rect(x, middle - 1, x + INDENT - 3, middle, colour);
    }

    /**
     * The row a chat link landed on, announcing itself and then letting go.
     *
     * Three pulses over a couple of seconds rather than a permanent selection: it has
     * to be found by an eye that was looking at the chat a moment ago, and then it has
     * to stop being the loudest thing on a screen the player is now reading.
     */
    private void drawFocusHighlight(AdvancementNode achievement, int rowX, int rowY, int rowWidth,
                                    int rowHeight, float alpha) {
        if (this.focus != achievement || this.focusAt < 0.0F) {
            return;
        }
        float age = this.elapsed - this.focusAt;
        if (age > FOCUS_SECONDS) {
            return;
        }
        float fade = 1.0F - age / FOCUS_SECONDS;
        float pulse = 0.35F + 0.65F * Math.abs((float) Math.sin(age * 4.2F));
        float strength = fade * pulse * alpha;

        Draw.gradientH(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                Draw.withAlpha(Theme.accent, 0.22F * strength),
                Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(rowX, rowY, rowX + rowWidth, rowY + rowHeight, 1.0F,
                Draw.withAlpha(Theme.accent, 0.75F * strength));
    }

    /** Challenges, which is what 1.7.10 called special. */
    private static boolean isSpecial(AdvancementNode achievement) {
        DisplayInfo display = display(achievement);
        return display != null && display.getType() == AdvancementType.CHALLENGE;
    }

    /**
     * The achievement's item.
     *
     * Through {@link ItemIcon} rather than straight to {@code RenderItem}: a block
     * drawn in one of these rows is real geometry, and it needs back-face culling
     * turned back on to come out the right way round. It was not, so every block in
     * this list was drawn inside out.
     */
    private void drawItemIcon(AdvancementNode achievement, int x, int y, float brightness) {
        DisplayInfo display = display(achievement);
        ItemIcon.draw(display == null ? ItemStack.EMPTY : display.getIcon(),
                x + ItemIcon.SIZE / 2.0F, y + ItemIcon.SIZE / 2.0F, brightness);
    }

    private void drawStatRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                             float alpha) {
        Stat<?> stat = this.statRows.get(index);
        int raw = this.stats.getValue(stat);
        String value = stat.format(raw);

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
        int wheel = com.console.uky.compat.Mouse.getEventDWheel();
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
            clearHighlight();
            this.list.moveSelection(-1);
            return;
        }
        if (keyCode == 208) {
            clearHighlight();
            this.list.moveSelection(1);
            return;
        }
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            clearHighlight();
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
        if (button.id >= ID_FILTER_BASE && button.id < ID_FILTER_BASE + FILTERS.length) {
            activeFilter = button.id - ID_FILTER_BASE;
            clearHighlight();
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

package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
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
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancementManager;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.stats.StatList;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Advancements and statistics, in this pack's language.
 *
 * Vanilla gives these two very different screens: advancements are a pannable tree
 * of icons you have to drag around to read, and statistics are three paged lists.
 * Both are the same kind of thing — a record of what you have done — so they share
 * one screen here with a tab each, and both are searchable, which the tree in
 * particular never was.
 *
 * <p>The advancement tree is deliberately not reproduced. Its layout carries no
 * information the parent link does not, and dragging a canvas to find out whether
 * you have tamed a wolf is worse than reading a list. Locked entries stay listed but
 * dimmed, so the list doubles as something to aim at.
 *
 * <p>This tab read achievements on 1.7.10. 1.12 deleted that system outright and
 * replaced it with advancements, so the source of the list changed even though what
 * the screen shows did not: a flat, searchable record with the reached entries lit.
 * The one thing that had no direct equivalent is per-entry progress, which
 * advancements have and achievements did not, so partially-completed entries now say
 * how far along they are.
 */
public class GuiProgressScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_TAB_BASE = 300;

    private static final int TAB_ADVANCEMENTS = 0;
    private static final int TAB_STATS = 1;
    private static final String[] TABS = {"gui.advancements", "gui.stats"};

    private final StatisticsManager stats;
    /** Remembered across openings, like the settings tabs. */
    private static int activeTab = TAB_ADVANCEMENTS;

    private final List<Advancement> advancements = new ArrayList<Advancement>();
    private final List<StatBase> statRows = new ArrayList<StatBase>();
    /** Every displayable advancement, before the search box narrows it. */
    private int advancementTotal;

    private GuiTextField search;
    private ProgressList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private final int[] tabX = new int[TABS.length];
    private final int[] tabWidth = new int[TABS.length];

    private int unlocked;

    public GuiProgressScreen(GuiScreen parent, StatisticsManager stats) {
        super(parent);
        this.stats = stats;
    }

    /** Opens straight onto one tab, so the two pause-menu buttons still differ. */
    public GuiProgressScreen(GuiScreen parent, StatisticsManager stats, int tab) {
        this(parent, stats);
        activeTab = Math.max(0, Math.min(tab, TABS.length - 1));
    }

    public static int advancementsTab() {
        return TAB_ADVANCEMENTS;
    }

    public static int statsTab() {
        return TAB_STATS;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        requestStats();
    }

    /**
     * Asks the server for this player's statistics.
     *
     * Without this every row reads zero, which is exactly what it looked like. A
     * client does not keep its own tally: {@code StatisticsManager} on this side is an
     * empty shell until the server is asked to fill it, and it answers with one
     * {@code SPacketStatistics} that the vanilla handler unpacks into the same object
     * this screen already holds. Vanilla's own statistics screen sends this from its
     * {@code initGui} for the same reason.
     *
     * <p>Nothing waits for the answer. The rows are built from {@link StatList}, which
     * is always present, and each one reads its number afresh every frame — so the
     * values simply appear when the packet lands, a tick or two later.
     */
    private void requestStats() {
        NetHandlerPlayClient connection = this.mc.getConnection();
        if (connection != null) {
            connection.sendPacket(
                    new CPacketClientStatus(CPacketClientStatus.State.REQUEST_STATS));
        }
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
        this.search = new GuiTextField(0, this.fontRenderer,
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
            this.tabWidth[i] = this.fontRenderer.getStringWidth(label) + label.length()
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

    private void applyFilter() {
        String query = this.search.getText().trim().toLowerCase();
        this.advancements.clear();
        this.statRows.clear();
        this.unlocked = 0;
        this.advancementTotal = 0;

        if (activeTab == TAB_ADVANCEMENTS) {
            for (Advancement advancement : allAdvancements()) {
                // Advancements with no display block are structural — the invisible
                // roots a datapack hangs a tree off, and the hidden helpers that only
                // exist to fire a criterion. Vanilla's own screen never draws them.
                if (advancement.getDisplay() == null) {
                    continue;
                }
                this.advancementTotal++;
                if (isDone(advancement)) {
                    this.unlocked++;
                }
                if (query.isEmpty() || nameOf(advancement).toLowerCase().contains(query)
                        || descriptionOf(advancement).toLowerCase().contains(query)) {
                    this.advancements.add(advancement);
                }
            }
        } else {
            // General stats first, then the mined-block stats. Item stats are left
            // out: there is one per item per action, which is thousands of rows of
            // mostly zeroes, and the search box is the better way to reach those.
            addStats(StatList.BASIC_STATS, query);
            addStats(StatList.MINE_BLOCK_STATS, query);
        }
        this.list.setSelected(-1);
    }

    private void addStats(List<? extends StatBase> source, String query) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            StatBase stat = source.get(i);
            if (stat == null) {
                continue;
            }
            if (query.isEmpty() || nameOf(stat).toLowerCase().contains(query)) {
                this.statRows.add(stat);
            }
        }
    }

    // ---------------------------------------------------------- advancements --

    /**
     * Every advancement there is to list.
     *
     * The integrated server first, and that is the whole point of this method rather
     * than a one-line getter. A client is only ever told about the advancements the
     * server considers <em>visible</em> — done, or within two steps of something done
     * — so on a fresh world the client's own list is nearly empty and on a brand new
     * one it is empty outright. 1.7.10 had no such problem: {@code AchievementList}
     * was a complete static table that every client held in full, which is what let
     * this screen list the locked entries as something to aim at.
     *
     * <p>In singleplayer the server is in this JVM and its catalogue is complete, so
     * that is what gets listed. On a dedicated server there is no way back to the full
     * set — vanilla's own advancement screen shows exactly the same visible subset —
     * so the client's list is the honest answer there.
     */
    private Iterable<Advancement> allAdvancements() {
        Minecraft mc = Minecraft.getMinecraft();
        MinecraftServer server = mc.getIntegratedServer();
        if (server != null) {
            try {
                Iterable<Advancement> all = server.getAdvancementManager().getAdvancements();
                if (all != null && all.iterator().hasNext()) {
                    return all;
                }
            } catch (Throwable t) {
                // Reading across to the server thread is only ever an optimisation
                // here; the client's own list below is always safe.
                UkyUI.LOGGER.warn("Could not read the server's advancement list", t);
            }
        }
        ClientAdvancementManager manager = advancementManager();
        return manager == null
                ? Collections.<Advancement>emptyList()
                : manager.getAdvancementList().getAdvancements();
    }

    private static ClientAdvancementManager advancementManager() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player == null || mc.player.connection == null
                ? null
                : mc.player.connection.getAdvancementManager();
    }

    /**
     * How far along an advancement is, or null if the client has not been told.
     *
     * {@code ClientAdvancementManager} keeps this in a private map and offers no
     * getter — the only supported way at it is to register as its single listener,
     * and doing that would evict whoever else holds the slot, vanilla's own
     * advancement screen included. So the map is read directly, located by type the
     * same way the rest of this mod locates vanilla fields, since the MCP name only
     * exists in a development environment.
     */
    private static Field progressMapField;
    private static boolean progressMapResolved;

    @SuppressWarnings("unchecked")
    private static AdvancementProgress progressOf(Advancement advancement) {
        ClientAdvancementManager manager = advancementManager();
        if (manager == null) {
            return null;
        }
        if (!progressMapResolved) {
            progressMapResolved = true;
            for (Field candidate : ClientAdvancementManager.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(candidate.getType())) {
                    candidate.setAccessible(true);
                    progressMapField = candidate;
                    break;
                }
            }
        }
        if (progressMapField == null) {
            return null;
        }
        try {
            Map<Advancement, AdvancementProgress> progress =
                    (Map<Advancement, AdvancementProgress>) progressMapField.get(manager);
            return progress == null ? null : progress.get(advancement);
        } catch (IllegalAccessException e) {
            // Everything still lists; nothing shows as reached.
            return null;
        }
    }

    private static boolean isDone(Advancement advancement) {
        AdvancementProgress progress = progressOf(advancement);
        return progress != null && progress.isDone();
    }

    /**
     * Whether this entry is the next thing that could be reached, rather than one
     * gated behind something else. A root has nothing in front of it.
     */
    private static boolean isReachable(Advancement advancement) {
        Advancement parent = advancement.getParent();
        return parent == null || isDone(parent);
    }

    private static String nameOf(StatBase stat) {
        try {
            return stat.getStatName().getUnformattedText();
        } catch (Throwable t) {
            // A modded stat with a broken name must not take the screen down.
            return stat.statId;
        }
    }

    private static String nameOf(Advancement advancement) {
        try {
            DisplayInfo display = advancement.getDisplay();
            return display == null ? advancement.getId().toString()
                    : display.getTitle().getUnformattedText();
        } catch (Throwable t) {
            return String.valueOf(advancement.getId());
        }
    }

    private static String descriptionOf(Advancement advancement) {
        try {
            DisplayInfo display = advancement.getDisplay();
            return display == null ? "" : display.getDescription().getUnformattedText();
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

        this.fontRenderer.drawString(
                I18n.format(TABS[activeTab], new Object[0]).toUpperCase(),
                this.panelX1 + 15, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        // Advancement count, right-aligned against the panel edge.
        if (activeTab == TAB_ADVANCEMENTS) {
            String tally = this.unlocked + " / " + this.advancementTotal;
            this.fontRenderer.drawString(tally,
                    this.panelX2 - 15 - this.fontRenderer.getStringWidth(tally),
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
        drawEmptyState();
    }

    /**
     * Says so when there is nothing to list.
     *
     * An empty list and a list that failed to load look identical — both are a blank
     * panel — and on this screen that is a real risk rather than a theoretical one:
     * advancements arrive over the network, so the list is genuinely empty until the
     * server has sent them.
     */
    private void drawEmptyState() {
        if (rowTotal() > 0) {
            return;
        }
        this.drawCenteredString(this.fontRenderer,
                I18n.format("uky.progress.none", new Object[0]),
                (this.panelX1 + this.panelX2) / 2, this.list.rowTop(0) + 12,
                Draw.withAlpha(Theme.textDim, 0.7F * this.fadeAlpha));
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
        int x1 = this.search.x - 5;
        int x2 = this.search.x + this.search.width + 5;
        int y1 = this.search.y - 4;
        int y2 = this.search.y + 12;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(Theme.surface, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2,
                Draw.withAlpha(this.search.isFocused() ? Theme.accent : Theme.separator,
                        this.fadeAlpha));
        if (this.search.getText().isEmpty()) {
            this.fontRenderer.drawString(
                    I18n.format("uky.progress.search", new Object[0]),
                    this.search.x, this.search.y,
                    Draw.withAlpha(Theme.textDim, 0.5F * this.fadeAlpha));
        }
        this.search.drawTextBox();
    }

    private int rowTotal() {
        return activeTab == TAB_ADVANCEMENTS ? this.advancements.size() : this.statRows.size();
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
            if (activeTab == TAB_ADVANCEMENTS) {
                drawAdvancementRow(index, rowX, rowY, rowWidth, rowHeight, alpha);
            } else {
                drawStatRow(index, rowX, rowY, rowWidth, rowHeight, alpha);
            }
        }

        @Override
        protected void onRowClicked(int index) {
            // Nothing to open; these rows are a record, not a control.
        }
    }

    private void drawAdvancementRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                                    float alpha) {
        Advancement advancement = this.advancements.get(index);
        AdvancementProgress progress = progressOf(advancement);
        boolean got = progress != null && progress.isDone();
        boolean reachable = isReachable(advancement);

        if (got) {
            Draw.rect(rowX, rowY, rowX + 2, rowY + rowHeight,
                    Draw.withAlpha(Theme.accent, 0.9F * alpha));
        }

        drawItemIcon(advancement, rowX + 5, rowY + 3, got ? 1.0F : 0.35F);

        // Reached reads normally, reachable-but-not-yet is dimmer, and everything
        // still gated behind a parent is dimmest — so the list shows a frontier.
        int nameColour = got ? Theme.textHover : (reachable ? Theme.text : Theme.textDisabled);

        // Something part-done says so on the right, and the name gives up the room
        // for it. Advancements can be several criteria wide, which achievements never
        // were, and "3/7" is the one thing this list can say that the old one could not.
        String tally = partialTally(progress);
        int tallyWidth = tally == null ? 0 : this.fontRenderer.getStringWidth(tally) + 6;
        if (tally != null) {
            this.fontRenderer.drawString(tally, rowX + rowWidth - 8 - tallyWidth + 6, rowY + 3,
                    Draw.withAlpha(Theme.accent, 0.75F * alpha));
        }

        int room = rowWidth - 34 - tallyWidth;
        String name = fit(nameOf(advancement), room);
        this.fontRenderer.drawString(name, rowX + 26, rowY + 3,
                Draw.withAlpha(nameColour, alpha));

        String description = fit(descriptionOf(advancement), rowWidth - 34);
        this.fontRenderer.drawString(description, rowX + 26, rowY + 13,
                Draw.withAlpha(Theme.textDim, (got ? 0.8F : 0.5F) * alpha));

        if (got) {
            Icons.check(rowX + rowWidth - 9, rowY + rowHeight / 2.0F, 8.0F,
                    Draw.withAlpha(Theme.accent, 0.95F * alpha));
        }
    }

    /** "3/7" for a multi-criterion entry part-way done, or null when there is nothing to say. */
    private static String partialTally(AdvancementProgress progress) {
        if (progress == null || progress.isDone() || !progress.hasProgress()) {
            return null;
        }
        try {
            return progress.getProgressText();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The advancement's item, drawn with the game's own item renderer.
     *
     * Wrapped in lighting setup and a try/catch: a datapack or a mod can point an
     * advancement at an item whose renderer throws, and one bad entry must not take
     * the list with it.
     */
    private void drawItemIcon(Advancement advancement, int x, int y, float brightness) {
        DisplayInfo display = advancement.getDisplay();
        if (display == null) {
            return;
        }
        ItemStack icon = display.getIcon();
        if (icon == null || icon.isEmpty()) {
            return;
        }
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(brightness, brightness, brightness, 1.0F);
            this.itemRender.renderItemAndEffectIntoGUI(icon, x, y);
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
        int raw = this.stats.readStat(stat);
        String value;
        try {
            value = stat.format(raw);
        } catch (Throwable t) {
            value = String.valueOf(raw);
        }

        int valueWidth = this.fontRenderer.getStringWidth(value);
        String name = fit(nameOf(stat),
                rowWidth - valueWidth - 24);
        int textY = rowY + (rowHeight - 8) / 2;

        // Untouched stats sit back so the ones with a number carry the list.
        boolean touched = raw > 0;
        this.fontRenderer.drawString(name, rowX + 8, textY,
                Draw.withAlpha(touched ? Theme.text : Theme.textDisabled, alpha));
        this.fontRenderer.drawString(value, rowX + rowWidth - 8 - valueWidth, textY,
                Draw.withAlpha(touched ? Theme.accent : Theme.textDim,
                        (touched ? 0.95F : 0.5F) * alpha));
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
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
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.list.mouseWheel(wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
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

package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.MenuOptionButton;
import com.console.uky.client.mods.ShaderOptions;
import com.console.uky.client.mods.ShaderPacks;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * The loaded shader pack's own settings.
 *
 * <p>Everything on this screen belongs to whoever wrote the pack: the rows, their
 * order, the pages they are grouped into and the words on them are all out of
 * {@code shaders.properties} and the pack's language files. See {@link ShaderOptions},
 * which reads them; this only lays them out in our widgets, so a pack's settings look
 * like the rest of the pack's menus rather than like a mod inside a mod.
 *
 * <p>A page of links opens another page of this same screen — {@code screen.X} in the
 * pack's own terms — which is how a pack with a hundred options stays navigable.
 *
 * <h2>Nothing is live</h2>
 *
 * <p>Changing a row queues a value; the shaders are recompiled only on Apply. That is
 * not a design choice so much as the shape of the thing: a reload rebuilds the whole
 * pipeline, and doing it per click would mean a second of stall for every switch
 * flipped. The header says how many changes are waiting, and leaving without applying
 * drops them.
 */
public class GuiShaderOptionsScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_APPLY = 201;
    private static final int ID_RESET = 202;
    private static final int ID_ROW_BASE = 300;

    /** The page being shown: null is the pack's main one. */
    private final String screenId;

    private final List<ShaderOptions.Entry> rows = new ArrayList<ShaderOptions.Entry>();
    /** Row widgets, index-aligned with {@link #rows}. */
    private final List<MenuButton> widgets = new ArrayList<MenuButton>();

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int padding;
    private int contentTop;
    private int footerRuleY;
    private int rowHeight;
    private int rowGap;
    /** Top of the block the hovered row's description is written into. */
    private int commentTop;
    private int commentLines;

    /** Vanilla's line spacing, which is what the font is drawn at everywhere else. */
    private static final int LINE_HEIGHT = 10;

    /** Eased scroll, and where it is heading. */
    private float scroll;
    private float scrollTarget;
    private int contentExtent;
    private final List<Integer> nominalY = new ArrayList<Integer>();

    private int mouseX;
    private int mouseY;

    public GuiShaderOptionsScreen(GuiScreen parent) {
        this(parent, null);
    }

    private GuiShaderOptionsScreen(GuiScreen parent, String screenId) {
        super(parent);
        this.screenId = screenId;
    }

    /** Whether the loaded pack has settings worth opening. */
    public static boolean available() {
        return ShaderOptions.available();
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        this.rows.clear();
        this.widgets.clear();
        this.nominalY.clear();
        this.rows.addAll(ShaderOptions.page(this.screenId));

        int panelWidth = Math.min((int) (this.width * 0.76F), 460);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        boolean cramped = this.height < 300;
        this.padding = cramped ? 12 : 16;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 16) - buttonHeight;
        this.footerRuleY = buttonY - (cramped ? 8 : 14);
        this.contentTop = this.panelY1 + (cramped ? 34 : 42);

        // Room for the author's description, reserved whether or not anything is being
        // hovered. Cut to one line it was a sentence with its end missing, which is
        // worse than no sentence: a pack's descriptions are where it says what an
        // option costs. The space is held permanently so the rows do not jump when the
        // pointer crosses one.
        this.commentLines = cramped ? 2 : 3;
        this.commentTop = this.footerRuleY - 4 - this.commentLines * LINE_HEIGHT;

        int available = this.commentTop - 8 - this.contentTop;
        int slot = Math.max(22, available / Math.max(1, Math.min(this.rows.size(), 8)));
        this.rowHeight = clamp(slot * 3 / 5, 14, 24);
        this.rowGap = clamp(slot - this.rowHeight, 3, 12);

        int left = this.panelX1 + this.padding;
        int rowWidth = panelWidth - this.padding * 2;
        int y = this.contentTop;
        for (int i = 0; i < this.rows.size(); i++) {
            MenuButton widget = widgetFor(i, left, y, rowWidth);
            this.widgets.add(widget);
            this.nominalY.add(Integer.valueOf(y));
            this.buttonList.add(widget);
            y += this.rowHeight + this.rowGap;
        }
        this.contentExtent = Math.max(0, y - this.contentTop);
        clampScroll();

        int gap = 10;
        int third = (rowWidth - gap * 2) / 3;
        MenuButton reset = new MenuButton(ID_RESET, left, buttonY, third, buttonHeight,
                I18n.format("uky.shaderPacks.reset", new Object[0]),
                MenuButton.Style.NORMAL);
        reset.entrance(0.05F);
        this.buttonList.add(reset);

        MenuButton apply = new MenuButton(ID_APPLY, left + third + gap, buttonY, third,
                buttonHeight, I18n.format("uky.shaderPacks.apply", new Object[0]),
                MenuButton.Style.NORMAL);
        apply.entrance(0.05F);
        this.buttonList.add(apply);

        MenuButton done = new MenuButton(ID_DONE, left + (third + gap) * 2, buttonY,
                rowWidth - (third + gap) * 2, buttonHeight,
                I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /** A row's widget: a switch, a cycle, or a link into another page. */
    private MenuButton widgetFor(final int index, int x, int y, int width) {
        final ShaderOptions.Entry entry = this.rows.get(index);
        if (entry.kind() == ShaderOptions.Kind.LINK) {
            MenuButton link = new MenuButton(ID_ROW_BASE + index, x, y, width,
                    this.rowHeight, entry.label() + "...", MenuButton.Style.NORMAL);
            link.align(MenuButton.Align.LEFT);
            link.entrance(stagger(index));
            return link;
        }
        MenuButton widget = new MenuOptionButton(ID_ROW_BASE + index, x, y, width,
                this.rowHeight, new MenuOptionButton.Source() {
                    @Override
                    public String label() {
                        return current(index).label();
                    }

                    @Override
                    public String value() {
                        ShaderOptions.Entry now = current(index);
                        return now.kind() == ShaderOptions.Kind.TOGGLE
                                ? "" : translatedValue(now);
                    }

                    @Override
                    public boolean toggle() {
                        return current(index).kind() == ShaderOptions.Kind.TOGGLE;
                    }

                    @Override
                    public boolean on() {
                        return current(index).on();
                    }

                    @Override
                    public void cycle() {
                        ShaderOptions.cycle(current(index));
                        // The queue is what the values are read from, so re-reading the
                        // page is what makes the row show what was just asked for — and
                        // the profile row makes that more than a formality: one click on
                        // it changes the value of every row under it.
                        refresh();
                    }

                    @Override
                    public boolean available() {
                        return true;
                    }
                });
        widget.entrance(stagger(index));
        return widget;
    }

    /** Capped, for a pack whose main page runs to forty rows. */
    private float stagger(int index) {
        return 0.05F + Math.min(index, 12) * 0.02F;
    }

    /** The row as it stands now; the list is replaced wholesale by {@link #refresh}. */
    private ShaderOptions.Entry current(int index) {
        return index < this.rows.size() ? this.rows.get(index) : this.rows.get(0);
    }

    /**
     * Re-reads the page without touching the layout.
     *
     * The rows cannot appear or disappear — the menu is the pack's and it does not
     * change under us — so only their values need collecting again, and the widgets
     * read those through {@link #current}.
     */
    private void refresh() {
        List<ShaderOptions.Entry> fresh = ShaderOptions.page(this.screenId);
        if (fresh.size() != this.rows.size()) {
            // Belt and braces: a differently shaped page means the widgets no longer
            // line up with it, and a rebuilt layout is the only honest answer.
            relayout();
            return;
        }
        this.rows.clear();
        this.rows.addAll(fresh);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** A value in the pack's own words where it gave any: {@code value.NAME.X}. */
    private static String translatedValue(ShaderOptions.Entry entry) {
        String key = "value." + entry.id() + "." + entry.value();
        String translated = I18n.format(key, new Object[0]);
        return key.equals(translated) ? entry.value() : translated;
    }

    // --------------------------------------------------------------- drawing --

    /** The same sky the pack list sits on, and for the same reason. */
    @Override
    protected boolean isStarfieldOnly() {
        return this.mc.world == null;
    }

    @Override
    protected void drawBackdrop() {
        if (this.mc.world == null) {
            super.drawBackdrop();
            return;
        }
        Draw.rect(0, 0, this.width, this.height,
                Draw.withAlpha(Theme.background, 0.45F * this.fadeAlpha));
    }

    @Override
    protected void drawOverlay() {
        if (this.mc.world != null) {
            Draw.vignette(this.width, this.height, 0.5F * this.fadeAlpha, 0xFF000000);
            return;
        }
        super.drawOverlay();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        applyScroll();
        GuiShaderPacksScreen.drawPanel(this.panelX1, this.panelY1, this.panelX2,
                this.panelY2, this.fadeAlpha);

        String heading = this.screenId == null
                ? ShaderPacks.selected()
                : ShaderOptions.pageTitle(this.screenId);
        this.fontRenderer.drawString(fit(heading, this.panelX2 - this.panelX1
                        - this.padding * 2 - 60).toUpperCase(),
                this.panelX1 + this.padding, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        // What is waiting to be compiled, said in the one place the eye already is.
        if (ShaderOptions.pending()) {
            String note = I18n.format("uky.shaderPacks.pending", new Object[0]);
            this.fontRenderer.drawString(note,
                    this.panelX2 - this.padding - this.fontRenderer.getStringWidth(note),
                    this.panelY1 + 14,
                    Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));
        }

        Draw.gradientH(this.panelX1 + this.padding, this.footerRuleY,
                this.panelX2 - this.padding, this.footerRuleY + 1,
                Draw.withAlpha(Theme.accent, 0.35F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        drawRowMarks();
        drawScrollbar();
        drawComment();
    }

    /**
     * The dot beside a row whose value is not the pack's own, and the link chevron.
     *
     * Drawn here rather than in the widget because both are about what the row means
     * to the pack rather than about the control: one says "you changed this", the
     * other says "this opens something".
     */
    private void drawRowMarks() {
        for (int i = 0; i < this.widgets.size(); i++) {
            MenuButton widget = this.widgets.get(i);
            if (!widget.visible) {
                continue;
            }
            ShaderOptions.Entry entry = this.rows.get(i);
            float cy = widget.y + widget.height / 2.0F;
            if (entry.kind() == ShaderOptions.Kind.LINK) {
                Icons.forward(this.panelX2 - this.padding - 7, cy, 8,
                        Draw.withAlpha(Theme.accent, 0.8F * this.fadeAlpha));
                continue;
            }
            if (entry.modified()) {
                Draw.rect(this.panelX1 + this.padding - 6, cy - 1.5F,
                        this.panelX1 + this.padding - 3, cy + 1.5F,
                        Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));
            }
        }
    }

    /**
     * The author's description of whatever the pointer is on.
     *
     * Along the footer rather than in a box under the cursor: these are sentences, not
     * labels, and a tooltip that covers the rows below the one being read is the thing
     * every shader menu gets wrong.
     */
    @SuppressWarnings("unchecked")
    private void drawComment() {
        for (int i = 0; i < this.widgets.size(); i++) {
            MenuButton widget = this.widgets.get(i);
            if (!widget.visible || this.rows.get(i).comment() == null) {
                continue;
            }
            if (this.mouseX < widget.x || this.mouseX > widget.x + widget.width
                    || this.mouseY < widget.y
                    || this.mouseY > widget.y + widget.height) {
                continue;
            }
            int width = this.panelX2 - this.panelX1 - this.padding * 2;
            // Wrapped, not cut. The last line is elided only when the description is
            // longer than the room reserved for it, and then it is elided at the end of
            // that line rather than at the end of the first.
            java.util.List<String> lines = this.fontRenderer
                    .listFormattedStringToWidth(this.rows.get(i).comment(), width);
            for (int line = 0; line < lines.size() && line < this.commentLines; line++) {
                String text = lines.get(line);
                boolean last = line == this.commentLines - 1 && lines.size() > this.commentLines;
                this.fontRenderer.drawString(last ? fit(text + " ...", width) : text,
                        this.panelX1 + this.padding, this.commentTop + line * LINE_HEIGHT,
                        Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
            }
            return;
        }
    }

    // -------------------------------------------------------------- scrolling --

    private int viewportHeight() {
        // Stops above the description block rather than at the rule: a row drawn over
        // the text explaining it is the thing this screen exists to avoid.
        return Math.max(20, this.commentTop - 4 - this.contentTop);
    }

    private float maxScroll() {
        return Math.max(0.0F, this.contentExtent - viewportHeight());
    }

    private void clampScroll() {
        float max = maxScroll();
        if (this.scrollTarget > max) {
            this.scrollTarget = max;
        }
        if (this.scrollTarget < 0.0F) {
            this.scrollTarget = 0.0F;
        }
        if (this.scroll > max) {
            this.scroll = max;
        }
    }

    /** Moves the rows to where the scroll says they are; see the settings screen. */
    private void applyScroll() {
        this.scroll = Ease.approach(this.scroll, this.scrollTarget, 0.05F, this.delta);
        int offset = Math.round(this.scroll);
        for (int i = 0; i < this.widgets.size(); i++) {
            MenuButton widget = this.widgets.get(i);
            widget.y = this.nominalY.get(i).intValue() - offset;
            widget.visible = widget.y >= this.contentTop
                    && widget.y + widget.height <= this.commentTop - 4;
        }
    }

    private void drawScrollbar() {
        float max = maxScroll();
        if (max <= 0.5F) {
            return;
        }
        int visible = viewportHeight();
        float trackX = this.panelX2 - this.padding + 6;
        float thumb = Math.max(18.0F, visible * visible / (float) this.contentExtent);
        float thumbY = this.contentTop + (visible - thumb) * (this.scroll / max);

        Draw.rect(trackX, this.contentTop, trackX + 2, this.contentTop + visible,
                Draw.withAlpha(Theme.separator, 0.35F * this.fadeAlpha));
        Draw.rect(trackX, thumbY, trackX + 2, thumbY + thumb,
                Draw.withAlpha(Theme.accent, 0.8F * this.fadeAlpha));
    }

    // ----------------------------------------------------------------- input --

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0 || maxScroll() <= 0.5F) {
            return;
        }
        this.scrollTarget -= (wheel > 0 ? 1 : -1) * (this.rowHeight + this.rowGap) * 2;
        clampScroll();
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button instanceof MenuOptionButton) {
            ((MenuOptionButton) button).cycle();
            return;
        }
        if (button.id >= ID_ROW_BASE && button.id - ID_ROW_BASE < this.rows.size()) {
            ShaderOptions.Entry entry = this.rows.get(button.id - ID_ROW_BASE);
            if (entry.kind() == ShaderOptions.Kind.LINK) {
                switchTo(new GuiShaderOptionsScreen(this, entry.id()));
            }
            return;
        }
        switch (button.id) {
            case ID_RESET:
                ShaderOptions.resetAll();
                refresh();
                break;
            case ID_APPLY:
                apply();
                break;
            case ID_DONE:
                apply();
                switchBack();
                break;
            default:
                break;
        }
    }

    /** Recompiles with whatever is queued, then re-reads what came of it. */
    private void apply() {
        if (!ShaderOptions.pending()) {
            return;
        }
        ShaderPacks.apply(ShaderPacks.NONE, ShaderPacks.enabled());
        refresh();
    }

    /**
     * Escape leaves the queue where it is rather than dropping it.
     *
     * A sub-page is left the same way it was entered, and dropping every queued change
     * on the way out of one would mean the way back to the list is also the way to lose
     * the last ten minutes of work. Discarding belongs to the screen that offered the
     * pack, and that is where it is.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
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

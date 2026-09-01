package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of strings, edited in the game rather than in a text file.
 *
 * <p>Every settings screen in this mod could show a config list and none of them could
 * change one: a list was a row that said "list" and did nothing when clicked, here and
 * in every other mod's settings drawn through {@link GuiModConfigScreen}. Which meant
 * the answer to a perfectly ordinary question — stop naming stone, add another link
 * under the menu, take that loading tip out — was to leave the game, find a file and
 * come back.
 *
 * <p>It lives behind the mod settings and nowhere else. The tabs of the main settings
 * screen are for the things a player changes while playing; a list is a decision a pack
 * author makes once, and it belongs where the rest of the config does.
 *
 * <p>What it edits is behind {@link Source}: a read, a write, and what the shipped value
 * was. The rows themselves are the same kind of thing whatever the list is for — a line
 * of text, in an order somebody chose — so the screen does not know or care whether it
 * is looking at block names, URLs or another mod's whitelist.
 *
 * <h2>What a row does</h2>
 *
 * <p>Click the text to edit it, the arrows to move it, the bin to take it out. The order
 * is preserved and editable because for half of these lists it is the whole meaning —
 * the links under the main menu are drawn in this order, and a resource pack list is
 * priority — and for the other half it costs nothing.
 *
 * <p>Nothing is written until the screen is left, and it is written on the way out
 * whichever way the player leaves. There is no Cancel: this is a list of lines, every
 * change to it is visible on screen the moment it is made, and a confirmation step over
 * something already undoable by looking at it is a step that only ever gets in the way.
 */
public class GuiListEditScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_ADD = 201;
    private static final int ID_RESET = 202;

    /**
     * Where the list comes from and goes back to.
     *
     * An abstract class rather than an interface so that the two things most callers do
     * not have — a default to reset to, a reason to forbid adding rows — do not have to
     * be written out at every call site.
     */
    public abstract static class Source {

        /** The list as it stands. Never null; an empty array is an empty list. */
        public abstract String[] load();

        /** Called once, on the way out, with the edited list. */
        public abstract void save(String[] values);

        /** What the list ships with, or null when there is nothing to go back to. */
        public String[] defaults() {
            return null;
        }

        /** False where the owner fixed the length — some mods' config lists do. */
        public boolean resizable() {
            return true;
        }

        /** One line under the title saying what an entry looks like, or null. */
        public String hint() {
            return null;
        }
    }

    private final String heading;
    private final Source source;

    private final List<String> entries = new ArrayList<String>();
    /** Whether the source has been read; see {@link #buildLayout}. */
    private boolean loaded;

    private EntryList list;
    private GuiTextField editor;
    /** Row being edited, or -1. */
    private int editing = -1;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int padding;
    private int listTop;

    private int mouseX;
    private int mouseY;

    public GuiListEditScreen(GuiScreen parent, String heading, Source source) {
        super(parent);
        this.heading = heading;
        this.source = source;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        // Once, not per layout: a window resize rebuilds this, and re-reading the
        // source there would undo everything typed since the screen opened.
        if (!this.loaded) {
            this.loaded = true;
            String[] source = this.source.load();
            for (int i = 0; source != null && i < source.length; i++) {
                this.entries.add(source[i] == null ? "" : source[i]);
            }
        }

        int panelWidth = Math.min((int) (this.width * 0.76F), 460);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        boolean cramped = this.height < 300;
        this.padding = cramped ? 12 : 16;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 16) - buttonHeight;
        int content = panelWidth - this.padding * 2;
        int left = this.panelX1 + this.padding;

        boolean hinted = this.source.hint() != null;
        this.listTop = this.panelY1 + (cramped ? 32 : 38) + (hinted ? 12 : 0);
        int listHeight = Math.max(40, buttonY - 14 - this.listTop);

        if (this.list == null) {
            this.list = new EntryList();
        }
        this.list.setBounds(left, this.listTop, content, listHeight, 20);

        int gap = 10;
        boolean resettable = this.source.defaults() != null;
        boolean addable = this.source.resizable();
        int buttons = 1 + (resettable ? 1 : 0) + (addable ? 1 : 0);
        int slot = (content - gap * (buttons - 1)) / buttons;
        int x = left;

        if (addable) {
            MenuButton add = new MenuButton(ID_ADD, x, buttonY, slot, buttonHeight,
                    I18n.format("uky.list.add", new Object[0]), MenuButton.Style.NORMAL);
            add.entrance(0.05F);
            this.buttonList.add(add);
            x += slot + gap;
        }
        if (resettable) {
            MenuButton reset = new MenuButton(ID_RESET, x, buttonY, slot, buttonHeight,
                    I18n.format("uky.list.reset", new Object[0]), MenuButton.Style.NORMAL);
            reset.entrance(0.06F);
            this.buttonList.add(reset);
            x += slot + gap;
        }
        MenuButton done = new MenuButton(ID_DONE, x, buttonY,
                this.panelX2 - this.padding - x, buttonHeight,
                I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.06F);
        this.buttonList.add(done);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.1F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.5F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.09F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.5F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_ABOVE;
    }

    /** Over a world this sits on the world, like every other screen reached in play. */
    @Override
    protected void drawBackdrop() {
        if (this.mc.world == null) {
            super.drawBackdrop();
            return;
        }
        Draw.rect(0, 0, this.width, this.height,
                Draw.withAlpha(Theme.background, 0.55F * this.fadeAlpha));
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.list.update(this.delta);

        GuiShaderPacksScreen.drawPanel(this.panelX1, this.panelY1, this.panelX2,
                this.panelY2, this.fadeAlpha);

        this.fontRenderer.drawString(
                fit(this.heading, this.panelX2 - this.panelX1 - this.padding * 2)
                        .toUpperCase(),
                this.panelX1 + this.padding, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        String hint = this.source.hint();
        if (hint != null) {
            this.fontRenderer.drawString(
                    fit(hint, this.panelX2 - this.panelX1 - this.padding * 2),
                    this.panelX1 + this.padding, this.listTop - 14,
                    Draw.withAlpha(Theme.textDim, 0.75F * this.fadeAlpha));
        }

        this.list.draw(mouseX, mouseY, this.fadeAlpha);

        if (this.entries.isEmpty()) {
            String empty = I18n.format("uky.list.empty", new Object[0]);
            this.fontRenderer.drawString(empty, this.panelX1 + this.padding + 10,
                    this.listTop + 12,
                    Draw.withAlpha(Theme.textDisabled, 0.85F * this.fadeAlpha));
        }

        // Drawn last and outside the list's clip: the field belongs to the screen while
        // it is open, and a row scrolled under the edge must not cut the text being
        // typed in half.
        if (this.editor != null) {
            Draw.rect(this.editor.x - 3, this.editor.y - 3,
                    this.editor.x + this.editor.width + 3, this.editor.y + 13,
                    Draw.withAlpha(Theme.surface, 0.92F * this.fadeAlpha));
            Draw.rect(this.editor.x - 3, this.editor.y + 12,
                    this.editor.x + this.editor.width + 3, this.editor.y + 13,
                    Draw.withAlpha(Theme.accent, this.fadeAlpha));
            this.editor.drawTextBox();
        }
    }

    private final class EntryList extends ScrollList {

        @Override
        public int rowCount() {
            return GuiListEditScreen.this.entries.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }
            boolean beingEdited = index == GuiListEditScreen.this.editing;
            Draw.rect(rowX, rowY + 3, rowX + 2, rowY + rowHeight - 3,
                    Draw.withAlpha(Theme.accent, (beingEdited ? 0.9F : 0.35F) * alpha));

            if (!beingEdited) {
                String text = GuiListEditScreen.this.entries.get(index);
                if (text.isEmpty()) {
                    text = I18n.format("uky.list.blank", new Object[0]);
                }
                GuiListEditScreen.this.fontRenderer.drawString(
                        GuiListEditScreen.this.fit(text, rowWidth - 54), rowX + 10,
                        rowY + 6, Draw.withAlpha(Theme.text, alpha));
            }

            drawRowControls(index, rowX, rowY, rowWidth, rowHeight, alpha);
        }

        /** Move up, move down, remove — in that order, right-aligned. */
        private void drawRowControls(int index, int rowX, int rowY, int rowWidth,
                                     int rowHeight, float alpha) {
            float cy = rowY + rowHeight / 2.0F;
            if (index > 0) {
                Icons.arrowUp(rowX + rowWidth - 34, cy - 4, 7,
                        Draw.withAlpha(Theme.textDim, 0.85F * alpha));
            }
            if (index < rowCount() - 1) {
                Icons.arrowDown(rowX + rowWidth - 34, cy + 4, 7,
                        Draw.withAlpha(Theme.textDim, 0.85F * alpha));
            }
            if (GuiListEditScreen.this.source.resizable()) {
                Icons.trash(rowX + rowWidth - 12, cy, 9,
                        Draw.withAlpha(Theme.danger, 0.85F * alpha));
            }
        }

        @Override
        protected void onRowClicked(int index) {
            // Handled in mouseClicked, which knows where in the row the click landed.
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        if (this.editor != null) {
            // A click anywhere commits what was typed. Landing on another row then
            // opens that one, which is what makes editing a list of ten a list of ten
            // clicks rather than twenty.
            commitEditor();
        }

        int index = this.list.rowIndexAt(mouseX, mouseY);
        if (index >= 0 && index < this.entries.size()) {
            int right = this.list.rowRight();
            if (this.source.resizable() && mouseX >= right - 20) {
                this.entries.remove(index);
                return;
            }
            if (mouseX >= right - 42 && mouseX < right - 24) {
                move(index, mouseY < this.list.rowTop(index) + 10 ? -1 : 1);
                return;
            }
            beginEditing(index);
            return;
        }
        // Not on a row: the scrollbar and the empty space under the last entry still
        // belong to the list, and only what misses all of it reaches the buttons.
        if (this.list.mouseClicked(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= this.entries.size()) {
            return;
        }
        String moved = this.entries.remove(index);
        this.entries.add(target, moved);
    }

    private void beginEditing(int index) {
        int rowY = this.list.rowTop(index);
        int x = this.panelX1 + this.padding + 10;
        int width = this.list.rowRight() - 48 - x;
        this.editor = new GuiTextField(0, this.fontRenderer, x, rowY + 5,
                Math.max(40, width), 12);
        this.editor.setMaxStringLength(512);
        // Vanilla's own box would sit inside the one drawn for it in drawContent; the
        // field brings the text and the caret and nothing else, like every other input
        // in this mod.
        this.editor.setEnableBackgroundDrawing(false);
        this.editor.setText(this.entries.get(index));
        this.editor.setFocused(true);
        this.editing = index;
    }

    /**
     * Takes what was typed.
     *
     * A blank line is kept rather than dropped: somebody who cleared a row is halfway
     * through replacing it, and deleting the row under them because they reached for
     * the backspace one time too many is the sort of thing that costs the whole list.
     * The bin is how a row goes away, and it is right there.
     */
    private void commitEditor() {
        if (this.editor == null) {
            return;
        }
        String text = this.editor.getText().trim();
        if (this.editing >= 0 && this.editing < this.entries.size()) {
            this.entries.set(this.editing, text);
        }
        this.editor = null;
        this.editing = -1;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.editor != null) {
            this.editor.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (this.editor != null) {
            // Escape and Enter both end the edit and neither leaves the screen: the
            // first press people make after typing is one of these two, and losing the
            // whole screen to it would be losing the edit as well.
            if (keyCode == 1 || keyCode == 28) {
                commitEditor();
                return;
            }
            this.editor.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == 1) {
            saveAndClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0 && this.editor == null) {
            this.list.mouseWheel(wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
        if (this.editor == null) {
            this.list.mouseDragged(mouseY);
        }
        super.mouseClickMove(mouseX, mouseY, button, heldTime);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void onAction(GuiButton button) {
        switch (button.id) {
            case ID_ADD:
                commitEditor();
                this.entries.add("");
                this.list.scrollTo(this.entries.size() - 1);
                // Straight into editing it: an empty row added and left alone is a
                // blank line in somebody's config, and adding one is only ever the
                // first half of typing something into it.
                beginEditing(this.entries.size() - 1);
                break;
            case ID_RESET:
                commitEditor();
                this.entries.clear();
                String[] shipped = this.source.defaults();
                for (int i = 0; shipped != null && i < shipped.length; i++) {
                    this.entries.add(shipped[i]);
                }
                break;
            case ID_DONE:
                saveAndClose();
                break;
            default:
                break;
        }
    }

    /**
     * Writes the list back and leaves.
     *
     * Blank rows are dropped here rather than as they appear — see
     * {@link #commitEditor} — so a row cleared and then thought better of survives
     * until the moment the list is actually written.
     */
    private void saveAndClose() {
        commitEditor();
        List<String> kept = new ArrayList<String>(this.entries.size());
        for (int i = 0; i < this.entries.size(); i++) {
            String entry = this.entries.get(i).trim();
            if (!entry.isEmpty()) {
                kept.add(entry);
            }
        }
        this.source.save(kept.toArray(new String[kept.size()]));
        switchBack();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

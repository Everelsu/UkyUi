package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.mods.ModConfigCatalog;
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
 * Settings screens belonging to other mods, gathered into one place.
 *
 * Forge hides these one per mod behind the mod list, which is tolerable with five
 * mods and hopeless with ninety — finding the one you want means scrolling a list
 * sorted by nothing in particular and clicking into each entry to see whether it even
 * has settings. Here only the mods that have settings appear at all, sorted by name
 * and searchable.
 *
 * <p>Rows are marked by what opening them will do. Most mods describe their settings
 * to Forge as data rather than drawing them, and those are redrawn here in the same
 * widgets as everything else. The rest wrote their own screen, and those open as
 * their author drew them — an honest mark is better than pretending the interface is
 * uniform when it cannot be.
 */
public class GuiModSettingsScreen extends MenuScreen {

    private static final int ID_DONE = 200;

    private final List<ModConfigCatalog.Entry> shown =
            new ArrayList<ModConfigCatalog.Entry>();

    private GuiTextField search;
    private ModList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    public GuiModSettingsScreen(GuiScreen parent) {
        super(parent);
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.60F), 430);
        this.panelX1 = Math.max(10, (int) (this.width * 0.06F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        String previous = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(0, this.fontRenderer,
                this.panelX1 + 15, this.panelY1 + 36, panelWidth - 30, 16);
        this.search.setMaxStringLength(48);
        this.search.setEnableBackgroundDrawing(false);
        this.search.setText(previous);
        this.search.setFocused(true);

        boolean cramped = this.height < 300;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 20) - buttonHeight;

        if (this.list == null) {
            this.list = new ModList();
        }
        int listTop = this.panelY1 + 60;
        this.list.setBounds(this.panelX1 + 15, listTop, panelWidth - 30,
                Math.max(40, buttonY - 12 - listTop), 24);

        rebuildRows();

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15, buttonY,
                panelWidth - 30, buttonHeight, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /** Applies the search box to the catalogue. */
    private void rebuildRows() {
        String query = this.search == null ? "" : this.search.getText().trim().toLowerCase();
        this.shown.clear();
        for (ModConfigCatalog.Entry entry : ModConfigCatalog.entries()) {
            // Matched against the id as well as the name: people look for "waila" as
            // often as for the words its author put in the title.
            if (query.isEmpty()
                    || entry.displayName().toLowerCase().contains(query)
                    || entry.modId().toLowerCase().contains(query)) {
                this.shown.add(entry);
            }
        }
    }

    // --------------------------------------------------------------- drawing --

    /** Low and far to the right, well clear of a panel that runs the full height. */
    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.86F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.72F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.13F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.55F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        float a = this.fadeAlpha;

        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.55F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));

        drawFitted(I18n.format("uky.modSettings.title", new Object[0]).toUpperCase(),
                this.panelX1 + 15, this.panelY1 + 14, this.panelX2 - this.panelX1 - 30,
                Draw.withAlpha(Theme.text, a));

        drawSearch(a);

        this.list.update(this.delta);
        this.list.draw(mouseX, mouseY, a);

        if (this.shown.isEmpty()) {
            String note = I18n.format(ModConfigCatalog.entries().isEmpty()
                    ? "uky.modSettings.none" : "uky.modSettings.noMatch", new Object[0]);
            drawFittedCentred(note, (this.panelX1 + this.panelX2) / 2, this.panelY1 + 100,
                    this.panelX2 - this.panelX1 - 30, Draw.withAlpha(Theme.textDim, 0.7F * a));
        }
    }

    private void drawSearch(float a) {
        int y = this.panelY1 + 32;
        int lineY = y + 22;
        Draw.rect(this.panelX1 + 15, lineY, this.panelX2 - 15, lineY + 1,
                Draw.fade(Theme.separator, a));

        if (this.search.getText().isEmpty()) {
            drawFitted(I18n.format("uky.modSettings.search", new Object[0]),
                    this.panelX1 + 15, y + 6, this.panelX2 - this.panelX1 - 30,
                    Draw.withAlpha(Theme.textDim, 0.55F * a));
        }
        this.search.drawTextBox();
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        if (button.id == ID_DONE) {
            switchBack();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            switchBack();
            return;
        }
        String before = this.search.getText();
        this.search.textboxKeyTyped(typedChar, keyCode);
        if (!this.search.getText().equals(before)) {
            rebuildRows();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        this.search.mouseClicked(mouseX, mouseY, button);
        if (this.list.mouseClicked(mouseX, mouseY)) {
            int index = this.list.rowIndexAt(mouseX, mouseY);
            if (index >= 0 && index < this.shown.size()) {
                open(this.shown.get(index));
            }
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.list.mouseWheel(wheel > 0 ? 1 : -1);
        }
    }

    /**
     * Opens a mod's settings, in our widgets where the mod described them as data and
     * in its own screen where it did not.
     */
    private void open(ModConfigCatalog.Entry entry) {
        GuiScreen screen = GuiModConfigScreen.forEntry(entry, this);
        if (screen == null) {
            screen = ModConfigCatalog.instantiate(entry, this);
        }
        if (screen != null) {
            this.mc.displayGuiScreen(screen);
        }
    }

    // ------------------------------------------------------------------ list --

    private final class ModList extends ScrollList {

        @Override
        public int rowCount() {
            return GuiModSettingsScreen.this.shown.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            ModConfigCatalog.Entry entry = GuiModSettingsScreen.this.shown.get(index);

            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }

            // A rail on rows we redraw ourselves. It is the same accent used everywhere
            // else for "this belongs to the interface you are looking at", which is
            // exactly what it means here.
            if (entry.redrawable) {
                Draw.rect(rowX, rowY + 2, rowX + 2, rowY + rowHeight - 2,
                        Draw.withAlpha(Theme.accent, 0.7F * alpha));
            }

            int textX = rowX + 10;
            String version = entry.version();
            int versionWidth = version.isEmpty() ? 0
                    : drawFittedRight(version, rowX + rowWidth - 20, rowY + 8, rowWidth / 3,
                            Draw.withAlpha(Theme.textDim, 0.6F * alpha));

            drawFitted(entry.displayName(), textX, rowY + 8,
                    rowWidth - 30 - versionWidth - (textX - rowX),
                    Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));

            Icons.forward(rowX + rowWidth - 8, rowY + rowHeight / 2.0F, 7,
                    Draw.withAlpha(Theme.textDim, (isHovered ? 0.9F : 0.45F) * alpha));
        }
    }
}

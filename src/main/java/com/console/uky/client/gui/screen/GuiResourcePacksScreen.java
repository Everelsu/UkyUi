package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.resource.ReloadRequirements;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resource packs.
 *
 * Vanilla asks you to drag entries between two lists, which is fiddly at any GUI
 * scale and gives no clue that order decides which pack wins. Here each row carries
 * its own controls — an arrow to move it across, and on the selected side arrows to
 * move it up and down — and the selected column is labelled so that the top of it is
 * plainly the one that overrides the rest.
 *
 * <p>Nothing is applied until Done: the repository, the options file and the
 * resource reload all happen together, exactly as vanilla sequences them, so a
 * half-made selection cannot be left behind.
 */
public class GuiResourcePacksScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_FOLDER = 201;

    /** Packs not in use, in repository order. */
    private final List<ResourcePackRepository.Entry> available =
            new ArrayList<ResourcePackRepository.Entry>();
    /** Packs in use, highest priority first — the order shown, top-down. */
    private final List<ResourcePackRepository.Entry> selected =
            new ArrayList<ResourcePackRepository.Entry>();

    private PackList availableList;
    private PackList selectedList;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int columnWidth;
    private int leftColumn;
    private int rightColumn;
    private int listTop;
    private int listHeight;

    private int mouseX;
    private int mouseY;

    public GuiResourcePacksScreen(GuiScreen parent) {
        super(parent);
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        if (this.available.isEmpty() && this.selected.isEmpty()) {
            loadPacks();
        }

        int panelWidth = Math.min((int) (this.width * 0.86F), 560);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        int padding = 16;
        int gap = 14;
        this.columnWidth = (panelWidth - padding * 2 - gap) / 2;
        this.leftColumn = this.panelX1 + padding;
        this.rightColumn = this.leftColumn + this.columnWidth + gap;

        boolean cramped = this.height < 300;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 18) - buttonHeight;

        // Enough room above the lists for a heading, its rule, and two wrapped lines
        // of the priority note — the note is the thing vanilla never says, so it is
        // given space rather than truncated into it.
        this.listTop = this.panelY1 + 82;
        this.listHeight = Math.max(40, buttonY - 12 - this.listTop);

        if (this.availableList == null) {
            this.availableList = new PackList(false);
            this.selectedList = new PackList(true);
        }
        this.availableList.setBounds(this.leftColumn, this.listTop, this.columnWidth,
                this.listHeight, 30);
        this.selectedList.setBounds(this.rightColumn, this.listTop, this.columnWidth,
                this.listHeight, 30);

        int half = (panelWidth - padding * 2 - gap) / 2;
        MenuButton folder = new MenuButton(ID_FOLDER, this.leftColumn, buttonY, half,
                buttonHeight, I18n.format("resourcePack.openFolder", new Object[0]),
                MenuButton.Style.NORMAL);
        folder.entrance(0.05F);
        this.buttonList.add(folder);

        MenuButton done = new MenuButton(ID_DONE, this.rightColumn, buttonY, half,
                buttonHeight, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /**
     * Reads the repository into the two columns.
     *
     * The repository stores selected packs lowest-priority first — the list is
     * applied in order, so the last one wins. The column shows them the other way up,
     * because "the one at the top is the one you see" is the only arrangement anyone
     * reads correctly. {@link #apply} reverses it back.
     */
    @SuppressWarnings("unchecked")
    private void loadPacks() {
        ResourcePackRepository repository = this.mc.getResourcePackRepository();
        repository.updateRepositoryEntriesAll();

        List<ResourcePackRepository.Entry> inUse =
                new ArrayList<ResourcePackRepository.Entry>(repository.getRepositoryEntries());
        List<ResourcePackRepository.Entry> all =
                new ArrayList<ResourcePackRepository.Entry>(repository.getRepositoryEntriesAll());
        all.removeAll(inUse);

        this.available.clear();
        this.available.addAll(all);

        this.selected.clear();
        this.selected.addAll(inUse);
        Collections.reverse(this.selected);

        // Loads each pack's metadata and icon. A pack with a broken pack.mcmeta still
        // gets a row — it just shows the error as its description, which is more use
        // than silently vanishing.
        for (ResourcePackRepository.Entry entry : this.available) {
            prepare(entry);
        }
        for (ResourcePackRepository.Entry entry : this.selected) {
            prepare(entry);
        }
    }

    private void prepare(ResourcePackRepository.Entry entry) {
        try {
            entry.updateResourcePack();
        } catch (Exception e) {
            UkyUI.LOGGER.warn("Could not read resource pack {}", entry.getResourcePackName(), e);
        }
    }

    // --------------------------------------------------------------- drawing --

    /**
     * Pulled far back and high on the left. This screen is the widest panel in the
     * mod, so there is no room for the hole beside it — it sits behind the top
     * corner instead, small and dim enough to stay out of the way of two lists.
     */
    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.08F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.14F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.075F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.45F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_ABOVE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.availableList.update(this.delta);
        this.selectedList.update(this.delta);

        drawGlassPanel();

        this.fontRenderer.drawString(
                I18n.format("resourcePack.title", new Object[0]).toUpperCase(),
                this.panelX1 + 16, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawColumnHeading(I18n.format("resourcePack.available.title", new Object[0]),
                this.leftColumn);
        drawColumnHeading(I18n.format("resourcePack.selected.title", new Object[0]),
                this.rightColumn);

        // Says which end of the selected column wins, which vanilla never does.
        // Wrapped rather than trimmed: the sentence is the whole point of it.
        drawWrapped(I18n.format("uky.resourcePack.topWins", new Object[0]),
                this.rightColumn, this.listTop - 24, this.columnWidth, 2,
                Draw.withAlpha(Theme.textDim, 0.65F * this.fadeAlpha), false);

        this.availableList.draw(mouseX, mouseY, this.fadeAlpha);
        this.selectedList.draw(mouseX, mouseY, this.fadeAlpha);

        if (this.selected.isEmpty()) {
            drawWrapped(I18n.format("uky.resourcePack.defaultOnly", new Object[0]),
                    this.rightColumn, this.listTop + 12, this.columnWidth - 8, 3,
                    Draw.withAlpha(Theme.textDisabled, 0.8F * this.fadeAlpha), true);
        }
        if (this.available.isEmpty()) {
            drawWrapped(I18n.format("uky.resourcePack.none", new Object[0]),
                    this.leftColumn, this.listTop + 12, this.columnWidth - 8, 3,
                    Draw.withAlpha(Theme.textDisabled, 0.8F * this.fadeAlpha), true);
        }
    }

    private void drawColumnHeading(String label, int x) {
        this.fontRenderer.drawString(label.toUpperCase(), x, this.listTop - 48,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
        Draw.gradientH(x, this.listTop - 34, x + this.columnWidth, this.listTop - 33,
                Draw.withAlpha(Theme.accent, 0.35F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** Wraps {@code text} to {@code width}, optionally centred, capped at {@code maxLines}. */
    private void drawWrapped(String text, int x, int y, int width, int maxLines, int colour,
                             boolean centred) {
        List<?> lines = this.fontRenderer.listFormattedStringToWidth(text, width);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String line = String.valueOf(lines.get(i));
            int drawX = centred
                    ? x + (width - this.fontRenderer.getStringWidth(line)) / 2
                    : x;
            this.fontRenderer.drawString(line, drawX, y + i * 10, colour);
        }
    }

    private void drawGlassPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.58F * a),
                Draw.withAlpha(Theme.background, 0.38F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(this.panelX1, this.panelY1, this.panelX2, this.panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.08F * a));
    }

    /** One column. {@code inUse} decides which controls a row carries. */
    private final class PackList extends ScrollList {
        private final boolean inUse;

        PackList(boolean inUse) {
            this.inUse = inUse;
        }

        private List<ResourcePackRepository.Entry> entries() {
            return this.inUse ? GuiResourcePacksScreen.this.selected
                    : GuiResourcePacksScreen.this.available;
        }

        @Override
        public int rowCount() {
            return entries().size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            ResourcePackRepository.Entry entry = entries().get(index);

            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }
            if (this.inUse) {
                // A rail on the in-use side, brightest at the top, so priority is
                // visible at a glance rather than only in the heading.
                float weight = 1.0F - (index / (float) Math.max(1, rowCount())) * 0.6F;
                Draw.rect(rowX, rowY + 1, rowX + 2, rowY + rowHeight - 1,
                        Draw.withAlpha(Theme.accent, 0.75F * weight * alpha));
            }

            drawIcon(entry, rowX + 5, rowY + (rowHeight - 20) / 2, alpha);

            int textX = rowX + 30;
            int controls = this.inUse ? 40 : 16;
            String name = GuiResourcePacksScreen.this.fit(
                    entry.getResourcePackName(), rowWidth - 34 - controls);
            this.fontRenderer().drawString(name, textX, rowY + 5,
                    Draw.withAlpha(Theme.text, alpha));

            String description = stripFormatting(entry.getTexturePackDescription());
            this.fontRenderer().drawString(
                    GuiResourcePacksScreen.this.fit(description,
                            rowWidth - 34 - controls),
                    textX, rowY + 16, Draw.withAlpha(Theme.textDim, 0.7F * alpha));

            drawRowControls(index, rowX, rowY, rowWidth, rowHeight, alpha);
        }

        private net.minecraft.client.gui.FontRenderer fontRenderer() {
            return GuiResourcePacksScreen.this.fontRenderer;
        }

        private void drawRowControls(int index, int rowX, int rowY, int rowWidth,
                                     int rowHeight, float alpha) {
            float cy = rowY + rowHeight / 2.0F;
            if (this.inUse) {
                // Up and down reorder; the arrow points back to the available column.
                if (index > 0) {
                    Icons.arrowUp(rowX + rowWidth - 30, cy - 6, 7,
                            Draw.withAlpha(Theme.textDim, 0.85F * alpha));
                }
                if (index < rowCount() - 1) {
                    Icons.arrowDown(rowX + rowWidth - 30, cy + 6, 7,
                            Draw.withAlpha(Theme.textDim, 0.85F * alpha));
                }
                Icons.back(rowX + rowWidth - 11, cy, 9,
                        Draw.withAlpha(Theme.danger, 0.85F * alpha));
            } else {
                // Mirrored chevron: into the selected column.
                Icons.forward(rowX + rowWidth - 11, cy, 9,
                        Draw.withAlpha(Theme.accent, 0.85F * alpha));
            }
        }

        @Override
        protected void onRowClicked(int index) {
            // Handled in mouseClicked, which knows where inside the row the click was.
        }
    }

    /** Descriptions may carry colour codes; they would render as literal glyphs here. */
    private static String stripFormatting(String text) {
        return text == null ? "" : TextFormatting.getTextWithoutFormattingCodes(text);
    }

    /**
     * The pack's own icon, or nothing if it has none.
     *
     * {@code bindTexturePackIcon} falls back to the default pack image when the pack
     * ships no {@code pack.png}, so this never draws a missing-texture checkerboard.
     */
    private void drawIcon(ResourcePackRepository.Entry entry, int x, int y, float alpha) {
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            entry.bindTexturePackIcon(this.mc.getTextureManager());
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // The icon is a 64x64 texture drawn into a 20px box.
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(x, y + 20);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(x + 20, y + 20);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(x + 20, y);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable t) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        if (handleColumnClick(this.availableList, false, mouseX, mouseY)) {
            return;
        }
        if (handleColumnClick(this.selectedList, true, mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Routes a click inside one column to whichever control it landed on.
     *
     * The row's own controls are tested before the list is allowed to treat it as a
     * plain selection, since every control here sits inside a row.
     */
    private boolean handleColumnClick(PackList list, boolean inUse, int mouseX, int mouseY) {
        int index = list.rowIndexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        List<ResourcePackRepository.Entry> source = inUse ? this.selected : this.available;
        if (index >= source.size()) {
            return false;
        }
        int right = list.rowRight();

        if (inUse) {
            if (mouseX >= right - 20) {
                // Back to available; it keeps repository order there, so append.
                this.available.add(this.selected.remove(index));
                return true;
            }
            if (mouseX >= right - 38 && mouseX < right - 22) {
                // Upper half of the row nudges up, lower half nudges down.
                boolean up = mouseY < list.rowTop(index) + 15;
                move(index, up ? -1 : 1);
                return true;
            }
        } else if (mouseX >= right - 20) {
            // New packs go on top: enabling one and not seeing it take effect is the
            // most common way this screen confuses people.
            this.selected.add(0, this.available.remove(index));
            return true;
        }

        list.mouseClicked(mouseX, mouseY);
        return true;
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= this.selected.size()) {
            return;
        }
        Collections.swap(this.selected, index, target);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
        this.availableList.mouseDragged(mouseY);
        this.selectedList.mouseDragged(mouseY);
        super.mouseClickMove(mouseX, mouseY, button, heldTime);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.availableList.mouseReleased();
        this.selectedList.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int notches = wheel > 0 ? 1 : -1;
        // Only the column under the pointer scrolls.
        if (this.mouseX < this.rightColumn) {
            this.availableList.mouseWheel(notches);
        } else {
            this.selectedList.mouseWheel(notches);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            apply();
            switchBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button.id == ID_FOLDER) {
            openFolder();
            return;
        }
        if (button.id == ID_DONE) {
            apply();
            switchBack();
        }
    }

    private void openFolder() {
        try {
            java.io.File dir = this.mc.getResourcePackRepository().getDirResourcepacks();
            // Same call vanilla uses; on the platforms where it is unsupported the
            // catch below keeps the screen alive.
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the resource pack folder", t);
        }
    }

    /**
     * Commits the selection.
     *
     * The order here matters and mirrors vanilla exactly: reverse back to
     * lowest-priority-first, hand the list to the repository, rewrite the options
     * list from it, save, then reload. Doing the reload before the options are
     * written would leave the two disagreeing if the game were closed mid-reload.
     */
    @SuppressWarnings("unchecked")
    private void apply() {
        ResourcePackRepository repository = this.mc.getResourcePackRepository();

        List<ResourcePackRepository.Entry> ordered =
                new ArrayList<ResourcePackRepository.Entry>(this.selected);
        Collections.reverse(ordered);
        repository.setRepositories(ordered);

        this.mc.gameSettings.resourcePacks.clear();
        for (ResourcePackRepository.Entry entry : ordered) {
            this.mc.gameSettings.resourcePacks.add(entry.getResourcePackName());
        }
        this.mc.gameSettings.saveOptions();
        // Everything: a resource pack can supply any kind of asset there is.
        FMLClientHandler.instance().refreshResources(ReloadRequirements.all());
        switchBack();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

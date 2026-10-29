package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import com.console.uky.compat.GuiButton;
import com.console.uky.compat.GuiScreen;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.ChatFormatting;
import com.console.uky.compat.GL11;

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
    private final List<Pack> available =
            new ArrayList<Pack>();
    /** Packs in use, highest priority first — the order shown, top-down. */
    private final List<Pack> selected =
            new ArrayList<Pack>();

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

    public GuiResourcePacksScreen(net.minecraft.client.gui.screens.Screen parent) {
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
                buttonHeight, I18n.get("pack.openFolder", new Object[0]),
                MenuButton.Style.NORMAL);
        folder.entrance(0.05F);
        this.buttonList.add(folder);

        MenuButton done = new MenuButton(ID_DONE, this.rightColumn, buttonY, half,
                buttonHeight, I18n.get("gui.done", new Object[0]),
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
        PackRepository repository = this.mc.getResourcePackRepository();
        repository.reload();

        // The game's own and the mods' resources are always on and cannot be moved;
        // they are left out here and put back by the repository on apply.
        List<Pack> inUse = new ArrayList<Pack>();
        for (Pack pack : repository.getSelectedPacks()) {
            if (!pack.isRequired() && !pack.isHidden()) {
                inUse.add(pack);
            }
        }
        List<Pack> all = new ArrayList<Pack>();
        for (Pack pack : repository.getAvailablePacks()) {
            if (!pack.isRequired() && !pack.isHidden()) {
                all.add(pack);
            }
        }
        all.removeAll(inUse);

        this.available.clear();
        this.available.addAll(all);

        this.selected.clear();
        this.selected.addAll(inUse);
        Collections.reverse(this.selected);
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

        this.fontRendererObj.drawString(
                I18n.get("resourcePack.title", new Object[0]).toUpperCase(),
                this.panelX1 + 16, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawColumnHeading(I18n.get("pack.available.title", new Object[0]),
                this.leftColumn);
        drawColumnHeading(I18n.get("pack.selected.title", new Object[0]),
                this.rightColumn);

        // Says which end of the selected column wins, which vanilla never does.
        // Wrapped rather than trimmed: the sentence is the whole point of it.
        drawWrapped(I18n.get("uky.resourcePack.topWins", new Object[0]),
                this.rightColumn, this.listTop - 24, this.columnWidth, 2,
                Draw.withAlpha(Theme.textDim, 0.65F * this.fadeAlpha), false);

        this.availableList.draw(mouseX, mouseY, this.fadeAlpha);
        this.selectedList.draw(mouseX, mouseY, this.fadeAlpha);

        if (this.selected.isEmpty()) {
            drawWrapped(I18n.get("uky.resourcePack.defaultOnly", new Object[0]),
                    this.rightColumn, this.listTop + 12, this.columnWidth - 8, 3,
                    Draw.withAlpha(Theme.textDisabled, 0.8F * this.fadeAlpha), true);
        }
        if (this.available.isEmpty()) {
            drawWrapped(I18n.get("uky.resourcePack.none", new Object[0]),
                    this.leftColumn, this.listTop + 12, this.columnWidth - 8, 3,
                    Draw.withAlpha(Theme.textDisabled, 0.8F * this.fadeAlpha), true);
        }
    }

    private void drawColumnHeading(String label, int x) {
        this.fontRendererObj.drawString(label.toUpperCase(), x, this.listTop - 48,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
        Draw.gradientH(x, this.listTop - 34, x + this.columnWidth, this.listTop - 33,
                Draw.withAlpha(Theme.accent, 0.35F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));
    }

    /** Wraps {@code text} to {@code width}, optionally centred, capped at {@code maxLines}. */
    private void drawWrapped(String text, int x, int y, int width, int maxLines, int colour,
                             boolean centred) {
        List<?> lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String line = String.valueOf(lines.get(i));
            int drawX = centred
                    ? x + (width - this.fontRendererObj.getStringWidth(line)) / 2
                    : x;
            this.fontRendererObj.drawString(line, drawX, y + i * 10, colour);
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

        private List<Pack> entries() {
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
            Pack entry = entries().get(index);

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
                    entry.getTitle().getString(), rowWidth - 34 - controls);
            this.fontRendererObj().drawString(name, textX, rowY + 5,
                    Draw.withAlpha(Theme.text, alpha));

            String description = stripFormatting(entry.getDescription().getString());
            this.fontRendererObj().drawString(
                    GuiResourcePacksScreen.this.fit(description,
                            rowWidth - 34 - controls),
                    textX, rowY + 16, Draw.withAlpha(Theme.textDim, 0.7F * alpha));

            drawRowControls(index, rowX, rowY, rowWidth, rowHeight, alpha);
        }

        private com.console.uky.compat.FontRenderer fontRendererObj() {
            return GuiResourcePacksScreen.this.fontRendererObj;
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
        return text == null ? "" : ChatFormatting.stripFormatting(text);
    }

    /**
     * The pack's own icon, or nothing if it has none.
     *
     * {@code bindTexturePackIcon} falls back to the default pack image when the pack
     * ships no {@code pack.png}, so this never draws a missing-texture checkerboard.
     */
    private void drawIcon(Pack entry, int x, int y, float alpha) {
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            net.minecraft.resources.ResourceLocation icon = iconOf(entry);
            if (icon == null) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                return;
            }
            GL11.bindTexture(icon);
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

    /** Icons by pack id; a null value means the pack has none. */
    private final java.util.Map<String, net.minecraft.resources.ResourceLocation> icons =
            new java.util.HashMap<String, net.minecraft.resources.ResourceLocation>();

    /** The pack's pack.png, read once, the way vanilla's own list reads it. */
    private net.minecraft.resources.ResourceLocation iconOf(Pack pack) {
        if (this.icons.containsKey(pack.getId())) {
            return this.icons.get(pack.getId());
        }
        net.minecraft.resources.ResourceLocation location = null;
        try (net.minecraft.server.packs.PackResources resources = pack.open()) {
            net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> png =
                    resources.getRootResource("pack.png");
            if (png != null) {
                try (java.io.InputStream in = png.get()) {
                    location = this.mc.getTextureManager().register("uky_pack_icon",
                            new net.minecraft.client.renderer.texture.DynamicTexture(
                                    com.mojang.blaze3d.platform.NativeImage.read(in)));
                }
            }
        } catch (Exception e) {
            UkyUI.LOGGER.warn("Could not read the icon of pack {}", pack.getId(), e);
        }
        this.icons.put(pack.getId(), location);
        return location;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
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
        List<Pack> source = inUse ? this.selected : this.available;
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
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        this.availableList.mouseReleased();
        this.selectedList.mouseReleased();
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = com.console.uky.compat.Mouse.getEventDWheel();
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
    protected void keyTyped(char typedChar, int keyCode) {
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
            net.minecraft.Util.getPlatform().openPath(this.mc.getResourcePackDirectory());
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
        PackRepository repository = this.mc.getResourcePackRepository();

        List<String> ordered = new ArrayList<String>();
        for (Pack entry : this.selected) {
            ordered.add(entry.getId());
        }
        Collections.reverse(ordered);
        // The repository puts the required packs back where they belong.
        repository.setSelected(ordered);
        // Vanilla's own bookkeeping: the options list, the save, and a reload only
        // if something actually changed.
        this.mc.options.updateResourcePacks(repository);
        switchBack();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

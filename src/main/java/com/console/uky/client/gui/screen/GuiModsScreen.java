package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod list: names on the left, details for the selection on the right.
 *
 * FML's own list works but is vanilla-styled and mixes the list and the detail
 * pane into one scrolling column. Splitting them means a long pack list stays
 * navigable while the description has room to breathe.
 */
public class GuiModsScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_CONFIG = 201;

    private final List<ModContainer> mods = new ArrayList<ModContainer>();

    private ModList list;
    private MenuButton configButton;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int detailX;

    public GuiModsScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void buildLayout() {
        this.mods.clear();
        this.mods.addAll(Loader.instance().getModList());

        int panelWidth = Math.min((int) (this.width * 0.72F), 440);
        this.panelX1 = Math.max(10, (this.width - panelWidth) / 2);
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(12, (int) (this.height * 0.10F));
        this.panelY2 = this.height - Math.max(12, (int) (this.height * 0.10F));

        int listWidth = (int) (panelWidth * 0.42F);
        this.detailX = this.panelX1 + 14 + listWidth + 14;

        if (this.list == null) {
            this.list = new ModList();
        }
        this.list.setBounds(this.panelX1 + 14, this.panelY1 + 34, listWidth,
                this.panelY2 - this.panelY1 - 76, 22);
        if (this.list.getSelected() < 0 && !this.mods.isEmpty()) {
            this.list.setSelected(0);
        }

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 14, this.panelY2 - 30,
                listWidth, 20, I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);

        this.configButton = new MenuButton(ID_CONFIG, this.detailX, this.panelY2 - 30, 110, 20,
                I18n.format("uky.mods.config", new Object[0]), MenuButton.Style.NORMAL);
        this.configButton.entrance(0.09F);
        this.buttonList.add(this.configButton);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.5F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 1.02F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.30F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.7F;
    }

    @Override
    protected int blackHolePose() {
        // From under the plane, low on the screen — the mod list sits above it.
        return LensLibrary.POSE_BELOW;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.list.update(this.delta);

        Draw.rect(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.80F * this.fadeAlpha));
        Draw.gradientH(panelX1, panelY1, panelX2, panelY1 + 1,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        this.fontRendererObj.drawString(I18n.format("uky.menu.mods", new Object[0]),
                panelX1 + 14, panelY1 + 14, Draw.withAlpha(Theme.text, this.fadeAlpha));
        String count = this.mods.size() + " " + I18n.format("uky.mods.loaded", new Object[0]);
        this.fontRendererObj.drawString(count,
                panelX2 - 14 - this.fontRendererObj.getStringWidth(count), panelY1 + 14,
                Draw.withAlpha(Theme.textDim, 0.8F * this.fadeAlpha));

        this.list.draw(mouseX, mouseY, this.fadeAlpha);
        drawDetails();
    }

    private void drawDetails() {
        int index = this.list.getSelected();
        if (index < 0 || index >= this.mods.size()) {
            this.configButton.visible = false;
            return;
        }
        ModContainer mod = this.mods.get(index);
        int x = this.detailX;
        int y = this.panelY1 + 34;
        int maxWidth = this.panelX2 - 14 - x;

        // Mod metadata is written by whoever made the mod, so none of these have a
        // length this layout can rely on — a long author list used to run off the
        // right of the panel.
        drawFitted(mod.getName(), x, y, maxWidth, Draw.withAlpha(Theme.text, this.fadeAlpha));
        y += 12;
        drawFitted(mod.getVersion(), x, y, maxWidth,
                Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));
        y += 11;
        drawFitted(mod.getModId(), x, y, maxWidth,
                Draw.withAlpha(Theme.textDim, 0.7F * this.fadeAlpha));
        y += 11;

        String authors = authorsOf(mod);
        if (!authors.isEmpty()) {
            drawFitted(authors, x, y, maxWidth,
                    Draw.withAlpha(Theme.textDim, 0.85F * this.fadeAlpha));
            y += 11;
        }
        y += 6;

        Draw.rect(x, y, x + maxWidth, y + 1, Draw.fade(Theme.separator, this.fadeAlpha));
        y += 8;

        String description = mod.getMetadata() == null ? "" : mod.getMetadata().description;
        if (description != null && !description.isEmpty()) {
            // drawSplitString is the one place vanilla's wrapping is exactly what we want.
            this.fontRendererObj.drawSplitString(description, x, y, maxWidth,
                    Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
        }

        this.configButton.visible = true;
        this.configButton.enabled = hasConfigScreen(mod);
    }

    /**
     * Author line from the mod's metadata.
     *
     * {@code authorList} is what mcmod.info fills in, but plenty of older mods only
     * set the free-text {@code credits} field, so both are worth checking before
     * giving up.
     */
    private String authorsOf(ModContainer mod) {
        if (mod.getMetadata() == null) {
            return "";
        }
        List<String> list = mod.getMetadata().authorList;
        if (list != null && !list.isEmpty()) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    joined.append(", ");
                }
                joined.append(list.get(i));
            }
            return I18n.format("uky.mods.by", new Object[0]) + " " + joined;
        }
        String credits = mod.getMetadata().credits;
        return credits == null ? "" : credits.trim();
    }

    /** FML only exposes a config GUI when the mod declared a factory. */
    private static boolean hasConfigScreen(ModContainer mod) {
        return mod.getGuiClassName() != null && !mod.getGuiClassName().isEmpty();
    }

    // ----------------------------------------------------------------- input --

    private final class ModList extends ScrollList {
        @Override
        public int rowCount() {
            return GuiModsScreen.this.mods.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            ModContainer mod = GuiModsScreen.this.mods.get(index);

            if (isSelected) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.accent, 0.12F * alpha));
                Draw.rect(rowX, rowY, rowX + 2, rowY + rowHeight,
                        Draw.withAlpha(Theme.accent, 0.9F * alpha));
            } else if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }

            int nameColor = isSelected ? Theme.textHover : Theme.text;
            GuiModsScreen.this.fontRendererObj.drawString(trim(mod.getName(), rowWidth - 14),
                    rowX + 8, rowY + 4, Draw.withAlpha(nameColor, alpha));
            GuiModsScreen.this.fontRendererObj.drawString(mod.getVersion(),
                    rowX + 8, rowY + 13, Draw.withAlpha(Theme.textDim, 0.7F * alpha));
        }

        private String trim(String text, int maxWidth) {
            if (GuiModsScreen.this.fontRendererObj.getStringWidth(text) <= maxWidth) {
                return text;
            }
            return GuiModsScreen.this.fit(text, maxWidth - 6);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
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
    protected void onAction(GuiButton button) {
        switch (button.id) {
            case ID_DONE:
                this.mc.displayGuiScreen(this.parent);
                break;
            case ID_CONFIG:
                openConfig();
                break;
            default:
                break;
        }
    }

    private void openConfig() {
        int index = this.list.getSelected();
        if (index < 0 || index >= this.mods.size()) {
            return;
        }
        ModContainer mod = this.mods.get(index);
        try {
            Class<?> factoryClass = Class.forName(mod.getGuiClassName(), true,
                    Loader.instance().getModClassLoader());
            cpw.mods.fml.client.IModGuiFactory factory =
                    (cpw.mods.fml.client.IModGuiFactory) factoryClass.newInstance();
            factory.initialize(this.mc);
            Class<? extends GuiScreen> screenClass = factory.mainConfigGuiClass();
            if (screenClass == null) {
                return;
            }
            GuiScreen screen = screenClass.getConstructor(GuiScreen.class).newInstance(this);
            this.mc.displayGuiScreen(screen);
        } catch (Exception e) {
            // A broken third-party factory must not take the menu down with it.
            com.console.uky.UkyUI.LOGGER.warn("Could not open config for " + mod.getModId(), e);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == 200) { // up
            this.list.moveSelection(-1);
            return;
        }
        if (keyCode == 208) { // down
            this.list.moveSelection(1);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

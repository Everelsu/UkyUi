package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.mods.ModConfigCatalog;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Theme;
import net.minecraftforge.fml.client.config.ConfigGuiType;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Another mod's settings, drawn in our widgets.
 *
 * Most mods do not draw their settings screen at all — they hand Forge a list of
 * {@link IConfigElement}, each of which knows its name, type, value, default and
 * permitted values, and Forge renders it. That list is a model, and a model can be
 * rendered by anyone, so a modpack's settings need not be a patchwork of every
 * screen style of the last decade.
 *
 * <p>The elements point at the mod's live properties, so editing here is editing the
 * real config. Saving follows Forge's own contract exactly: values are set on the
 * elements, then {@code OnConfigChangedEvent} is posted for the owning mod, which is
 * what every Forge mod listens for in order to write its file. Nothing here writes
 * another mod's config itself — that is the mod's own business and it knows where its
 * file is.
 */
public class GuiModConfigScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_DEFAULTS = 201;

    private final String modId;
    private final String heading;
    private final List<IConfigElement> elements;

    /** Set once anything is edited, so an untouched visit posts no event. */
    private boolean edited;
    private boolean requiresMcRestart;

    private GuiTextField editor;
    private int editing = -1;

    private ElementList list;
    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private GuiModConfigScreen(GuiScreen parent, String modId, String heading,
                               List<IConfigElement> elements) {
        super(parent);
        this.modId = modId;
        this.heading = heading;
        this.elements = elements;
    }

    /**
     * Builds the screen for a mod, or returns null if its settings cannot be redrawn.
     *
     * A null here is not a failure — it means the mod drew its own screen instead of
     * describing one, and the caller should open that.
     */
    public static GuiModConfigScreen forEntry(ModConfigCatalog.Entry entry, GuiScreen parent) {
        try {
            List<IConfigElement> elements = ModConfigCatalog.harvest(entry, parent);
            if (elements == null) {
                return null;
            }
            return new GuiModConfigScreen(parent, entry.modId(), entry.displayName(),
                    visible(elements));
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read the settings of mod {}", entry.modId(), t);
            return null;
        }
    }

    /** A category one level down, keeping the same mod id so saving still targets it. */
    private GuiModConfigScreen child(IConfigElement category) {
        String name = label(category);
        return new GuiModConfigScreen(this, this.modId, this.heading + "  ·  " + name,
                visible(category.getChildElements()));
    }

    @SuppressWarnings("unchecked")
    private static List<IConfigElement> visible(List<IConfigElement> source) {
        List<IConfigElement> kept = new ArrayList<IConfigElement>();
        if (source != null) {
            for (IConfigElement element : source) {
                // Mods mark internal entries as hidden and Forge honours it; a screen
                // that showed them would be showing more than the mod's own does.
                if (element != null && element.showInGui()) {
                    kept.add(element);
                }
            }
        }
        return kept;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.70F), 500);
        this.panelX1 = Math.max(10, (int) (this.width * 0.06F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        boolean cramped = this.height < 300;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 20) - buttonHeight;

        if (this.list == null) {
            this.list = new ElementList();
        }
        int listTop = this.panelY1 + 44;
        this.list.setBounds(this.panelX1 + 15, listTop, panelWidth - 30,
                Math.max(40, buttonY - 12 - listTop), 22);

        int gap = 8;
        int half = (panelWidth - 30 - gap) / 2;
        MenuButton defaults = new MenuButton(ID_DEFAULTS, this.panelX1 + 15, buttonY,
                half, buttonHeight, I18n.format("uky.modSettings.defaults", new Object[0]),
                MenuButton.Style.NORMAL);
        defaults.entrance(0.05F);
        this.buttonList.add(defaults);

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15 + half + gap, buttonY,
                half, buttonHeight, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.88F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.24F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.10F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.5F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_IN_PLANE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        float a = this.fadeAlpha;

        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.55F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));

        drawFitted(this.heading.toUpperCase(), this.panelX1 + 15, this.panelY1 + 14,
                this.panelX2 - this.panelX1 - 30, Draw.withAlpha(Theme.text, a));
        Draw.rect(this.panelX1 + 15, this.panelY1 + 32, this.panelX2 - 15, this.panelY1 + 33,
                Draw.fade(Theme.separator, a));

        this.list.update(this.delta);
        this.list.draw(mouseX, mouseY, a);

        if (this.editor != null) {
            this.editor.drawTextBox();
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        if (button.id == ID_DONE) {
            commitEditor();
            applyAndClose();
        } else if (button.id == ID_DEFAULTS) {
            for (IConfigElement element : this.elements) {
                if (element.isProperty()) {
                    element.setToDefault();
                    this.edited = true;
                    this.requiresMcRestart |= element.requiresMcRestart();
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (this.editor != null) {
            if (keyCode == 1 || keyCode == 28) {
                commitEditor();
                return;
            }
            this.editor.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == 1) {
            applyAndClose();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);

        if (this.editor != null) {
            commitEditor();
            return;
        }
        if (this.list.mouseClicked(mouseX, mouseY)) {
            int index = this.list.rowIndexAt(mouseX, mouseY);
            if (index >= 0 && index < this.elements.size()) {
                activate(index, mouseX);
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

    /** Click behaviour depends entirely on what kind of thing the row is. */
    private void activate(int index, int mouseX) {
        IConfigElement element = this.elements.get(index);

        if (!element.isProperty()) {
            this.mc.displayGuiScreen(child(element));
            return;
        }

        // The reset arrow sits at the right end and takes precedence over the row.
        if (mouseX > this.list.rowRight() - 14 && !element.isDefault()) {
            element.setToDefault();
            this.edited = true;
            this.requiresMcRestart |= element.requiresMcRestart();
            return;
        }

        if (element.getType() == ConfigGuiType.BOOLEAN && !element.isList()) {
            set(element, Boolean.valueOf(!isTrue(element.get())));
            return;
        }

        String[] valid = element.getValidValues();
        if (valid != null && valid.length > 0 && !element.isList()) {
            String current = String.valueOf(element.get());
            int at = 0;
            for (int i = 0; i < valid.length; i++) {
                if (valid[i].equals(current)) {
                    at = i;
                    break;
                }
            }
            set(element, valid[(at + 1) % valid.length]);
            return;
        }

        if (element.isList()) {
            // Lists want an editor of their own; opening one here would be a worse
            // answer than saying plainly that this one is not editable yet.
            return;
        }

        beginEditing(index, element);
    }

    private void beginEditing(int index, IConfigElement element) {
        int rowY = this.list.rowTop(index);
        int width = 120;
        this.editor = new GuiTextField(0, this.fontRenderer,
                this.list.rowRight() - width - 16, rowY + 5, width, 14);
        this.editor.setMaxStringLength(256);
        this.editor.setText(String.valueOf(element.get()));
        this.editor.setFocused(true);
        this.editing = index;
    }

    /**
     * Parses what was typed and keeps it only if it fits the element's type.
     *
     * A number field that silently accepted "seven" would write nonsense into someone
     * else's config file, so a value that will not parse is simply dropped.
     */
    private void commitEditor() {
        if (this.editor == null) {
            return;
        }
        String text = this.editor.getText().trim();
        IConfigElement element = this.elements.get(this.editing);
        this.editor = null;
        this.editing = -1;

        try {
            if (element.getType() == ConfigGuiType.INTEGER) {
                set(element, Integer.valueOf(Integer.parseInt(text)));
            } else if (element.getType() == ConfigGuiType.DOUBLE) {
                set(element, Double.valueOf(Double.parseDouble(text)));
            } else {
                set(element, text);
            }
        } catch (NumberFormatException ignored) {
            // Left as it was.
        }
    }

    @SuppressWarnings("unchecked")
    private void set(IConfigElement element, Object value) {
        element.set(value);
        this.edited = true;
        this.requiresMcRestart |= element.requiresMcRestart();
    }

    /**
     * Hands the change back to the mod that owns it.
     *
     * This is what Forge's own screen does on the way out, and it is the only correct
     * way to do it from outside: the mod holds its {@code Configuration} and is the
     * only thing that knows when and where to write it.
     */
    private void applyAndClose() {
        if (this.edited && Loader.isModLoaded(this.modId)) {
            try {
                boolean worldRunning = this.mc.world != null;
                // 1.7.10 posted these on FML's own bus; 1.12.2 has only the one.
                MinecraftForge.EVENT_BUS.post(
                        new ConfigChangedEvent.OnConfigChangedEvent(this.modId, null,
                                worldRunning, this.requiresMcRestart));
                MinecraftForge.EVENT_BUS.post(
                        new ConfigChangedEvent.PostConfigChangedEvent(this.modId, null,
                                worldRunning, this.requiresMcRestart));
            } catch (Throwable t) {
                UkyUI.LOGGER.warn("Mod {} threw while saving its config", this.modId, t);
            }
        }
        switchBack();
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue()
                : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String label(IConfigElement element) {
        String key = element.getLanguageKey();
        if (key != null && !key.isEmpty()) {
            String translated = I18n.format(key, new Object[0]);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return element.getName();
    }

    // ------------------------------------------------------------------ list --

    private final class ElementList extends ScrollList {

        @Override
        public int rowCount() {
            return GuiModConfigScreen.this.elements.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            IConfigElement element = GuiModConfigScreen.this.elements.get(index);
            boolean category = !element.isProperty();

            if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }
            if (category) {
                Draw.rect(rowX, rowY + 2, rowX + 2, rowY + rowHeight - 2,
                        Draw.withAlpha(Theme.accent, 0.55F * alpha));
            }

            int textY = rowY + (rowHeight - 8) / 2;
            int changed = element.isProperty() && !element.isDefault() ? 14 : 0;

            if (category) {
                drawFitted(label(element), rowX + 10, textY, rowWidth - 30,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
                Icons.forward(rowX + rowWidth - 8, rowY + rowHeight / 2.0F, 7,
                        Draw.withAlpha(Theme.textDim, (isHovered ? 0.9F : 0.45F) * alpha));
                return;
            }

            if (element.getType() == ConfigGuiType.BOOLEAN && !element.isList()) {
                drawToggle(rowX + rowWidth - 24 - changed, rowY + rowHeight / 2.0F,
                        isTrue(element.get()), alpha);
                drawFitted(label(element), rowX + 10, textY,
                        rowWidth - 40 - changed - 10,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
            } else {
                String value = GuiModConfigScreen.this.editing == index ? ""
                        : String.valueOf(element.isList()
                                ? I18n.format("uky.modSettings.list", new Object[0])
                                : element.get());
                int valueWidth = drawFittedRight(value, rowX + rowWidth - 8 - changed, textY,
                        rowWidth / 2, Draw.withAlpha(Theme.textDim, 0.85F * alpha));
                drawFitted(label(element), rowX + 10, textY,
                        rowWidth - 18 - changed - valueWidth - 10,
                        Draw.withAlpha(isHovered ? Theme.textHover : Theme.text, alpha));
            }

            // Only where there is something to undo, so the arrow means "this differs
            // from what the mod shipped" as much as it means "click to reset".
            if (changed > 0) {
                Icons.refresh(rowX + rowWidth - 7, rowY + rowHeight / 2.0F, 9,
                        Draw.withAlpha(Theme.textDim, 0.8F * alpha));
            }
        }

        /** The same square switch the rest of the interface uses. */
        private void drawToggle(float x, float cy, boolean on, float alpha) {
            float w = 20.0F;
            float h = 9.0F;
            Draw.rect(x, cy - h / 2.0F, x + w, cy + h / 2.0F,
                    Draw.fade(on ? Draw.withAlpha(Theme.accent, 0.45F)
                            : Draw.withAlpha(Theme.background, 0.8F), alpha));
            Draw.border(x, cy - h / 2.0F, x + w, cy + h / 2.0F, 1.0F,
                    Draw.fade(on ? Theme.accent : Theme.panelBorder, alpha));

            float inset = 1.5F;
            float knobW = (w - inset * 2.0F) * 0.42F;
            float knobX = x + inset + (on ? (w - inset * 2.0F - knobW) : 0.0F);
            Draw.rect(knobX, cy - h / 2.0F + inset, knobX + knobW, cy + h / 2.0F - inset,
                    Draw.fade(on ? Theme.accent : Theme.textDim, alpha));
        }
    }
}

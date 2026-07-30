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
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Key bindings.
 *
 * Vanilla pages through a fixed-height list of grey buttons two columns wide, with
 * no search and no indication that two actions share a key until one of them
 * mysteriously stops working. With a modpack's worth of bindings that is close to
 * unusable.
 *
 * <p>Here the list is searchable, grouped under its categories, and every binding
 * that shares a key with another is marked on both rows — the conflict is a property
 * of the pair, so showing it on only one of them would be arbitrary. Rebinding is
 * click-then-press; Escape clears a binding, as in vanilla.
 */
public class GuiControlsScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_RESET_ALL = 201;

    private final GameSettings settings;

    /**
     * Flattened list of rows: category headers and bindings interleaved, because a
     * scrolling list wants one index space and the grouping is only visual.
     */
    private final List<Object> rows = new ArrayList<Object>();
    /** Key codes bound more than once, so both sides of a clash can be marked. */
    private final List<Integer> clashing = new ArrayList<Integer>();

    private GuiTextField search;
    private BindingList list;

    /** Binding awaiting a key press, or null when not rebinding. */
    private KeyBinding capturing;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private float resetHover;
    private int mouseX;
    private int mouseY;

    /** Marks a category heading in {@link #rows}. */
    private static final class Header {
        final String label;

        Header(String label) {
            this.label = label;
        }
    }

    public GuiControlsScreen(GuiScreen parent, GameSettings settings) {
        super(parent);
        this.settings = settings;
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
        this.settings.saveOptions();
        super.onGuiClosed();
    }

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.62F), 440);
        this.panelX1 = Math.max(10, (int) (this.width * 0.05F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        String previous = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(this.fontRendererObj,
                this.panelX1 + 15, this.panelY1 + 36, panelWidth - 30, 16);
        this.search.setMaxStringLength(48);
        this.search.setEnableBackgroundDrawing(false);
        this.search.setText(previous);
        this.search.setFocused(true);

        boolean cramped = this.height < 300;
        int buttonHeight = cramped ? 20 : 24;
        int buttonY = this.panelY2 - (cramped ? 10 : 20) - buttonHeight;

        if (this.list == null) {
            this.list = new BindingList();
        }
        int listTop = this.panelY1 + 60;
        this.list.setBounds(this.panelX1 + 15, listTop, panelWidth - 30,
                Math.max(40, buttonY - 12 - listTop), 20);

        rebuildRows();

        int gap = 8;
        int half = (panelWidth - 30 - gap) / 2;
        MenuButton reset = new MenuButton(ID_RESET_ALL, this.panelX1 + 15, buttonY,
                half, buttonHeight, I18n.format("controls.resetAll", new Object[0]),
                MenuButton.Style.NORMAL);
        reset.entrance(0.05F);
        this.buttonList.add(reset);

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 15 + half + gap, buttonY,
                half, buttonHeight, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /**
     * Rebuilds the visible rows and the clash set.
     *
     * A category header is only emitted when at least one of its bindings survived
     * the filter, so searching never leaves an empty heading behind.
     */
    private void rebuildRows() {
        String query = this.search.getText().trim().toLowerCase();
        this.rows.clear();
        this.clashing.clear();

        KeyBinding[] all = this.settings.keyBindings;

        // A key is in conflict if two *different* bindings hold it. Unbound (0) does
        // not count: any number of actions may be left unbound.
        for (int i = 0; i < all.length; i++) {
            int code = all[i].getKeyCode();
            if (code == 0 || this.clashing.contains(Integer.valueOf(code))) {
                continue;
            }
            for (int j = i + 1; j < all.length; j++) {
                if (all[j].getKeyCode() == code) {
                    this.clashing.add(Integer.valueOf(code));
                    break;
                }
            }
        }

        // Grouped in the order the categories first appear, which is the order the
        // game registered them — mods after vanilla, which is what people expect.
        Map<String, List<KeyBinding>> grouped = new LinkedHashMap<String, List<KeyBinding>>();
        for (int i = 0; i < all.length; i++) {
            KeyBinding binding = all[i];
            if (!matches(binding, query)) {
                continue;
            }
            String category = binding.getKeyCategory();
            List<KeyBinding> bucket = grouped.get(category);
            if (bucket == null) {
                bucket = new ArrayList<KeyBinding>();
                grouped.put(category, bucket);
            }
            bucket.add(binding);
        }

        for (Map.Entry<String, List<KeyBinding>> entry : grouped.entrySet()) {
            this.rows.add(new Header(I18n.format(entry.getKey(), new Object[0])));
            this.rows.addAll(entry.getValue());
        }
    }

    private boolean matches(KeyBinding binding, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (I18n.format(binding.getKeyDescription(), new Object[0]).toLowerCase().contains(query)) {
            return true;
        }
        // Searching by the key itself is the fast way to answer "what is on F?".
        return keyName(binding.getKeyCode()).toLowerCase().contains(query);
    }

    /** Display name for a key code, including mouse buttons and "unbound". */
    private static String keyName(int code) {
        if (code == 0) {
            return I18n.format("key.unbound", new Object[0]);
        }
        if (code < 0) {
            // Mouse buttons are stored as -100 + button.
            return I18n.format("key.mouseButton", new Object[]{Integer.valueOf(code + 101)});
        }
        String name = Keyboard.getKeyName(code);
        return name == null ? "?" : name;
    }

    // --------------------------------------------------------------- drawing --

    /**
     * Low and hard right, almost edge-on. The settings screen this is opened from
     * looks down on the disk from above, so arriving here swings the camera down
     * and around rather than cutting to a different picture.
     */
    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.90F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.80F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.11F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.60F;
    }

    @Override
    protected int blackHolePose() {
        return LensLibrary.POSE_IN_PLANE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.list.update(this.delta);

        drawGlassPanel();

        this.fontRendererObj.drawString(
                I18n.format("controls.title", new Object[0]).toUpperCase(),
                this.panelX1 + 15, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        // Standing instruction while a key is being captured — without it the screen
        // gives no clue that it is waiting for input.
        if (this.capturing != null) {
            String hint = I18n.format("uky.controls.press", new Object[0]);
            this.fontRendererObj.drawString(hint,
                    this.panelX2 - 15 - this.fontRendererObj.getStringWidth(hint),
                    this.panelY1 + 14, Draw.withAlpha(Theme.accent, 0.95F * this.fadeAlpha));
        }

        drawSearchBox();
        this.list.draw(mouseX, mouseY, this.fadeAlpha);
    }

    private void drawGlassPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.52F * a),
                Draw.withAlpha(Theme.background, 0.30F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
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
                    I18n.format("uky.controls.search", new Object[0]),
                    this.search.xPosition, this.search.yPosition,
                    Draw.withAlpha(Theme.textDim, 0.5F * this.fadeAlpha));
        }
        this.search.drawTextBox();
    }

    private final class BindingList extends ScrollList {
        @Override
        public int rowCount() {
            return GuiControlsScreen.this.rows.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            Object row = GuiControlsScreen.this.rows.get(index);
            if (row instanceof Header) {
                drawHeaderRow((Header) row, rowX, rowY, rowWidth, rowHeight, alpha);
            } else {
                drawBindingRow((KeyBinding) row, rowX, rowY, rowWidth, rowHeight,
                        isHovered, alpha);
            }
        }

        @Override
        protected void onRowClicked(int index) {
            Object row = GuiControlsScreen.this.rows.get(index);
            if (row instanceof KeyBinding) {
                GuiControlsScreen.this.beginCapture((KeyBinding) row);
            }
        }
    }

    private void drawHeaderRow(Header header, int rowX, int rowY, int rowWidth, int rowHeight,
                               float alpha) {
        int textY = rowY + (rowHeight - 8) / 2;
        this.fontRendererObj.drawString(header.label.toUpperCase(), rowX + 2, textY,
                Draw.withAlpha(Theme.accent, 0.85F * alpha));
        // Rule running from the label to the right edge, so the grouping reads without
        // a heavy band behind it.
        int labelEnd = rowX + 6 + this.fontRendererObj.getStringWidth(header.label.toUpperCase());
        Draw.gradientH(labelEnd, textY + 3, rowX + rowWidth, textY + 4,
                Draw.withAlpha(Theme.accent, 0.35F * alpha),
                Draw.withAlpha(Theme.accent, 0.0F));
    }

    private void drawBindingRow(KeyBinding binding, int rowX, int rowY, int rowWidth,
                                int rowHeight, boolean isHovered, float alpha) {
        boolean capturingThis = binding == this.capturing;
        boolean conflict = binding.getKeyCode() != 0
                && this.clashing.contains(Integer.valueOf(binding.getKeyCode()));
        boolean changed = binding.getKeyCode() != binding.getKeyCodeDefault();

        if (isHovered || capturingThis) {
            Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                    Draw.withAlpha(capturingThis ? Theme.accent : Theme.text,
                            (capturingThis ? 0.14F : 0.06F) * alpha));
        }

        String label = I18n.format(binding.getKeyDescription(), new Object[0]);
        this.fontRendererObj.drawString(
                fit(label, rowWidth - 108),
                rowX + 6, rowY + (rowHeight - 8) / 2,
                Draw.withAlpha(conflict ? Theme.danger : Theme.text, alpha));

        // The key sits in a box on the right, which is also the click target.
        int boxWidth = 74;
        int boxX = rowX + rowWidth - boxWidth - (changed ? 16 : 0);
        int boxY = rowY + 2;
        int boxH = rowHeight - 4;

        int edge = capturingThis ? Theme.accent : (conflict ? Theme.danger : Theme.separator);
        Draw.rect(boxX, boxY, boxX + boxWidth, boxY + boxH,
                Draw.withAlpha(0x000000, 0.45F * alpha));
        Draw.border(boxX, boxY, boxX + boxWidth, boxY + boxH, 1.0F,
                capturingThis || conflict
                        ? Draw.withAlpha(edge, 0.9F * alpha)
                        : Draw.fade(Theme.separator, alpha));

        String key = capturingThis
                ? "> " + I18n.format("uky.controls.press", new Object[0]) + " <"
                : keyName(binding.getKeyCode());
        key = fit(key, boxWidth - 8);
        int keyColour = capturingThis ? Theme.accent
                : (conflict ? Theme.danger : (binding.getKeyCode() == 0 ? Theme.textDisabled
                        : Theme.text));
        this.fontRendererObj.drawString(key,
                boxX + (boxWidth - this.fontRendererObj.getStringWidth(key)) / 2,
                boxY + (boxH - 8) / 2, Draw.withAlpha(keyColour, alpha));

        // Reset arrow, shown only where there is something to reset.
        if (changed) {
            Icons.refresh(rowX + rowWidth - 7, rowY + rowHeight / 2.0F, 9,
                    Draw.withAlpha(Theme.textDim, 0.8F * alpha));
        }
    }

    // ----------------------------------------------------------------- input --

    private void beginCapture(KeyBinding binding) {
        this.capturing = binding;
    }

    private void applyKey(int code) {
        if (this.capturing == null) {
            return;
        }
        this.settings.setOptionKeyBinding(this.capturing, code);
        this.capturing = null;
        // Rebuilds the lookup the game uses at runtime; without it the change does
        // not take effect until a restart.
        KeyBinding.resetKeyBindingArrayAndHash();
        rebuildRows();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // While capturing, any click is the binding — including a mouse button, which
        // is a legitimate thing to bind.
        if (this.capturing != null) {
            applyKey(-100 + button);
            return;
        }

        this.search.mouseClicked(mouseX, mouseY, button);

        // The reset arrow sits inside the row, so it has to be tested before the row
        // click hands off to rebinding.
        int hit = rowAt(mouseX, mouseY);
        if (hit >= 0) {
            Object row = this.rows.get(hit);
            if (row instanceof KeyBinding) {
                KeyBinding binding = (KeyBinding) row;
                if (binding.getKeyCode() != binding.getKeyCodeDefault()
                        && mouseX >= this.list.rowRight() - 16) {
                    this.settings.setOptionKeyBinding(binding, binding.getKeyCodeDefault());
                    KeyBinding.resetKeyBindingArrayAndHash();
                    rebuildRows();
                    return;
                }
            }
        }

        if (this.list.mouseClicked(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    /** Index of the row under the pointer, or -1. */
    private int rowAt(int mouseX, int mouseY) {
        return this.list.rowIndexAt(mouseX, mouseY);
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
        if (this.capturing != null) {
            // Escape clears the binding rather than leaving capture, matching vanilla.
            applyKey(keyCode == Keyboard.KEY_ESCAPE ? 0 : keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.settings.saveOptions();
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            rebuildRows();
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
        if (button.id == ID_RESET_ALL) {
            KeyBinding[] all = this.settings.keyBindings;
            for (int i = 0; i < all.length; i++) {
                this.settings.setOptionKeyBinding(all[i], all[i].getKeyCodeDefault());
            }
            KeyBinding.resetKeyBindingArrayAndHash();
            rebuildRows();
            return;
        }
        if (button.id == ID_DONE) {
            this.settings.saveOptions();
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

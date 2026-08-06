package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;

/**
 * Adding a server, editing one, and connecting to an address once.
 *
 * All three are the same form with a different number of fields, so they are one
 * screen with a mode rather than two vanilla dialogs that look nothing like the
 * card grid they are opened from. Vanilla's versions are plain grey boxes on the
 * dirt background; this keeps the black hole and the panel language.
 *
 * <p>The result is handed back the way vanilla's are, through the parent's
 * {@code confirmClicked}, so {@code GuiServersScreen} keeps the single dispatch
 * point it already has for adding, editing and connecting.
 */
public class GuiServerEditScreen extends MenuScreen {

    public enum Mode {
        /** New entry, kept in the list. */
        ADD,
        /** Existing entry. */
        EDIT,
        /** One-off connection, deliberately not saved. */
        DIRECT
    }

    private static final int ID_CONFIRM = 1;
    private static final int ID_CANCEL = 2;

    private final Mode mode;
    private final ServerData draft;

    private GuiTextField nameField;
    private GuiTextField addressField;
    /** Which field has focus; the two are driven by hand so Tab can move between them. */
    private boolean addressFocused;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private float confirmHover;
    private float cancelHover;
    private float resourceHover;
    private int mouseX;
    private int mouseY;

    private boolean done;

    public GuiServerEditScreen(GuiScreen parent, Mode mode, ServerData draft) {
        super(parent);
        this.mode = mode;
        this.draft = draft;
    }

    private boolean hasName() {
        return this.mode != Mode.DIRECT;
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
        super.onGuiClosed();
    }

    @Override
    protected void buildLayout() {
        int panelWidth = Math.min((int) (this.width * 0.56F), 320);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;

        // Sized to what is actually laid out. The resource row and the buttons are
        // both positioned back from the bottom edge, so any slack in this figure
        // shows up as a hole in the middle of the form rather than as margin.
        int rows = hasName() ? 2 : 1;
        int panelHeight = 52 + rows * 40 + (this.mode == Mode.DIRECT ? 0 : 26) + 44;
        this.panelY1 = Math.max(20, (this.height - panelHeight) / 2);
        this.panelY2 = this.panelY1 + panelHeight;

        int fieldWidth = panelWidth - 36;
        int y = this.panelY1 + 52;

        if (hasName()) {
            this.nameField = new GuiTextField(0, this.fontRenderer,
                    this.panelX1 + 18, y, fieldWidth, 16);
            this.nameField.setMaxStringLength(64);
            this.nameField.setEnableBackgroundDrawing(false);
            this.nameField.setText(this.draft.serverName == null ? "" : this.draft.serverName);
            y += 40;
        }

        this.addressField = new GuiTextField(1, this.fontRenderer,
                this.panelX1 + 18, y, fieldWidth, 16);
        this.addressField.setMaxStringLength(128);
        this.addressField.setEnableBackgroundDrawing(false);
        this.addressField.setText(this.draft.serverIP == null ? "" : this.draft.serverIP);

        // The address is the one field that must be filled, so it starts focused when
        // there is nothing else to type.
        this.addressFocused = !hasName();
        applyFocus();
    }

    private void applyFocus() {
        if (this.nameField != null) {
            this.nameField.setFocused(!this.addressFocused);
        }
        this.addressField.setFocused(this.addressFocused);
    }

    private int buttonRowY() {
        return this.panelY2 - 32;
    }

    private int resourceRowY() {
        return buttonRowY() - 26;
    }

    // --------------------------------------------------------------- drawing --

    /**
     * Plain dark, like the world and server lists this is reached from.
     *
     * The hole was tried here and does not belong: everything on the multiplayer path
     * sits on flat dark, so a lit backdrop on one dialog in the middle of that flow
     * reads as a different application. It also costs a full trace to draw behind a
     * form that covers most of it.
     */
    @Override
    protected boolean isVoid() {
        return true;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        drawPanel();

        String title = I18n.format(titleKey(), new Object[0]);
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, (this.width - titleWidth) / 2, this.panelY1 + 16,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        if (hasName()) {
            drawField(this.nameField, I18n.format("addServer.enterName", new Object[0]), false);
        }
        drawField(this.addressField, I18n.format("addServer.enterIp", new Object[0]), true);

        if (this.mode != Mode.DIRECT) {
            drawResourceToggle();
        }
        drawButtons();
    }

    private String titleKey() {
        switch (this.mode) {
            case DIRECT: return "selectServer.direct";
            case EDIT: return "selectServer.edit";
            default: return "selectServer.add";
        }
    }

    private void drawPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.62F * a),
                Draw.withAlpha(Theme.background, 0.40F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(this.panelX1, this.panelY1, this.panelX2, this.panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.10F * a));
    }

    /** Caption above, dark strip, and a rule that lights up when focused. */
    private void drawField(GuiTextField field, String caption, boolean withGlobe) {
        float x1 = field.x - 5;
        float x2 = field.x + field.getWidth() + 5;
        float y1 = field.y - 4;
        float y2 = field.y + 12;

        this.fontRenderer.drawString(caption, (int) x1 + 1, (int) y1 - 12,
                Draw.withAlpha(Theme.textDim, 0.85F * this.fadeAlpha));
        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(0x000000, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2, Draw.withAlpha(
                field.isFocused() ? Theme.accent : Theme.separator, this.fadeAlpha));

        if (withGlobe) {
            Icons.globe(x2 - 9, (y1 + y2) / 2.0F, 9,
                    Draw.withAlpha(Theme.textDim, 0.55F * this.fadeAlpha));
        }
        field.drawTextBox();
    }

    /**
     * Server resource packs: ask / allow / block, cycled in place.
     *
     * Vanilla puts this on a full-width button whose label is the entire sentence;
     * here the setting is the label and the value sits to the right, matching how
     * every other option in this interface reads.
     */
    private void drawResourceToggle() {
        int x1 = this.panelX1 + 18;
        int x2 = this.panelX2 - 18;
        int y = resourceRowY();
        boolean over = inside(x1, y, x2 - x1, 18);
        this.resourceHover = Ease.approach(this.resourceHover, over ? 1.0F : 0.0F,
                0.05F, this.delta);

        Draw.rect(x1, y, x2, y + 18,
                Draw.withAlpha(0x000000, (0.42F + this.resourceHover * 0.2F) * this.fadeAlpha));
        Draw.rect(x1, y, x1 + 2, y + 18,
                Draw.withAlpha(Theme.accent, (0.7F + this.resourceHover * 0.3F) * this.fadeAlpha));

        // The enum carries its own translated label, so there is no key to rebuild.
        String value = this.draft.getResourceMode().getMotd().getUnformattedText();
        // Value first: it is the part that changes, so it keeps the width it needs
        // and the caption beside it takes whatever is left.
        int valueWidth = drawFittedRight(value, x2 - 8, y + 5, (x2 - x1) / 2,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.accent, this.resourceHover),
                        this.fadeAlpha));

        String label = I18n.format("addServer.resourcePack", new Object[0]);
        drawFitted(label, x1 + 8, y + 5, x2 - 8 - valueWidth - 6 - (x1 + 8),
                Draw.withAlpha(Theme.text, this.fadeAlpha));
    }

    private void drawButtons() {
        int gap = 8;
        int x1 = this.panelX1 + 18;
        int total = this.panelX2 - 18 - x1;
        int half = (total - gap) / 2;
        int y = buttonRowY();

        boolean overConfirm = inside(x1, y, half, 20);
        boolean overCancel = inside(x1 + half + gap, y, half, 20);
        this.confirmHover = Ease.approach(this.confirmHover, overConfirm ? 1.0F : 0.0F,
                0.05F, this.delta);
        this.cancelHover = Ease.approach(this.cancelHover, overCancel ? 1.0F : 0.0F,
                0.05F, this.delta);

        boolean enabled = canConfirm();
        float confirmAlpha = this.fadeAlpha * (enabled ? 1.0F : 0.35F);
        int fill = Draw.mix(Theme.accent, Theme.textHover, this.confirmHover * 0.2F);
        Draw.rect(x1, y, x1 + half, y + 20,
                Draw.withAlpha(fill, (0.62F + this.confirmHover * 0.18F) * confirmAlpha));
        if (enabled && this.confirmHover > 0.02F) {
            Draw.glow(x1, y, x1 + half, y + 20, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.3F * this.confirmHover * this.fadeAlpha), 4);
        }
        centred(confirmLabel(), x1, half, y + 6, Draw.withAlpha(0x0B0B0E, confirmAlpha));

        int cancelX = x1 + half + gap;
        Draw.rect(cancelX, y, cancelX + half, y + 20,
                Draw.withAlpha(0x000000, (0.5F + this.cancelHover * 0.2F) * this.fadeAlpha));
        Draw.border(cancelX, y, cancelX + half, y + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.cancelHover),
                        this.fadeAlpha));
        centred(I18n.format("gui.cancel", new Object[0]), cancelX, half, y + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.cancelHover),
                        this.fadeAlpha));
    }

    private String confirmLabel() {
        return this.mode == Mode.DIRECT
                ? I18n.format("selectServer.select", new Object[0])
                : I18n.format("addServer.add", new Object[0]);
    }

    private void centred(String text, int x, int width, int y, int colour) {
        String trimmed = fit(text, width - 8);
        int w = this.fontRenderer.getStringWidth(trimmed);
        this.fontRenderer.drawString(trimmed, x + (width - w) / 2, y, colour);
    }

    private boolean inside(int x, int y, int width, int height) {
        return this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + height;
    }

    private boolean canConfirm() {
        String address = this.addressField.getText().trim();
        if (address.isEmpty()) {
            return false;
        }
        return !hasName() || !this.nameField.getText().trim().isEmpty();
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        if (hasName() && this.nameField.getText() != null) {
            this.nameField.mouseClicked(mouseX, mouseY, button);
            if (this.nameField.isFocused()) {
                this.addressFocused = false;
                applyFocus();
            }
        }
        this.addressField.mouseClicked(mouseX, mouseY, button);
        if (this.addressField.isFocused()) {
            this.addressFocused = true;
            applyFocus();
        }

        if (this.mode != Mode.DIRECT) {
            int rx1 = this.panelX1 + 18;
            if (inside(rx1, resourceRowY(), this.panelX2 - 18 - rx1, 18)) {
                cycleResourceMode();
                return;
            }
        }

        int gap = 8;
        int x1 = this.panelX1 + 18;
        int half = (this.panelX2 - 18 - x1 - gap) / 2;
        int y = buttonRowY();
        if (inside(x1, y, half, 20)) {
            confirm();
            return;
        }
        if (inside(x1 + half + gap, y, half, 20)) {
            cancel();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private void cycleResourceMode() {
        ServerData.ServerResourceMode[] modes = ServerData.ServerResourceMode.values();
        int next = (this.draft.getResourceMode().ordinal() + 1) % modes.length;
        this.draft.setResourceMode(modes[next]);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            cancel();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            confirm();
            return;
        }
        if (keyCode == 15 && hasName()) {
            this.addressFocused = !this.addressFocused;
            applyFocus();
            return;
        }
        if (hasName()) {
            this.nameField.textboxKeyTyped(typedChar, keyCode);
        }
        this.addressField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        if (hasName()) {
            this.nameField.updateCursorCounter();
        }
        this.addressField.updateCursorCounter();
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control here is drawn, not a GuiButton.
    }

    /**
     * Writes the fields into the draft and hands back through the parent, exactly as
     * vanilla's dialogs do — so the servers screen needs no new plumbing.
     */
    private void confirm() {
        if (this.done || !canConfirm()) {
            return;
        }
        this.done = true;
        if (hasName()) {
            this.draft.serverName = this.nameField.getText().trim();
        }
        this.draft.serverIP = this.addressField.getText().trim();
        this.parent.confirmClicked(true, 0);
    }

    private void cancel() {
        if (this.done) {
            return;
        }
        this.done = true;
        this.parent.confirmClicked(false, 0);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

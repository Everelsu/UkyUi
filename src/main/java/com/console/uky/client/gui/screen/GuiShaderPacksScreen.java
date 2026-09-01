package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.MenuOptionButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.mods.ShaderOptions;
import com.console.uky.client.mods.ShaderPacks;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Shader packs, in this pack's language.
 *
 * <p>Iris ships a screen of its own and this mod used to hand the player straight to
 * it, which is the one thing on the Graphics tab that opened something drawn in
 * somebody else's style. It also did not survive the trip: over a world it left the
 * world showing through where its list should have been, and from the title screen it
 * left a dark rectangle there — in both cases with none of the packs on it. The
 * switches around the hole were drawn; the list, which is the entire point of the
 * screen, was not.
 *
 * <p>So the packs are read as a model — see {@link ShaderPacks} — and drawn here. What
 * the screen offers is what Iris's offered: the switch, the packs, the folder they live
 * in, the loaded pack's own settings, and Cancel / Apply / Done over the lot.
 *
 * <h2>Nothing happens until Apply</h2>
 *
 * <p>Picking a row stages a pack rather than loading it. A load is not cheap — the
 * whole pipeline is destroyed and rebuilt, which is a real stall on a heavy pack — so
 * clicking through five packs to see their names must not be five recompiles. Apply is
 * what commits, Done applies and leaves, and Cancel drops what was staged, including
 * anything queued on the pack's own settings screen.
 *
 * <h2>Both places it is opened from</h2>
 *
 * <p>A shader pack cannot be judged against a menu backdrop, so in a world this screen
 * keeps the world: the panel sits on a scrim over live gameplay, the way the pause menu
 * does, and applying a pack is visible behind it immediately. Out of a world it takes
 * the usual sky with the black hole left out of it — a pack is chosen by how light
 * behaves, and the brightest thing on screen should not be something the pack has no
 * say over.
 */
public class GuiShaderPacksScreen extends MenuScreen {

    private static final int ID_DONE = 200;
    private static final int ID_FOLDER = 201;
    private static final int ID_TOGGLE = 202;
    private static final int ID_OPTIONS = 203;
    private static final int ID_APPLY = 204;
    private static final int ID_CANCEL = 205;

    /** The packs in the folder, read once per layout. */
    private final List<String> packs = new ArrayList<String>();

    private PackList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int listTop;
    private int listHeight;
    private int padding;

    private int mouseX;
    private int mouseY;

    /**
     * What Iris has actually loaded, and whether it is running.
     *
     * Read from Iris rather than remembered, and re-read after every apply: an apply
     * that failed — a pack deleted between the listing and the click, a shader that
     * would not compile — must leave the screen showing what is true rather than what
     * was asked for.
     */
    private String applied = ShaderPacks.NONE;
    private boolean enabled;

    /** What has been picked here and not yet applied. */
    private String pendingPack = ShaderPacks.NONE;
    private boolean pendingEnabled;

    /** Whether the folder has been read; see {@link #buildLayout}. */
    private boolean loaded;

    /** Seconds left on the line saying a dropped pack was taken. */
    private float droppedNotice;
    /** How many the last drop added, for that line. */
    private int droppedCount;

    public GuiShaderPacksScreen(GuiScreen parent) {
        super(parent);
    }

    /** Whether there is anything here to open. */
    public static boolean available() {
        return ShaderPacks.available();
    }

    /**
     * Starts listening for a pack dragged onto the window.
     *
     * Here rather than in {@code buildLayout}, which also runs on a resize: this is a
     * channel that is opened and closed, and opening one that is already open is the
     * kind of thing a backend is entitled to object to.
     */
    @Override
    public void initGui() {
        super.initGui();
        ShaderPacks.startFileDrop();
    }

    /** And stops, including on the way to the pack's own settings screen. */
    @Override
    public void onGuiClosed() {
        ShaderPacks.stopFileDrop();
        super.onGuiClosed();
    }

    // ---------------------------------------------------------------- layout --

    @Override
    @SuppressWarnings("unchecked")
    protected void buildLayout() {
        // Once, not per layout: a resize rebuilds this and must not throw away what has
        // been staged — least of all when the folder is empty, where the switch is the
        // only thing there is to stage.
        if (!this.loaded) {
            this.loaded = true;
            readState();
            this.pendingPack = this.applied;
            this.pendingEnabled = this.enabled;
        }

        int panelWidth = Math.min((int) (this.width * 0.76F), 460);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(10, (int) (this.height * 0.07F));
        this.panelY2 = this.height - Math.max(10, (int) (this.height * 0.07F));

        boolean cramped = this.height < 300;
        this.padding = cramped ? 12 : 16;
        int buttonHeight = cramped ? 18 : 22;
        int gap = 8;
        int content = panelWidth - this.padding * 2;
        int left = this.panelX1 + this.padding;

        // Two rows of buttons along the bottom: what the screen can open, then what it
        // does with what has been picked.
        int actionsY = this.panelY2 - (cramped ? 8 : 14) - buttonHeight;
        int linksY = actionsY - gap - buttonHeight;

        // The switch first, above the list: whether shaders run at all is the question
        // that decides whether anything below it matters.
        // Below the title and the line under it that invites a pack to be dropped in.
        int toggleY = this.panelY1 + (cramped ? 36 : 42);
        MenuButton toggle = new MenuOptionButton(ID_TOGGLE, left, toggleY, content,
                buttonHeight, new MenuOptionButton.Source() {
                    @Override
                    public String label() {
                        return I18n.format("uky.shaderPacks.enabled", new Object[0]);
                    }

                    @Override
                    public String value() {
                        return "";
                    }

                    @Override
                    public boolean toggle() {
                        return true;
                    }

                    @Override
                    public boolean on() {
                        return GuiShaderPacksScreen.this.pendingEnabled;
                    }

                    @Override
                    public void cycle() {
                        GuiShaderPacksScreen.this.pendingEnabled =
                                !GuiShaderPacksScreen.this.pendingEnabled;
                    }

                    @Override
                    public boolean available() {
                        return true;
                    }
                });
        toggle.entrance(0.04F);
        this.buttonList.add(toggle);

        this.listTop = toggleY + buttonHeight + (cramped ? 10 : 16);
        this.listHeight = Math.max(40, linksY - 12 - this.listTop);

        if (this.list == null) {
            this.list = new PackList();
        }
        this.list.setBounds(left, this.listTop, content, this.listHeight, 20);
        this.list.setSelected(indexOf(this.pendingPack));

        int half = (content - gap) / 2;
        MenuButton folder = new MenuButton(ID_FOLDER, left, linksY, half, buttonHeight,
                I18n.format("uky.shaderPacks.folder", new Object[0]),
                MenuButton.Style.NORMAL);
        folder.entrance(0.05F);
        this.buttonList.add(folder);

        // Offered only when there is a pack loaded that describes any: an author who
        // exposed nothing gets no door to an empty room.
        MenuButton options = new MenuButton(ID_OPTIONS, left + half + gap, linksY, half,
                buttonHeight, I18n.format("uky.shaderPacks.options", new Object[0]),
                MenuButton.Style.NORMAL);
        options.entrance(0.06F);
        options.enabled = this.enabled && GuiShaderOptionsScreen.available();
        this.buttonList.add(options);

        int third = (content - gap * 2) / 3;
        MenuButton cancel = new MenuButton(ID_CANCEL, left, actionsY, third, buttonHeight,
                I18n.format("gui.cancel", new Object[0]), MenuButton.Style.NORMAL);
        cancel.entrance(0.06F);
        this.buttonList.add(cancel);

        MenuButton apply = new MenuButton(ID_APPLY, left + third + gap, actionsY, third,
                buttonHeight, I18n.format("uky.shaderPacks.apply", new Object[0]),
                MenuButton.Style.NORMAL);
        apply.entrance(0.07F);
        this.buttonList.add(apply);

        MenuButton done = new MenuButton(ID_DONE, left + (third + gap) * 2, actionsY,
                content - (third + gap) * 2, buttonHeight,
                I18n.format("gui.done", new Object[0]), MenuButton.Style.PRIMARY);
        done.entrance(0.07F);
        this.buttonList.add(done);
    }

    /** Re-reads the folder and what is loaded from it. */
    private void readState() {
        this.packs.clear();
        this.packs.addAll(ShaderPacks.packs());
        this.applied = ShaderPacks.selected();
        this.enabled = ShaderPacks.enabled();
    }

    private int indexOf(String pack) {
        if (pack == null) {
            return -1;
        }
        for (int i = 0; i < this.packs.size(); i++) {
            if (pack.equals(this.packs.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Whether anything is staged and waiting for Apply. */
    private boolean dirty() {
        return this.pendingEnabled != this.enabled
                || !this.pendingPack.equals(this.applied)
                || ShaderOptions.pending();
    }

    // -------------------------------------------------------------- applying --

    /**
     * Loads what has been staged.
     *
     * <p>One call, whatever changed: the pack name, the switch and anything queued on
     * the settings screen all take effect on the same reload, because there is only one
     * reload to be had and doing it twice would double the only expensive thing here.
     */
    private void apply() {
        if (!dirty()) {
            return;
        }
        ShaderPacks.apply(this.pendingPack, this.pendingEnabled);
        readState();
        this.pendingPack = this.applied;
        this.pendingEnabled = this.enabled;
        // The options button turns on and off with what is loaded, and that has just
        // changed; the rest of the layout is unaffected and settles rather than replays.
        relayout();
    }

    /**
     * Takes anything dragged onto the window since the last frame.
     *
     * The list is re-read rather than appended to, so a pack lands in the same place
     * Iris's own ordering would have put it — and the folder is the truth about what is
     * in it, which a list built by two different routes would eventually stop being.
     */
    private void takeDroppedPacks() {
        if (this.droppedNotice > 0.0F) {
            this.droppedNotice -= this.delta;
        }
        int added = ShaderPacks.acceptDropped();
        if (added <= 0) {
            return;
        }
        this.droppedCount = added;
        this.droppedNotice = 4.0F;
        String staged = this.pendingPack;
        readState();
        // What was picked survives the refresh; only the folder changed.
        this.pendingPack = staged;
        this.list.setSelected(indexOf(this.pendingPack));
    }

    /** Throws away everything staged, including the pack's own queued settings. */
    private void discard() {
        ShaderOptions.discard();
        this.pendingPack = this.applied;
        this.pendingEnabled = this.enabled;
    }

    // --------------------------------------------------------------- drawing --

    /**
     * Out of a world: the sky, with the hole left out.
     *
     * The backdrop is otherwise the one every other menu has. A black hole beside a
     * list of shader packs is the brightest thing on the screen and the one thing on it
     * no shader is responsible for — which is exactly the comparison this screen exists
     * to let somebody make.
     */
    @Override
    protected boolean isStarfieldOnly() {
        return this.mc.world == null;
    }

    /**
     * In a world, the world stays.
     *
     * A shader pack is a change to how the world looks, and a screen that covers the
     * world to choose one asks the player to remember what it looked like a moment ago.
     * Same scrim as the pause menu, for the same reason and so the two match.
     */
    @Override
    protected void drawBackdrop() {
        if (this.mc.world == null) {
            super.drawBackdrop();
            return;
        }
        Draw.rect(0, 0, this.width, this.height,
                Draw.withAlpha(Theme.background, 0.45F * this.fadeAlpha));
        Draw.gradientV(0, 0, this.width, this.height * 0.3F,
                Draw.withAlpha(Theme.background, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.background, 0.0F));
    }

    @Override
    protected void drawOverlay() {
        if (this.mc.world != null) {
            // No grain or scanlines over live gameplay — they would be read as the
            // shader doing it, which is the one thing this screen must not do.
            Draw.vignette(this.width, this.height, 0.5F * this.fadeAlpha, 0xFF000000);
            return;
        }
        super.drawOverlay();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.list.update(this.delta);
        takeDroppedPacks();

        drawPanel(this.panelX1, this.panelY1, this.panelX2, this.panelY2, this.fadeAlpha);

        this.fontRenderer.drawString(title().toUpperCase(),
                this.panelX1 + this.padding, this.panelY1 + 14,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        // The invitation, where a pack that has just arrived also reports itself. One
        // line for both, because they are the same conversation and it only ever has
        // one side speaking at a time.
        if (this.droppedNotice > 0.0F) {
            String said = I18n.format("uky.shaderPacks.dropped", new Object[0])
                    + " " + this.droppedCount;
            this.fontRenderer.drawString(said, this.panelX1 + this.padding,
                    this.panelY1 + 26,
                    Draw.withAlpha(Theme.accent,
                            Math.min(1.0F, this.droppedNotice) * 0.95F * this.fadeAlpha));
        } else if (ShaderPacks.fileDropSupported()) {
            this.fontRenderer.drawString(
                    I18n.format("uky.shaderPacks.drop", new Object[0]),
                    this.panelX1 + this.padding, this.panelY1 + 26,
                    Draw.withAlpha(Theme.textDim, 0.7F * this.fadeAlpha));
        }

        // Said where the eye already is rather than by lighting up a button: the point
        // is that the screen is not showing the state of the game yet.
        if (dirty()) {
            String note = I18n.format("uky.shaderPacks.pending", new Object[0]);
            this.fontRenderer.drawString(note,
                    this.panelX2 - this.padding - this.fontRenderer.getStringWidth(note),
                    this.panelY1 + 14,
                    Draw.withAlpha(Theme.accent, 0.9F * this.fadeAlpha));
        }

        this.list.draw(mouseX, mouseY, this.fadeAlpha);

        if (this.packs.isEmpty()) {
            // The folder button below is the answer to this, so the sentence only has
            // to say that there is nothing rather than what to do about it.
            String empty = I18n.format("uky.shaderPacks.none", new Object[0]);
            this.fontRenderer.drawString(empty,
                    this.panelX1 + this.padding
                            + (this.panelX2 - this.panelX1 - this.padding * 2
                                    - this.fontRenderer.getStringWidth(empty)) / 2,
                    this.listTop + 14,
                    Draw.withAlpha(Theme.textDisabled, 0.85F * this.fadeAlpha));
        }
    }

    /** Iris names this screen itself; our own wording is only the fallback. */
    private static String title() {
        String translated = I18n.format("options.iris.shaderPackSelection", new Object[0]);
        return "options.iris.shaderPackSelection".equals(translated)
                ? I18n.format("uky.settings.shaderPacks", new Object[0])
                : translated;
    }

    /**
     * The same glass the settings panel is drawn on.
     *
     * Shared with the pack's own settings screen, which is the same panel with
     * different rows in it and would be a different screen if it were drawn even
     * slightly differently.
     */
    static void drawPanel(int x1, int y1, int x2, int y2, float a) {
        Draw.gradientV(x1, y1, x2, y2, Draw.withAlpha(Theme.background, 0.58F * a),
                Draw.withAlpha(Theme.background, 0.38F * a));
        Draw.rect(x1, y1, x2, y1 + 1, Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(x1, y1, x1 + 2, y2, Draw.withAlpha(Theme.accent, 0.6F * a),
                Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.text, 0.08F * a));
    }

    private final class PackList extends ScrollList {

        @Override
        public int rowCount() {
            return GuiShaderPacksScreen.this.packs.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            String pack = GuiShaderPacksScreen.this.packs.get(index);
            boolean live = pack.equals(GuiShaderPacksScreen.this.applied);
            boolean staged = pack.equals(GuiShaderPacksScreen.this.pendingPack);

            if (isHovered || staged) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, (staged ? 0.09F : 0.06F) * alpha));
            }
            if (staged) {
                // The rail marks what is picked; the tick below marks what is loaded.
                // They are the same row until Apply is pressed, and the gap between
                // them for as long as it is not is the whole state of this screen.
                Draw.rect(rowX, rowY + 1, rowX + 2, rowY + rowHeight - 1,
                        Draw.withAlpha(Theme.accent, 0.9F * alpha));
            }

            int colour = staged ? Theme.textHover : (live ? Theme.text : Theme.textDim);
            GuiShaderPacksScreen.this.fontRenderer.drawString(
                    GuiShaderPacksScreen.this.fit(pack, rowWidth - 30),
                    rowX + 10, rowY + 6, Draw.withAlpha(colour, alpha));

            if (live && GuiShaderPacksScreen.this.enabled) {
                Icons.check(rowX + rowWidth - 10, rowY + rowHeight / 2.0F, 8.0F,
                        Draw.withAlpha(Theme.accent, 0.95F * alpha));
            }
        }

        @Override
        protected void onRowClicked(int index) {
            if (index >= 0 && index < GuiShaderPacksScreen.this.packs.size()) {
                // Picking a pack is asking to see it, so the switch comes on with it.
                // Turning shaders back off is one click on the row above.
                GuiShaderPacksScreen.this.pendingPack =
                        GuiShaderPacksScreen.this.packs.get(index);
                GuiShaderPacksScreen.this.pendingEnabled = true;
            }
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
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
            discard();
            switchBack();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void onAction(GuiButton button) {
        if (button instanceof MenuOptionButton) {
            ((MenuOptionButton) button).cycle();
            return;
        }
        switch (button.id) {
            case ID_FOLDER:
                openFolder();
                break;
            case ID_OPTIONS:
                // The settings belong to the pack that is loaded, not to the one that
                // is picked, so a staged pack is loaded on the way in. Otherwise the
                // rows would be another pack's and applying them would be nonsense.
                apply();
                if (GuiShaderOptionsScreen.available()) {
                    switchTo(new GuiShaderOptionsScreen(this));
                }
                break;
            case ID_APPLY:
                apply();
                break;
            case ID_CANCEL:
                discard();
                switchBack();
                break;
            case ID_DONE:
                apply();
                switchBack();
                break;
            default:
                break;
        }
    }

    /**
     * Opens the folder packs are read from.
     *
     * Nothing staged is lost by it — the game keeps running behind the window that
     * opens — and a pack dropped in while this screen is up shows on the next visit.
     */
    private void openFolder() {
        java.io.File dir = ShaderPacks.folder();
        if (dir == null) {
            return;
        }
        try {
            if (!dir.exists()) {
                // Iris creates it on start-up, but a folder deleted since then should
                // still open rather than doing nothing at all.
                dir.mkdirs();
            }
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not open the shader pack folder", t);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

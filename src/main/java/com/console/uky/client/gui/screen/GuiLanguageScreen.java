package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.gui.widget.ScrollList;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.LensLibrary;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Language picker with a search box.
 *
 * Vanilla's list is unsorted, unsearchable and unlabelled beyond the language's
 * own name, which is unhelpful precisely when you cannot read the current one.
 * This shows both the native and the English name, keeps the active language
 * pinned in view, and filters as you type — the pattern LanguageReload popularised.
 */
public class GuiLanguageScreen extends MenuScreen {

    private static final int ID_DONE = 200;

    private final LanguageManager languageManager;
    private final List<Language> all = new ArrayList<Language>();
    private final List<Language> filtered = new ArrayList<Language>();

    private GuiTextField search;
    private LanguageList list;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    /** Smoothed highlight for the row being applied, so selection is not a hard cut. */
    private float applyFlash;

    public GuiLanguageScreen(GuiScreen parent) {
        super(parent);
        this.languageManager = net.minecraft.client.Minecraft.getMinecraft().getLanguageManager();
    }

    @Override
    protected void buildLayout() {
        this.all.clear();
        this.all.addAll(this.languageManager.getLanguages());

        int panelWidth = Math.min((int) (this.width * 0.52F), 300);
        this.panelX1 = Math.max(10, (int) (this.width * 0.07F));
        this.panelX2 = this.panelX1 + panelWidth;
        this.panelY1 = Math.max(12, (int) (this.height * 0.10F));
        this.panelY2 = this.height - Math.max(12, (int) (this.height * 0.10F));

        String previous = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(this.fontRendererObj,
                this.panelX1 + 14, this.panelY1 + 30, panelWidth - 28, 16);
        this.search.setMaxStringLength(32);
        this.search.setEnableBackgroundDrawing(false);
        this.search.setText(previous);
        this.search.setFocused(true);

        if (this.list == null) {
            this.list = new LanguageList();
        }
        this.list.setBounds(this.panelX1 + 14, this.panelY1 + 54, panelWidth - 28,
                this.panelY2 - this.panelY1 - 96, 22);

        applyFilter();

        MenuButton done = new MenuButton(ID_DONE, this.panelX1 + 14, this.panelY2 - 30,
                panelWidth - 28, 20, I18n.format("gui.done", new Object[0]),
                MenuButton.Style.PRIMARY);
        done.entrance(0.05F);
        this.buttonList.add(done);
    }

    /**
     * How many rows at the top are the pinned current language rather than results.
     * Either 0 or 1; kept as a count so the row drawing does not have to re-derive it.
     */
    private int pinnedRows;

    /**
     * Rebuilds the visible list: the active language first, then everything matching,
     * in alphabetical order.
     *
     * Pinning the active one is the thing worth copying from LanguageReload. Vanilla
     * leaves it wherever the load order put it, so the one language you most need to
     * see — the one you are trying to change away from, in a script you may not read
     * — can be anywhere in a list of a hundred. Alphabetical order for the rest is
     * for the same reason: {@code getLanguages()} order is arbitrary.
     *
     * <p>While a query is active the pin is dropped, because then the list is
     * answering a question and a row that ignores the filter is just confusing.
     */
    private void applyFilter() {
        String query = this.search.getText().trim().toLowerCase();
        Language current = this.languageManager.getCurrentLanguage();
        boolean pin = query.isEmpty() && current != null;

        this.filtered.clear();
        this.pinnedRows = 0;
        if (pin) {
            this.filtered.add(current);
            this.pinnedRows = 1;
        }

        List<Language> matches = new ArrayList<Language>();
        for (int i = 0; i < this.all.size(); i++) {
            Language language = this.all.get(i);
            if (pin && language.equals(current)) {
                continue;
            }
            if (query.isEmpty() || matches(language, query)) {
                matches.add(language);
            }
        }
        Collections.sort(matches, BY_NAME);
        this.filtered.addAll(matches);

        // Keep the active language selected across filtering when it survives.
        this.list.setSelected(this.filtered.indexOf(current));
        if (this.list.getSelected() >= 0) {
            this.list.scrollTo(this.list.getSelected());
        }
    }

    private static boolean matches(Language language, String query) {
        return language.toString().toLowerCase().contains(query)
                || language.getLanguageCode().toLowerCase().contains(query);
    }

    private static final Comparator<Language> BY_NAME = new Comparator<Language>() {
        @Override
        public int compare(Language a, Language b) {
            return a.toString().compareToIgnoreCase(b.toString());
        }
    };

    /**
     * The language's own name, without the region {@code toString} appends.
     *
     * 1.7.10 keeps {@code name} and {@code region} private with no accessors, so the
     * formatted string is all there is to work from.
     */
    private static String nameOf(Language language) {
        String full = language.toString();
        int bracket = full.lastIndexOf(" (");
        return bracket > 0 ? full.substring(0, bracket) : full;
    }

    /** The region half of {@code toString}, or empty if there is none. */
    private static String regionOf(Language language) {
        String full = language.toString();
        int bracket = full.lastIndexOf(" (");
        if (bracket < 0 || !full.endsWith(")")) {
            return "";
        }
        return full.substring(bracket + 2, full.length() - 1);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterX() {
        return this.width * 0.78F;
    }

    @Override
    protected float blackHoleCenterY() {
        return this.height * 0.45F;
    }

    @Override
    protected float blackHoleRadius() {
        return Math.min(this.width, this.height) * 0.22F;
    }

    @Override
    protected float blackHoleIntensity() {
        return 0.85F;
    }

    @Override
    protected int blackHolePose() {
        // Almost exactly edge-on: the thinnest, sharpest silhouette.
        return LensLibrary.POSE_IN_PLANE;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.list.update(this.delta);
        this.applyFlash = Ease.approach(this.applyFlash, 0.0F, 0.15F, this.delta);

        Draw.rect(panelX1, panelY1, panelX2, panelY2,
                Draw.withAlpha(Theme.background, 0.80F * this.fadeAlpha));
        Draw.gradientH(panelX1, panelY1, panelX1 + 2, panelY2,
                Draw.withAlpha(Theme.accent, 0.55F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        this.fontRendererObj.drawString(I18n.format("options.language", new Object[0]),
                panelX1 + 14, panelY1 + 12, Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawSearchBox();
        this.list.draw(mouseX, mouseY, this.fadeAlpha);

        // Vanilla's own warning, kept because it is genuinely useful.
        String notice = I18n.format("options.languageWarning", new Object[0]);
        this.fontRendererObj.drawString(notice, panelX1 + 14, panelY2 - 44,
                Draw.withAlpha(Theme.textDim, 0.6F * this.fadeAlpha));
    }

    private void drawSearchBox() {
        // The box is built around the field rather than from loose offsets. It used to
        // extend 6 below and 4 above, so the underline sat well clear of the text and
        // the placeholder was drawn 4 lower than typed text would be — the field
        // looked skewed and the caption looked like it belonged to something else.
        int x1 = this.search.xPosition - 5;
        int x2 = this.search.xPosition + this.search.width + 5;
        int y1 = this.search.yPosition - 4;
        int y2 = this.search.yPosition + 12;

        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(Theme.surface, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2,
                Draw.withAlpha(this.search.isFocused() ? Theme.accent : Theme.separator,
                        this.fadeAlpha));

        if (this.search.getText().isEmpty()) {
            // Same baseline the field itself draws at, so the caption sits exactly
            // where the first typed character will appear.
            this.fontRendererObj.drawString(I18n.format("uky.language.search", new Object[0]),
                    this.search.xPosition, this.search.yPosition,
                    Draw.withAlpha(Theme.textDim, 0.5F * this.fadeAlpha));
        }
        this.search.drawTextBox();
    }

    private final class LanguageList extends ScrollList {
        @Override
        public int rowCount() {
            return GuiLanguageScreen.this.filtered.size();
        }

        @Override
        protected void drawRow(int index, int rowX, int rowY, int rowWidth, int rowHeight,
                               boolean isHovered, boolean isSelected, float alpha) {
            Language language = GuiLanguageScreen.this.filtered.get(index);
            boolean active = language.equals(
                    GuiLanguageScreen.this.languageManager.getCurrentLanguage());

            if (active) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.accent, (0.12F + GuiLanguageScreen.this.applyFlash * 0.2F) * alpha));
                Draw.rect(rowX, rowY, rowX + 2, rowY + rowHeight,
                        Draw.withAlpha(Theme.accent, 0.9F * alpha));
            } else if (isHovered) {
                Draw.rect(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                        Draw.withAlpha(Theme.text, 0.06F * alpha));
            }

            int color = active ? Theme.textHover : Theme.text;
            GuiLanguageScreen screen = GuiLanguageScreen.this;

            // Name on top; region and code together underneath. Splitting them puts
            // the word you are actually looking for at the front of the row instead of
            // buried in "Name (Region)".
            String name = screen.fontRendererObj.trimStringToWidth(nameOf(language),
                    rowWidth - 16);
            screen.fontRendererObj.drawString(name, rowX + 8, rowY + 4,
                    Draw.withAlpha(color, alpha));

            String region = regionOf(language);
            String sub = region.isEmpty()
                    ? language.getLanguageCode()
                    : region + "  ·  " + language.getLanguageCode();
            screen.fontRendererObj.drawString(
                    screen.fontRendererObj.trimStringToWidth(sub, rowWidth - 16),
                    rowX + 8, rowY + 13, Draw.withAlpha(Theme.textDim, 0.65F * alpha));

            // The pinned row is the current language shown out of order, so it says so
            // and is fenced off from the results below it.
            if (index == 0 && screen.pinnedRows == 1) {
                Draw.rect(rowX, rowY + rowHeight - 1, rowX + rowWidth, rowY + rowHeight,
                        Draw.fade(Theme.separator, alpha));
            }
        }

        @Override
        protected void onRowClicked(int index) {
            GuiLanguageScreen.this.apply(index);
        }
    }

    private void apply(int index) {
        if (index < 0 || index >= this.filtered.size()) {
            return;
        }
        Language language = this.filtered.get(index);
        if (language.equals(this.languageManager.getCurrentLanguage())) {
            return;
        }
        this.languageManager.setCurrentLanguage(language);
        this.mc.gameSettings.language = language.getLanguageCode();
        this.mc.refreshResources();
        this.fontRendererObj.setUnicodeFlag(this.languageManager.isCurrentLocaleUnicode()
                || this.mc.gameSettings.forceUnicodeFont);
        this.fontRendererObj.setBidiFlag(this.languageManager.isCurrentLanguageBidirectional());
        this.mc.gameSettings.saveOptions();
        this.applyFlash = 1.0F;
        // Labels changed language, so the controls have to be rebuilt — but not the
        // entrance animation, which would blink the screen through black.
        relayout();
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.search.mouseClicked(mouseX, mouseY, button);
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
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == 200) {
            this.list.moveSelection(-1);
            return;
        }
        if (keyCode == 208) {
            this.list.moveSelection(1);
            return;
        }
        if (keyCode == 28) { // enter applies the highlighted row
            apply(this.list.getSelected());
            return;
        }
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            applyFilter();
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
        if (button.id == ID_DONE) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

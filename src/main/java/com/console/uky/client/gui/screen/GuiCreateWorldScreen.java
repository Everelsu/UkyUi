package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.compat.GuiButton;
import com.console.uky.compat.GuiTextField;
import com.console.uky.compat.Saves;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.storage.LevelSummary;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * World creation, in this mod's style.
 *
 * <p>Drawn over vanilla's own {@link CreateWorldScreen}, which is kept alive as the
 * model: its {@link WorldCreationUiState} holds the name, seed, mode, world type and
 * switches, it already knows the datapack set and the presets the installed mods
 * added, and its own create action is what turns all of that into a world. This screen
 * only edits that state and asks it to go. The parts that are a screen of their own —
 * game rules, datapacks, experiments, a world type's customiser — stay vanilla's,
 * behind "More" and "Customize", and come back here when closed.
 *
 * <p>Re-Create needs nothing extra: vanilla fills the same state from the old save.
 */
public class GuiCreateWorldScreen extends MenuScreen {

    private static final class Mode {
        final WorldCreationUiState.SelectedGameMode mode;
        final String id;
        final boolean hardcore;

        Mode(WorldCreationUiState.SelectedGameMode mode, String id, boolean hardcore) {
            this.mode = mode;
            this.id = id;
            this.hardcore = hardcore;
        }
    }

    private static final Mode[] MODES = new Mode[] {
            new Mode(WorldCreationUiState.SelectedGameMode.SURVIVAL, "survival", false),
            new Mode(WorldCreationUiState.SelectedGameMode.HARDCORE, "hardcore", true),
            new Mode(WorldCreationUiState.SelectedGameMode.CREATIVE, "creative", false),
    };

    private static final int TILE_GAP = 6;

    /**
     * The vanilla screen asked to show itself, which is let through once rather than
     * wrapped again; see {@link #wrap}.
     */
    private static CreateWorldScreen showVanilla;

    private final CreateWorldScreen delegate;
    private final WorldCreationUiState state;

    private GuiTextField nameField;
    private GuiTextField seedField;

    private final List<WorldCreationUiState.WorldTypeEntry> types =
            new ArrayList<WorldCreationUiState.WorldTypeEntry>();
    private int typePage;
    private int typeColumns = 1;
    private int typeRows = 1;
    private int typeRowHeight;
    private int typePerPage = 1;
    private int typePageCount = 1;

    private boolean created;
    private boolean delegateReady;

    private int contentX;
    private int contentWidth;
    private int modeY;
    private int modeHeight;
    private int typeY;
    private int typeHeight;
    private int toggleY;
    private int toggleHeight;
    private int actionY;

    private final float[] modeHover = new float[MODES.length];
    private float[] typeHover = new float[0];
    private final float[] toggleHover = new float[3];
    private float createHover;
    private float backHover;
    private float customizeHover;
    private float moreHover;
    private float prevPageHover;
    private float nextPageHover;
    private float hardcoreMood;
    private int mouseX;
    private int mouseY;

    private GuiCreateWorldScreen(Screen parent, CreateWorldScreen delegate) {
        super(parent);
        this.delegate = delegate;
        this.state = delegate.getUiState();
    }

    /**
     * This screen in place of vanilla's, or null to let vanilla's through.
     *
     * The parent is the one vanilla's screen returns to, so leaving lands where it would.
     */
    public static Screen wrap(CreateWorldScreen vanilla, Screen parent) {
        if (vanilla == showVanilla) {
            showVanilla = null;
            return null;
        }
        return new GuiCreateWorldScreen(parent, vanilla);
    }

    @Override
    protected boolean isVoid() {
        return true;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        // Vanilla's own init is what hands it the game; its create action needs that.
        if (!this.delegateReady) {
            this.delegate.init(this.mc, this.width, this.height);
            this.delegateReady = true;
        }
        collectTypes();

        int margin = Math.max(24, (int) (this.width * 0.08F));
        this.contentX = margin;
        this.contentWidth = this.width - margin * 2;

        int headerBottom = 52;
        int bottomLimit = this.height - 20;
        int actionHeight = 20;
        int fields = 44;
        int available = Math.max(80, bottomLimit - headerBottom - fields - actionHeight - 24);

        this.modeHeight = clamp(available * 5 / 12, 34, 62);
        this.typeRowHeight = clamp(available * 4 / 12, 24, 40);
        this.toggleHeight = clamp(available * 3 / 12, 18, 26);
        layoutTypeGrid(available);

        int tiles = this.modeHeight + this.typeHeight + this.toggleHeight;
        int gap = clamp((available - tiles) / 2, 10, 30);
        int blockHeight = fields + tiles + gap * 2 + 24 + actionHeight;
        int slack = Math.max(0, (bottomLimit - headerBottom) - blockHeight);
        int blockTop = headerBottom + slack / 3;

        this.modeY = blockTop + fields;
        this.typeY = this.modeY + this.modeHeight + gap;
        this.toggleY = this.typeY + this.typeHeight + gap;
        this.actionY = this.toggleY + this.toggleHeight + 24;

        int fieldWidth = (this.contentWidth - 12) / 2;
        String name = this.nameField != null ? this.nameField.getText() : initialName();
        String seed = this.seedField != null ? this.seedField.getText() : this.state.getSeed();

        this.nameField = new GuiTextField(this.fontRendererObj,
                this.contentX + 1, blockTop + 13, fieldWidth - 2, 16);
        this.nameField.setMaxStringLength(32);
        this.nameField.setEnableBackgroundDrawing(false);
        this.nameField.setText(name);
        this.nameField.setFocused(true);

        this.seedField = new GuiTextField(this.fontRendererObj,
                this.contentX + fieldWidth + 13, blockTop + 13, fieldWidth - 2, 16);
        this.seedField.setEnableBackgroundDrawing(false);
        this.seedField.setText(seed);

        this.typeHover = new float[this.types.size()];
    }

    /**
     * The name the field opens with: vanilla's, unless it is the bare default and that
     * is taken, in which case the first free "New World N". Two worlds both called "New
     * World" are two tiles nobody can tell apart.
     */
    private String initialName() {
        String name = this.state.getName();
        String base = I18n.get("selectWorld.newWorld");
        if (!name.equals(base)) {
            return name;
        }
        Set<String> taken = new HashSet<String>();
        try {
            for (LevelSummary summary : Saves.list()) {
                taken.add(summary.getLevelName().trim());
            }
        } catch (Exception e) {
            return base;
        }
        if (!taken.contains(base)) {
            return base;
        }
        for (int n = 2; n < 1000; n++) {
            if (!taken.contains(base + " " + n)) {
                return base + " " + n;
            }
        }
        return base;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void layoutTypeGrid(int available) {
        int count = this.types.size();
        this.typeColumns = clamp(this.contentWidth / 150, 2, 4);
        this.typeColumns = Math.max(1, Math.min(this.typeColumns, count));
        int spare = available - this.modeHeight - this.toggleHeight - 20;
        int fits = Math.max(1, (spare + TILE_GAP) / (this.typeRowHeight + TILE_GAP));
        int wanted = (count + this.typeColumns - 1) / this.typeColumns;
        this.typeRows = Math.max(1, Math.min(Math.min(fits, wanted), 3));
        this.typeHeight = this.typeRows * this.typeRowHeight + (this.typeRows - 1) * TILE_GAP;
        this.typePerPage = Math.max(1, this.typeColumns * this.typeRows);
        this.typePageCount = Math.max(1, (count + this.typePerPage - 1) / this.typePerPage);
        int slot = this.types.indexOf(this.state.getWorldType());
        this.typePage = slot < 0 ? 0 : slot / this.typePerPage;
    }

    private int typeTileWidth() {
        return (this.contentWidth - TILE_GAP * (this.typeColumns - 1)) / this.typeColumns;
    }

    private int typeTileX(int cell) {
        return this.contentX + (cell % this.typeColumns) * (typeTileWidth() + TILE_GAP);
    }

    private int typeTileY(int cell) {
        return this.typeY + (cell / this.typeColumns) * (this.typeRowHeight + TILE_GAP);
    }

    /**
     * Every world type there is, the alternative ones — debug, and whatever mods file
     * there — after the usual ones. Vanilla hides the second list behind Shift; on a grid
     * of tiles there is room for both.
     */
    private void collectTypes() {
        this.types.clear();
        this.types.addAll(this.state.getNormalPresetList());
        for (WorldCreationUiState.WorldTypeEntry entry : this.state.getAltPresetList()) {
            if (!this.types.contains(entry)) {
                this.types.add(entry);
            }
        }
        if (this.types.isEmpty()) {
            this.types.add(this.state.getWorldType());
        }
    }

    private int selectedMode() {
        WorldCreationUiState.SelectedGameMode mode = this.state.getGameMode();
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].mode == mode) {
                return i;
            }
        }
        return 0;
    }

    private boolean isHardcore() {
        return this.state.isHardcore();
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hardcoreMood = Ease.approach(this.hardcoreMood,
                isHardcore() ? 1.0F : 0.0F, 0.22F, this.delta);
        drawHardcoreWash();
        drawHeader();
        drawFields();
        drawModes();
        drawTypes();
        drawToggles();
        drawActions();
    }

    /** Hardcore turns the room red, slowly, and breathes. */
    private void drawHardcoreWash() {
        if (this.hardcoreMood <= 0.01F) {
            return;
        }
        float mood = this.hardcoreMood * this.fadeAlpha;
        float breath = 0.78F
                + (float) Math.sin(this.elapsed * 1.15F) * 0.14F
                + (float) Math.sin(this.elapsed * 0.41F) * 0.08F;
        float strength = mood * breath;
        Draw.radialGlow(this.width * 0.5F, this.height * 1.02F,
                Math.max(this.width, this.height) * 0.95F,
                Draw.withAlpha(Theme.danger, 0.20F * strength),
                Draw.withAlpha(Theme.danger, 0.0F));
        float band = this.width * 0.28F;
        Draw.gradientH(0, 0, band, this.height,
                Draw.withAlpha(Theme.danger, 0.13F * strength),
                Draw.withAlpha(Theme.danger, 0.0F));
        Draw.gradientH(this.width - band, 0, this.width, this.height,
                Draw.withAlpha(Theme.danger, 0.0F),
                Draw.withAlpha(Theme.danger, 0.13F * strength));
        Draw.gradientV(0, this.height * 0.72F, this.width, this.height,
                Draw.withAlpha(Theme.danger, 0.0F),
                Draw.withAlpha(Theme.danger, 0.10F * strength));
    }

    private void drawHeader() {
        this.fontRendererObj.drawString(I18n.get("selectWorld.create").toUpperCase(),
                this.contentX, 26, Draw.withAlpha(Theme.text, this.fadeAlpha));
        Draw.gradientH(this.contentX, 40, this.contentX + this.contentWidth, 41,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        this.backHover = Ease.approach(this.backHover, isOverBack() ? 1.0F : 0.0F,
                0.05F, this.delta);
        int colour = Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.backHover),
                this.fadeAlpha);
        int right = this.contentX + this.contentWidth;
        Icons.back(right - 6, 30, 9, colour);
        String cancel = I18n.get("gui.cancel");
        this.fontRendererObj.drawString(cancel,
                right - 16 - this.fontRendererObj.getStringWidth(cancel), 26, colour);
    }

    private boolean isOverBack() {
        int right = this.contentX + this.contentWidth;
        return this.mouseX >= right - 56 && this.mouseX <= right
                && this.mouseY >= 20 && this.mouseY <= 40;
    }

    private void drawFields() {
        drawField(this.nameField, I18n.get("selectWorld.enterName"), false);
        drawField(this.seedField, I18n.get("selectWorld.enterSeed"), true);
    }

    private void drawField(GuiTextField field, String caption, boolean withDice) {
        float x1 = field.xPosition - 1;
        float x2 = field.xPosition + field.getWidth() + 1;
        float y1 = field.yPosition - 3;
        float y2 = field.yPosition + 15;
        boolean focused = field.isFocused();

        this.fontRendererObj.drawString(caption, (int) x1, (int) y1 - 12,
                Draw.withAlpha(Theme.textDim, 0.85F * this.fadeAlpha));
        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(0x000000, 0.55F * this.fadeAlpha));
        Draw.rect(x1, y2 - 1, x2, y2, Draw.withAlpha(
                focused ? Theme.accent : Theme.separator, this.fadeAlpha));
        if (withDice) {
            Icons.dice(x2 - 9, (y1 + y2) / 2.0F, 9,
                    Draw.withAlpha(Theme.textDim, 0.6F * this.fadeAlpha));
        }
        field.drawTextBox();
        if (withDice && field.getText().isEmpty() && !focused) {
            this.fontRendererObj.drawString(I18n.get("selectWorld.seedInfo"),
                    field.xPosition + 2, field.yPosition + 1,
                    Draw.withAlpha(Theme.textDisabled, 0.7F * this.fadeAlpha));
        }
    }

    private void drawModes() {
        drawSectionLabel(I18n.get("selectWorld.gameMode"), this.modeY - 12);
        int selectedMode = selectedMode();
        int tileWidth = (this.contentWidth - TILE_GAP * (MODES.length - 1)) / MODES.length;
        for (int i = 0; i < MODES.length; i++) {
            int x = this.contentX + i * (tileWidth + TILE_GAP);
            boolean over = inside(x, this.modeY, tileWidth, this.modeHeight);
            this.modeHover[i] = Ease.approach(this.modeHover[i], over ? 1.0F : 0.0F,
                    0.05F, this.delta);
            boolean selected = i == selectedMode;
            boolean danger = MODES[i].hardcore;
            int fill = drawTile(x, this.modeY, tileWidth, this.modeHeight,
                    this.modeHover[i], selected, danger, true);

            int accent = danger ? Theme.danger : Theme.accent;
            int tint = Draw.withAlpha(selected ? accent
                    : Draw.mix(Theme.textDim, accent, this.modeHover[i]), this.fadeAlpha);
            float iconSize = Math.min(16.0F, this.modeHeight * 0.4F);
            float iconX = x + 14;
            float iconY = this.modeY + 15;
            if (i == 0) {
                Icons.sword(iconX, iconY, iconSize, tint);
            } else if (i == 1) {
                Icons.skull(iconX, iconY, iconSize, tint, fill);
            } else {
                Icons.spark(iconX, iconY, iconSize, tint);
            }

            String label = I18n.get("selectWorld.gameMode." + MODES[i].id);
            drawFitted(label, x + 26, (int) (iconY - 4), tileWidth - 32,
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));

            String blurbKey = MODES[i].hardcore
                    ? "uky.gamemode.hardcore"
                    : "selectWorld.gameMode." + MODES[i].id;
            drawTileBlurb(x, tileWidth, this.modeY + 30,
                    I18n.get(blurbKey + ".line1"), I18n.get(blurbKey + ".line2"));
        }
    }

    private void drawTileBlurb(int x, int tileWidth, float y, String line1, String line2) {
        int colour = Draw.withAlpha(Theme.textDim, 0.75F * this.fadeAlpha);
        int maxWidth = tileWidth - 20;
        String text = line1;
        if (line2 != null && !line2.isEmpty()) {
            text = text + " " + line2;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, maxWidth);
        int room = Math.max(1, (int) ((this.modeY + this.modeHeight - 6 - y) / 10));
        for (int i = 0; i < lines.size() && i < room; i++) {
            String line = lines.get(i);
            if (i == room - 1 && lines.size() > room) {
                line = this.fontRendererObj.trimStringToWidth(line,
                        maxWidth - this.fontRendererObj.getStringWidth("...")).trim() + "...";
            }
            this.fontRendererObj.drawString(line, x + 10, (int) y + i * 10, colour);
        }
    }

    private void drawTypes() {
        drawSectionLabel(I18n.get("selectWorld.mapType"), this.typeY - 12);
        drawTypePager();

        WorldCreationUiState.WorldTypeEntry current = this.state.getWorldType();
        int count = this.types.size();
        int first = this.typePage * this.typePerPage;
        int tileWidth = typeTileWidth();
        for (int cell = 0; cell < this.typePerPage; cell++) {
            int slot = first + cell;
            if (slot >= count) {
                break;
            }
            WorldCreationUiState.WorldTypeEntry type = this.types.get(slot);
            int x = typeTileX(cell);
            int y = typeTileY(cell);
            boolean over = inside(x, y, tileWidth, this.typeRowHeight);
            this.typeHover[slot] = Ease.approach(this.typeHover[slot], over ? 1.0F : 0.0F,
                    0.05F, this.delta);
            boolean selected = type.equals(current);
            drawTile(x, y, tileWidth, this.typeRowHeight, this.typeHover[slot],
                    selected, false, true);

            int tint = Draw.withAlpha(selected ? Theme.accent
                    : Draw.mix(Theme.textDim, Theme.accent, this.typeHover[slot]), this.fadeAlpha);
            float cy = y + this.typeRowHeight * 0.5F;
            float iconSize = Math.min(14.0F, this.typeRowHeight * 0.5F);
            String id = type.preset() == null ? "" : type.preset().getRegisteredName();
            if (id.contains("flat")) {
                Icons.flat(x + 13, cy, iconSize, tint);
            } else if (id.contains("large") || type.isAmplified()) {
                Icons.globe(x + 13, cy, iconSize, tint);
            } else {
                Icons.terrain(x + 13, cy, iconSize, tint);
            }
            String label = fit(type.describePreset().getString(), tileWidth - 30);
            this.fontRendererObj.drawString(label, x + 24, (int) (cy - 4),
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));
        }
    }

    private static final int PAGER_ARROW_ROOM = 14;

    private void drawTypePager() {
        if (this.typePageCount <= 1) {
            return;
        }
        int y = this.typeY - 12;
        this.prevPageHover = Ease.approach(this.prevPageHover, isOverPrevPage() ? 1.0F : 0.0F,
                0.05F, this.delta);
        this.nextPageHover = Ease.approach(this.nextPageHover, isOverNextPage() ? 1.0F : 0.0F,
                0.05F, this.delta);
        String counter = (this.typePage + 1) + "/" + this.typePageCount;
        int counterWidth = this.fontRendererObj.getStringWidth(counter);
        int right = this.contentX + this.contentWidth;
        this.fontRendererObj.drawString(counter, right - PAGER_ARROW_ROOM - counterWidth, y,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
        Icons.back(right - PAGER_ARROW_ROOM - counterWidth - 10, y + 4, 8,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.accent, this.prevPageHover),
                        this.fadeAlpha));
        Icons.forward(right - 5, y + 4, 8,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.accent, this.nextPageHover),
                        this.fadeAlpha));
    }

    private boolean isOverPrevPage() {
        if (this.typePageCount <= 1) {
            return false;
        }
        String counter = (this.typePage + 1) + "/" + this.typePageCount;
        int right = this.contentX + this.contentWidth
                - PAGER_ARROW_ROOM - this.fontRendererObj.getStringWidth(counter);
        return this.mouseX >= right - 18 && this.mouseX <= right - 2
                && this.mouseY >= this.typeY - 16 && this.mouseY <= this.typeY - 2;
    }

    private boolean isOverNextPage() {
        if (this.typePageCount <= 1) {
            return false;
        }
        int right = this.contentX + this.contentWidth;
        return this.mouseX >= right - 14 && this.mouseX <= right
                && this.mouseY >= this.typeY - 16 && this.mouseY <= this.typeY - 2;
    }

    private void turnTypePage(int step) {
        if (this.typePageCount <= 1) {
            return;
        }
        this.typePage = (this.typePage + step + this.typePageCount) % this.typePageCount;
        click();
    }

    private void drawToggles() {
        int columns = 3;
        int tileWidth = (this.contentWidth - TILE_GAP * (columns - 1)) / columns;
        boolean hardcore = isHardcore();
        for (int i = 0; i < columns; i++) {
            int x = this.contentX + i * (tileWidth + TILE_GAP);
            // Hardcore locks the bonus chest and cheats, as vanilla does.
            boolean locked = hardcore && i > 0;
            boolean on = toggleValue(i) && !locked;
            boolean over = !locked && inside(x, this.toggleY, tileWidth, this.toggleHeight);
            this.toggleHover[i] = Ease.approach(this.toggleHover[i], over ? 1.0F : 0.0F,
                    0.05F, this.delta);

            float alpha = this.fadeAlpha * (locked ? 0.4F : 1.0F);
            int fill = Draw.withAlpha(0x000000, (0.5F + this.toggleHover[i] * 0.2F) * alpha);
            Draw.rect(x, this.toggleY, x + tileWidth, this.toggleY + this.toggleHeight, fill);
            Draw.border(x, this.toggleY, x + tileWidth, this.toggleY + this.toggleHeight, 1.0F,
                    on
                            ? Draw.withAlpha(Theme.accent, alpha)
                            : Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, this.toggleHover[i]),
                                    (0.13F + this.toggleHover[i] * 0.6F) * alpha));

            float box = this.toggleHeight - 10;
            float bx = x + 6;
            float by = this.toggleY + 5;
            Draw.border(bx, by, bx + box, by + box, 1.0F,
                    on ? Draw.withAlpha(Theme.accent, alpha)
                       : Draw.withAlpha(Theme.text, 0.3F * alpha));
            if (on) {
                Icons.check(bx + box / 2.0F, by + box / 2.0F, box * 0.8F,
                        Draw.withAlpha(Theme.accent, alpha));
            }
            String label = fit(toggleLabel(i), tileWidth - (int) box - 14);
            this.fontRendererObj.drawString(label, (int) (bx + box + 6),
                    (int) (this.toggleY + this.toggleHeight / 2.0F - 4),
                    Draw.withAlpha(on ? Theme.text : Theme.textDim, alpha));
        }
        if (hardcore) {
            String note = fit(I18n.get("uky.gamemode.hardcore.warning"), this.contentWidth);
            this.fontRendererObj.drawString(note, this.contentX,
                    this.toggleY + this.toggleHeight + 6,
                    Draw.withAlpha(Theme.danger, 0.85F * this.fadeAlpha));
        }
    }

    private boolean toggleValue(int index) {
        switch (index) {
            case 0: return this.state.isGenerateStructures();
            case 1: return this.state.isBonusChest();
            default: return this.state.isAllowCommands();
        }
    }

    private String toggleLabel(int index) {
        String key;
        switch (index) {
            case 0: key = "selectWorld.mapFeatures"; break;
            case 1: key = "selectWorld.bonusItems"; break;
            default: key = "selectWorld.allowCommands"; break;
        }
        String label = I18n.get(key).trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        return label;
    }

    private static final int CREATE_WIDTH = 130;
    private static final int SIDE_WIDTH = 110;

    private int createX() {
        return this.contentX + this.contentWidth - CREATE_WIDTH;
    }

    private int customizeX() {
        return createX() - SIDE_WIDTH - 8;
    }

    private int moreX() {
        return this.contentX;
    }

    private void drawActions() {
        int createX = createX();
        boolean over = inside(createX, this.actionY, CREATE_WIDTH, 20);
        this.createHover = Ease.approach(this.createHover, over ? 1.0F : 0.0F, 0.05F, this.delta);
        int fill = Draw.mix(Theme.accent, Theme.textHover, this.createHover * 0.25F);
        Draw.rect(createX, this.actionY, createX + CREATE_WIDTH, this.actionY + 20,
                Draw.withAlpha(fill, (0.85F + this.createHover * 0.15F) * this.fadeAlpha));
        if (this.createHover > 0.02F) {
            Draw.glow(createX, this.actionY, createX + CREATE_WIDTH, this.actionY + 20, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.3F * this.createHover * this.fadeAlpha), 4);
        }
        String label = I18n.get("selectWorld.create").toUpperCase();
        label = fit(label, CREATE_WIDTH - 10);
        int labelWidth = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label, createX + (CREATE_WIDTH - labelWidth) / 2,
                this.actionY + 6, Draw.withAlpha(0x0B0B0E, this.fadeAlpha));

        if (isCustomizable()) {
            this.customizeHover = drawSideButton(customizeX(), I18n.get("selectWorld.customizeType"),
                    this.customizeHover);
        }
        // Game rules, datapacks and experiments: vanilla's tabs, all of it.
        this.moreHover = drawSideButton(moreX(), I18n.get("selectWorld.moreWorldOptions"),
                this.moreHover);
    }

    private float drawSideButton(int x, String text, float hover) {
        boolean over = inside(x, this.actionY, SIDE_WIDTH, 20);
        hover = Ease.approach(hover, over ? 1.0F : 0.0F, 0.05F, this.delta);
        Draw.rect(x, this.actionY, x + SIDE_WIDTH, this.actionY + 20,
                Draw.withAlpha(0x000000, (0.5F + hover * 0.2F) * this.fadeAlpha));
        Draw.border(x, this.actionY, x + SIDE_WIDTH, this.actionY + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, hover), this.fadeAlpha));
        String shown = fit(text, SIDE_WIDTH - 12);
        int w = this.fontRendererObj.getStringWidth(shown);
        this.fontRendererObj.drawString(shown, x + (SIDE_WIDTH - w) / 2, this.actionY + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, hover), this.fadeAlpha));
        return hover;
    }

    private boolean isCustomizable() {
        return this.state.getPresetEditor() != null;
    }

    private void drawSectionLabel(String text, int y) {
        String label = text.trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        this.fontRendererObj.drawString(label.toUpperCase(), this.contentX, y,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
    }

    /** A selectable tile; returns its fill so a cut-out icon can match it. */
    private int drawTile(int x, int y, int width, int height, float hover,
                         boolean selected, boolean danger, boolean glow) {
        int accent = danger ? Theme.danger : Theme.accent;
        float lift = selected ? 0.28F : hover * 0.2F;
        int fill = Draw.withAlpha(0x000000, (0.55F + lift) * this.fadeAlpha);
        Draw.rect(x, y, x + width, y + height, fill);
        if (selected) {
            Draw.rect(x, y, x + 2, y + height, Draw.withAlpha(accent, this.fadeAlpha));
        }
        Draw.border(x, y, x + width, y + height, 1.0F,
                selected
                        ? Draw.withAlpha(accent, this.fadeAlpha)
                        : Draw.withAlpha(Draw.mix(Theme.text, accent, hover),
                                (0.13F + hover * 0.6F) * this.fadeAlpha));
        if (glow && (selected || hover > 0.02F)) {
            Draw.glow(x, y, x + width, y + height, 4.0F,
                    Draw.withAlpha(accent,
                            0.22F * Math.max(hover, selected ? 0.6F : 0.0F) * this.fadeAlpha), 3);
        }
        return fill;
    }

    private boolean inside(int x, int y, int width, int height) {
        return this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + height;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (Transitions.isBusy()) {
            return;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.nameField.mouseClicked(mouseX, mouseY, button);
        this.seedField.mouseClicked(mouseX, mouseY, button);

        if (isOverBack()) {
            cancel();
            return;
        }
        int tileWidth = (this.contentWidth - TILE_GAP * (MODES.length - 1)) / MODES.length;
        for (int i = 0; i < MODES.length; i++) {
            int x = this.contentX + i * (tileWidth + TILE_GAP);
            if (inside(x, this.modeY, tileWidth, this.modeHeight)) {
                this.state.setGameMode(MODES[i].mode);
                click();
                return;
            }
        }
        if (clickedType() || clickedToggle()) {
            return;
        }
        if (inside(createX(), this.actionY, CREATE_WIDTH, 20)) {
            create();
            return;
        }
        if (isCustomizable() && inside(customizeX(), this.actionY, SIDE_WIDTH, 20)) {
            PresetEditor editor = this.state.getPresetEditor();
            commitFields();
            this.mc.setScreen(editor.createEditScreen(this.delegate, this.state.getSettings()));
            return;
        }
        if (inside(moreX(), this.actionY, SIDE_WIDTH, 20)) {
            commitFields();
            showVanilla = this.delegate;
            this.mc.setScreen(this.delegate);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickedType() {
        if (isOverPrevPage()) {
            turnTypePage(-1);
            return true;
        }
        if (isOverNextPage()) {
            turnTypePage(1);
            return true;
        }
        int count = this.types.size();
        int first = this.typePage * this.typePerPage;
        int tileWidth = typeTileWidth();
        for (int cell = 0; cell < this.typePerPage; cell++) {
            int slot = first + cell;
            if (slot >= count) {
                break;
            }
            if (inside(typeTileX(cell), typeTileY(cell), tileWidth, this.typeRowHeight)) {
                this.state.setWorldType(this.types.get(slot));
                click();
                return true;
            }
        }
        return false;
    }

    private boolean clickedToggle() {
        int columns = 3;
        int tileWidth = (this.contentWidth - TILE_GAP * (columns - 1)) / columns;
        for (int i = 0; i < columns; i++) {
            int x = this.contentX + i * (tileWidth + TILE_GAP);
            if (!inside(x, this.toggleY, tileWidth, this.toggleHeight)) {
                continue;
            }
            if (isHardcore() && i > 0) {
                return true;
            }
            switch (i) {
                case 0: this.state.setGenerateStructures(!this.state.isGenerateStructures()); break;
                case 1: this.state.setBonusChest(!this.state.isBonusChest()); break;
                default: this.state.setAllowCommands(!this.state.isAllowCommands()); break;
            }
            click();
            return true;
        }
        return false;
    }

    private void click() {
        if (UiConfig.buttonSounds) {
            UkySounds.play(UkySounds.BUTTON, 0.7F, 1.0F);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            cancel();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {   // enter
            create();
            return;
        }
        if (keyCode == 15) {                      // tab swaps fields
            boolean toSeed = this.nameField.isFocused();
            this.nameField.setFocused(!toSeed);
            this.seedField.setFocused(toSeed);
            return;
        }
        this.nameField.textboxKeyTyped(typedChar, keyCode);
        this.seedField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = com.console.uky.compat.Mouse.getEventDWheel();
        if (wheel == 0 || this.typePageCount <= 1) {
            return;
        }
        boolean overGrid = this.mouseX >= this.contentX
                && this.mouseX <= this.contentX + this.contentWidth
                && this.mouseY >= this.typeY - 16
                && this.mouseY <= this.typeY + this.typeHeight;
        if (overGrid) {
            turnTypePage(wheel > 0 ? -1 : 1);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.nameField.updateCursorCounter();
        this.seedField.updateCursorCounter();
    }

    @Override
    protected void onAction(GuiButton button) {
        // Everything here is drawn, not a GuiButton.
    }

    /** The fields are this screen's; the state only hears about them when it matters. */
    private void commitFields() {
        String name = this.nameField.getText().trim();
        this.state.setName(name.isEmpty() ? I18n.get("selectWorld.newWorld") : name);
        this.state.setSeed(this.seedField.getText());
    }

    /** Back where vanilla's screen would go, with its temporary datapack folder cleaned up. */
    private void cancel() {
        this.delegate.popScreen();
    }

    /**
     * Falls into the hole, then hands the world to vanilla's own create action — which
     * writes the save, checks experimental features and starts the server.
     */
    private void create() {
        if (this.created) {
            return;
        }
        this.created = true;
        commitFields();
        Transitions.dive(new Runnable() {
            @Override
            public void run() {
                try {
                    Method onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
                    onCreate.setAccessible(true);
                    onCreate.invoke(GuiCreateWorldScreen.this.delegate);
                } catch (Throwable t) {
                    UkyUI.LOGGER.error("Could not create the world", t);
                    GuiCreateWorldScreen.this.created = false;
                }
            }
        });
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

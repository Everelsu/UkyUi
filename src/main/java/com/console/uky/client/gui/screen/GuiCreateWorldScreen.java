package com.console.uky.client.gui.screen;

import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.world.UkyLoadingScreen;
import com.console.uky.client.world.WorldPreviews;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * World creation, in the same language as the world list.
 *
 * Vanilla stacks eight identical grey buttons whose labels have to be read one by
 * one, and half of them cycle through their options on click so you cannot see
 * what the alternatives are without pressing. Here every choice is laid out as
 * tiles: the options are all visible at once, the selected one is lit, and a line
 * of description sits on the tile rather than floating underneath the button.
 *
 * The creation itself is deliberately identical to vanilla's, down to the seed
 * parsing and the folder-name sanitising, which is borrowed from
 * {@link GuiCreateWorld} rather than reimplemented — world folders have rules
 * (reserved Windows device names among them) that are not worth getting wrong.
 */
public class GuiCreateWorldScreen extends MenuScreen {

    /** One selectable game mode. */
    private static final class Mode {
        final String id;
        final boolean hardcore;

        Mode(String id, boolean hardcore) {
            this.id = id;
            this.hardcore = hardcore;
        }
    }

    private static final Mode[] MODES = new Mode[] {
            new Mode("survival", false),
            new Mode("hardcore", true),
            new Mode("creative", false),
    };

    private static final int TILE_GAP = 6;

    private GuiTextField nameField;
    private GuiTextField seedField;

    private int selectedMode;
    /** Index into {@link WorldType#worldTypes}. */
    private int selectedType;
    private final List<Integer> types = new ArrayList<Integer>();

    /**
     * Which page of world types is on screen.
     *
     * A pack with a worldgen mod or three can register a dozen creatable types, and
     * they all have to be reachable — a type you cannot select is a type the mod
     * might as well not have registered. The grid below shows as many as the space
     * honestly holds and pages through the rest.
     */
    private int typePage;
    private int typeColumns = 1;
    private int typeRows = 1;
    /** Height of one row of type tiles; {@link #typeHeight} is the whole block. */
    private int typeRowHeight;
    private int typePerPage = 1;
    private int typePageCount = 1;

    private boolean generateStructures = true;
    private boolean bonusChest;
    private boolean allowCheats;

    /** Generator options for customised world types; vanilla stores the same string. */
    public String generatorOptions = "";

    private boolean created;

    // Layout, all computed in buildLayout.
    private int contentX;
    private int contentWidth;
    private int modeY;
    private int modeHeight;
    private int typeY;
    private int typeHeight;
    private int toggleY;
    private int toggleHeight;
    private int actionY;

    private float[] modeHover = new float[MODES.length];
    private float[] typeHover = new float[0];
    private float[] toggleHover = new float[3];
    private float createHover;
    private float backHover;
    private float customizeHover;
    private float prevPageHover;
    private float nextPageHover;

    /**
     * Eased weight of the hardcore mood, 0 to 1.
     *
     * Kept separate from the selection so the room warms up and cools down instead of
     * switching: choosing hardcore should feel like a decision rather than a radio
     * button. It also drives a slow pulse, which is what stops it reading as a flat
     * red filter somebody forgot to take off.
     */
    private float hardcoreMood;

    private int mouseX;
    private int mouseY;

    /**
     * Name and seed to open with, when this screen was not opened blank.
     *
     * The fields themselves are built in {@link #buildLayout()}, which runs after the
     * constructor and again on every resize, so a value set on the widget here would
     * be thrown away before it was ever seen.
     */
    private String initialName;
    private String initialSeed;

    public GuiCreateWorldScreen(GuiScreen parent) {
        super(parent);
    }

    /**
     * World creation pre-filled from an existing world — vanilla's "Re-Create".
     *
     * The stock world list has this button and ours had dropped it, which is a
     * capability lost rather than a control moved: it is the only way to get a second
     * world on the same seed and the same generator settings without writing the seed
     * down by hand first, and the settings it copies (generator options above all) are
     * not shown anywhere a player could copy them from.
     *
     * <p>The mapping is vanilla's own, from {@code GuiCreateWorld.func_146318_a},
     * including the copy-of name — this should produce the same world the stock button
     * would, or it is not the same feature.
     */
    public static GuiCreateWorldScreen recreating(GuiScreen parent, WorldInfo info) {
        GuiCreateWorldScreen screen = new GuiCreateWorldScreen(parent);
        screen.initialName = I18n.format("selectWorld.newWorld.copyOf",
                new Object[] { info.getWorldName() });
        screen.initialSeed = String.valueOf(info.getSeed());
        screen.selectedType = info.getTerrainType().getWorldTypeID();
        screen.generatorOptions = info.getGeneratorOptions();
        screen.generateStructures = info.isMapFeaturesEnabled();
        screen.allowCheats = info.areCommandsAllowed();

        if (info.isHardcoreModeEnabled()) {
            screen.selectedMode = 1;
        } else if (info.getGameType().isCreative()) {
            screen.selectedMode = 2;
        } else {
            screen.selectedMode = 0;
        }
        // The bonus chest is deliberately not carried over, exactly as in vanilla: it
        // is a one-off at spawn rather than a property of the world being copied.
        return screen;
    }

    @Override
    protected boolean isVoid() {
        // Same side of the dive as the world list it was opened from.
        return true;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    public void initGui() {
        super.initGui();
        // Text fields need the key repeat that vanilla screens switch on.
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    protected void buildLayout() {
        collectTypes();

        int margin = Math.max(24, (int) (this.width * 0.08F));
        this.contentX = margin;
        this.contentWidth = this.width - margin * 2;

        // Everything is sized from what is left over rather than from constants, so
        // the screen works from GUI scale 4 on a small window up to scale 1 on a
        // large one.
        int headerBottom = 52;
        int bottomLimit = this.height - 20;
        int actionHeight = 20;

        // Name and seed fields take a fixed strip; the three tile sections share the
        // rest in a 5 : 4 : 3 ratio, which keeps the mode tiles readable when space
        // is tight. The 4 is one row of world types; layoutTypeGrid then spends any
        // slack left over on further rows of them, and pages whatever still does not
        // fit rather than growing past the space.
        int fields = 44;
        int available = Math.max(80, bottomLimit - headerBottom - fields - actionHeight - 24);

        this.modeHeight = clamp(available * 5 / 12, 34, 62);
        this.typeRowHeight = clamp(available * 4 / 12, 24, 40);
        this.toggleHeight = clamp(available * 3 / 12, 18, 26);
        layoutTypeGrid(available);

        int tiles = this.modeHeight + this.typeHeight + this.toggleHeight;
        int gap = clamp((available - tiles) / 2, 10, 30);

        // The rows are capped, so on a tall screen they do not use everything they
        // are offered. The block is centred in what is left rather than pinned to
        // the top with the action row stranded at the bottom — that left a band of
        // empty screen straight down the middle of the layout.
        int blockHeight = fields + tiles + gap * 2 + 24 + actionHeight;
        int slack = Math.max(0, (bottomLimit - headerBottom) - blockHeight);
        int blockTop = headerBottom + slack / 3;

        this.modeY = blockTop + fields;
        this.typeY = this.modeY + this.modeHeight + gap;
        this.toggleY = this.typeY + this.typeHeight + gap;
        this.actionY = this.toggleY + this.toggleHeight + 24;

        int fieldWidth = (this.contentWidth - 12) / 2;
        String name = this.nameField != null
                ? this.nameField.getText()
                : (this.initialName != null
                        ? this.initialName
                        : unusedDefaultName());
        String seed = this.seedField != null
                ? this.seedField.getText()
                : (this.initialSeed != null ? this.initialSeed : "");

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

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Shapes the world-type grid: how many columns, how many rows, how many pages.
     *
     * This used to be one row of at most four, with the leftovers reachable only by
     * clicking the tile that was already selected — which rotated the row. Nothing on
     * screen said so, so a modded type past the fourth was, in practice, unselectable:
     * you had to guess that a tile doing nothing visible was in fact a paging control.
     * Now the grid takes as many rows as the space genuinely holds, and anything still
     * left over is paged with arrows that are drawn, labelled and countable.
     *
     * @param available vertical space the three tile rows share
     */
    private void layoutTypeGrid(int available) {
        int count = this.types.size();

        // Roughly 150 units is what a type tile needs before its label starts being
        // cut; below two columns the grid is not a grid and the tiles look like a
        // list that lost its rows.
        this.typeColumns = clamp(this.contentWidth / 150, 2, 4);
        this.typeColumns = Math.max(1, Math.min(this.typeColumns, count));

        // What is left for the type block once the other two rows and the minimum air
        // between them are taken out. Rows are added only while they actually fit —
        // a second row squeezed into a first row's space is worse than a page arrow.
        int spare = available - this.modeHeight - this.toggleHeight - 20;
        int fits = Math.max(1, (spare + TILE_GAP) / (this.typeRowHeight + TILE_GAP));
        int wanted = (count + this.typeColumns - 1) / this.typeColumns;
        this.typeRows = Math.max(1, Math.min(Math.min(fits, wanted), 3));

        this.typeHeight = this.typeRows * this.typeRowHeight
                + (this.typeRows - 1) * TILE_GAP;

        this.typePerPage = Math.max(1, this.typeColumns * this.typeRows);
        this.typePageCount = Math.max(1, (count + this.typePerPage - 1) / this.typePerPage);

        // Open on the page the current selection is on. Anything else means the screen
        // can come back from a resize showing a page the selected type is not on, with
        // no tile lit anywhere.
        int slot = this.types.indexOf(Integer.valueOf(this.selectedType));
        this.typePage = slot < 0 ? 0 : slot / this.typePerPage;
    }

    /** Width of one type tile in the current grid. */
    private int typeTileWidth() {
        return (this.contentWidth - TILE_GAP * (this.typeColumns - 1)) / this.typeColumns;
    }

    /** Screen rect of the tile in grid cell {@code cell}, as {x, y}. */
    private int typeTileX(int cell) {
        return this.contentX + (cell % this.typeColumns) * (typeTileWidth() + TILE_GAP);
    }

    private int typeTileY(int cell) {
        return this.typeY + (cell / this.typeColumns) * (this.typeRowHeight + TILE_GAP);
    }

    /** Every world type that can actually be created, mods included. */
    private void collectTypes() {
        this.types.clear();
        for (int i = 0; i < WorldType.worldTypes.length; i++) {
            WorldType type = WorldType.worldTypes[i];
            if (type != null && type.getCanBeCreated()) {
                this.types.add(Integer.valueOf(i));
            }
        }
        if (this.types.isEmpty()) {
            this.types.add(Integer.valueOf(0));
        }
        if (!this.types.contains(Integer.valueOf(this.selectedType))) {
            this.selectedType = this.types.get(0).intValue();
        }
    }

    private boolean isHardcore() {
        return MODES[this.selectedMode].hardcore;
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

    /**
     * Red wash over the backdrop while hardcore is selected.
     *
     * Two layers doing different jobs: a wide radial bloom from low on the screen,
     * which is what actually tints the room, and a pair of edge gradients that keep
     * the middle — where the text is — comparatively clear. The breathing comes from
     * two slow sines at different periods, so it drifts instead of blinking on a
     * count. Drawn before everything else, so it colours the scene rather than the
     * controls sitting in it.
     */
    private void drawHardcoreWash() {
        if (this.hardcoreMood <= 0.01F) {
            return;
        }
        float mood = this.hardcoreMood * this.fadeAlpha;
        // Two periods that do not share a factor, so the pulse never lands on a beat.
        float breath = 0.78F
                + (float) Math.sin(this.elapsed * 1.15F) * 0.14F
                + (float) Math.sin(this.elapsed * 0.41F) * 0.08F;
        float strength = mood * breath;

        Draw.radialGlow(this.width * 0.5F, this.height * 1.02F,
                Math.max(this.width, this.height) * 0.95F,
                Draw.withAlpha(Theme.danger, 0.20F * strength),
                Draw.withAlpha(Theme.danger, 0.0F));

        // Held off the centre so the form stays readable.
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
        this.fontRendererObj.drawString(
                I18n.format("selectWorld.create", new Object[0]).toUpperCase(),
                this.contentX, 26, Draw.withAlpha(Theme.text, this.fadeAlpha));
        Draw.gradientH(this.contentX, 40, this.contentX + this.contentWidth, 41,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        this.backHover = Ease.approach(this.backHover, isOverBack() ? 1.0F : 0.0F,
                0.05F, this.delta);
        int colour = Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.backHover),
                this.fadeAlpha);
        // Chevron first, label right-aligned against it. Fixed offsets worked for
        // "Back" and ran the chevron through the last letter of "Отмена".
        int right = this.contentX + this.contentWidth;
        Icons.back(right - 6, 30, 9, colour);
        String cancel = I18n.format("gui.cancel", new Object[0]);
        this.fontRendererObj.drawString(cancel,
                right - 16 - this.fontRendererObj.getStringWidth(cancel), 26, colour);
    }

    private boolean isOverBack() {
        int right = this.contentX + this.contentWidth;
        return this.mouseX >= right - 56 && this.mouseX <= right
                && this.mouseY >= 20 && this.mouseY <= 40;
    }

    private void drawFields() {
        drawField(this.nameField, I18n.format("selectWorld.enterName", new Object[0]), false);
        drawField(this.seedField, I18n.format("selectWorld.enterSeed", new Object[0]), true);
    }

    /** A field is a caption, a dark strip and a rule that lights up when focused. */
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

        // The seed field's placeholder explains what leaving it blank does, which
        // vanilla only says in a separate line of grey text below the box.
        if (withDice && field.getText().isEmpty() && !focused) {
            this.fontRendererObj.drawString(
                    I18n.format("selectWorld.seedInfo", new Object[0]),
                    field.xPosition + 2, field.yPosition + 1,
                    Draw.withAlpha(Theme.textDisabled, 0.7F * this.fadeAlpha));
        }
    }

    private void drawModes() {
        drawSectionLabel(I18n.format("selectWorld.gameMode", new Object[0]), this.modeY - 12);

        int tileWidth = (this.contentWidth - TILE_GAP * (MODES.length - 1)) / MODES.length;
        for (int i = 0; i < MODES.length; i++) {
            int x = this.contentX + i * (tileWidth + TILE_GAP);
            boolean over = inside(x, this.modeY, tileWidth, this.modeHeight);
            this.modeHover[i] = Ease.approach(this.modeHover[i], over ? 1.0F : 0.0F,
                    0.05F, this.delta);

            boolean selected = i == this.selectedMode;
            boolean danger = MODES[i].hardcore;
            int fill = drawTile(x, this.modeY, tileWidth, this.modeHeight,
                    this.modeHover[i], selected, danger, true);

            int accent = danger ? Theme.danger : Theme.accent;
            int tint = Draw.withAlpha(selected ? accent
                    : Draw.mix(Theme.textDim, accent, this.modeHover[i]), this.fadeAlpha);

            float iconSize = Math.min(16.0F, this.modeHeight * 0.4F);
            float iconX = x + 14;
            // Pinned near the top: the description underneath then gets the whole
            // rest of the tile, however tall the window makes it.
            float iconY = this.modeY + 15;
            if (i == 0) {
                Icons.sword(iconX, iconY, iconSize, tint);
            } else if (i == 1) {
                Icons.skull(iconX, iconY, iconSize, tint, fill);
            } else {
                Icons.spark(iconX, iconY, iconSize, tint);
            }

            String label = I18n.format("selectWorld.gameMode." + MODES[i].id, new Object[0]);
            drawFitted(label, (int) (x + 26), (int) (iconY - 4), (int) (tileWidth - 32),
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));

            // Description wrapped into the tile instead of printed under the button
            // in grey. Hardcore uses our own wording: vanilla's two halves are
            // written to wrap inside a narrow button, so whichever half landed here
            // read as a dangling fragment ("...difficulty and only one life").
            String blurbKey = MODES[i].hardcore
                    ? "uky.gamemode.hardcore"
                    : "selectWorld.gameMode." + MODES[i].id;
            drawTileBlurb(x, tileWidth, this.modeY + 30,
                    I18n.format(blurbKey + ".line1", new Object[0]),
                    I18n.format(blurbKey + ".line2", new Object[0]));
        }
    }

    /**
     * Vanilla's two description lines, re-wrapped to the tile.
     *
     * They are pre-split for a 150px button, so pasting them in as-is either
     * overflows or gets cut mid-word — and in a language like Russian that happens
     * on nearly every line. Joining them and re-wrapping to the actual width lets
     * the text use the space it has.
     */
    @SuppressWarnings("unchecked")
    private void drawTileBlurb(int x, int tileWidth, float y, String line1, String line2) {
        int colour = Draw.withAlpha(Theme.textDim, 0.75F * this.fadeAlpha);
        int maxWidth = tileWidth - 20;

        String text = line1;
        if (line2 != null && !line2.isEmpty()) {
            text = text + " " + line2;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, maxWidth);

        // Only as many lines as the tile can hold; the last visible one takes an
        // ellipsis so a cut-off sentence does not look like a rendering fault.
        int room = Math.max(1, (int) ((this.modeY + this.modeHeight - 6 - y) / 10));
        for (int i = 0; i < lines.size() && i < room; i++) {
            String line = lines.get(i);
            if (i == room - 1 && lines.size() > room) {
                // The cut here is vertical — there are more lines than the tile can
                // hold — so the ellipsis has to be forced on. fit() only marks a line
                // that is too wide, and the last line that fits usually is not, which
                // left blurbs ending on a bare comma as though the text were broken.
                line = this.fontRendererObj.trimStringToWidth(line,
                        maxWidth - this.fontRendererObj.getStringWidth("...")).trim() + "...";
            }
            this.fontRendererObj.drawString(line, x + 10, (int) y + i * 10, colour);
        }
    }

    private void drawTypes() {
        drawSectionLabel(I18n.format("selectWorld.mapType", new Object[0]), this.typeY - 12);
        drawTypePager();

        int count = this.types.size();
        int first = this.typePage * this.typePerPage;
        int tileWidth = typeTileWidth();

        for (int cell = 0; cell < this.typePerPage; cell++) {
            int slot = first + cell;
            if (slot >= count) {
                break;
            }
            int typeIndex = this.types.get(slot).intValue();
            WorldType type = WorldType.worldTypes[typeIndex];
            int x = typeTileX(cell);
            int y = typeTileY(cell);
            boolean over = inside(x, y, tileWidth, this.typeRowHeight);
            this.typeHover[slot] = Ease.approach(this.typeHover[slot], over ? 1.0F : 0.0F,
                    0.05F, this.delta);

            boolean selected = typeIndex == this.selectedType;
            drawTile(x, y, tileWidth, this.typeRowHeight, this.typeHover[slot],
                    selected, false, true);

            int tint = Draw.withAlpha(selected ? Theme.accent
                    : Draw.mix(Theme.textDim, Theme.accent, this.typeHover[slot]), this.fadeAlpha);
            float cy = y + this.typeRowHeight * 0.5F;
            float iconSize = Math.min(14.0F, this.typeRowHeight * 0.5F);
            String name = type.getWorldTypeName().toLowerCase();
            if (name.contains("flat")) {
                Icons.flat(x + 13, cy, iconSize, tint);
            } else if (name.contains("large") || name.contains("amplified")) {
                Icons.globe(x + 13, cy, iconSize, tint);
            } else {
                Icons.terrain(x + 13, cy, iconSize, tint);
            }

            String label = fit(typeLabel(type), tileWidth - 30);
            this.fontRendererObj.drawString(label, x + 24, (int) (cy - 4),
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));
        }
    }

    /**
     * What to call a world type.
     *
     * Vanilla's own types have a translation; a mod's frequently does not, and
     * {@code I18n.format} hands back the untranslated key when it finds no entry —
     * so the tile would read "generator.mymod.skyland" instead of a name. The
     * registered type name is at least a word, which is the whole point of showing it.
     */
    private static String typeLabel(WorldType type) {
        String key = type.getTranslateName();
        String translated = I18n.format(key, new Object[0]);
        return translated.equals(key) ? type.getWorldTypeName() : translated;
    }

    /**
     * "&lt; 2/3 &gt;" beside the section heading, when there is more than one page.
     *
     * Drawn on the heading's own line rather than under the grid: that is where the
     * eye already is when it is reading what this row of tiles is, and it puts the
     * count — the part that says there is more to see — next to the words rather than
     * at the bottom of a block the player has already decided is all of it.
     */
    private void drawTypePager() {
        if (this.typePageCount <= 1) {
            return;
        }
        int y = this.typeY - 12;
        boolean overPrev = isOverPrevPage();
        boolean overNext = isOverNextPage();
        this.prevPageHover = Ease.approach(this.prevPageHover, overPrev ? 1.0F : 0.0F,
                0.05F, this.delta);
        this.nextPageHover = Ease.approach(this.nextPageHover, overNext ? 1.0F : 0.0F,
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

    /** Space kept clear to the right of the page counter for the forward arrow. */
    private static final int PAGER_ARROW_ROOM = 14;

    private boolean isOverPrevPage() {
        if (this.typePageCount <= 1) {
            return false;
        }
        String counter = (this.typePage + 1) + "/" + this.typePageCount;
        int right = this.contentX + this.contentWidth
                - PAGER_ARROW_ROOM - this.fontRendererObj.getStringWidth(counter);
        // Generous: the arrow itself is eight units across, which is not a target.
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

    /** Moves the grid {@code step} pages along, wrapping at either end. */
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

            String label = fit(toggleLabel(i),
                    tileWidth - (int) box - 14);
            this.fontRendererObj.drawString(label, (int) (bx + box + 6),
                    (int) (this.toggleY + this.toggleHeight / 2.0F - 4),
                    Draw.withAlpha(on ? Theme.text : Theme.textDim, alpha));
        }

        if (hardcore) {
            // Our own short line rather than half of vanilla's split sentence, which
            // arrived here as "...difficulty and only one life" with no beginning.
            String note = fit(
                    I18n.format("uky.gamemode.hardcore.warning", new Object[0]),
                    this.contentWidth);
            this.fontRendererObj.drawString(note,
                    this.contentX, this.toggleY + this.toggleHeight + 6,
                    Draw.withAlpha(Theme.danger, 0.85F * this.fadeAlpha));
        }
    }

    private boolean toggleValue(int index) {
        switch (index) {
            case 0: return this.generateStructures;
            case 1: return this.bonusChest;
            default: return this.allowCheats;
        }
    }

    private String toggleLabel(int index) {
        String key;
        switch (index) {
            case 0: key = "selectWorld.mapFeatures"; break;
            case 1: key = "selectWorld.bonusItems"; break;
            default: key = "selectWorld.allowCommands"; break;
        }
        // These strings are written for vanilla's cycling buttons, which append
        // "ON"/"OFF" after a colon. A checkbox says that already, so the dangling
        // punctuation goes.
        String label = I18n.format(key, new Object[0]).trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        return label;
    }

    private void drawActions() {
        int createWidth = 130;
        int createX = this.contentX + this.contentWidth - createWidth;
        boolean over = inside(createX, this.actionY, createWidth, 20);
        this.createHover = Ease.approach(this.createHover, over ? 1.0F : 0.0F, 0.05F, this.delta);

        // The one obvious next action, so it is the only filled control on screen.
        int fill = Draw.mix(Theme.accent, Theme.textHover, this.createHover * 0.25F);
        Draw.rect(createX, this.actionY, createX + createWidth, this.actionY + 20,
                Draw.withAlpha(fill, (0.85F + this.createHover * 0.15F) * this.fadeAlpha));
        if (this.createHover > 0.02F) {
            Draw.glow(createX, this.actionY, createX + createWidth, this.actionY + 20, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.3F * this.createHover * this.fadeAlpha), 4);
        }
        String label = I18n.format("selectWorld.create", new Object[0]).toUpperCase();
        int labelWidth = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label,
                createX + (createWidth - labelWidth) / 2, this.actionY + 6,
                Draw.withAlpha(0x0B0B0E, this.fadeAlpha));

        if (isCustomizable()) {
            int customWidth = 110;
            int customX = createX - customWidth - 8;
            boolean overCustom = inside(customX, this.actionY, customWidth, 20);
            this.customizeHover = Ease.approach(this.customizeHover, overCustom ? 1.0F : 0.0F,
                    0.05F, this.delta);
            Draw.rect(customX, this.actionY, customX + customWidth, this.actionY + 20,
                    Draw.withAlpha(0x000000, (0.5F + this.customizeHover * 0.2F) * this.fadeAlpha));
            Draw.border(customX, this.actionY, customX + customWidth, this.actionY + 20, 1.0F,
                    Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.customizeHover),
                            this.fadeAlpha));
            String custom = I18n.format("selectWorld.customizeType", new Object[0]);
            custom = fit(custom, customWidth - 12);
            int w = this.fontRendererObj.getStringWidth(custom);
            this.fontRendererObj.drawString(custom, customX + (customWidth - w) / 2,
                    this.actionY + 6, Draw.withAlpha(
                            Draw.mix(Theme.textDim, Theme.textHover, this.customizeHover),
                            this.fadeAlpha));
        }
    }

    private boolean isCustomizable() {
        WorldType type = WorldType.worldTypes[this.selectedType];
        return type != null && type.isCustomizable();
    }

    private void drawSectionLabel(String text, int y) {
        // Several of these keys end in a colon because vanilla appends the current
        // value to them; here they are headings, so the punctuation goes.
        String label = text.trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        this.fontRendererObj.drawString(label.toUpperCase(), this.contentX, y,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));
    }

    /**
     * Common tile plate. Returns the fill colour used, so an icon that has to punch
     * holes in itself can paint them in the same shade.
     */
    private int drawTile(int x, int y, int width, int height, float hover,
                         boolean selected, boolean danger, boolean glow) {
        int accent = danger ? Theme.danger : Theme.accent;
        float lift = selected ? 0.28F : hover * 0.2F;
        int fill = Draw.withAlpha(0x000000, (0.55F + lift) * this.fadeAlpha);

        Draw.rect(x, y, x + width, y + height, fill);
        if (selected) {
            // A left rail rather than a heavier border: the selection reads even
            // when several tiles are hovered in sequence.
            Draw.rect(x, y, x + 2, y + height, Draw.withAlpha(accent, this.fadeAlpha));
        }
        // Ten tiles at full-strength borders turn the screen into a wireframe, so
        // an unselected edge is barely there until the pointer finds it.
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
                this.selectedMode = i;
                click();
                return;
            }
        }

        if (clickedType()) {
            return;
        }
        if (clickedToggle()) {
            return;
        }

        int createWidth = 130;
        int createX = this.contentX + this.contentWidth - createWidth;
        if (inside(createX, this.actionY, createWidth, 20)) {
            create();
            return;
        }
        if (isCustomizable()) {
            int customX = createX - 118;
            if (inside(customX, this.actionY, 110, 20)) {
                WorldType.worldTypes[this.selectedType].onCustomizeButton(this.mc, bridge());
                return;
            }
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
            if (!inside(typeTileX(cell), typeTileY(cell), tileWidth, this.typeRowHeight)) {
                continue;
            }
            // A tile does one thing and it is the obvious one. Clicking the selected
            // tile used to rotate the row, which is how the extra types were reached;
            // the pager does that now, visibly.
            this.selectedType = this.types.get(slot).intValue();
            click();
            return true;
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
                // Hardcore rules out cheats and the bonus chest; the tile is drawn
                // dimmed and does nothing rather than silently changing a value
                // that will be discarded.
                return true;
            }
            switch (i) {
                case 0: this.generateStructures = !this.generateStructures; break;
                case 1: this.bonusChest = !this.bonusChest; break;
                default: this.allowCheats = !this.allowCheats; break;
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

    /**
     * The wheel turns the type pages while the pointer is over them.
     *
     * The arrows are the discoverable control; this is the one anybody who has already
     * worked out that the grid has pages will reach for first.
     */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
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
        this.nameField.updateCursorCounter();
        this.seedField.updateCursorCounter();
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control here is drawn, not a GuiButton.
    }

    private void cancel() {
        if (this.parent instanceof MenuScreen) {
            switchBack();
        } else {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    // ---------------------------------------------------------------- create --

    /**
     * Same sequence vanilla runs, in the same order.
     *
     * The seed is parsed as a long when it looks like one and hashed when it does
     * not, which is what makes word seeds work; a literal zero falls back to random
     * because zero is the "unset" value the field starts at.
     */
    private void create() {
        if (this.created) {
            return;
        }
        this.created = true;

        String name = this.nameField.getText().trim();
        if (name.isEmpty()) {
            name = I18n.format("selectWorld.newWorld", new Object[0]);
        }

        long seed = new Random().nextLong();
        String raw = this.seedField.getText();
        if (raw != null && !raw.isEmpty()) {
            try {
                long parsed = Long.parseLong(raw);
                if (parsed != 0L) {
                    seed = parsed;
                }
            } catch (NumberFormatException e) {
                seed = raw.hashCode();
            }
        }

        WorldType type = WorldType.worldTypes[this.selectedType];
        type.onGUICreateWorldPress();

        boolean hardcore = isHardcore();
        WorldSettings.GameType gameType =
                WorldSettings.GameType.getByName(hardcore ? "survival" : MODES[this.selectedMode].id);
        WorldSettings settings = new WorldSettings(seed, gameType,
                this.generateStructures, hardcore, type);
        settings.func_82750_a(this.generatorOptions);
        if (this.bonusChest && !hardcore) {
            settings.enableBonusChest();
        }
        if (this.allowCheats && !hardcore) {
            settings.enableCommands();
        }

        final String folder = folderName(name);
        final String displayName = name;
        final WorldSettings finalSettings = settings;
        // A brand new world has no capture yet, so the loading screen stays dark — which
        // is right. Naming it here is what stops the previous world's picture showing up
        // behind the progress bar.
        WorldPreviews.setEnteringWorld(folder);

        // Down the hole, and the world is launched once the screen is black.
        //
        // Creating a world used to cut: the form was on screen one frame and the loading
        // screen the next. Every other way of leaving this menu is staged — picking a
        // world dives, opening a list dives, coming back climbs out — so the one action
        // that actually takes you somewhere was the only one that just happened. Diving
        // costs the three quarters of a second it takes to go dark, and the dark it
        // arrives at is the loading screen's own colour, so there is no boundary left to
        // see between the two.
        Transitions.dive(new Runnable() {
            @Override
            public void run() {
                // Both inside the dive rather than before it: see GuiWorldsScreen.play —
                // the loading screen has to be claimed as late as possible, because the
                // game replaces its own after mod init and on every resize.
                UkyLoadingScreen.install(GuiCreateWorldScreen.this.mc);
                GuiCreateWorldScreen.this.mc.displayGuiScreen(null);
                GuiCreateWorldScreen.this.mc.launchIntegratedServer(
                        folder, displayName, finalSettings);
            }
        });
    }

    /**
     * The default name, with a number on it if worlds by that name already exist.
     *
     * Vanilla offers "New World" every time and only ever de-duplicates the folder on
     * disk, so a player who creates several worlds without renaming ends up with a
     * list where every entry reads the same and the only way to tell them apart is to
     * remember the order they were made in. The number goes in the field before the
     * world is created, not silently at creation, so it is visible and still editable.
     *
     * @return the base name, or the base name followed by the first free number
     */
    private String unusedDefaultName() {
        String base = I18n.format("selectWorld.newWorld", new Object[0]);

        java.util.Set<String> taken = new java.util.HashSet<String>();
        try {
            @SuppressWarnings("unchecked")
            java.util.List<net.minecraft.world.storage.SaveFormatComparator> saves =
                    this.mc.getSaveLoader().getSaveList();
            for (int i = 0; i < saves.size(); i++) {
                String existing = saves.get(i).getDisplayName();
                if (existing != null) {
                    taken.add(existing.trim());
                }
            }
        } catch (Throwable t) {
            // An unreadable saves folder is the world list's problem to report, not
            // this field's. Without the list there is nothing to collide with.
            return base;
        }

        if (!taken.contains(base)) {
            return base;
        }
        // Starts at 2, so the first two worlds read "New World" and "New World 2"
        // rather than "New World 1" and a bare one that looks like it came first.
        for (int n = 2; n < 1000; n++) {
            String candidate = base + " " + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    /**
     * Folder name for a world called {@code name}.
     *
     * Delegated to vanilla: the rules include stripping characters the save format
     * rejects and dodging the reserved Windows device names, and getting either
     * wrong produces a world that cannot be saved.
     */
    private String folderName(String name) {
        String folder = name;
        for (char c : ChatAllowedCharacters.allowedCharacters) {
            folder = folder.replace(c, '_');
        }
        if (folder.trim().isEmpty()) {
            folder = "World";
        }
        return GuiCreateWorld.func_146317_a(this.mc.getSaveLoader(), folder);
    }

    // The customiser round-trip. Only one can be in flight, since it is modal.
    private static GuiCreateWorld pendingBridge;
    private static GuiCreateWorldScreen pendingOwner;

    /**
     * Some world types open their own customisation screen — superflat's layer
     * editor, for one — and {@code onCustomizeButton} accepts nothing but a
     * {@link GuiCreateWorld} to return to. So one is handed over purely as a
     * carrier for the generator options, and {@link #resume} swaps this screen back
     * in when the customiser tries to display it.
     */
    private GuiCreateWorld bridge() {
        GuiCreateWorld vanilla = new GuiCreateWorld(this.parent);
        vanilla.field_146334_a = this.generatorOptions;
        pendingBridge = vanilla;
        pendingOwner = this;
        return vanilla;
    }

    /**
     * If {@code gui} is a carrier this screen handed out, takes the edited options
     * back and returns the screen to restore. Null for any other GuiCreateWorld,
     * which is then someone opening world creation from elsewhere.
     */
    public static GuiCreateWorldScreen resume(GuiScreen gui) {
        if (gui == null || gui != pendingBridge) {
            return null;
        }
        GuiCreateWorldScreen owner = pendingOwner;
        owner.generatorOptions = pendingBridge.field_146334_a;
        pendingBridge = null;
        pendingOwner = null;
        return owner;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

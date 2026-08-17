package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.world.UkyLoadingScreen;
import com.console.uky.client.world.WorldEntryFade;
import com.console.uky.client.world.TileCracks;
import com.console.uky.client.world.TileShatter;
import com.console.uky.client.world.WorldPreviews;
import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraft.world.storage.WorldInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Singleplayer worlds as cards, on the far side of the dive.
 *
 * A world is a place, so it is shown as one: the last thing seen there, captured
 * automatically on the way out. A name and a date tell you almost nothing about
 * which save is which months later; a picture of the base tells you at a glance.
 *
 * The actions live on the card rather than in a button strip below the list. With
 * a strip you have to select first and act second, and the buttons are nowhere
 * near the thing they act on; on the card, what you point at is what you affect.
 * Worlds never left yet stay black, which reads correctly as "unvisited".
 */
public class GuiWorldsScreen extends MenuScreen {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy  HH:mm");

    /** Which control on a card the pointer is over. */
    private static final int HIT_NONE = 0;
    private static final int HIT_PLAY = 1;
    private static final int HIT_RENAME = 2;
    private static final int HIT_DELETE = 3;
    private static final int HIT_RECREATE = 4;

    private static final int TILE_GAP = 10;
    private static final float TILE_ASPECT = 16.0F / 9.0F;

    private final List<SaveFormatComparator> worlds = new ArrayList<SaveFormatComparator>();
    /** Smoothed hover weight per card, including the create card at index -1. */
    private float[] hoverAmount = new float[0];
    private float createHover;

    private int columns;
    /**
     * Seconds the bin must be held before a world is destroyed.
     *
     * Taken from the length of the sound that plays under it rather than picked for
     * feel, so the rise ends exactly where the world breaks. Releasing early runs the
     * fill back and cuts the sound, so letting go really is cancelling.
     */
    private static final float DELETE_HOLD_SECONDS = UkySounds.DELETE_HOLD_SECONDS;

    /**
     * Seconds to run the fill back down after letting go.
     *
     * A flat figure, not a multiple of the fill. It used to unwind at twice the fill
     * rate, which was half a second back when the hold was one; tied to the length of
     * the sound the hold became four and a half, and the unwind with it — so a released
     * bar sat there draining for over two seconds. That is not just slow, it puts the
     * sound out of step: grabbing the bin again restarts the take from its beginning
     * while the bar carries on from wherever it had drained to, and from then on the
     * rise and the fill are describing different amounts of time. Snapping back means a
     * second grab always starts both from nothing.
     */
    private static final float DELETE_UNWIND_SECONDS = 0.14F;

    /** Card whose bin is being held, or -1. */
    private int holdCard = -1;
    private float holdProgress;
    /**
     * Set after a Shift-click deletes something, cleared when the button comes up.
     *
     * Without it the hold starts the instant the deletion finishes: the button is
     * still physically down, the pointer is still over a bin, and the cards have all
     * shuffled up one — so the gesture would carry straight on into destroying
     * whichever world moved into the space the last one just left.
     */
    private boolean holdSuppressed;
    /**
     * The break, drawn on the card before it happens, and then broken along.
     *
     * Created when the hold starts and dropped when it is let go, so the pattern belongs
     * to that press: a second attempt cracks the tile somewhere else. Handed to
     * {@link TileShatter} at the end, which is what makes the pieces come apart along the
     * cracks the player was watching rather than along a fresh set nobody has seen.
     */
    private TileCracks cracks;
    /** Plays after the world is gone; holds nothing but pixels. */
    private TileShatter shatter;

    private int tileWidth;
    private int tileHeight;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;

    private float scroll;
    private float scrollTarget;

    private int hoveredCard = -2;   // -1 is the create card, -2 is nothing
    private int hoveredAction = HIT_NONE;

    private boolean launching;

    public GuiWorldsScreen(GuiScreen parent) {
        super(parent);
    }

    /**
     * Plain dark. Nothing behind the cards at all.
     *
     * A dimmed black hole was tried back here and is not wanted: the previews are
     * the only pictures this screen should have, and anything textured behind them
     * competes. Past the horizon there is just the dark.
     */
    @Override
    protected boolean isVoid() {
        return true;
    }

    /**
     * No vignette either.
     *
     * The overlay's darkened edges are meant to hold the eye on artwork. Over a
     * grid on a flat dark field they read as a smudge framing the container, which
     * is the one thing this layout does not need. The dive blackout still runs.
     */
    @Override
    protected void drawOverlay() {
        float blackout = Transitions.blackout();
        if (blackout > 0.002F) {
            Draw.rect(0, 0, this.width, this.height, Draw.withAlpha(0x000000, blackout));
        }
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        loadWorlds();

        int margin = Math.max(24, (int) (this.width * 0.06F));
        this.gridX = margin;
        this.gridWidth = this.width - margin * 2;
        this.gridY = 54;
        this.gridHeight = this.height - this.gridY - 30;

        // Cards want to be big enough to read a screenshot in. Column count follows
        // from a target width rather than being fixed, so it adapts to the window.
        int target = 200;
        this.columns = Math.max(1, Math.min(5, (this.gridWidth + TILE_GAP) / (target + TILE_GAP)));
        this.tileWidth = (this.gridWidth - TILE_GAP * (this.columns - 1)) / this.columns;

        // A card taller than the grid it lives in is simply clipped, which on a short
        // window meant one enormous half-visible tile. Capping the width by the
        // height available keeps a whole row on screen; the row is then narrower than
        // the grid, so it gets centred rather than left hanging off to one side.
        int maxByHeight = (int) (this.gridHeight * TILE_ASPECT);
        if (this.tileWidth > maxByHeight) {
            this.tileWidth = Math.max(80, maxByHeight);
            int rowWidth = this.tileWidth * this.columns + TILE_GAP * (this.columns - 1);
            this.gridX += Math.max(0, (this.gridWidth - rowWidth) / 2);
        }
        this.tileHeight = (int) (this.tileWidth / TILE_ASPECT);

        this.hoverAmount = new float[this.worlds.size()];
        clampScroll();
    }

    @SuppressWarnings("unchecked")
    private void loadWorlds() {
        this.worlds.clear();
        try {
            List<SaveFormatComparator> list = this.mc.getSaveLoader().getSaveList();
            this.worlds.addAll(list);
            // Most recently played first: almost always the one wanted.
            Collections.sort(this.worlds);

            // Anything cached under a folder that is no longer here is dropped, and this
            // is the general form of a bug that showed up as one specific thing: a world
            // created after another was deleted opening on a photograph of the deleted
            // one. Both caches are keyed by folder name and Minecraft reissues folder
            // names, so a stale entry is not merely stale — it belongs to whoever gets
            // that name next. Doing it from here rather than at each deletion covers
            // every way a save can go, including ones this screen never sees.
            Set<String> live = new HashSet<String>();
            for (int i = 0; i < this.worlds.size(); i++) {
                live.add(this.worlds.get(i).getFileName());
            }
            WorldPreviews.retainOnly(live);
        } catch (Exception e) {
            UkyUI.LOGGER.error("Could not read the world list", e);
        }
    }

    /** Total rows including the create card, which sits first. */
    private int rowCount() {
        return (this.worlds.size() + 1 + this.columns - 1) / this.columns;
    }

    private int contentHeight() {
        return rowCount() * (this.tileHeight + TILE_GAP) - TILE_GAP;
    }

    private void clampScroll() {
        float max = Math.max(0.0F, contentHeight() - this.gridHeight);
        if (this.scrollTarget > max) {
            this.scrollTarget = max;
        }
        if (this.scrollTarget < 0.0F) {
            this.scrollTarget = 0.0F;
        }
    }

    /** Screen x of the card at {@code slot}, where slot 0 is the create card. */
    private int slotX(int slot) {
        return this.gridX + (slot % this.columns) * (this.tileWidth + TILE_GAP);
    }

    private float slotY(int slot) {
        return this.gridY + (slot / this.columns) * (this.tileHeight + TILE_GAP) - this.scroll;
    }

    // --------------------------------------------------------------- drawing --

    // ---- opening a world -----------------------------------------------------

    /** Seconds the tile takes to grow from the grid to the whole screen. */
    private static final float ZOOM_SECONDS = 0.42F;

    private int zoomIndex = -1;
    private float zoomProgress;
    private float zoomFromX;
    private float zoomFromY;
    private float zoomFromW;
    private float zoomFromH;
    private Runnable zoomAction;

    /**
     * Grows the clicked tile out to fill the screen, then launches.
     *
     * The loading screen that follows shows the same picture stretched over the same
     * full screen, so if this ends where that begins there is no cut anywhere between
     * clicking a world and standing in it — the tile simply becomes the window.
     */
    private void beginZoom(int index, Runnable action) {
        if (this.zoomIndex >= 0) {
            return;
        }
        int slot = index + 1;
        this.zoomIndex = index;
        this.zoomProgress = 0.0F;
        this.zoomFromX = slotX(slot);
        this.zoomFromY = slotY(slot);
        this.zoomFromW = this.tileWidth;
        this.zoomFromH = this.tileHeight;
        this.zoomAction = action;
    }

    private boolean isZooming() {
        return this.zoomIndex >= 0;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        if (isZooming()) {
            drawZoom();
            return;
        }

        this.scroll = Ease.approach(this.scroll, this.scrollTarget, 0.05F, this.delta);

        drawHeader();
        updateHover(mouseX, mouseY);
        updateHold();

        Draw.beginClip(this.gridX, this.gridY, this.gridWidth, this.gridHeight);
        drawCreateCard(0);
        for (int i = 0; i < this.worlds.size(); i++) {
            drawWorldCard(i, i + 1);
        }
        Draw.endClip();

        drawShatter();
        drawScrollHint();
    }

    private void drawZoom() {
        this.zoomProgress += this.delta / ZOOM_SECONDS;
        float t = Ease.outCubic(Math.min(1.0F, this.zoomProgress));

        // Everything else is already gone; the tile is the only thing on screen.
        Draw.rect(0, 0, this.width, this.height, Theme.background);

        float x1 = this.zoomFromX * (1.0F - t);
        float y1 = this.zoomFromY * (1.0F - t);
        float x2 = this.zoomFromX + this.zoomFromW
                + (this.width - this.zoomFromX - this.zoomFromW) * t;
        float y2 = this.zoomFromY + this.zoomFromH
                + (this.height - this.zoomFromY - this.zoomFromH) * t;

        ResourceLocation preview = this.zoomIndex < this.worlds.size()
                ? WorldPreviews.texture(this.worlds.get(this.zoomIndex).getFileName())
                : null;
        if (preview != null) {
            // Brightens toward the loading screen's own level as it grows, so the two
            // meet at the same exposure rather than stepping.
            int tint = (int) ((0.62F + 0.38F * t) * 255.0F);
            Draw.textureCover(preview, x1, y1, x2 - x1, y2 - y1,
                    WorldEntryFade.PREVIEW_W, WorldEntryFade.PREVIEW_H,
                    1.0F, 0.0F, 0.0F, Draw.withAlpha(tint << 16 | tint << 8 | tint, 1.0F));
        } else {
            Draw.rect(x1, y1, x2, y2, Draw.withAlpha(0x000000, 0.9F));
        }

        // The border thins out as the tile stops being a tile.
        float edge = 1.0F - t;
        if (edge > 0.02F) {
            Draw.border(x1, y1, x2, y2, 1.0F, Draw.withAlpha(Theme.accent, edge));
        }

        if (this.zoomProgress >= 1.0F) {
            Runnable action = this.zoomAction;
            this.zoomAction = null;
            this.zoomIndex = -1;
            if (action != null) {
                action.run();
            }
        }
    }

    private void drawHeader() {
        String title = I18n.format("selectWorld.title", new Object[0]);
        this.fontRendererObj.drawString(title, this.gridX, 26,
                Draw.withAlpha(Theme.text, this.fadeAlpha));
        Draw.gradientH(this.gridX, 40, this.gridX + this.gridWidth, 41,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        // Back chevron, top right.
        boolean over = isOverBack(this.lastMouseX, this.lastMouseY);
        int colour = Draw.withAlpha(over ? Theme.textHover : Theme.textDim, this.fadeAlpha);
        Icons.back(this.gridX + this.gridWidth - 10, 30, 9, colour);
        this.fontRendererObj.drawString(I18n.format("gui.back", new Object[0]),
                this.gridX + this.gridWidth - 8 + 6 - 44, 26, colour);
    }

    private boolean isOverBack(int mouseX, int mouseY) {
        int right = this.gridX + this.gridWidth;
        return mouseX >= right - 56 && mouseX <= right && mouseY >= 20 && mouseY <= 40;
    }

    private void updateHover(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.hoveredCard = -2;
        this.hoveredAction = HIT_NONE;

        boolean inGrid = mouseX >= this.gridX && mouseX < this.gridX + this.gridWidth
                && mouseY >= this.gridY && mouseY < this.gridY + this.gridHeight;

        if (inGrid) {
            int total = this.worlds.size() + 1;
            for (int slot = 0; slot < total; slot++) {
                float y = slotY(slot);
                int x = slotX(slot);
                if (mouseX >= x && mouseX < x + this.tileWidth
                        && mouseY >= y && mouseY < y + this.tileHeight) {
                    this.hoveredCard = slot - 1;
                    if (this.hoveredCard >= 0) {
                        this.hoveredAction = hitTestActions(mouseX, mouseY, x, y);
                    }
                    break;
                }
            }
        }

        this.createHover = Ease.approach(this.createHover,
                this.hoveredCard == -1 ? 1.0F : 0.0F, 0.05F, this.delta);
        for (int i = 0; i < this.hoverAmount.length; i++) {
            this.hoverAmount[i] = Ease.approach(this.hoverAmount[i],
                    this.hoveredCard == i ? 1.0F : 0.0F, 0.05F, this.delta);
        }
    }

    /** Icon boxes sit in the card's top-right; the rest of the card plays it. */
    private int hitTestActions(int mouseX, int mouseY, int cardX, float cardY) {
        float box = iconBox();
        float y0 = cardY + 4;

        if (mouseY >= y0 && mouseY <= y0 + box) {
            if (mouseX >= actionX(cardX, 0) && mouseX <= actionX(cardX, 0) + box) {
                return HIT_DELETE;
            }
            if (mouseX >= actionX(cardX, 1) && mouseX <= actionX(cardX, 1) + box) {
                return HIT_RENAME;
            }
            if (mouseX >= actionX(cardX, 2) && mouseX <= actionX(cardX, 2) + box) {
                return HIT_RECREATE;
            }
        }
        return HIT_PLAY;
    }

    /**
     * Left edge of the card's {@code slot}-th icon button, counting from the right.
     *
     * One expression for both the draw and the hit test. They were written out twice
     * with the same arithmetic, which held for two buttons and is exactly the kind of
     * thing that stops holding when a third arrives.
     */
    private float actionX(int cardX, int slot) {
        float box = iconBox();
        return cardX + this.tileWidth - (box + 4) * (slot + 1);
    }

    private float iconBox() {
        return Math.max(14.0F, this.tileWidth * 0.11F);
    }

    private void drawCreateCard(int slot) {
        int x = slotX(slot);
        float y = slotY(slot);
        if (y + this.tileHeight < this.gridY || y > this.gridY + this.gridHeight) {
            return;
        }
        float hover = this.createHover;
        float alpha = this.fadeAlpha;

        Draw.rect(x, y, x + this.tileWidth, y + this.tileHeight,
                Draw.withAlpha(0x000000, (0.55F + hover * 0.25F) * alpha));
        Draw.border(x, y, x + this.tileWidth, y + this.tileHeight, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, hover), alpha));

        float cx = x + this.tileWidth / 2.0F;
        float cy = y + this.tileHeight / 2.0F - 6;
        int colour = Draw.withAlpha(Draw.mix(Theme.textDim, Theme.accent, hover), alpha);
        Icons.plus(cx, cy, 18 + hover * 3, 2.0F, colour);

        String label = I18n.format("selectWorld.create", new Object[0]);
        int w = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label, (int) (cx - w / 2.0F),
                (int) (cy + 16), colour);

        if (hover > 0.02F) {
            Draw.glow(x, y, x + this.tileWidth, y + this.tileHeight, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.25F * hover * alpha), 4);
        }
    }

    private void drawWorldCard(int index, int slot) {
        int x = slotX(slot);
        float y = slotY(slot);
        if (y + this.tileHeight < this.gridY || y > this.gridY + this.gridHeight) {
            return;
        }

        SaveFormatComparator world = this.worlds.get(index);
        float hover = this.hoverAmount[index];
        float alpha = this.fadeAlpha;
        float x2 = x + this.tileWidth;
        float y2 = y + this.tileHeight;

        // Black plate: a world with no capture yet is exactly this and nothing more.
        Draw.rect(x, y, x2, y2, Draw.withAlpha(0x000000, 0.9F * alpha));

        ResourceLocation preview = WorldPreviews.texture(world.getFileName());
        if (preview != null) {
            // Dimmed at rest, lifting under the pointer.
            int tint = (int) ((0.62F + hover * 0.38F) * 255.0F);
            Draw.texture(preview, x, y, this.tileWidth, this.tileHeight,
                    Draw.withAlpha(tint << 16 | tint << 8 | tint, alpha));
        }

        // Caption band, always dark enough to read over any screenshot.
        Draw.gradientV(x, y2 - 26, x2, y2,
                Draw.withAlpha(0x000000, 0.0F), Draw.withAlpha(0x000000, 0.85F * alpha));

        String name = fit(world.getDisplayName(),
                this.tileWidth - 12);
        this.fontRendererObj.drawString(name, x + 6, (int) (y2 - 20),
                Draw.withAlpha(hover > 0.5F ? Theme.textHover : Theme.text, alpha));
        this.fontRendererObj.drawString(DATE.format(new Date(world.getLastTimePlayed())),
                x + 6, (int) (y2 - 10), Draw.withAlpha(Theme.textDim, 0.85F * alpha));

        if (world.isHardcoreModeEnabled()) {
            this.fontRendererObj.drawString("HARDCORE", x + 6, (int) y + 6,
                    Draw.withAlpha(Theme.danger, 0.95F * alpha));
        }

        // The break spreading while the bin is held. Over the picture and the caption but
        // under the buttons, because it is happening to the card rather than being part
        // of the controls.
        if (this.holdCard == index && this.holdProgress > 0.0F && this.cracks != null) {
            this.cracks.draw(x, y, this.tileWidth, this.tileHeight,
                    Ease.clamp01(this.holdProgress), alpha);
            // The frame heats up with it, so the whole card is committing rather than
            // just its surface.
            Draw.border(x, y, x2, y2, 1.0F,
                    Draw.withAlpha(Theme.danger,
                            (0.3F + 0.7F * Ease.clamp01(this.holdProgress)) * alpha));
        }

        if (hover > 0.02F) {
            drawCardActions(index, x, y, hover, alpha);
            Draw.border(x, y, x2, y2, 1.0F, Draw.withAlpha(Theme.accent, hover * alpha));
            Draw.glow(x, y, x2, y2, 5.0F, Draw.withAlpha(Theme.accent, 0.25F * hover * alpha), 4);
        }
    }

    /**
     * The shards, drawn over everything and outside the grid's clip.
     *
     * Deliberately unclipped: a tile bursting apart should be allowed to throw pieces
     * past the edge of the list rather than have them vanish at a boundary the player
     * cannot see.
     */
    private void drawShatter() {
        if (this.shatter == null) {
            return;
        }
        this.shatter.advance(this.delta);
        this.shatter.draw(this.fadeAlpha);
        if (this.shatter.isFinished()) {
            this.shatter = null;
        }
    }

    private void drawCardActions(int index, int x, float y, float hover, float alpha) {
        float box = iconBox();
        float a = hover * alpha;

        // Play fills the card, so the target is the whole picture, not a small button.
        boolean playHot = this.hoveredCard == index && this.hoveredAction == HIT_PLAY;
        float cx = x + this.tileWidth / 2.0F;
        float cy = y + this.tileHeight / 2.0F - 6;
        float ring = box * 1.15F;
        Draw.circle(cx, cy, ring, Draw.withAlpha(0x000000, (playHot ? 0.55F : 0.35F) * a));
        Draw.ring(cx, cy, ring, 1.5F,
                Draw.withAlpha(playHot ? Theme.accent : Theme.text, (playHot ? 0.95F : 0.5F) * a));
        Icons.play(cx + 1.0F, cy, box * 0.8F,
                Draw.withAlpha(playHot ? Theme.textHover : Theme.text, a));

        drawIconButton(actionX(x, 2), y + 4, box, a,
                this.hoveredAction == HIT_RECREATE && this.hoveredCard == index, ICON_RECREATE);
        drawIconButton(actionX(x, 1), y + 4, box, a,
                this.hoveredAction == HIT_RENAME && this.hoveredCard == index, ICON_RENAME);
        float binX = actionX(x, 0);
        boolean overBin = this.hoveredAction == HIT_DELETE && this.hoveredCard == index;
        drawIconButton(binX, y + 4, box, a, overBin, ICON_DELETE);
        if (overBin && isShiftKeyDown()) {
            // Shown full, because that is what Shift means: the bar is already at the
            // end and the next click is the one that does it. Saying so with the same
            // fill the hold uses means there is nothing new to learn — the button looks
            // the way it looks a moment before a world breaks.
            drawBinFill(binX, y + 4, box, alpha, 1.0F);
        } else if (this.holdCard == index && this.holdProgress > 0.0F) {
            drawBinFill(binX, y + 4, box, alpha, Ease.clamp01(this.holdProgress));
        }
    }

    /**
     * The hold filling the bin button up.
     *
     * It was a ring drawn around the button, which was wrong twice over: a circle in a
     * square button is the same clash that got the round toggles replaced, and putting
     * it outside meant the mark grew over the picture instead of staying with the
     * control being held.
     *
     * <p>Filling the button itself from the bottom reads as charging, keeps every
     * pixel of the effect inside the thing under the pointer, and needs no shape the
     * rest of the interface does not already use.
     *
     * <p>{@code sweep} rather than the field it used to read, because a Shift-click
     * draws the same fill at its full height without any hold having happened.
     */
    private void drawBinFill(float x, float y, float box, float alpha, float sweep) {
        Draw.rect(x, y + box * (1.0F - sweep), x + box, y + box,
                Draw.withAlpha(Theme.danger, 0.55F * alpha));
        // A brighter line riding the top of the fill, so the movement is legible even
        // over the last few percent when the fill itself has stopped growing much.
        float edge = y + box * (1.0F - sweep);
        Draw.rect(x, edge, x + box, edge + 1.0F, Draw.withAlpha(Theme.danger, alpha));
        Draw.border(x, y, x + box, y + box, 1.0F, Draw.withAlpha(Theme.danger, alpha));

        Icons.trash(x + box / 2.0F, y + box / 2.0F, box * 0.58F,
                Draw.withAlpha(Draw.mix(Theme.danger, 0xFFFFFF, sweep), alpha));
    }

    private static final int ICON_RENAME = 0;
    private static final int ICON_DELETE = 1;
    private static final int ICON_RECREATE = 2;

    private void drawIconButton(float x, float y, float box, float alpha, boolean hot,
                                int icon) {
        int accent = icon == ICON_DELETE ? Theme.danger : Theme.accent;
        Draw.rect(x, y, x + box, y + box, Draw.withAlpha(0x000000, (hot ? 0.7F : 0.45F) * alpha));
        Draw.border(x, y, x + box, y + box, 1.0F,
                Draw.withAlpha(hot ? accent : Theme.separator, alpha));

        int colour = Draw.withAlpha(hot ? accent : Theme.textDim, alpha);
        float cx = x + box / 2.0F;
        float cy = y + box / 2.0F;
        if (icon == ICON_DELETE) {
            Icons.trash(cx, cy, box * 0.58F, colour);
        } else if (icon == ICON_RECREATE) {
            // The same world again: a cycle, which is what it does to the seed.
            Icons.refresh(cx, cy, box * 0.62F, colour);
        } else {
            Icons.pencil(cx, cy, box * 0.55F, colour);
        }
    }

    /**
     * Fades the grid's top and bottom edges, but only when there is something to
     * scroll.
     *
     * The point is to stop a card being sliced off mid-picture at the clip edge.
     * With everything already on screen there is nothing to hide, and the two black
     * bands just sat there as a scrim around the container.
     */
    private void drawScrollHint() {
        // Nothing. The edge fades were meant to stop a card being sliced at the clip
        // boundary, but on a flat dark field they just read as a smudge framing the
        // grid — and the clip already cuts cleanly on its own.
    }

    private int lastMouseX;
    private int lastMouseY;

    // ----------------------------------------------------------------- input --

    /**
     * Leaving mid-hold must not leave the rise playing.
     *
     * The screen can go while the button is still down — Escape, or the world being
     * opened from underneath — and nothing else would ever stop the sound.
     */
    @Override
    public void onGuiClosed() {
        UkySounds.stopDeleteHold();
        super.onGuiClosed();
    }

    /**
     * Advances or unwinds the hold on the bin.
     *
     * Driven from the button being physically down rather than from a click, because
     * the gesture is the confirmation: there is no dialogue to agree with, so the only
     * thing that can mean "yes" is continuing to hold. Moving off the bin unwinds it
     * too, which makes sliding away the natural cancel.
     */
    private void updateHold() {
        boolean down = org.lwjgl.input.Mouse.isButtonDown(0);
        if (!down) {
            this.holdSuppressed = false;
        }
        boolean holding = down
                && !this.holdSuppressed
                // Shift already means "now", and the click has already done it. Letting
                // the hold run as well would charge a bar for a world that is gone.
                && !isShiftKeyDown()
                && this.hoveredAction == HIT_DELETE
                && this.hoveredCard >= 0
                && this.hoveredCard < this.worlds.size()
                && (this.holdCard == -1 || this.holdCard == this.hoveredCard);

        if (holding) {
            if (this.holdCard != this.hoveredCard || this.cracks == null) {
                this.cracks = new TileCracks();
            }
            this.holdCard = this.hoveredCard;
            // Started here rather than on the first frame of the press, because the
            // sound and the fill have to begin together for the rise to line up with
            // it. Repeated calls after the first do nothing.
            UkySounds.startDeleteHold();
            this.holdProgress += this.delta / DELETE_HOLD_SECONDS;
            if (this.holdProgress >= 1.0F) {
                destroyHeldWorld();
            }
            return;
        }

        if (this.holdProgress > 0.0F) {
            // Let go before the end: the rise is cut off, because it is a rise towards
            // something that is now not going to happen.
            UkySounds.stopDeleteHold();
        }

        this.holdProgress -= this.delta / DELETE_UNWIND_SECONDS;
        if (this.holdProgress <= 0.0F) {
            this.holdProgress = 0.0F;
            this.holdCard = -1;
            // Dropped with the press it belonged to, so the next attempt cracks the tile
            // somewhere new. Keeping it would make a released-and-retried hold resume a
            // break already half drawn, which reads as damage the card has kept.
            this.cracks = null;
        }
    }

    /**
     * Deletes the held world, then breaks its tile apart where it stood.
     *
     * In that order deliberately. The animation is decoration and the deletion is
     * not, so the deletion happens first and the shards are cut from a picture of
     * something that is already gone. Nothing about the animation can decide whether
     * the world survived.
     */
    private void destroyHeldWorld() {
        int index = this.holdCard;
        this.holdCard = -1;
        this.holdProgress = 0.0F;
        // The rise has arrived; the break takes over from here.
        UkySounds.stopDeleteHold();
        destroyWorld(index);
    }

    /**
     * Deletes the world at {@code index}, however the screen decided to ask.
     *
     * Two gestures arrive here — the four and a half seconds of holding the bin, and a
     * Shift-click that skips them — and neither of them is what this does. Deleting a
     * world is one piece of work with one set of hazards (an open directory handle, a
     * folder that is not there any more), and having it written out once is what keeps
     * the fast path from being the one where a hazard was forgotten.
     */
    private void destroyWorld(int index) {
        if (index < 0 || index >= this.worlds.size()) {
            return;
        }
        UkySounds.play(UkySounds.DELETE_BREAK);

        SaveFormatComparator world = this.worlds.get(index);
        String folder = world.getFileName();
        float x = slotX(index + 1);
        float y = slotY(index + 1);
        ResourceLocation preview = WorldPreviews.texture(folder);

        try {
            ISaveFormat saveFormat = this.mc.getSaveLoader();
            // flushCache first: the save format keeps the directory open, and on
            // Windows an open handle makes the delete fail silently.
            saveFormat.flushCache();
            saveFormat.deleteWorldDirectory(folder);
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not delete world {}", folder, t);
        }

        // The picture has to be forgotten along with the world, and it is easy to see why
        // only after it goes wrong. Both preview caches are keyed by folder name, and
        // Minecraft reissues folder names: creating a world takes the first free one, so
        // this deletion frees exactly the name the next world created will be given. The
        // cache still holding an entry under it meant that world opened on a photograph
        // of this one — a picture of somewhere that had been deleted.
        //
        // Forgotten rather than freed: the shards below are drawn from this very texture
        // for the next second. See WorldPreviews.forget.
        WorldPreviews.forget(folder);

        // The cracks from the hold, so the pieces part along the lines that were on screen
        // a frame ago. A Shift-click has no hold behind it and so no pattern; that gets a
        // fresh one, which is the same break without the anticipation.
        TileCracks pattern = this.cracks != null ? this.cracks : new TileCracks();
        this.cracks = null;
        this.shatter = new TileShatter(pattern, preview, x, y, this.tileWidth, this.tileHeight);
        loadWorlds();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (Transitions.isBusy() || isZooming()) {
            return;
        }
        if (isOverBack(mouseX, mouseY)) {
            back();
            return;
        }
        if (this.hoveredCard == -1) {
            this.mc.displayGuiScreen(new GuiCreateWorldScreen(this));
            return;
        }
        if (this.hoveredCard >= 0 && this.hoveredCard < this.worlds.size()) {
            final SaveFormatComparator world = this.worlds.get(this.hoveredCard);
            switch (this.hoveredAction) {
                case HIT_RENAME:
                    this.mc.displayGuiScreen(new GuiWorldPromptScreen(this,
                            GuiWorldPromptScreen.Mode.RENAME, world.getFileName()));
                    return;
                case HIT_RECREATE:
                    recreate(world);
                    return;
                case HIT_DELETE:
                    // Nothing on click. The bin is a hold, and a dialogue on top of a
                    // hold would be two confirmations for one action.
                    //
                    // Shift is the exception, and it is the only one. The hold exists
                    // because a world is months of work and a misclick must not be able
                    // to take it; somebody clearing out six test worlds is not
                    // misclicking, and making them wait twenty-seven seconds to say so
                    // is the hold protecting nothing. Holding a modifier down is a
                    // deliberate act in its own right, so it stands in for the four and
                    // a half seconds rather than removing them.
                    if (isShiftKeyDown()) {
                        this.holdSuppressed = true;
                        destroyWorld(this.hoveredCard);
                    }
                    return;
                default:
                    // Grows the tile to fill the screen first; play() runs when it
                    // gets there and the loading screen picks the picture up.
                    beginZoom(this.hoveredCard, new Runnable() {
                        @Override
                        public void run() {
                            play(world);
                        }
                    });
                    return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scrollTarget -= (wheel > 0 ? 1 : -1) * (this.tileHeight + TILE_GAP) * 0.6F;
            clampScroll();
        }
    }

    /**
     * Opens world creation pre-filled from this world — vanilla's "Re-Create".
     *
     * The settings it copies live in the save's level.dat and nowhere the player can
     * read them, so this is the only route to a second world on the same seed and the
     * same generator options. Reading that file can fail — a save half-written by a
     * crash, a folder that has been deleted since the list was built — and a button
     * that quietly does nothing is better than one that takes the game down, so a
     * failure leaves the list exactly where it was.
     */
    private void recreate(SaveFormatComparator world) {
        WorldInfo info = null;
        try {
            info = this.mc.getSaveLoader().getWorldInfo(world.getFileName());
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Could not read {} to re-create it", world.getFileName(), t);
        }
        if (info != null) {
            this.mc.displayGuiScreen(GuiCreateWorldScreen.recreating(this, info));
        }
    }

    private void play(SaveFormatComparator world) {
        if (this.launching) {
            return;
        }
        this.launching = true;

        // Tell the loading screen which picture to show while the world comes up.
        WorldPreviews.setEnteringWorld(world.getFileName());
        // Has to happen here: the game replaces its loading screen after mod init
        // and again on every resize, so start-up is too early to claim it.
        UkyLoadingScreen.install(this.mc);

        this.mc.displayGuiScreen(null);
        // FML's loader adds the checks vanilla does not: missing mods, old save
        // formats. It insists on a GuiSelectWorld to return to if it has to warn,
        // so it gets a throwaway one pointing back where this screen came from.
        FMLClientHandler.instance().tryLoadExistingWorld(
                new GuiSelectWorld(this.parent), world.getFileName(), world.getDisplayName());
    }

    private void back() {
        // Climb back out of the hole on the way to the title screen.
        Transitions.emerge();
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control on this screen is drawn, not a GuiButton.
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            back();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

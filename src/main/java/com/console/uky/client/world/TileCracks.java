package com.console.uky.client.world;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.Random;

/**
 * The break, before it happens: cracks spreading across a tile while the bin is held.
 *
 * <p>The hold on the bin is four and a half seconds of a bar filling up inside a button
 * fourteen pixels across, and the thing actually at stake is the whole card next to it.
 * So the card says it too — a point of impact and cracks working outwards from it, deeper
 * the longer the button is down.
 *
 * <p><b>These are the same cracks it breaks along.</b> That is the whole reason this is a
 * class of its own rather than some lines drawn in the world list. It holds the cut — the
 * impact point and the run of border samples — and hands it to {@link TileShatter} when
 * the moment comes, so the pieces that fly apart are bounded by exactly the cracks that
 * were on screen a frame earlier. Generating a fresh pattern at the deletion would have
 * been simpler and would have thrown that away: the tile would craze one way and then
 * break another, which reads as two unrelated effects played back to back.
 *
 * <p>Everything is stored in fractions of the tile rather than in pixels, so the same
 * pattern survives a window resize while the button is still down.
 */
public final class TileCracks {

    /**
     * Cracks, and therefore shards. Twenty-four.
     *
     * The count is shared with the break for the reason above: one crack is one shard's
     * leading edge. Enough to read as shattered rather than as cut into slices, few enough
     * that each line is still followable across a card two hundred pixels wide.
     */
    private static final int COUNT = 24;

    /** How much of the run each crack spends growing; the rest it spends already there. */
    private static final float GROW_SHARE = 0.55F;

    /** Impact point, as a fraction of the tile. */
    private final float hitX;
    private final float hitY;
    /** Where each crack meets the border, as a fraction of the perimeter, sorted. */
    private final float[] along;
    /** Per crack: when it starts growing, and which way its branch leans. */
    private final float[] startAt;
    private final float[] branchLean;

    public TileCracks() {
        Random random = new Random();
        this.hitX = 0.34F + random.nextFloat() * 0.32F;
        this.hitY = 0.32F + random.nextFloat() * 0.30F;

        this.along = new float[COUNT];
        // The four corners are fixed samples. Without them a wedge spanning a corner is
        // closed off by the straight line between its neighbours and the corner of the
        // picture is simply missing from the break — see TileShatter, which relies on this.
        this.along[0] = 0.0F;
        this.along[1] = 0.25F;
        this.along[2] = 0.5F;
        this.along[3] = 0.75F;
        for (int i = 4; i < COUNT; i++) {
            this.along[i] = random.nextFloat();
        }
        Arrays.sort(this.along);

        this.startAt = new float[COUNT];
        this.branchLean = new float[COUNT];
        for (int i = 0; i < COUNT; i++) {
            // Staggered, and not in order around the circle: cracks appearing one after
            // another clockwise would read as a wipe. Scattering when each one starts is
            // what makes it read as the material giving way in several places.
            this.startAt[i] = random.nextFloat() * (1.0F - GROW_SHARE);
            this.branchLean[i] = (random.nextFloat() - 0.5F) * 1.1F;
        }
    }

    /** Impact point in tile fractions, for the break to radiate from the same place. */
    public float hitX() {
        return this.hitX;
    }

    public float hitY() {
        return this.hitY;
    }

    /** Border samples as fractions of the perimeter, sorted. Not copied — read only. */
    public float[] along() {
        return this.along;
    }

    public int count() {
        return COUNT;
    }

    /**
     * Draws the cracks over a tile.
     *
     * @param progress how far the hold has got, 0 to 1
     * @param alpha    the screen's own fade, so this disappears with the card
     */
    public void draw(float x, float y, float w, float h, float progress, float alpha) {
        if (progress <= 0.002F || alpha <= 0.002F) {
            return;
        }
        float perimeter = 2.0F * (w + h);
        float originX = x + this.hitX * w;
        float originY = y + this.hitY * h;

        // Heat at the point of impact, arriving before any crack does. It is what gives
        // the first half-second of the hold something to show at all — a crack a few
        // pixels long is not visible, and a hold that looks like it is doing nothing for
        // half a second reads as a button that has not registered.
        float bloom = Ease.outCubic(Math.min(1.0F, progress / 0.35F));
        Draw.radialGlow(originX, originY, Math.min(w, h) * (0.10F + progress * 0.30F),
                Draw.withAlpha(Theme.danger, 0.45F * bloom * alpha),
                Draw.withAlpha(Theme.danger, 0.0F));

        for (int i = 0; i < COUNT; i++) {
            float grown = Ease.clamp01((progress - this.startAt[i]) / GROW_SHARE);
            if (grown <= 0.0F) {
                continue;
            }
            // Eased out, so a crack shoots and then creeps. Linear growth is the one
            // thing that makes a crack look drawn rather than torn.
            float reach = Ease.outCubic(grown);

            float edgeX = x + edgeAlongX(this.along[i], w, h, perimeter);
            float edgeY = y + edgeAlongY(this.along[i], w, h, perimeter);
            float tipX = originX + (edgeX - originX) * reach;
            float tipY = originY + (edgeY - originY) * reach;

            // Brightening as the hold nears its end, and hot at the core rather than
            // simply red — the last second wants to look like something about to give.
            int colour = Draw.mix(Theme.danger, 0xFFFFFF, 0.15F + progress * 0.45F);
            Draw.line(originX, originY, tipX, tipY,
                    1.0F + progress * 1.2F,
                    Draw.withAlpha(colour, (0.35F + progress * 0.55F) * alpha));

            // One branch each, from part way along, on alternate cracks only. Every crack
            // branching is a cobweb; half of them branching is a break.
            if ((i & 1) == 0 && reach > 0.45F) {
                float fromX = originX + (edgeX - originX) * 0.5F;
                float fromY = originY + (edgeY - originY) * 0.5F;
                float dirX = tipX - fromX;
                float dirY = tipY - fromY;
                float lean = this.branchLean[i];
                float cos = MathHelper.cos(lean);
                float sin = MathHelper.sin(lean);
                Draw.line(fromX, fromY,
                        fromX + (dirX * cos - dirY * sin) * 0.55F,
                        fromY + (dirX * sin + dirY * cos) * 0.55F,
                        1.0F,
                        Draw.withAlpha(colour, (0.22F + progress * 0.38F) * alpha));
            }
        }
    }

    /**
     * A point on the tile's border, {@code fraction} of the way clockwise from top-left.
     *
     * Two methods rather than a returned pair, so nothing is allocated per crack per
     * frame. {@link TileShatter} needs the identical mapping and calls these.
     */
    static float edgeAlongX(float fraction, float w, float h, float perimeter) {
        float along = fraction * perimeter;
        if (along < w) {
            return along;
        }
        if (along < w + h) {
            return w;
        }
        if (along < 2.0F * w + h) {
            return w - (along - w - h);
        }
        return 0.0F;
    }

    static float edgeAlongY(float fraction, float w, float h, float perimeter) {
        float along = fraction * perimeter;
        if (along < w) {
            return 0.0F;
        }
        if (along < w + h) {
            return along - w;
        }
        if (along < 2.0F * w + h) {
            return h;
        }
        return h - (along - 2.0F * w - h);
    }
}

package com.console.uky.client.world;

import com.console.uky.client.render.Draw;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * A world tile coming apart.
 *
 * The shards are cut from the world's own preview — the grid is laid over the
 * picture and each piece keeps the corner of it that it was standing on — so what
 * breaks is that world and not a coloured rectangle standing in for it. It is the
 * one moment in the interface where the picture is destroyed rather than merely
 * removed, and it should look like the thing you are actually losing.
 *
 * <p>The whole effect is an overlay. It holds no reference to the list it came from
 * and the world is gone from disk before the first frame of it is drawn, so nothing
 * about the animation can affect whether the deletion happened.
 */
public final class TileShatter {

    /**
     * Grid the tile is cut on.
     *
     * It was 7x4. Twenty-eight pieces of a tile are big enough that each one still
     * reads as a rectangle, so it looked less like breaking than like the tile coming
     * unstuck and sliding apart. At 14x8 no single piece is large enough to be read as
     * a shape of its own, and the eye sees the burst rather than its parts.
     */
    private static final int COLUMNS = 14;
    private static final int ROWS = 8;

    /** Units per second squared. Positive is down, matching the GUI's axis. */
    private static final float GRAVITY = 1500.0F;
    private static final float LIFE_SECONDS = 0.85F;

    private final ResourceLocation texture;
    private final float halfW;
    private final float halfH;

    private final float[] x;
    private final float[] y;
    private final float[] vx;
    private final float[] vy;
    private final float[] angle;
    private final float[] spin;

    private float age;

    /**
     * @param texture the world's preview, or null to break into plain dark shards
     */
    public TileShatter(ResourceLocation texture, float tileX, float tileY,
                       float tileW, float tileH) {
        this.texture = texture;
        this.halfW = tileW / COLUMNS / 2.0F;
        this.halfH = tileH / ROWS / 2.0F;

        int count = COLUMNS * ROWS;
        this.x = new float[count];
        this.y = new float[count];
        this.vx = new float[count];
        this.vy = new float[count];
        this.angle = new float[count];
        this.spin = new float[count];

        // Seeded from the tile's position rather than the clock: the same tile breaks
        // the same way twice, which matters not at all to a player and a great deal
        // when trying to tell whether a change to this made it better or worse.
        Random random = new Random((long) (tileX * 7919 + tileY * 104729));

        float centreX = tileX + tileW / 2.0F;
        float centreY = tileY + tileH / 2.0F;

        for (int i = 0; i < count; i++) {
            float px = tileX + (i % COLUMNS + 0.5F) * (tileW / COLUMNS);
            float py = tileY + (i / COLUMNS + 0.5F) * (tileH / ROWS);
            this.x[i] = px;
            this.y[i] = py;

            // Thrown along the direction out from the centre, at a speed that does
            // not depend on how far out the piece started. Scaling speed by distance —
            // which is what it did — makes the grid expand as a grid, every piece
            // holding its neighbours' relative position, and that is exactly what made
            // it read as sliding rather than shattering. A normalised direction with a
            // large random spread breaks the formation immediately.
            float dx = px - centreX;
            float dy = py - centreY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.001F) {
                length = 0.001F;
            }
            float speed = 260.0F + random.nextFloat() * 340.0F;
            this.vx[i] = dx / length * speed + (random.nextFloat() - 0.5F) * 220.0F;
            this.vy[i] = dy / length * speed - 260.0F - random.nextFloat() * 200.0F;

            this.angle[i] = 0.0F;
            this.spin[i] = (random.nextFloat() - 0.5F) * 1600.0F;
        }
    }

    public void advance(float deltaSeconds) {
        this.age += deltaSeconds;
        for (int i = 0; i < this.x.length; i++) {
            this.vy[i] += GRAVITY * deltaSeconds;
            this.x[i] += this.vx[i] * deltaSeconds;
            this.y[i] += this.vy[i] * deltaSeconds;
            this.angle[i] += this.spin[i] * deltaSeconds;
        }
    }

    public boolean isFinished() {
        return this.age >= LIFE_SECONDS;
    }

    public void draw(float screenAlpha) {
        float life = 1.0F - this.age / LIFE_SECONDS;
        if (life <= 0.0F) {
            return;
        }
        // Held at full through the first half, then faded. Fading from the first frame
        // makes the burst look weak at the moment it should be at its most violent.
        float alpha = (life > 0.55F ? 1.0F : life / 0.55F) * screenAlpha;
        // Shrinking and darkening as they go, so the pieces read as receding debris
        // rather than as the picture politely fading out where it happens to be.
        float shrink = 0.45F + life * 0.55F;
        int tint = Draw.withAlpha(Draw.mix(0x000000, 0xFFFFFF, 0.35F + life * 0.65F), alpha);

        for (int i = 0; i < this.x.length; i++) {
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            float u1 = column / (float) COLUMNS;
            float u2 = (column + 1) / (float) COLUMNS;
            float v1 = row / (float) ROWS;
            float v2 = (row + 1) / (float) ROWS;

            if (this.texture != null) {
                Draw.textureShard(this.texture, this.x[i], this.y[i],
                        this.halfW * shrink, this.halfH * shrink,
                        this.angle[i], u1, v1, u2, v2, tint);
            } else {
                Draw.rect(this.x[i] - this.halfW * shrink, this.y[i] - this.halfH * shrink,
                        this.x[i] + this.halfW * shrink, this.y[i] + this.halfH * shrink,
                        Draw.withAlpha(0x101014, alpha));
            }
        }
    }
}

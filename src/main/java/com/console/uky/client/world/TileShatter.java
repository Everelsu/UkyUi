package com.console.uky.client.world;

import com.console.uky.client.render.Draw;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * A world tile coming apart.
 *
 * The shards are cut from the world's own preview — each piece keeps the part of the
 * picture it was standing on — so what breaks is that world and not a coloured
 * rectangle standing in for it. It is the one moment in the interface where the
 * picture is destroyed rather than merely removed, and it should look like the thing
 * you are actually losing.
 *
 * <h2>Why it is cut this way</h2>
 *
 * <p>This was a grid: fourteen columns by eight rows, a hundred and twelve pieces. It
 * did not work, and no amount of making the grid finer was going to fix it, because the
 * problem was not the size of the pieces — it was that <b>every piece was a
 * rectangle</b>. A picture divided into rectangles and pushed outwards reads as tiles
 * coming unstuck and sliding apart. It cannot read as breaking, because nothing breaks
 * into rectangles.
 *
 * <p>So the tile is now cut the way glass actually goes: a point of impact, and long
 * thin wedges radiating out of it to the edges. Each shard is a triangle with the
 * impact at its apex and a run of the tile's own border as its base, which means
 * <em>the pieces vary in shape and size for free</em> — the border samples are spaced
 * randomly, so a shard is as wide as the gap it happened to span. The four corners are
 * always sampled, which is what makes the wedges tile the rectangle exactly instead of
 * cutting the corners off.
 *
 * <p>Twenty-odd triangles rather than a hundred and twelve quads, so it is also cheaper
 * than what it replaces.
 *
 * <p>The whole effect is an overlay. It holds no reference to the list it came from and
 * the world is gone from disk before the first frame of it is drawn, so nothing about
 * the animation can affect whether the deletion happened.
 */
public final class TileShatter {

    /** Units per second squared. Positive is down, matching the GUI's axis. */
    private static final float GRAVITY = 1500.0F;
    private static final float LIFE_SECONDS = 0.85F;

    private final ResourceLocation texture;

    /** Per shard: three vertices relative to its own centroid, and their texture coords. */
    private final float[] localX;
    private final float[] localY;
    private final float[] u;
    private final float[] v;

    private final float[] x;
    private final float[] y;
    private final float[] vx;
    private final float[] vy;
    private final float[] angle;
    private final float[] spin;

    private float age;

    /**
     * @param pattern the cut to break along — the cracks the player has been watching
     *                spread while they held the bin. Never null; a deletion with no hold
     *                behind it (Shift-click) makes a fresh one, which is the same thing
     *                minus the anticipation.
     * @param texture the world's preview, or null to break into plain dark shards
     */
    public TileShatter(TileCracks pattern, ResourceLocation texture, float tileX,
                       float tileY, float tileW, float tileH) {
        this.texture = texture;
        Random random = new Random();

        float hitX = pattern.hitX() * tileW;
        float hitY = pattern.hitY() * tileH;

        float perimeter = 2.0F * (tileW + tileH);
        float[] along = pattern.along();
        int count = pattern.count();
        this.localX = new float[count * 3];
        this.localY = new float[count * 3];
        this.u = new float[count * 3];
        this.v = new float[count * 3];
        this.x = new float[count];
        this.y = new float[count];
        this.vx = new float[count];
        this.vy = new float[count];
        this.angle = new float[count];
        this.spin = new float[count];

        float furthest = (float) Math.sqrt(tileW * tileW + tileH * tileH);

        for (int i = 0; i < count; i++) {
            // Each shard spans this border sample to the next, wrapping at the end — so
            // its two long sides are the two cracks that were on screen either side of it.
            float a = along[i];
            float b = along[(i + 1) % count];

            float ax = TileCracks.edgeAlongX(a, tileW, tileH, perimeter);
            float ay = TileCracks.edgeAlongY(a, tileW, tileH, perimeter);
            float bx = TileCracks.edgeAlongX(b, tileW, tileH, perimeter);
            float by = TileCracks.edgeAlongY(b, tileW, tileH, perimeter);

            float cx = (hitX + ax + bx) / 3.0F;
            float cy = (hitY + ay + by) / 3.0F;

            int o = i * 3;
            store(o, hitX - cx, hitY - cy, hitX / tileW, hitY / tileH);
            store(o + 1, ax - cx, ay - cy, ax / tileW, ay / tileH);
            store(o + 2, bx - cx, by - cy, bx / tileW, by / tileH);

            this.x[i] = tileX + cx;
            this.y[i] = tileY + cy;

            // Thrown outwards from the impact, hardest nearest it — which is what makes
            // the break look like it came from somewhere rather than expanding evenly.
            float dx = cx - hitX;
            float dy = cy - hitY;
            float distance = Math.max(1.0F, (float) Math.sqrt(dx * dx + dy * dy));
            float nearness = 1.0F - Math.min(1.0F, distance / furthest);
            float speed = 150.0F + nearness * 320.0F + random.nextFloat() * 160.0F;

            this.vx[i] = dx / distance * speed + (random.nextFloat() - 0.5F) * 200.0F;
            this.vy[i] = dy / distance * speed - 240.0F - random.nextFloat() * 200.0F;
            this.angle[i] = 0.0F;
            this.spin[i] = (random.nextFloat() - 0.5F) * 1100.0F;
        }
    }

    private void store(int index, float lx, float ly, float texU, float texV) {
        this.localX[index] = lx;
        this.localY[index] = ly;
        this.u[index] = texU;
        this.v[index] = texV;
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
        int flat = Draw.withAlpha(0x101014, alpha);

        for (int i = 0; i < this.x.length; i++) {
            double radians = Math.toRadians(this.angle[i]);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            int o = i * 3;

            float x1 = this.x[i] + rotX(o, cos, sin, shrink);
            float y1 = this.y[i] + rotY(o, cos, sin, shrink);
            float x2 = this.x[i] + rotX(o + 1, cos, sin, shrink);
            float y2 = this.y[i] + rotY(o + 1, cos, sin, shrink);
            float x3 = this.x[i] + rotX(o + 2, cos, sin, shrink);
            float y3 = this.y[i] + rotY(o + 2, cos, sin, shrink);

            if (this.texture != null) {
                Draw.textureTriangle(this.texture,
                        x1, y1, this.u[o], this.v[o],
                        x2, y2, this.u[o + 1], this.v[o + 1],
                        x3, y3, this.u[o + 2], this.v[o + 2], tint);
            } else {
                // A world never visited has no picture to break; the shape of the break
                // is the whole of the effect there.
                Draw.triangle(x1, y1, x2, y2, x3, y3, flat);
            }
        }
    }

    private float rotX(int index, float cos, float sin, float shrink) {
        return (this.localX[index] * cos - this.localY[index] * sin) * shrink;
    }

    private float rotY(int index, float cos, float sin, float shrink) {
        return (this.localX[index] * sin + this.localY[index] * cos) * shrink;
    }
}

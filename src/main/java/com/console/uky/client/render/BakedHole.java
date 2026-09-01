package com.console.uky.client.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;

/**
 * One camera pose of the black hole, reduced to what drawing actually needs.
 *
 * A {@link LensMap} is ~20 bytes per texel of intermediate results; keeping a
 * dozen of those in memory is not on. Baking throws all of it away except the
 * handful of numbers per <em>glowing</em> texel that the per-frame shading reads,
 * which for a typical pose is under a tenth of the raw table.
 *
 * Two GL layers are kept: a static occlusion mask (what the hole blocks) and an
 * emission texture (what the disk emits), re-shaded as the disk turns.
 */
public final class BakedHole {

    private final int width;
    private final int height;
    private final float shadowTexels;

    /** Indices of the texels that gather any light, in row order. */
    private final int[] indices;
    /** Offset into {@link #indices} where each row begins. */
    private final int[] rowOffsets;

    // Time-invariant halves of the shading formula, parallel to `indices`.
    private final float[] noiseU;
    private final float[] noiseV;
    private final float[] omega;
    private final float[] gain;
    private final int[] rgb;

    /** Static occlusion alpha, kept until the texture is uploaded then released. */
    private byte[] occlusionAlpha;

    private int occlusionTex = -1;
    private int emissionTex = -1;

    public BakedHole(LensMap map) {
        this.width = map.width;
        this.height = map.height;
        this.shadowTexels = map.shadowRadiusInTexels();

        int count = this.width * this.height;
        int glowing = 0;
        for (int i = 0; i < count; i++) {
            if (map.emission[i] > 0.0F) {
                glowing++;
            }
        }

        this.indices = new int[glowing];
        this.rowOffsets = new int[this.height + 1];
        this.noiseU = new float[glowing];
        this.noiseV = new float[glowing];
        this.omega = new float[glowing];
        this.gain = new float[glowing];
        this.rgb = new int[glowing];
        this.occlusionAlpha = new byte[count];

        int hot = 0xFF000000 | 0xFFF3E4;
        int mid = Theme.accent;
        int cold = Theme.accentAlt;

        int at = 0;
        for (int y = 0; y < this.height; y++) {
            this.rowOffsets[y] = at;
            int base = y * this.width;
            for (int x = 0; x < this.width; x++) {
                int i = base + x;
                float o = map.opacity[i];
                this.occlusionAlpha[i] = (byte) (int) (Math.min(1.0F, Math.max(0.0F, o)) * 255.0F);

                if (map.emission[i] <= 0.0F) {
                    continue;
                }
                float t = map.meanT[i];
                this.indices[at] = i;
                this.noiseU[at] = map.meanAz[i] * BlackHole.NOISE_AZIMUTH_FREQ;
                this.noiseV[at] = t * BlackHole.NOISE_RADIAL_FREQ;
                // Keplerian: the inner disk laps the outer one.
                this.omega[at] = (float) (1.0 / Math.pow(0.25F + t, 1.5));
                this.gain[at] = map.emission[i] * map.boost[i] * BlackHole.DISK_GAIN;
                this.rgb[at] = (t < 0.35F
                        ? Draw.mix(hot, mid, t / 0.35F)
                        : Draw.mix(mid, cold, (t - 0.35F) / 0.65F)) & 0xFFFFFF;
                at++;
            }
        }
        this.rowOffsets[this.height] = at;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /** Apparent shadow radius in texels, used to scale the quad on screen. */
    public float getShadowTexels() {
        return this.shadowTexels;
    }

    // ------------------------------------------------------------- rendering --

    /**
     * Shades this pose into {@code argb} and uploads it.
     *
     * @param rows       fraction of rows to refresh this call, 1 for all of them.
     *                   The resting pose spreads the work over several frames; a
     *                   pose being flicked past during a turn is only shown for a
     *                   frame or two, so it has to be done in one go.
     * @param bandCursor which slice to refresh when {@code rows} is fractional
     */
    public void upload(float spin, int[] argb, IntBuffer staging, int bands, int bandCursor) {
        ensureTextures(staging);

        int y0;
        int y1;
        if (bands <= 1) {
            y0 = 0;
            y1 = this.height;
        } else {
            int rowsPerBand = this.height / bands;
            y0 = bandCursor * rowsPerBand;
            y1 = bandCursor == bands - 1 ? this.height : y0 + rowsPerBand;
        }

        shade(spin, argb, y0, y1);

        GlStateManager.bindTexture(this.emissionTex);
        staging.clear();
        staging.put(argb, y0 * this.width, (y1 - y0) * this.width);
        staging.flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, y0, this.width, y1 - y0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, staging);
    }

    private void shade(float spin, int[] argb, int y0, int y1) {
        int from = this.rowOffsets[y0];
        int to = this.rowOffsets[y1];
        for (int k = from; k < to; k++) {
            float n = BlackHole.sampleNoise(this.noiseU[k] + spin * this.omega[k], this.noiseV[k]);
            float lum = this.gain[k] * (BlackHole.CONTRAST_FLOOR + n * BlackHole.CONTRAST_RANGE);
            int i = this.indices[k];
            if (lum <= 0.004F) {
                argb[i] = 0;
                continue;
            }
            if (lum > 1.0F) {
                lum = 1.0F;
            }
            argb[i] = ((int) (lum * 255.0F) << 24) | this.rgb[k];
        }
    }

    public void bindOcclusion() {
        GlStateManager.bindTexture(this.occlusionTex);
    }

    public void bindEmission() {
        GlStateManager.bindTexture(this.emissionTex);
    }

    public boolean hasTextures() {
        return this.emissionTex >= 0;
    }

    private void ensureTextures(IntBuffer staging) {
        if (this.emissionTex >= 0) {
            return;
        }
        this.occlusionTex = allocate();
        uploadOcclusion(staging);
        this.emissionTex = allocate();
        // The mask lives in VRAM now; the CPU copy is dead weight.
        this.occlusionAlpha = null;
    }

    private void uploadOcclusion(IntBuffer staging) {
        int rowsPerUpload = Math.max(1, staging.capacity() / this.width);
        for (int y0 = 0; y0 < this.height; y0 += rowsPerUpload) {
            int y1 = Math.min(this.height, y0 + rowsPerUpload);
            staging.clear();
            for (int i = y0 * this.width; i < y1 * this.width; i++) {
                staging.put((this.occlusionAlpha[i] & 0xFF) << 24);
            }
            staging.flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, y0, this.width, y1 - y0,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, staging);
        }
    }

    private int allocate() {
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        // Linear filtering is what lets the table fill a large area cleanly.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, this.width, this.height, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        return id;
    }

    /** Convenience for callers that need a staging buffer big enough for this pose. */
    public IntBuffer createStaging(int bands) {
        int rows = this.height / Math.max(1, bands) + this.height % Math.max(1, bands) + 1;
        return BufferUtils.createIntBuffer(rows * this.width);
    }
}

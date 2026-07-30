package com.console.uky.client.render;

import com.console.uky.UkyUI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The accretion disk's turbulence, baked once into a 3D texture.
 *
 * The shader used to evaluate the noise field analytically at every sample: three
 * octaves of value noise, eight hashed lattice corners each, twenty-odd arithmetic
 * operations per hash — about 240 operations for one lookup. That would be
 * affordable if it happened once per pixel, but the disk is marched volumetrically
 * with ten sub-samples per segment across tens of segments, so a pixel that crosses
 * the disk paid for it hundreds of times. It was, by a wide margin, the most
 * expensive thing in the frame.
 *
 * <p>The field does not depend on time or on the camera — the disk's rotation is
 * applied to the <em>sample point</em> before the lookup, not to the field itself —
 * so all of that work was recomputing a constant. Baked into a volume it becomes a
 * single filtered fetch, and the trilinear interpolation the hardware does for free
 * is the same interpolation the analytic version was doing by hand.
 *
 * <p>The bake is tileable, so {@code GL_REPEAT} carries it across a disk far wider
 * than the volume itself.
 */
public final class NoiseVolume {

    /**
     * Edge length in texels.
     *
     * The finest octave has features a quarter of a unit across, and {@link #PERIOD}
     * units mapped onto this many texels leaves eight per unit — two per feature,
     * which is the least that can represent it at all.
     */
    private static final int SIZE = 128;

    /** World units the volume spans before it repeats. */
    public static final float PERIOD = 16.0F;

    /** Weights and frequencies of the octaves, matching the shader this replaces. */
    private static final int[] OCTAVE_FREQUENCY = {1, 2, 4};
    private static final float[] OCTAVE_WEIGHT = {0.5F, 0.25F, 0.125F};

    private static int texture = 0;
    private static boolean attempted;

    private NoiseVolume() {
    }

    /**
     * Uploads the volume if it is not already resident, and returns its name.
     *
     * @return the texture name, or 0 if the volume could not be created — the caller
     *         is expected to fall back to evaluating the noise in the shader
     */
    public static int ensureUploaded() {
        if (texture != 0 || attempted) {
            return texture;
        }
        attempted = true;

        try {
            long started = System.nanoTime();
            ByteBuffer data = bake();

            int name = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, name);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_LUMINANCE8, SIZE, SIZE, SIZE, 0,
                    GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE, data);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                GL11.glDeleteTextures(name);
                UkyUI.LOGGER.warn("Black hole: 3D noise upload failed (GL error {}); "
                        + "falling back to computing it in the shader", Integer.valueOf(error));
                return 0;
            }

            texture = name;
            UkyUI.LOGGER.info("Black hole: baked {}^3 noise volume in {} ms",
                    Integer.valueOf(SIZE),
                    Long.valueOf((System.nanoTime() - started) / 1_000_000L));
        } catch (Throwable t) {
            UkyUI.LOGGER.warn("Black hole: could not bake the noise volume; "
                    + "falling back to computing it in the shader", t);
            texture = 0;
        }
        return texture;
    }

    /** Binds the volume to the given texture unit. */
    public static void bind(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** Unbinds and drops the volume, so a resource reload does not leak it. */
    public static void dispose() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        attempted = false;
    }

    // ----------------------------------------------------------------- baking --

    /**
     * Evaluates the summed octaves over the volume.
     *
     * Run across every available core: this is a few tens of millions of independent
     * lattice lookups, and on one thread it is long enough to be felt as a stall on
     * the first frame that needs the hole.
     */
    private static ByteBuffer bake() throws InterruptedException {
        final byte[] out = new byte[SIZE * SIZE * SIZE];
        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int slice = t;
            final int count = threads;
            workers[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int z = slice; z < SIZE; z += count) {
                        fillSlice(out, z);
                    }
                }
            }, "uky-noise-bake-" + t);
            workers[t].setDaemon(true);
            workers[t].start();
        }
        for (int t = 0; t < threads; t++) {
            workers[t].join();
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(out.length).order(ByteOrder.nativeOrder());
        buffer.put(out);
        buffer.flip();
        return buffer;
    }

    private static void fillSlice(byte[] out, int z) {
        // Texel centres, so the sample grid lines up with what the hardware
        // interpolates between rather than sitting half a texel off it.
        float step = PERIOD / SIZE;
        int base = z * SIZE * SIZE;
        for (int y = 0; y < SIZE; y++) {
            int row = base + y * SIZE;
            for (int x = 0; x < SIZE; x++) {
                float value = fbm((x + 0.5F) * step, (y + 0.5F) * step, (z + 0.5F) * step);
                int quantised = (int) (value * 255.0F + 0.5F);
                out[row + x] = (byte) (quantised < 0 ? 0 : (quantised > 255 ? 255 : quantised));
            }
        }
    }

    private static float fbm(float x, float y, float z) {
        float sum = 0.0F;
        for (int i = 0; i < OCTAVE_FREQUENCY.length; i++) {
            int f = OCTAVE_FREQUENCY[i];
            sum += OCTAVE_WEIGHT[i] * vnoise(x * f, y * f, z * f, f);
        }
        return sum;
    }

    /**
     * Value noise on the unit lattice, wrapping at {@code PERIOD * frequency} cells.
     *
     * The wrap is what makes the bake tileable. The shader's original used
     * frequencies of 2.13 and 4.31, which cannot tile against each other at any
     * period; whole multiples can, and at these scales the difference is not
     * something the eye picks out of a volumetric integral.
     */
    private static float vnoise(float x, float y, float z, int frequency) {
        int period = (int) PERIOD * frequency;

        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;

        float u = xf * xf * (3.0F - 2.0F * xf);
        float v = yf * yf * (3.0F - 2.0F * yf);
        float w = zf * zf * (3.0F - 2.0F * zf);

        float c000 = hash(xi, yi, zi, period);
        float c100 = hash(xi + 1, yi, zi, period);
        float c010 = hash(xi, yi + 1, zi, period);
        float c110 = hash(xi + 1, yi + 1, zi, period);
        float c001 = hash(xi, yi, zi + 1, period);
        float c101 = hash(xi + 1, yi, zi + 1, period);
        float c011 = hash(xi, yi + 1, zi + 1, period);
        float c111 = hash(xi + 1, yi + 1, zi + 1, period);

        float x00 = c000 + (c100 - c000) * u;
        float x10 = c010 + (c110 - c010) * u;
        float x01 = c001 + (c101 - c001) * u;
        float x11 = c011 + (c111 - c011) * u;
        float y0 = x00 + (x10 - x00) * v;
        float y1 = x01 + (x11 - x01) * v;
        return y0 + (y1 - y0) * w;
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Integer hash on the wrapped lattice, so opposite faces of the volume agree. */
    private static float hash(int x, int y, int z, int period) {
        int h = Math.floorMod(x, period) * 73856093
              ^ Math.floorMod(y, period) * 19349663
              ^ Math.floorMod(z, period) * 83492791;
        h ^= h >>> 13;
        h *= 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}

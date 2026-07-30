package com.console.uky.client.render;

/**
 * Precomputed gravitational lensing and accretion-disk integral for a
 * Schwarzschild black hole.
 *
 * <h2>Why a table</h2>
 * Tracing geodesics is the only thing that makes a black hole look right — the
 * disk has to wrap over and under the shadow, the photon ring has to emerge on
 * its own, and the far side has to be visibly stretched. Doing that per pixel per
 * frame is out of reach here. But the geometry never changes: the camera does not
 * move and the hole does not deform. Only the <em>matter</em> in the disk moves.
 *
 * So the expensive half runs exactly once, on a background thread. For every
 * texel it stores how much light that ray gathered, how opaque the path was, and
 * which part of the disk contributed most — enough to re-shade it each frame with
 * a little arithmetic.
 *
 * <h2>The physics</h2>
 * Units are G = c = M = 1, so the horizon sits at r = 2 and the photon sphere at
 * r = 3. For null geodesics the orbit equation d²u/dφ² + u = 3Mu² is equivalent to
 * the Cartesian acceleration
 * <pre>a = -3M · h² · r⃗ / |r⃗|⁵</pre>
 * with h⃗ = r⃗ × v⃗ the conserved angular momentum, which integrates in 3D without
 * switching coordinate frames.
 *
 * <p>The coefficient is easy to get wrong: for {@code a = -C·h²/r⁴} the orbit
 * equation comes out as {@code u'' + u = C·u²}, so matching {@code 3Mu²} needs
 * {@code C = 3M}. The widely copied {@code 1.5} silently sets M = ½ and halves
 * every deflection.
 *
 * <p>The disk is <em>volumetric</em>, not a surface. A razor-thin opaque plane
 * gives hard, aliased edges and speckle where the marching step straddles it;
 * marching through a soft slab and accumulating emission with absorption is what
 * produces the glow the reference simulator has. The density profile is the
 * standard one: zero at the inner edge, rising to a peak just outside it, then
 * falling as r^-3/2 and tapering linearly to nothing at the rim.
 */
public final class LensMap {

    public static final byte HIT_SKY = 0;
    public static final byte HIT_DISK = 1;
    public static final byte HIT_SHADOW = 2;

    /**
     * Table resolution and camera pitch, fixed per instance.
     *
     * The resting pose the menu spends its time on is traced at full resolution;
     * the poses that only exist to be flicked through during a turn are traced
     * small, because nothing is legible mid-rotation anyway.
     */
    public final int width;
    public final int height;
    /** Camera elevation above the disk plane, radians. Negative looks from below. */
    public final double elevation;

    private static final double SCHWARZSCHILD = 2.0;
    /** Disk extent, matching the reference: 3 and 15 Schwarzschild radii. */
    private static final double DISK_INNER = SCHWARZSCHILD * 3.0;
    private static final double DISK_OUTER = SCHWARZSCHILD * 15.0;
    /** Slab half-height grows with radius; the density inside it is Gaussian. */
    private static final double THICKNESS_RATIO = 0.05;
    /** Gaussian sigma as a fraction of the local thickness. */
    private static final double SIGMA_RATIO = 0.10;

    private static final double CAMERA_DISTANCE = 85.0;
    private static final double TAN_HALF_FOV = 0.30;

    private static final double HORIZON = SCHWARZSCHILD * 1.01;
    private static final double ESCAPE = CAMERA_DISTANCE * 1.6;
    private static final int MAX_STEPS = 6000;
    /** Fine step used while inside the disk slab, where detail matters. */
    private static final double MARCH_STEP = 0.05;

    /** Critical impact parameter 3√3·M: the apparent radius of the shadow. */
    private static final double SHADOW_B = 3.0 * Math.sqrt(3.0);

    public final byte[] type;
    /** Light gathered along the ray, before any per-frame noise modulation. */
    public final float[] emission;
    /** 1 − transmission: how much of the background this path blocks. */
    public final float[] opacity;
    /** Emission-weighted normalised disk radius, 0 at the inner edge. */
    public final float[] meanT;
    /** Emission-weighted azimuth, radians. */
    public final float[] meanAz;
    /** Doppler and gravitational brightness factor where most light came from. */
    public final float[] boost;

    public LensMap(int width, int height, double elevation) {
        this.width = width;
        this.height = height;
        this.elevation = elevation;
        int count = width * height;
        this.type = new byte[count];
        this.emission = new float[count];
        this.opacity = new float[count];
        this.meanT = new float[count];
        this.meanAz = new float[count];
        this.boost = new float[count];
    }

    /**
     * Apparent radius of the shadow measured in texels — what a caller needs in
     * order to scale the table to a requested on-screen radius.
     */
    public float shadowRadiusInTexels() {
        double angular = SHADOW_B / CAMERA_DISTANCE;
        return (float) (angular / TAN_HALF_FOV * (this.height / 2.0));
    }

    /**
     * Fills the table. Slow — call from a background thread.
     *
     * Rays are independent, so the rows are split across every core available;
     * at this resolution single-threaded would take the best part of half a minute.
     */
    public void compute() {
        final double camY = -CAMERA_DISTANCE * Math.cos(this.elevation);
        final double camZ = CAMERA_DISTANCE * Math.sin(this.elevation);

        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
        Thread[] workers = new Thread[threads];
        for (int w = 0; w < threads; w++) {
            final int slice = w;
            final int stride = threads;
            workers[w] = new Thread(new Runnable() {
                @Override
                public void run() {
                    new Tracer().run(slice, stride, camY, camZ);
                }
            }, "UKY Lensing " + w);
            workers[w].setDaemon(true);
            workers[w].setPriority(Thread.MIN_PRIORITY);
            workers[w].start();
        }
        for (int w = 0; w < threads; w++) {
            try {
                workers[w].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * One worker's share of the trace. All the mutable integration state lives
     * here rather than on the map, so the workers never touch each other.
     */
    private final class Tracer {

        private final double[] scratch = new double[12];

        private double accEmission;
        private double accTransmission;
        private double accWeightedT;
        private double accSin;
        private double accCos;
        private double accBoost;

        void run(int slice, int stride, double camY, double camZ) {
            // Basis: forward toward the origin, right horizontal, up completing it.
            double fy = -camY, fz = -camZ;
            double fl = Math.sqrt(fy * fy + fz * fz);
            fy /= fl; fz /= fl;
            double uy = -fz;
            double uz = fy;
            double aspect = (double) width / height;

            for (int py = slice; py < height; py += stride) {
                double sy = (1.0 - 2.0 * (py + 0.5) / height) * TAN_HALF_FOV;
                for (int px = 0; px < width; px++) {
                    double sx = (2.0 * (px + 0.5) / width - 1.0) * TAN_HALF_FOV * aspect;

                    double dx = sx;
                    double dy = fy + uy * sy;
                    double dz = fz + uz * sy;
                    double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    trace(px + py * width, 0.0, camY, camZ, dx / dl, dy / dl, dz / dl);
                }
            }
        }

    /** Integrates one photon backwards from the camera and records what it meets. */
    private void trace(int index, double x, double y, double z,
                       double vx, double vy, double vz) {
        double hx = y * vz - z * vy;
        double hy = z * vx - x * vz;
        double hz = x * vy - y * vx;
        double h2 = hx * hx + hy * hy + hz * hz;

        accEmission = 0.0;
        accTransmission = 1.0;
        accWeightedT = 0.0;
        accSin = 0.0;
        accCos = 0.0;
        accBoost = 0.0;

        byte outcome = HIT_SKY;

        for (int step = 0; step < MAX_STEPS; step++) {
            double r = Math.sqrt(x * x + y * y + z * z);
            if (r < HORIZON) {
                outcome = HIT_SHADOW;
                break;
            }
            if (r > ESCAPE) {
                outcome = HIT_SKY;
                break;
            }
            if (accTransmission < 0.004) {
                // Fully absorbed: nothing behind this can matter.
                outcome = HIT_DISK;
                break;
            }

            // Step scaled to distance, and tightened hard near the photon sphere.
            // Rays that wind several times around r = 3M are exactly the ones that
            // produce the photon ring hugging the shadow; integrate them coarsely
            // and they either drift into the horizon or leave a speckled arc.
            double dt = r < 12.0 ? 0.012 * r : 0.05 * r;
            if (dt > 1.5) {
                dt = 1.5;
            }

            double px = x, py = y, pz = z;

            double k1x = vx, k1y = vy, k1z = vz;
            accel(x, y, z, h2, 0);
            double a1x = scratch[0], a1y = scratch[1], a1z = scratch[2];

            double k2x = vx + 0.5 * dt * a1x;
            double k2y = vy + 0.5 * dt * a1y;
            double k2z = vz + 0.5 * dt * a1z;
            accel(x + 0.5 * dt * k1x, y + 0.5 * dt * k1y, z + 0.5 * dt * k1z, h2, 3);
            double a2x = scratch[3], a2y = scratch[4], a2z = scratch[5];

            double k3x = vx + 0.5 * dt * a2x;
            double k3y = vy + 0.5 * dt * a2y;
            double k3z = vz + 0.5 * dt * a2z;
            accel(x + 0.5 * dt * k2x, y + 0.5 * dt * k2y, z + 0.5 * dt * k2z, h2, 6);
            double a3x = scratch[6], a3y = scratch[7], a3z = scratch[8];

            double k4x = vx + dt * a3x;
            double k4y = vy + dt * a3y;
            double k4z = vz + dt * a3z;
            accel(x + dt * k3x, y + dt * k3y, z + dt * k3z, h2, 9);
            double a4x = scratch[9], a4y = scratch[10], a4z = scratch[11];

            x += dt / 6.0 * (k1x + 2.0 * k2x + 2.0 * k3x + k4x);
            y += dt / 6.0 * (k1y + 2.0 * k2y + 2.0 * k3y + k4y);
            z += dt / 6.0 * (k1z + 2.0 * k2z + 2.0 * k3z + k4z);
            vx += dt / 6.0 * (a1x + 2.0 * a2x + 2.0 * a3x + a4x);
            vy += dt / 6.0 * (a1y + 2.0 * a2y + 2.0 * a3y + a4y);
            vz += dt / 6.0 * (a1z + 2.0 * a2z + 2.0 * a3z + a4z);

            // Only bother sub-marching where the segment can touch the slab.
            if (mayTouchDisk(px, py, pz) || mayTouchDisk(x, y, z)) {
                march(px, py, pz, x, y, z, vx, vy, vz);
            }
        }

        if (outcome == HIT_SKY && accEmission > 0.0) {
            outcome = HIT_DISK;
        }
        store(index, outcome);
    }

    /**
     * Walks the segment in small steps, accumulating emission and absorption.
     * This is where the disk's soft, glowing body comes from.
     */
    private void march(double x0, double y0, double z0, double x1, double y1, double z1,
                       double vx, double vy, double vz) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / MARCH_STEP);
        if (steps < 1) {
            steps = 1;
        } else if (steps > 60) {
            steps = 60;
        }
        double ds = length / steps;

        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double px = x0 + dx * f;
            double py = y0 + dy * f;
            double pz = z0 + dz * f;

            double l = Math.sqrt(px * px + py * py);
            if (l <= DISK_INNER || l >= DISK_OUTER) {
                continue;
            }
            double thickness = THICKNESS_RATIO * l;
            if (Math.abs(pz) > thickness * 0.5) {
                continue;
            }

            double sigma = thickness * SIGMA_RATIO;
            double vertical = Math.exp(-(pz * pz) / (2.0 * sigma * sigma));
            // Zero at the inner edge, r^-3/2 falloff, linear taper to the rim.
            double radial = Math.pow(l, -1.5)
                    * (1.0 - Math.sqrt(DISK_INNER / l))
                    * (1.0 - (l - DISK_INNER) / (DISK_OUTER - DISK_INNER));
            if (radial <= 0.0) {
                continue;
            }

            double density = vertical * radial * DENSITY_SCALE;
            double luminosity = 5.0 / l;
            double contribution = accTransmission * density * luminosity * ds;

            accEmission += contribution;
            accWeightedT += contribution * ((l - DISK_INNER) / (DISK_OUTER - DISK_INNER));
            double az = Math.atan2(py, px);
            accSin += contribution * Math.sin(az);
            accCos += contribution * Math.cos(az);
            accBoost += contribution * dopplerAt(px, py, l, vx, vy);

            accTransmission *= Math.exp(-density * ds);
        }
    }

    private void store(int index, byte outcome) {
        type[index] = outcome;
        opacity[index] = outcome == HIT_SHADOW ? 1.0F : (float) (1.0 - accTransmission);

        if (accEmission <= 0.0) {
            return;
        }
        emission[index] = (float) accEmission;
        meanT[index] = (float) (accWeightedT / accEmission);
        meanAz[index] = (float) Math.atan2(accSin, accCos);
        boost[index] = (float) (accBoost / accEmission);
    }

    private void accel(double x, double y, double z, double h2, int out) {
        double r2 = x * x + y * y + z * z;
        double r5 = r2 * r2 * Math.sqrt(r2);
        double c = -GEODESIC_C * h2 / r5;
        scratch[out] = c * x;
        scratch[out + 1] = c * y;
        scratch[out + 2] = c * z;
    }
    }

    /** Cheap rejection: is this point anywhere near the disk slab? */
    private static boolean mayTouchDisk(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y);
        if (l < DISK_INNER * 0.9 || l > DISK_OUTER * 1.1) {
            return false;
        }
        return Math.abs(z) < l * THICKNESS_RATIO;
    }

    /**
     * Combined Doppler and gravitational shift. The reference simulator leaves
     * this out, but without it the disk is symmetric and loses the one-sided glare
     * that reads as "this thing is spinning".
     */
    private static double dopplerAt(double px, double py, double l, double vx, double vy) {
        double speed = Math.sqrt(1.0 / l);
        double ox = -py / l * speed;
        double oy = px / l * speed;

        double nl = Math.sqrt(vx * vx + vy * vy);
        double towardCamera = nl < 1e-9 ? 0.0 : -(vx * ox + vy * oy) / nl;

        double gamma = 1.0 / Math.sqrt(1.0 - speed * speed);
        double doppler = 1.0 / (gamma * (1.0 - towardCamera));
        double gravity = Math.sqrt(1.0 - SCHWARZSCHILD / l);

        double g = doppler * gravity;
        double factor = g * g * g;
        return factor > 6.0 ? 6.0 : factor;
    }

    /** Tuned so a typical ray through the brightest part reaches order-one emission. */
    private static final double DENSITY_SCALE = 900.0;

    /** {@code C = 3M}; with M = 1 the horizon is at r = 2 and the ISCO at r = 6. */
    private static final double GEODESIC_C = 3.0;
}

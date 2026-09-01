package com.console.uky.client.render;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The set of camera poses the black hole can be seen from.
 *
 * A pose cannot be rotated after the fact — the lensing table bakes one camera
 * angle, and re-tracing takes seconds. So instead of one angle, a short ladder of
 * them is traced up front: from well below the disk plane, through edge-on, to
 * looking down on it. Screens pick a rung; moving between screens walks the
 * ladder, which is what makes the hole turn instead of cutting.
 *
 * The rung the menu rests on is traced at full resolution. The rest exist only to
 * be flicked past during a turn, so they are traced small — nothing is legible
 * mid-rotation, and a dozen full-resolution tables would not fit in memory or in
 * the loading budget.
 */
public final class LensLibrary {

    /** Elevations in radians, from below the plane to above it. */
    private static final double[] ELEVATIONS = {
        -0.62, -0.44, -0.30, -0.19, -0.10, -0.03,
         0.03,  0.115, 0.20,  0.30,  0.44,  0.62, 0.85
    };

    /** The rung the title screen rests on: near edge-on, slightly above. */
    public static final int POSE_EDGE_ON = 7;
    /** Looking down on the disk. */
    public static final int POSE_ABOVE = 11;
    /** Looking up from under it. */
    public static final int POSE_BELOW = 1;
    /** Almost exactly in the plane — the thin, hardest-edged view. */
    public static final int POSE_IN_PLANE = 5;

    private static final int HERO_WIDTH = 1280;
    private static final int HERO_HEIGHT = 896;
    private static final int STEP_WIDTH = 448;
    private static final int STEP_HEIGHT = 314;

    /**
     * Written by the trace thread, read by the render thread. An atomic array
     * rather than a plain one so each pose is safely published the moment it is
     * finished, instead of the whole ladder appearing at once.
     */
    private static final AtomicReferenceArray<BakedHole> POSES =
            new AtomicReferenceArray<BakedHole>(ELEVATIONS.length);
    private static Thread worker;
    /**
     * Set when the shader turns out to be unusable after all — the driver claimed
     * support but the program would not compile or link. The tables then have to be
     * traced late rather than during loading.
     */
    private static volatile boolean forcedFallback;

    /** Falls back to the traced tables and starts them if they are not running. */
    public static void forceTableFallback() {
        forcedFallback = true;
        warmUp();
    }

    private LensLibrary() {
    }

    public static int poseCount() {
        return ELEVATIONS.length;
    }

    /**
     * Camera pitch for a fractional place on the ladder.
     *
     * The shader path takes this straight as a uniform, which is what turns the
     * ladder from a flipbook into genuinely continuous rotation — the rungs stop
     * being distinct images and become just the keyframes the camera eases along.
     */
    public static float elevationAt(float poseIndex) {
        if (poseIndex <= 0.0F) {
            return (float) ELEVATIONS[0];
        }
        if (poseIndex >= ELEVATIONS.length - 1) {
            return (float) ELEVATIONS[ELEVATIONS.length - 1];
        }
        int lower = (int) poseIndex;
        float f = poseIndex - lower;
        return (float) (ELEVATIONS[lower] + (ELEVATIONS[lower + 1] - ELEVATIONS[lower]) * f);
    }

    /**
     * The pose at {@code index}, or null if it has not been traced yet. Callers
     * must cope with null: the ladder fills in over several seconds.
     */
    public static BakedHole pose(int index) {
        if (index < 0 || index >= POSES.length()) {
            return null;
        }
        return POSES.get(index);
    }

    public static boolean isRestingPoseReady() {
        return POSES.get(POSE_EDGE_ON) != null;
    }

    /**
     * Starts tracing, once per session. Safe to call repeatedly.
     *
     * Skipped entirely when the GPU can run the shader: the tables exist only
     * because a fixed-function pipeline cannot integrate geodesics, and tracing
     * them anyway would burn sixteen seconds and thirty-odd megabytes for nothing.
     */
    public static synchronized void warmUp() {
        if (worker != null) {
            return;
        }
        // Only trace when the shader is known not to be an option — either the driver
        // was asked and said no, or a program was built and would not run. Asked during
        // mod loading the driver often cannot be reached at all (the loading screen owns
        // the GL context, and LWJGL keeps capabilities per thread), and starting thirteen
        // poses of CPU tracing on that maybe costs the load far more than the tables are
        // worth. See ShaderProgram.isKnownUnsupported.
        if (!forcedFallback && !ShaderProgram.isKnownUnsupported()) {
            return;
        }
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                traceAll();
            }
        }, "UKY Lensing");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static void traceAll() {
        // Progressive refinement on the resting pose. The full-resolution trace
        // takes the better part of ten seconds; on a small pack the menu would be
        // up long before then with nothing in it. A small version of the same
        // angle takes under a second, so it goes up first and the sharp one
        // replaces it in place — identical geometry, so the swap does not move
        // anything, it only gets crisper.
        LensMap preview = new LensMap(STEP_WIDTH, STEP_HEIGHT, ELEVATIONS[POSE_EDGE_ON]);
        preview.compute();
        POSES.set(POSE_EDGE_ON, new BakedHole(preview));

        LensMap hero = new LensMap(HERO_WIDTH, HERO_HEIGHT, ELEVATIONS[POSE_EDGE_ON]);
        hero.compute();
        POSES.set(POSE_EDGE_ON, new BakedHole(hero));

        // Then the rest of the ladder, outward from the resting pose so the nearest
        // rungs — the ones a short turn needs — become usable first.
        for (int offset = 1; offset < ELEVATIONS.length; offset++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int index = POSE_EDGE_ON + sign * offset;
                if (index < 0 || index >= ELEVATIONS.length || POSES.get(index) != null) {
                    continue;
                }
                LensMap map = new LensMap(STEP_WIDTH, STEP_HEIGHT, ELEVATIONS[index]);
                map.compute();
                POSES.set(index, new BakedHole(map));
            }
        }
    }
}

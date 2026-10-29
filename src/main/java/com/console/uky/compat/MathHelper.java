package com.console.uky.compat;

import net.minecraft.util.Mth;

/** The few 1.7.10 MathHelper names this mod used, over {@link Mth}. */
public final class MathHelper {

    private MathHelper() {
    }

    public static int ceiling_double_int(double v) {
        return Mth.ceil(v);
    }

    public static int ceiling_float_int(float v) {
        return Mth.ceil(v);
    }

    public static int floor_double(double v) {
        return Mth.floor(v);
    }

    public static int floor_float(float v) {
        return Mth.floor(v);
    }

    public static float sin(float v) {
        return Mth.sin(v);
    }

    public static float cos(float v) {
        return Mth.cos(v);
    }

    public static float sqrt_float(float v) {
        return Mth.sqrt(v);
    }

    public static float clamp_float(float v, float min, float max) {
        return Mth.clamp(v, min, max);
    }

    public static int clamp_int(int v, int min, int max) {
        return Mth.clamp(v, min, max);
    }
}

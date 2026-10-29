package com.console.uky.compat;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * 1.7.10's ScaledResolution: the GUI's size in GUI units and the factor between those
 * and framebuffer pixels. The window works these out itself now; this only reads them.
 */
public final class ScaledResolution {

    private final int width;
    private final int height;
    private final double widthD;
    private final double heightD;
    private final int factor;

    public ScaledResolution(Minecraft mc, int displayWidth, int displayHeight) {
        Window window = mc.getWindow();
        this.factor = Math.max(1, (int) window.getGuiScale());
        this.widthD = (double) displayWidth / this.factor;
        this.heightD = (double) displayHeight / this.factor;
        this.width = (int) Math.ceil(this.widthD);
        this.height = (int) Math.ceil(this.heightD);
    }

    public ScaledResolution(Minecraft mc) {
        this(mc, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    public int getScaledWidth() {
        return this.width;
    }

    public int getScaledHeight() {
        return this.height;
    }

    public double getScaledWidth_double() {
        return this.widthD;
    }

    public double getScaledHeight_double() {
        return this.heightD;
    }

    public int getScaleFactor() {
        return this.factor;
    }
}

package com.console.uky.compat;

import com.mojang.blaze3d.systems.RenderSystem;

/** glActiveTexture through {@link RenderSystem}, so its texture-unit cache stays true. */
public class GL13 extends org.lwjgl.opengl.GL13 {

    protected GL13() {
    }

    public static void glActiveTexture(int unit) {
        RenderSystem.activeTexture(unit);
    }
}

package com.console.uky.client.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * Vanilla {@link FontRenderer} rewired to work before the resource system is up.
 *
 * The splash owns its GL context on a separate thread, so the normal
 * {@code TextureManager} path is unusable: texture binding is redirected to a
 * {@link SplashTexture} we uploaded ourselves, and resource reads go straight to
 * Minecraft's default resource pack (which exists from the very first frame).
 *
 * Same trick FML's own splash uses; reimplemented here because its version is
 * private.
 */
final class SplashFont extends FontRenderer {

    private final SplashTexture texture;

    SplashFont(SplashTexture texture, ResourceLocation location) {
        super(Minecraft.getMinecraft().gameSettings, location, null, false);
        this.texture = texture;
        // Re-run the glyph-width scan now that `texture` is assigned; the super
        // constructor already called it once, before this field existed.
        super.onResourceManagerReload(null);
    }

    @Override
    protected void bindTexture(ResourceLocation location) {
        // FontRenderer's constructor binds before our field is assigned; nothing
        // is drawn that early, so skipping the bind is safe.
        if (this.texture != null) {
            this.texture.bind();
        }
    }

    @Override
    protected InputStream getResourceInputStream(ResourceLocation location) throws IOException {
        return Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(location);
    }
}

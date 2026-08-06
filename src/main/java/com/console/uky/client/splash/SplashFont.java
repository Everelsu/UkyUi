package com.console.uky.client.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.data.IMetadataSection;
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

    /**
     * The glyph pages, straight out of the jar.
     *
     * 1.7.10's hook handed back a stream; 1.12.2's hands back an {@link IResource}, so
     * one is wrapped around the same stream. It deliberately still comes from the
     * default resource pack rather than the resource manager: this font is built for
     * the loading splash, which draws before the resource manager exists.
     */
    @Override
    protected IResource getResource(final ResourceLocation location) throws IOException {
        final InputStream stream =
                Minecraft.getMinecraft().defaultResourcePack.getInputStream(location);
        return new IResource() {
            @Override
            public ResourceLocation getResourceLocation() {
                return location;
            }

            @Override
            public InputStream getInputStream() {
                return stream;
            }

            @Override
            public boolean hasMetadata() {
                return false;
            }

            @Override
            public <T extends IMetadataSection> T getMetadata(String sectionName) {
                return null;
            }

            @Override
            public String getResourcePackName() {
                return "uky-splash";
            }

            @Override
            public void close() throws IOException {
                stream.close();
            }
        };
    }
}

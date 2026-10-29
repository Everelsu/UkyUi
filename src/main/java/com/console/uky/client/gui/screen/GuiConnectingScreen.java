package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.compat.GuiScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The "connecting to the server" screen.
 *
 * Wrapped rather than replaced, the same way the world loading screen is: vanilla's
 * {@code GuiConnecting} owns the network manager, the handshake and the timeout, and
 * reimplementing that to change how it looks would be trading a working connection
 * for a nicer spinner. Its {@code updateScreen} still runs every tick, so the
 * connection proceeds exactly as it would have; only the drawing is ours.
 *
 * <p>Deliberately not a {@code MenuScreen}: everything on the multiplayer path sits
 * on flat dark, and this is the middle of that path.
 */
public class GuiConnectingScreen extends GuiScreen {

    private final net.minecraft.client.gui.screens.Screen delegate;
    /** Size the delegate was set up at; -1 until it has been. */
    private int delegateWidth = -1;
    private int delegateHeight = -1;

    private long lastFrameNanos = System.nanoTime();
    private float elapsed;

    private float cancelHover;
    private int mouseX;
    private int mouseY;
    /** One press only: the channel is closed by it, and closing it twice is not a no-op. */
    private boolean cancelled;

    public GuiConnectingScreen(net.minecraft.client.gui.screens.Screen delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initGui() {
        prepareDelegate();
    }

    /**
     * Hands the delegate a world and a resolution, if that has not happened yet.
     *
     * This is not belt and braces — it is the whole connection. Vanilla's
     * {@code GuiConnecting} opens the socket from its <em>own</em> {@code initGui},
     * and ours only ran from this screen's, which Forge lets any mod skip: cancelling
     * {@code GuiScreenEvent.InitGuiEvent.Pre} makes
     * {@code GuiScreen.setWorldAndResolution} fill in the size and return without
     * calling {@code initGui} at all. When that happened the delegate was never
     * started, so its {@code updateScreen} had no network manager to poll and the
     * client sat on "Connecting to the server..." until it timed out — and the
     * disconnect screen that followed then crashed for the same underlying reason.
     *
     * <p>Driven from {@code updateScreen} and the draw instead, so it happens on
     * whichever of them comes first and cannot be skipped by anybody.
     */
    private void prepareDelegate() {
        if (this.delegateWidth == this.width && this.delegateHeight == this.height) {
            return;
        }
        this.delegateWidth = this.width;
        this.delegateHeight = this.height;
        this.delegate.init(this.mc, this.width, this.height);
    }

    /**
     * What vanilla's screen says it is doing — connecting, logging in, joining — read
     * off its own status field, so this one never says anything that one would not.
     * By name: the screen holds other components too, and NeoForge keeps Mojang's names.
     */
    private String status() {
        try {
            Field field = this.delegate.getClass().getDeclaredField("status");
            field.setAccessible(true);
            Component status = (Component) field.get(this.delegate);
            if (status != null) {
                return status.getString();
            }
        } catch (Throwable ignored) {
            // Fall through to the generic line.
        }
        return I18n.get("connect.connecting");
    }

    @Override
    public void updateScreen() {
        // This is what actually drives the connection; skipping it would hang here.
        prepareDelegate();
        this.delegate.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        prepareDelegate();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        this.elapsed += Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        Draw.rect(0, 0, this.width, this.height, Theme.background);
        Draw.vignette(this.width, this.height, 0.7F, 0xFF000000);

        String message = status();
        this.drawCenteredString(this.fontRendererObj, message,
                this.width / 2, this.height / 2 - 16, Draw.withAlpha(Theme.text, 0.9F));

        drawIndicator();
        drawCancel();
    }

    /** The same indeterminate sweep the world loading screen uses. */
    private void drawIndicator() {
        float barWidth = Math.min(this.width * 0.34F, 220.0F);
        float x = (this.width - barWidth) / 2.0F;
        float y = this.height / 2.0F + 2.0F;

        Draw.rect(x, y, x + barWidth, y + 1.0F, Draw.withAlpha(Theme.textDim, 0.20F));

        float sweep = barWidth * 0.28F;
        float t = (this.elapsed * 0.55F) % 1.0F;
        float head = x - sweep + (barWidth + sweep) * Ease.inOutCubic(t);
        float from = Math.max(x, head);
        float to = Math.min(x + barWidth, head + sweep);
        if (to > from) {
            Draw.gradientH(from, y, (from + to) / 2.0F, y + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.9F));
            Draw.gradientH((from + to) / 2.0F, y, to, y + 1.0F,
                    Draw.withAlpha(Theme.accent, 0.9F), Draw.withAlpha(Theme.accent, 0.0F));
        }
    }

    // ------------------------------------------------------------- cancelling --

    /** Where the button sits. One place, so the draw and the hit test cannot drift. */
    private int cancelX() {
        return (this.width - cancelWidth()) / 2;
    }

    private int cancelWidth() {
        return Math.min(200, Math.max(120, this.width / 3));
    }

    private int cancelY() {
        return (int) (this.height / 2.0F + 34.0F);
    }

    /**
     * The way out, which vanilla has and this screen had lost.
     *
     * {@code GuiConnecting} puts a Cancel button on itself, and wrapping the screen to
     * restyle it took the button with it — so a server that was slow, unreachable or
     * simply the wrong address left the player watching a progress sweep with nothing
     * to press and no key that did anything. Escape included, because this screen's
     * {@code keyTyped} was empty on the grounds that vanilla's is too. Vanilla's can
     * afford to be: vanilla has the button.
     */
    private void drawCancel() {
        int x = cancelX();
        int y = cancelY();
        int width = cancelWidth();
        boolean over = this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + 20;
        this.cancelHover = Ease.approach(this.cancelHover, over ? 1.0F : 0.0F, 0.05F, 0.016F);

        // Outlined rather than filled: cancelling is not what the player came here to
        // do, and the one filled control on the multiplayer path is the one that acts.
        Draw.rect(x, y, x + width, y + 20,
                Draw.withAlpha(0x000000, (0.45F + this.cancelHover * 0.2F) * 0.9F));
        Draw.border(x, y, x + width, y + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.cancelHover), 0.9F));

        String label = I18n.get("gui.cancel", new Object[0]);
        int labelWidth = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label, x + (width - labelWidth) / 2, y + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.cancelHover), 0.95F));
    }

    /**
     * Aborts the handshake and goes back, doing what vanilla's button does.
     *
     * Not by calling that button's handler: {@code actionPerformed} is an MCP name, so
     * reflection on it would work in development and fail in a built pack. The two
     * pieces of state it touches are found by field <em>type</em> instead — the same
     * technique the screen swapper uses — and the network manager is then closed by a
     * direct call, which the reobfuscator remaps like any other.
     *
     * <p>The flag comes first, exactly as it does in vanilla. It is what tells the
     * connect thread that nobody is waiting any more; without it, a handshake that
     * fails after we have left still displays its own disconnected screen over
     * whatever the player is looking at by then.
     */
    private void cancel() {
        if (this.cancelled) {
            return;
        }
        this.cancelled = true;

        net.minecraft.client.gui.screens.Screen parent = null;
        try {
            markAborted();
            Connection connection = fieldOfType(Connection.class);
            if (connection != null) {
                connection.disconnect(Component.translatable("connect.aborted"));
            }
        } catch (Throwable t) {
            // Leaving matters more than leaving tidily: a screen with no way out is
            // the bug being fixed. The socket is dropped by the connect thread when
            // it notices, and by the process at the latest.
            UkyUI.LOGGER.warn("Could not abort the connection cleanly", t);
        }
        try {
            parent = fieldOfType(net.minecraft.client.gui.screens.Screen.class);
        } catch (Throwable ignored) {
            // Null falls back to the main menu below, which is still a way out.
        }
        this.mc.setScreen(parent);
    }

    /** Sets the delegate's "the player left" flag; it declares exactly one boolean. */
    private void markAborted() throws IllegalAccessException {
        for (Field field : this.delegate.getClass().getDeclaredFields()) {
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.setBoolean(this.delegate, true);
                return;
            }
        }
    }

    /** The delegate's first non-static field of the given type, or null. */
    @SuppressWarnings("unchecked")
    private <T> T fieldOfType(Class<T> wanted) throws IllegalAccessException {
        for (Field field : this.delegate.getClass().getDeclaredFields()) {
            if (wanted.isAssignableFrom(field.getType())
                    && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return (T) field.get(this.delegate);
            }
        }
        return null;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int x = cancelX();
        int y = cancelY();
        if (mouseX >= x && mouseX < x + cancelWidth() && mouseY >= y && mouseY < y + 20) {
            cancel();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // Escape
            cancel();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.HttpUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldSettings;

/**
 * Opening a world to the local network.
 *
 * Vanilla offers this as two cycling grey buttons whose labels are whole sentences,
 * on the dirt background, with no indication of what actually happens when you press
 * Start. Here the two choices are tiles like the world-creation screen's, so the
 * options are all visible at once rather than one-at-a-time behind a click, and the
 * consequence of each is written on it.
 *
 * <p>The port can be chosen, as in newer versions: the field starts on a free one picked
 * the way vanilla picks it, any other can be typed in, and an empty field means "any
 * free one". Whether the typed one can be used is said next to it before Start is
 * pressed — out of range or already taken — rather than after, in chat.
 *
 * <p>Kept on plain dark like the rest of the multiplayer path.
 */
public class GuiShareScreen extends MenuScreen {

    private static final String[] MODES = {"survival", "creative", "adventure"};

    private int selectedMode;
    private boolean allowCheats;

    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;
    private int modeY;
    private int modeHeight;
    private int cheatsY;
    private int portY;
    private int actionY;

    // ---- the port ----
    private static final int PORT_OK = 0, PORT_AUTO = 1, PORT_RANGE = 2, PORT_BUSY = 3;
    private GuiTextField portField;
    /** What the field said, kept across a re-layout (which builds a new field). */
    private String portText;
    /** The text the state below was worked out for: checking a port opens a socket. */
    private String portChecked;
    private int portState;

    private final float[] modeHover = new float[MODES.length];
    private float cheatsHover;
    private float startHover;
    private float cancelHover;

    private int mouseX;
    private int mouseY;
    private boolean started;

    public GuiShareScreen(GuiScreen parent) {
        super(parent);
    }

    /** Plain dark, matching the server list and the world list. */
    @Override
    protected boolean isVoid() {
        return true;
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        // Default to the mode the world is already running, which is nearly always
        // what the guests should get. Read off the integrated server rather than the
        // player controller: 1.7.10 has a setter for the client's game type but no
        // getter, and the server's is the authoritative one anyway.
        if (this.mc.getIntegratedServer() != null) {
            WorldSettings.GameType current = this.mc.getIntegratedServer().getGameType();
            if (current != null) {
                for (int i = 0; i < MODES.length; i++) {
                    if (MODES[i].equals(current.getName())) {
                        this.selectedMode = i;
                        break;
                    }
                }
            }
        }

        int panelWidth = Math.min((int) (this.width * 0.72F), 420);
        this.panelX1 = (this.width - panelWidth) / 2;
        this.panelX2 = this.panelX1 + panelWidth;

        boolean cramped = this.height < 300;
        // Tall enough for a wrapped description rather than a truncated one. The
        // blurb used to be drawn as a single line cut off with an ellipsis — "Ищите
        // ресурсы, мас…" — which tells a player choosing a mode nothing at all, and
        // was the more visible for the panel having a band of empty space under it.
        this.modeHeight = cramped ? 34 : 62;
        int panelHeight = 52 + this.modeHeight + 12 + 22 + 16 + 20 + 20 + 40;
        this.panelY1 = Math.max(16, (this.height - panelHeight) / 2);
        this.panelY2 = this.panelY1 + panelHeight;

        this.modeY = this.panelY1 + 46;
        this.cheatsY = this.modeY + this.modeHeight + 12;
        this.portY = this.cheatsY + 22 + 24;
        this.actionY = this.panelY2 - 32;

        if (this.portText == null) {
            this.portText = String.valueOf(freePort());
        }
        this.portField = new GuiTextField(this.fontRendererObj, this.panelX1 + 18 + 5, this.portY + 4, 44, 16);
        this.portField.setMaxStringLength(5);
        this.portField.setEnableBackgroundDrawing(false);
        this.portField.setText(this.portText);
        this.portField.setFocused(true);
    }

    /** A free port, picked the way vanilla's shareToLAN picks one. */
    private static int freePort() {
        try {
            int port = HttpUtil.func_76181_a();
            return port > 0 ? port : 25564;
        } catch (IOException e) {
            return 25564;
        }
    }

    private int columnWidth() {
        int gap = 6;
        return (this.panelX2 - 18 - (this.panelX1 + 18) - gap * (MODES.length - 1)) / MODES.length;
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        drawPanel();

        String title = I18n.format("lanServer.title", new Object[0]);
        int titleWidth = this.fontRendererObj.getStringWidth(title);
        this.fontRendererObj.drawString(title, (this.width - titleWidth) / 2, this.panelY1 + 16,
                Draw.withAlpha(Theme.text, this.fadeAlpha));

        drawModes();
        drawCheats();
        drawPort();
        drawActions();
    }

    private void drawPanel() {
        float a = this.fadeAlpha;
        Draw.gradientV(this.panelX1, this.panelY1, this.panelX2, this.panelY2,
                Draw.withAlpha(Theme.background, 0.62F * a),
                Draw.withAlpha(Theme.background, 0.40F * a));
        Draw.rect(this.panelX1, this.panelY1, this.panelX2, this.panelY1 + 1,
                Draw.withAlpha(0xFFFFFF, 0.07F * a));
        Draw.gradientH(this.panelX1, this.panelY1, this.panelX1 + 2, this.panelY2,
                Draw.withAlpha(Theme.accent, 0.6F * a), Draw.withAlpha(Theme.accent, 0.0F));
        Draw.border(this.panelX1, this.panelY1, this.panelX2, this.panelY2, 1.0F,
                Draw.withAlpha(Theme.text, 0.10F * a));
    }

    private void drawModes() {
        this.fontRendererObj.drawString(
                I18n.format("selectWorld.gameMode", new Object[0]).toUpperCase(),
                this.panelX1 + 18, this.modeY - 12,
                Draw.withAlpha(Theme.textDim, 0.9F * this.fadeAlpha));

        int gap = 6;
        int width = columnWidth();
        for (int i = 0; i < MODES.length; i++) {
            int x = this.panelX1 + 18 + i * (width + gap);
            boolean over = inside(x, this.modeY, width, this.modeHeight);
            this.modeHover[i] = Ease.approach(this.modeHover[i], over ? 1.0F : 0.0F,
                    0.05F, this.delta);
            boolean selected = i == this.selectedMode;

            Draw.rect(x, this.modeY, x + width, this.modeY + this.modeHeight,
                    Draw.withAlpha(0x000000,
                            (0.55F + (selected ? 0.28F : this.modeHover[i] * 0.2F)) * this.fadeAlpha));
            if (selected) {
                Draw.rect(x, this.modeY, x + 2, this.modeY + this.modeHeight,
                        Draw.withAlpha(Theme.accent, this.fadeAlpha));
            }
            Draw.border(x, this.modeY, x + width, this.modeY + this.modeHeight, 1.0F,
                    selected
                            ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                            : Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, this.modeHover[i]),
                                    (0.13F + this.modeHover[i] * 0.6F) * this.fadeAlpha));

            String label = I18n.format("selectWorld.gameMode." + MODES[i], new Object[0]);
            this.fontRendererObj.drawString(
                    fit(label, width - 16),
                    x + 8, this.modeY + 8,
                    Draw.withAlpha(selected ? Theme.textHover : Theme.text, this.fadeAlpha));

            if (this.modeHeight >= 40) {
                // Both halves, and wrapped to the card rather than cut at it. Vanilla
                // splits this text across two keys precisely because it does not fit on
                // one line, and taking only the first and trimming it threw away the
                // half that says what the mode actually does.
                drawBlurb(i, x + 8, this.modeY + 22, width - 16);
            }
        }
    }

    /**
     * A mode's description, wrapped into the card.
     *
     * The two vanilla keys are joined before wrapping rather than drawn as the two
     * lines they are: where they break is a decision made for vanilla's own card width,
     * and this card is a different width on every window. Joining them and re-flowing
     * puts the break where this layout needs it.
     *
     * <p>Lines past what the card can hold are dropped rather than drawn over its
     * edge. That is a real case at {@code cramped} sizes and in the longer
     * translations, and a description spilling onto the button below it would be worse
     * than a description that stops.
     */
    private void drawBlurb(int mode, int x, int y, int maxWidth) {
        String key = "selectWorld.gameMode." + MODES[mode];
        String blurb = I18n.format(key + ".line1", new Object[0]);
        String second = I18n.format(key + ".line2", new Object[0]);
        // A key with no translation comes back as the key itself; that is not a line.
        if (!second.isEmpty() && !second.startsWith(key)) {
            blurb = blurb + " " + second;
        }

        int lineHeight = this.fontRendererObj.FONT_HEIGHT;
        int room = (this.modeY + this.modeHeight - 6 - y) / lineHeight;
        if (room <= 0) {
            return;
        }
        List<?> lines = this.fontRendererObj.listFormattedStringToWidth(blurb, maxWidth);
        for (int i = 0; i < lines.size() && i < room; i++) {
            this.fontRendererObj.drawString(String.valueOf(lines.get(i)), x, y + i * lineHeight,
                    Draw.withAlpha(Theme.textDim, 0.7F * this.fadeAlpha));
        }
    }

    private void drawCheats() {
        int x1 = this.panelX1 + 18;
        int x2 = this.panelX2 - 18;
        boolean over = inside(x1, this.cheatsY, x2 - x1, 22);
        this.cheatsHover = Ease.approach(this.cheatsHover, over ? 1.0F : 0.0F, 0.05F, this.delta);

        Draw.rect(x1, this.cheatsY, x2, this.cheatsY + 22,
                Draw.withAlpha(0x000000, (0.5F + this.cheatsHover * 0.2F) * this.fadeAlpha));
        Draw.border(x1, this.cheatsY, x2, this.cheatsY + 22, 1.0F,
                this.allowCheats
                        ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                        : Draw.withAlpha(Draw.mix(Theme.text, Theme.accent, this.cheatsHover),
                                (0.13F + this.cheatsHover * 0.6F) * this.fadeAlpha));

        float box = 12.0F;
        float bx = x1 + 6;
        float by = this.cheatsY + 5;
        Draw.border(bx, by, bx + box, by + box, 1.0F,
                this.allowCheats
                        ? Draw.withAlpha(Theme.accent, this.fadeAlpha)
                        : Draw.withAlpha(Theme.text, 0.3F * this.fadeAlpha));
        if (this.allowCheats) {
            Icons.check(bx + box / 2.0F, by + box / 2.0F, box * 0.8F,
                    Draw.withAlpha(Theme.accent, this.fadeAlpha));
        }

        String label = I18n.format("selectWorld.allowCommands", new Object[0]).trim();
        while (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).trim();
        }
        this.fontRendererObj.drawString(label, (int) (bx + box + 8), this.cheatsY + 7,
                Draw.withAlpha(this.allowCheats ? Theme.text : Theme.textDim, this.fadeAlpha));
    }

    /** Caption above, the dark strip with the number in it, and what can be said about it beside. */
    private void drawPort() {
        if (this.portField == null) {
            return;
        }
        float a = this.fadeAlpha;
        float x1 = this.portField.xPosition - 5;
        float x2 = this.portField.xPosition + this.portField.getWidth() + 5;
        float y1 = this.portField.yPosition - 4;
        float y2 = this.portField.yPosition + 12;
        this.fontRendererObj.drawString(I18n.format("uky.share.port", new Object[0]).toUpperCase(),
                this.panelX1 + 18, this.portY - 12, Draw.withAlpha(Theme.textDim, 0.9F * a));
        Draw.rect(x1, y1, x2, y2, Draw.withAlpha(0x000000, 0.55F * a));
        int state = portState();
        boolean bad = state == PORT_RANGE || state == PORT_BUSY;
        Draw.rect(x1, y2 - 1, x2, y2, Draw.withAlpha(bad ? Theme.danger : Theme.accent, a));
        this.portField.drawTextBox();
        String key = state == PORT_OK ? "uky.share.portFree"
                : state == PORT_AUTO ? "uky.share.portAuto"
                : state == PORT_BUSY ? "uky.share.portBusy" : "uky.share.portRange";
        int colour = state == PORT_OK ? 0xFF6ECB63 : bad ? Theme.danger : Theme.textDim;
        this.fontRendererObj.drawString(I18n.format(key, new Object[0]), (int) x2 + 8, this.portY + 4,
                Draw.withAlpha(colour, 0.9F * a));
    }

    /** What the field's port amounts to; worked out again only when the text changes. */
    private int portState() {
        String text = this.portField == null ? "" : this.portField.getText().trim();
        if (text.equals(this.portChecked)) {
            return this.portState;
        }
        this.portChecked = text;
        this.portText = text;
        if (text.isEmpty()) {
            return this.portState = PORT_AUTO;
        }
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return this.portState = PORT_RANGE;
        }
        if (port < 1024 || port > 65535) {
            return this.portState = PORT_RANGE;
        }
        // Taken by anything at all on this machine: a server, another game, another
        // world opened to LAN. Bound and let go at once, which is how vanilla checks too.
        try {
            new ServerSocket(port).close();
            return this.portState = PORT_OK;
        } catch (IOException e) {
            return this.portState = PORT_BUSY;
        }
    }

    private boolean portUsable() {
        int state = portState();
        return state == PORT_OK || state == PORT_AUTO;
    }

    private void drawActions() {
        int gap = 8;
        int x1 = this.panelX1 + 18;
        int total = this.panelX2 - 18 - x1;
        int half = (total - gap) / 2;

        boolean overStart = inside(x1, this.actionY, half, 20);
        boolean overCancel = inside(x1 + half + gap, this.actionY, half, 20);
        this.startHover = Ease.approach(this.startHover, overStart ? 1.0F : 0.0F, 0.05F, this.delta);
        this.cancelHover = Ease.approach(this.cancelHover, overCancel ? 1.0F : 0.0F, 0.05F, this.delta);

        int fill = Draw.mix(Theme.accent, Theme.textHover, this.startHover * 0.2F);
        // Dimmed while the port can't be used: Start would only fail.
        float can = portUsable() ? 1.0F : 0.35F;
        Draw.rect(x1, this.actionY, x1 + half, this.actionY + 20,
                Draw.withAlpha(fill, (0.62F + this.startHover * 0.18F) * this.fadeAlpha * can));
        if (this.startHover > 0.02F) {
            Draw.glow(x1, this.actionY, x1 + half, this.actionY + 20, 5.0F,
                    Draw.withAlpha(Theme.accent, 0.3F * this.startHover * this.fadeAlpha), 4);
        }
        centred(I18n.format("lanServer.start", new Object[0]), x1, half, this.actionY + 6,
                Draw.withAlpha(0x0B0B0E, this.fadeAlpha));

        int cancelX = x1 + half + gap;
        Draw.rect(cancelX, this.actionY, cancelX + half, this.actionY + 20,
                Draw.withAlpha(0x000000, (0.5F + this.cancelHover * 0.2F) * this.fadeAlpha));
        Draw.border(cancelX, this.actionY, cancelX + half, this.actionY + 20, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, this.cancelHover), this.fadeAlpha));
        centred(I18n.format("gui.cancel", new Object[0]), cancelX, half, this.actionY + 6,
                Draw.withAlpha(Draw.mix(Theme.textDim, Theme.textHover, this.cancelHover),
                        this.fadeAlpha));
    }

    private void centred(String text, int x, int width, int y, int colour) {
        String trimmed = fit(text, width - 8);
        int w = this.fontRendererObj.getStringWidth(trimmed);
        this.fontRendererObj.drawString(trimmed, x + (width - w) / 2, y, colour);
    }

    private boolean inside(int x, int y, int width, int height) {
        return this.mouseX >= x && this.mouseX < x + width
                && this.mouseY >= y && this.mouseY < y + height;
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        int gap = 6;
        int width = columnWidth();
        for (int i = 0; i < MODES.length; i++) {
            int x = this.panelX1 + 18 + i * (width + gap);
            if (inside(x, this.modeY, width, this.modeHeight)) {
                this.selectedMode = i;
                return;
            }
        }
        if (inside(this.panelX1 + 18, this.cheatsY, this.panelX2 - 18 - (this.panelX1 + 18), 22)) {
            this.allowCheats = !this.allowCheats;
            return;
        }
        if (this.portField != null) {
            this.portField.mouseClicked(mouseX, mouseY, button);
            this.portField.setFocused(true); // the only field: typing always goes to it
        }

        int x1 = this.panelX1 + 18;
        int half = (this.panelX2 - 18 - x1 - 8) / 2;
        if (inside(x1, this.actionY, half, 20)) {
            start();
            return;
        }
        if (inside(x1 + half + 8, this.actionY, half, 20)) {
            switchBack();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            switchBack();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            start();
            return;
        }
        // Digits, and the keys that move and erase; nothing else goes into a port.
        if (this.portField != null && (Character.isDigit(typedChar) || keyCode == 14 || keyCode == 211
                || keyCode == 203 || keyCode == 205 || keyCode == 199 || keyCode == 207
                || GuiScreen.isCtrlKeyDown())) {
            this.portField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.portField != null) {
            this.portField.updateCursorCounter();
        }
    }

    /**
     * Opens the world and reports the port in chat, exactly as vanilla does — the
     * port is the one thing a guest actually needs, so losing it would make this
     * screen worse than the one it replaces.
     */
    private void start() {
        IntegratedServer server = this.mc.getIntegratedServer();
        if (this.started || server == null || !portUsable()) {
            return;
        }
        this.started = true;
        this.mc.displayGuiScreen(null);

        String text = this.portField == null ? "" : this.portField.getText().trim();
        int port = text.isEmpty() ? freePort() : Integer.parseInt(text);
        boolean ok = share(server, port, WorldSettings.GameType.getByName(MODES[this.selectedMode]),
                this.allowCheats);
        String message = ok
                ? I18n.format("commands.publish.started", new Object[]{String.valueOf(port)})
                : I18n.format("commands.publish.failed", new Object[0]);
        this.mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
    }

    /**
     * Vanilla's {@code IntegratedServer.shareToLAN}, step for step, on the port chosen
     * here rather than one it picks: listen on it, mark the world public, announce it on
     * the network, set the guests' game mode and whether they may cheat.
     *
     * <p>The two private fields go by both their names, the workspace's and the built
     * game's. If they can't be reached the world is still open; it just isn't announced,
     * and is joined by address instead of turning up in the list.
     */
    private static boolean share(IntegratedServer server, int port, WorldSettings.GameType type,
                                 boolean cheats) {
        try {
            server.func_147137_ag().addLanEndpoint((InetAddress) null, port);
        } catch (IOException e) {
            UkyUI.LOGGER.warn("Could not open the world to LAN on port " + port, e);
            return false;
        }
        UkyUI.LOGGER.info("Started on " + port);
        try {
            ObfuscationReflectionHelper.setPrivateValue(IntegratedServer.class, server, Boolean.TRUE,
                    "isPublic", "field_71346_p");
            ThreadLanServerPing ping = new ThreadLanServerPing(server.getMOTD(), String.valueOf(port));
            ObfuscationReflectionHelper.setPrivateValue(IntegratedServer.class, server, ping,
                    "lanServerPing", "field_71345_q");
            ping.start();
        } catch (Exception e) {
            UkyUI.LOGGER.warn("The world is open on " + port + " but not announced on the network", e);
        }
        server.getConfigurationManager().func_152604_a(type);
        server.getConfigurationManager().setCommandsAllowedForAll(cheats);
        return true;
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control here is drawn, not a GuiButton.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

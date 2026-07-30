package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Servers as cards, matching the world picker.
 *
 * A server's own 64×64 icon is the closest thing it has to a screenshot, so it
 * plays the same role the world capture does: blown up as the card's face,
 * blurred by the upscale, with the name and MOTD over it. Servers that publish no
 * icon get the same black plate an unvisited world gets.
 */
public class GuiServersScreen extends MenuScreen implements GuiYesNoCallback {

    private static final int HIT_JOIN = 0;
    private static final int HIT_EDIT = 1;
    private static final int HIT_DELETE = 2;

    private static final int TILE_GAP = 10;
    private static final float TILE_ASPECT = 16.0F / 9.0F;

    private ServerList servers;
    private OldServerPinger pinger;

    /** Decoded server icons, keyed by address. */
    private final Map<String, ResourceLocation> icons = new HashMap<String, ResourceLocation>();

    private float[] hoverAmount = new float[0];
    private float addHover;

    private int columns;
    private int tileWidth;
    private int tileHeight;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;

    private float scroll;
    private float scrollTarget;

    private int hoveredCard = -2;
    private int hoveredAction = HIT_JOIN;
    private int lastMouseX;
    private int lastMouseY;

    private int pendingDelete = -1;

    public GuiServersScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    /** Plain dark behind the cards, and no vignette; see {@code GuiWorldsScreen}. */
    protected boolean isVoid() {
        return true;
    }

    @Override
    protected void drawOverlay() {
        float blackout = Transitions.blackout();
        if (blackout > 0.002F) {
            Draw.rect(0, 0, this.width, this.height, Draw.withAlpha(0x000000, blackout));
        }
    }

    // ---------------------------------------------------------------- layout --

    @Override
    protected void buildLayout() {
        if (this.servers == null) {
            this.servers = new ServerList(this.mc);
            this.servers.loadServerList();
            this.pinger = new OldServerPinger();
            pingAll();
        }

        int margin = Math.max(24, (int) (this.width * 0.06F));
        this.gridX = margin;
        this.gridWidth = this.width - margin * 2;
        this.gridY = 54;
        this.gridHeight = this.height - this.gridY - 30;

        int target = 200;
        this.columns = Math.max(1, Math.min(5, (this.gridWidth + TILE_GAP) / (target + TILE_GAP)));
        this.tileWidth = (this.gridWidth - TILE_GAP * (this.columns - 1)) / this.columns;

        // Same cap as the world list: a card taller than the grid is just clipped,
        // so the width gives way and the shorter row is centred.
        int maxByHeight = (int) (this.gridHeight * TILE_ASPECT);
        if (this.tileWidth > maxByHeight) {
            this.tileWidth = Math.max(80, maxByHeight);
            int rowWidth = this.tileWidth * this.columns + TILE_GAP * (this.columns - 1);
            this.gridX += Math.max(0, (this.gridWidth - rowWidth) / 2);
        }
        this.tileHeight = (int) (this.tileWidth / TILE_ASPECT);

        this.hoverAmount = new float[this.servers.countServers()];
        clampScroll();
    }

    /** Kicks off a ping for every entry; results land asynchronously. */
    private void pingAll() {
        for (int i = 0; i < this.servers.countServers(); i++) {
            final ServerData data = this.servers.getServerData(i);
            data.pingToServer = -2L;
            data.serverMOTD = "";
            data.populationInfo = "";
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        GuiServersScreen.this.pinger.func_147224_a(data);
                    } catch (Exception e) {
                        data.pingToServer = -1L;
                        data.populationInfo = "";
                    }
                }
            }, "UKY Server Pinger").start();
        }
    }

    private int rowCount() {
        return (this.servers.countServers() + 1 + this.columns - 1) / this.columns;
    }

    private void clampScroll() {
        float content = rowCount() * (this.tileHeight + TILE_GAP) - TILE_GAP;
        float max = Math.max(0.0F, content - this.gridHeight);
        this.scrollTarget = Math.max(0.0F, Math.min(this.scrollTarget, max));
    }

    private int slotX(int slot) {
        return this.gridX + (slot % this.columns) * (this.tileWidth + TILE_GAP);
    }

    private float slotY(int slot) {
        return this.gridY + (slot / this.columns) * (this.tileHeight + TILE_GAP) - this.scroll;
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.scroll = Ease.approach(this.scroll, this.scrollTarget, 0.05F, this.delta);
        try {
            this.pinger.func_147223_a();
        } catch (Exception e) {
            // A failed ping tick is not worth a broken screen.
        }

        drawHeader();
        updateHover(mouseX, mouseY);

        Draw.beginClip(this.gridX, this.gridY, this.gridWidth, this.gridHeight);
        drawAddCard(0);
        for (int i = 0; i < this.servers.countServers(); i++) {
            drawServerCard(i, i + 1);
        }
        Draw.endClip();

        drawEdgeFade();
    }

    private void drawHeader() {
        this.fontRendererObj.drawString(I18n.format("multiplayer.title", new Object[0]),
                this.gridX, 26, Draw.withAlpha(Theme.text, this.fadeAlpha));
        Draw.gradientH(this.gridX, 40, this.gridX + this.gridWidth, 41,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        boolean over = isOverBack(this.lastMouseX, this.lastMouseY);
        int colour = Draw.withAlpha(over ? Theme.textHover : Theme.textDim, this.fadeAlpha);
        Icons.back(this.gridX + this.gridWidth - 10, 30, 9, colour);
        this.fontRendererObj.drawString(I18n.format("gui.back", new Object[0]),
                this.gridX + this.gridWidth - 46, 26, colour);

        boolean overRefresh = isOverRefresh(this.lastMouseX, this.lastMouseY);
        Icons.refresh(this.gridX + this.gridWidth - 78, 30, 11,
                Draw.withAlpha(overRefresh ? Theme.accent : Theme.textDim, this.fadeAlpha));

        // Direct connect lives here rather than on the plus tile, which now does what
        // a plus should: add a server to the list and keep it.
        boolean overDirect = isOverDirect(this.lastMouseX, this.lastMouseY);
        String direct = I18n.format("selectServer.direct", new Object[0]);
        this.fontRendererObj.drawString(direct,
                this.gridX + this.gridWidth - 96 - this.fontRendererObj.getStringWidth(direct), 26,
                Draw.withAlpha(overDirect ? Theme.textHover : Theme.textDim, this.fadeAlpha));
    }

    private boolean isOverBack(int mouseX, int mouseY) {
        int right = this.gridX + this.gridWidth;
        return mouseX >= right - 56 && mouseX <= right && mouseY >= 20 && mouseY <= 40;
    }

    private boolean isOverRefresh(int mouseX, int mouseY) {
        int right = this.gridX + this.gridWidth;
        return mouseX >= right - 90 && mouseX <= right - 66 && mouseY >= 20 && mouseY <= 40;
    }

    private boolean isOverDirect(int mouseX, int mouseY) {
        int right = this.gridX + this.gridWidth;
        int width = this.fontRendererObj.getStringWidth(
                I18n.format("selectServer.direct", new Object[0]));
        return mouseX >= right - 96 - width && mouseX <= right - 96
                && mouseY >= 20 && mouseY <= 40;
    }

    private void updateHover(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.hoveredCard = -2;
        this.hoveredAction = HIT_JOIN;

        boolean inGrid = mouseX >= this.gridX && mouseX < this.gridX + this.gridWidth
                && mouseY >= this.gridY && mouseY < this.gridY + this.gridHeight;
        if (inGrid) {
            int total = this.servers.countServers() + 1;
            for (int slot = 0; slot < total; slot++) {
                float y = slotY(slot);
                int x = slotX(slot);
                if (mouseX >= x && mouseX < x + this.tileWidth
                        && mouseY >= y && mouseY < y + this.tileHeight) {
                    this.hoveredCard = slot - 1;
                    if (this.hoveredCard >= 0) {
                        this.hoveredAction = hitTestActions(mouseX, mouseY, x, y);
                    }
                    break;
                }
            }
        }

        this.addHover = Ease.approach(this.addHover,
                this.hoveredCard == -1 ? 1.0F : 0.0F, 0.05F, this.delta);
        for (int i = 0; i < this.hoverAmount.length; i++) {
            this.hoverAmount[i] = Ease.approach(this.hoverAmount[i],
                    this.hoveredCard == i ? 1.0F : 0.0F, 0.05F, this.delta);
        }
    }

    private int hitTestActions(int mouseX, int mouseY, int cardX, float cardY) {
        float box = iconBox();
        float y0 = cardY + 4;
        float editX = cardX + this.tileWidth - box * 2 - 8;
        float deleteX = cardX + this.tileWidth - box - 4;
        if (mouseY >= y0 && mouseY <= y0 + box) {
            if (mouseX >= editX && mouseX <= editX + box) {
                return HIT_EDIT;
            }
            if (mouseX >= deleteX && mouseX <= deleteX + box) {
                return HIT_DELETE;
            }
        }
        return HIT_JOIN;
    }

    private float iconBox() {
        return Math.max(14.0F, this.tileWidth * 0.11F);
    }

    private void drawAddCard(int slot) {
        int x = slotX(slot);
        float y = slotY(slot);
        if (y + this.tileHeight < this.gridY || y > this.gridY + this.gridHeight) {
            return;
        }
        float hover = this.addHover;
        float alpha = this.fadeAlpha;

        Draw.rect(x, y, x + this.tileWidth, y + this.tileHeight,
                Draw.withAlpha(0x000000, (0.55F + hover * 0.25F) * alpha));
        Draw.border(x, y, x + this.tileWidth, y + this.tileHeight, 1.0F,
                Draw.fade(Draw.mix(Theme.separator, Theme.accent, hover), alpha));

        float cx = x + this.tileWidth / 2.0F;
        float cy = y + this.tileHeight / 2.0F - 6;
        int colour = Draw.withAlpha(Draw.mix(Theme.textDim, Theme.accent, hover), alpha);
        Icons.plus(cx, cy, 18 + hover * 3, 2.0F, colour);

        String label = I18n.format("selectServer.add", new Object[0]);
        int w = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label, (int) (cx - w / 2.0F), (int) (cy + 16), colour);
    }

    private void drawServerCard(int index, int slot) {
        int x = slotX(slot);
        float y = slotY(slot);
        if (y + this.tileHeight < this.gridY || y > this.gridY + this.gridHeight) {
            return;
        }

        ServerData data = this.servers.getServerData(index);
        float hover = this.hoverAmount[index];
        float alpha = this.fadeAlpha;
        float x2 = x + this.tileWidth;
        float y2 = y + this.tileHeight;

        Draw.rect(x, y, x2, y2, Draw.withAlpha(0x000000, 0.9F * alpha));

        ResourceLocation icon = iconFor(data);
        if (icon != null) {
            // The 64x64 icon blown up to fill the card: soft, but it is the only
            // picture a server offers and it makes entries recognisable at a glance.
            int tint = (int) ((0.45F + hover * 0.35F) * 255.0F);
            Draw.texture(icon, x, y, this.tileWidth, this.tileHeight,
                    Draw.withAlpha(tint << 16 | tint << 8 | tint, alpha));
        }

        Draw.gradientV(x, y2 - 34, x2, y2,
                Draw.withAlpha(0x000000, 0.0F), Draw.withAlpha(0x000000, 0.88F * alpha));

        String name = this.fontRendererObj.trimStringToWidth(data.serverName, this.tileWidth - 12);
        this.fontRendererObj.drawString(name, x + 6, (int) (y2 - 28),
                Draw.withAlpha(hover > 0.5F ? Theme.textHover : Theme.text, alpha));

        String motd = data.serverMOTD == null ? "" : data.serverMOTD.replace('\n', ' ');
        this.fontRendererObj.drawString(
                this.fontRendererObj.trimStringToWidth(motd, this.tileWidth - 12),
                x + 6, (int) (y2 - 18), Draw.withAlpha(Theme.textDim, 0.85F * alpha));

        drawStatus(data, x + 6, (int) (y2 - 9), alpha);

        if (hover > 0.02F) {
            drawCardActions(index, x, y, hover, alpha);
            Draw.border(x, y, x2, y2, 1.0F, Draw.withAlpha(Theme.accent, hover * alpha));
            Draw.glow(x, y, x2, y2, 5.0F, Draw.withAlpha(Theme.accent, 0.25F * hover * alpha), 4);
        }
    }

    /** Ping and population, with a dot coloured by how healthy the ping is. */
    private void drawStatus(ServerData data, int x, int y, float alpha) {
        String text;
        int dot;
        if (data.pingToServer == -2L) {
            text = "...";
            dot = Theme.textDim;
        } else if (data.pingToServer < 0L) {
            text = I18n.format("multiplayer.status.cannot_connect", new Object[0]);
            dot = Theme.danger;
        } else {
            text = data.pingToServer + " ms";
            if (data.populationInfo != null && !data.populationInfo.isEmpty()) {
                text = text + "   " + data.populationInfo.replaceAll("§.", "");
            }
            dot = data.pingToServer < 150L ? 0xFF6ECB63
                    : (data.pingToServer < 400L ? Theme.accent : Theme.danger);
        }
        Draw.rect(x, y + 1, x + 3, y + 4, Draw.withAlpha(dot, alpha));
        this.fontRendererObj.drawString(text, x + 7, y,
                Draw.withAlpha(Theme.textDim, 0.8F * alpha));
    }

    private void drawCardActions(int index, int x, float y, float hover, float alpha) {
        float box = iconBox();
        float a = hover * alpha;

        boolean joinHot = this.hoveredCard == index && this.hoveredAction == HIT_JOIN;
        float cx = x + this.tileWidth / 2.0F;
        float cy = y + this.tileHeight / 2.0F - 10;
        float ring = box * 1.15F;
        Draw.circle(cx, cy, ring, Draw.withAlpha(0x000000, (joinHot ? 0.6F : 0.4F) * a));
        Draw.ring(cx, cy, ring, 1.5F,
                Draw.withAlpha(joinHot ? Theme.accent : Theme.text, (joinHot ? 0.95F : 0.5F) * a));
        Icons.play(cx + 1.0F, cy, box * 0.8F,
                Draw.withAlpha(joinHot ? Theme.textHover : Theme.text, a));

        drawIconButton(x + this.tileWidth - box * 2 - 8, y + 4, box, a,
                this.hoveredAction == HIT_EDIT && this.hoveredCard == index, false);
        drawIconButton(x + this.tileWidth - box - 4, y + 4, box, a,
                this.hoveredAction == HIT_DELETE && this.hoveredCard == index, true);
    }

    private void drawIconButton(float x, float y, float box, float alpha, boolean hot,
                                boolean destructive) {
        int accent = destructive ? Theme.danger : Theme.accent;
        Draw.rect(x, y, x + box, y + box, Draw.withAlpha(0x000000, (hot ? 0.7F : 0.45F) * alpha));
        Draw.border(x, y, x + box, y + box, 1.0F,
                Draw.withAlpha(hot ? accent : Theme.separator, alpha));
        int colour = Draw.withAlpha(hot ? accent : Theme.textDim, alpha);
        if (destructive) {
            Icons.trash(x + box / 2.0F, y + box / 2.0F, box * 0.58F, colour);
        } else {
            Icons.pencil(x + box / 2.0F, y + box / 2.0F, box * 0.55F, colour);
        }
    }

    private void drawEdgeFade() {
        // Removed for the same reason as the world list's: a black gradient over a
        // black field is a smudge around the grid, not a hint.
    }

    /** Decodes and uploads a server's base64 icon once, then reuses it. */
    private ResourceLocation iconFor(ServerData data) {
        String encoded = data.getBase64EncodedIconData();
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        String key = data.serverIP + '|' + encoded.hashCode();
        ResourceLocation cached = this.icons.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] bytes = Base64.decodeBase64(encoded.getBytes("UTF-8"));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            ResourceLocation location = new ResourceLocation("uky",
                    "servericon/" + Math.abs(key.hashCode()));
            this.mc.getTextureManager().loadTexture(location, new DynamicTexture(image));
            this.icons.put(key, location);
            return location;
        } catch (Exception e) {
            UkyUI.LOGGER.warn("Could not decode the icon for " + data.serverIP, e);
            this.icons.put(key, null);
            return null;
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (Transitions.isBusy()) {
            return;
        }
        if (isOverBack(mouseX, mouseY)) {
            back();
            return;
        }
        if (isOverRefresh(mouseX, mouseY)) {
            pingAll();
            return;
        }
        if (isOverDirect(mouseX, mouseY)) {
            beginDirectConnect();
            return;
        }
        if (this.hoveredCard == -1) {
            beginAddServer();
            return;
        }
        if (this.hoveredCard >= 0 && this.hoveredCard < this.servers.countServers()) {
            ServerData data = this.servers.getServerData(this.hoveredCard);
            switch (this.hoveredAction) {
                case HIT_EDIT:
                    beginEditServer(this.hoveredCard, data);
                    return;
                case HIT_DELETE:
                    confirmDelete(this.hoveredCard);
                    return;
                default:
                    join(data);
                    return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scrollTarget -= (wheel > 0 ? 1 : -1) * (this.tileHeight + TILE_GAP) * 0.6F;
            clampScroll();
        }
    }

    private void join(ServerData data) {
        this.mc.displayGuiScreen(new GuiConnecting(this, this.mc, data));
    }

    // ---- adding, editing, connecting ----------------------------------------
    //
    // Vanilla's editor screens hand control back through the parent's
    // confirmClicked, which is a plain GuiScreen method — so this screen can be the
    // parent directly. What it cannot do is tell the three flows apart on its own,
    // hence the mode. Before this, the plus tile and the pencil both opened direct
    // connect and confirmClicked only knew about deletion, so a server added here was
    // never saved and editing one did nothing at all.

    private static final int MODE_NONE = 0;
    private static final int MODE_DELETE = 1;
    private static final int MODE_ADD = 2;
    private static final int MODE_EDIT = 3;
    private static final int MODE_DIRECT = 4;

    private int mode = MODE_NONE;
    private int editIndex = -1;
    private ServerData draft;

    private void beginAddServer() {
        this.mode = MODE_ADD;
        this.draft = new ServerData(
                I18n.format("selectServer.defaultName", new Object[0]), "");
        this.mc.displayGuiScreen(new GuiServerEditScreen(
                this, GuiServerEditScreen.Mode.ADD, this.draft));
    }

    /**
     * Edits a copy, not the live entry.
     *
     * The editor writes straight into the {@link ServerData} it is handed, so passing
     * the real one would apply the changes even when the player cancels.
     */
    private void beginEditServer(int index, ServerData existing) {
        this.mode = MODE_EDIT;
        this.editIndex = index;
        this.draft = new ServerData(existing.serverName, existing.serverIP);
        this.draft.func_152584_a(existing.func_152586_b());
        this.mc.displayGuiScreen(new GuiServerEditScreen(this, GuiServerEditScreen.Mode.EDIT, this.draft));
    }

    private void beginDirectConnect() {
        this.mode = MODE_DIRECT;
        this.draft = new ServerData(
                I18n.format("selectServer.defaultName", new Object[0]), "");
        this.mc.displayGuiScreen(new GuiServerEditScreen(this, GuiServerEditScreen.Mode.DIRECT, this.draft));
    }

    private void confirmDelete(int index) {
        this.mode = MODE_DELETE;
        this.pendingDelete = index;
        ServerData data = this.servers.getServerData(index);
        this.mc.displayGuiScreen(new GuiYesNo(this,
                I18n.format("selectServer.deleteQuestion", new Object[0]),
                "'" + data.serverName + "' "
                        + I18n.format("selectServer.deleteWarning", new Object[0]),
                I18n.format("selectServer.deleteButton", new Object[0]),
                I18n.format("gui.cancel", new Object[0]), 0));
    }

    @Override
    public void confirmClicked(boolean confirmed, int id) {
        int finished = this.mode;
        this.mode = MODE_NONE;

        if (!confirmed) {
            this.pendingDelete = -1;
            this.editIndex = -1;
            this.draft = null;
            this.mc.displayGuiScreen(this);
            return;
        }

        switch (finished) {
            case MODE_DELETE:
                if (this.pendingDelete >= 0 && this.pendingDelete < this.servers.countServers()) {
                    this.servers.removeServerData(this.pendingDelete);
                    this.servers.saveServerList();
                }
                break;
            case MODE_ADD:
                this.servers.addServerData(this.draft);
                this.servers.saveServerList();
                break;
            case MODE_EDIT:
                if (this.editIndex >= 0 && this.editIndex < this.servers.countServers()) {
                    ServerData live = this.servers.getServerData(this.editIndex);
                    live.serverName = this.draft.serverName;
                    live.serverIP = this.draft.serverIP;
                    live.func_152584_a(this.draft.func_152586_b());
                    this.servers.saveServerList();
                }
                break;
            case MODE_DIRECT:
                // Straight in, deliberately not saved: that is the whole point of a
                // one-off connection.
                join(this.draft);
                this.pendingDelete = -1;
                this.editIndex = -1;
                this.draft = null;
                return;
            default:
                break;
        }

        this.pendingDelete = -1;
        this.editIndex = -1;
        this.draft = null;
        this.mc.displayGuiScreen(this);
    }

    private void back() {
        Transitions.emerge();
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (this.pinger != null) {
            this.pinger.func_147226_b();
        }
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control on this screen is drawn, not a GuiButton.
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            back();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

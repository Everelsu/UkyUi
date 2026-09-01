package com.console.uky.client.gui.screen;

import net.minecraftforge.fml.client.FMLClientHandler;
import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Icons;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.world.TileCracks;
import com.console.uky.client.world.TileShatter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.ServerPinger;
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
 *
 * <p>Deleting one is the world picker's gesture too — hold the bin, or Shift-click it.
 * It used to be a vanilla {@code GuiYesNo}, which was wrong in three ways at once: it
 * threw the player out to a grey stone screen in the middle of a dark one, it asked a
 * question the pointer had already answered by being on the bin, and it meant the two
 * lists in this menu confirmed the same destructive action in two different ways. The
 * hold is the confirmation — there is nothing to agree with, so the only thing that can
 * mean "yes" is not letting go — and sliding off it is the cancel.
 */
public class GuiServersScreen extends MenuScreen implements GuiYesNoCallback {

    private static final int HIT_JOIN = 0;
    private static final int HIT_EDIT = 1;
    private static final int HIT_DELETE = 2;

    private static final int TILE_GAP = 10;
    private static final float TILE_ASPECT = 16.0F / 9.0F;

    private ServerList servers;
    private ServerPinger pinger;

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

    // ---- deleting -----------------------------------------------------------
    //
    // The world picker's mechanism, whole: the same two durations, the same sound
    // under the rise, the same fill inside the bin, and the same Shift-click past it.
    // Both are the destructive action on a card in a grid, and a player who has
    // learned one has learned the other.

    /** Seconds the bin must be held; the length of the sound that plays under it. */
    private static final float DELETE_HOLD_SECONDS = UkySounds.DELETE_HOLD_SECONDS;
    /** Seconds to run the fill back down after letting go. */
    private static final float DELETE_UNWIND_SECONDS = 0.14F;

    /** Card whose bin is being held, or -1. */
    private int holdCard = -1;
    private float holdProgress;
    /** Set by a Shift-click, cleared when the button comes up; see the world picker. */
    private boolean holdSuppressed;
    /** The break drawn on the card while the bin is held, and then broken along. */
    private TileCracks cracks;
    /** Plays after the entry is gone; holds nothing but pixels. */
    private TileShatter shatter;

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
            // Vanilla's multiplayer screen opens with this and it is not optional.
            // FML keeps two maps of per-server data — what the server answered about
            // its mod list, and whether it is blocked — and this call is the only
            // thing that creates them; the fields have no initialiser. Skipping it
            // left both null, and both are dereferenced on paths this screen uses:
            // bindServerListData reads one on every ping reply, so no ping ever
            // completed and every server eventually read as not answering, and
            // connectToServer reads the other, so joining one died on an NPE that
            // named this screen.
            FMLClientHandler.instance().setupServerList();

            this.servers = new ServerList(this.mc);
            this.servers.loadServerList();
            this.pinger = new ServerPinger();
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

    // ------------------------------------------------------------------ ping --

    private static final int PING_PENDING = 0;
    private static final int PING_ONLINE = 1;
    /** The address does not resolve: a typo, or a host that no longer exists. */
    private static final int PING_UNRESOLVED = 2;
    /** The address resolves but nothing answered: down, firewalled, wrong port. */
    private static final int PING_UNREACHABLE = 3;
    /** Answered nothing at all within {@link #PING_TIMEOUT_MS}. */
    private static final int PING_TIMED_OUT = 4;

    /**
     * How long an entry may sit unanswered before it is called a failure.
     *
     * {@code OldServerPinger} has no timeout of its own: a host that accepts the
     * connection and then says nothing leaves the entry pending for as long as the
     * screen is open. Ten seconds is well past a working server on a bad line.
     */
    private static final long PING_TIMEOUT_MS = 10000L;

    /**
     * One entry's ping, as this screen sees it.
     *
     * {@link ServerData} cannot answer the question on its own.
     * {@code OldServerPinger.func_147224_a} sets {@code pingToServer = -1} at the
     * <em>start</em> of a probe, not only when one fails, so a negative ping means
     * "pending or failed" and nothing can tell those apart — every server on the
     * screen read as unreachable for as long as it was being asked. It also cannot
     * distinguish a name that does not resolve from a host that does not answer,
     * which are different problems with different fixes.
     */
    private static final class Probe {
        /** Written by the ping thread, read every frame by the client thread. */
        volatile int state = PING_PENDING;
        /**
         * When the probe actually began, not when it was queued.
         *
         * There are five threads for any number of servers, so a long list waits its
         * turn. Timed from the queueing, an entry could burn its whole allowance
         * sitting in that queue and be called unanswered before anything had asked
         * it. Zero until a thread picks it up, which {@code stateOf} reads as "not
         * started yet" and therefore never as late.
         */
        volatile long startedAt;
    }

    private Probe[] probes = new Probe[0];

    /**
     * Five threads, shared and reused, the way vanilla's own server list does it.
     *
     * A thread per entry meant a list of forty servers spawned forty threads, and
     * every press of refresh spawned forty more — none of them bounded by anything.
     */
    private static final java.util.concurrent.ExecutorService PINGERS =
            java.util.concurrent.Executors.newFixedThreadPool(5,
                    new java.util.concurrent.ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable task) {
                            Thread thread = new Thread(task, "UKY Server Pinger");
                            // Daemon: a probe against a black-holed address must never
                            // be the reason the game will not close.
                            thread.setDaemon(true);
                            return thread;
                        }
                    });

    /** Kicks off a ping for every entry; results land asynchronously. */
    private void pingAll() {
        int count = this.servers.countServers();
        Probe[] fresh = new Probe[count];
        for (int i = 0; i < count; i++) {
            fresh[i] = ping(this.servers.getServerData(i));
        }
        this.probes = fresh;
    }

    /**
     * Queues one entry's probe and hands back the record its answer lands in.
     *
     * Split out from {@link #pingAll} so that a server added or edited on this screen
     * can be asked by itself. It used to have no way to be: {@link #probes} is indexed
     * by position and was only ever rebuilt wholesale, so an entry added after the
     * screen opened had no probe at all — {@code probeFor} returned null, which
     * {@link #stateOf} reads as "still being asked", and the card sat on "Asking the
     * server..." until the list was refreshed by hand.
     */
    private Probe ping(final ServerData data) {
        final Probe probe = new Probe();

        data.pingToServer = -2L;
        data.serverMOTD = "";
        data.populationInfo = "";

        PINGERS.execute(new Runnable() {
            @Override
            public void run() {
                probe.startedAt = System.currentTimeMillis();
                try {
                    GuiServersScreen.this.pinger.ping(data);
                } catch (java.net.UnknownHostException e) {
                    probe.state = PING_UNRESOLVED;
                    data.populationInfo = "";
                } catch (Exception e) {
                    probe.state = PING_UNREACHABLE;
                    data.populationInfo = "";
                }
            }
        });
        return probe;
    }

    /** Puts {@code probe} at {@code index}, growing the array when it is off the end. */
    private void setProbe(int index, Probe probe) {
        Probe[] snapshot = this.probes;
        if (index < 0) {
            return;
        }
        if (index < snapshot.length) {
            snapshot[index] = probe;
            return;
        }
        Probe[] grown = new Probe[index + 1];
        System.arraycopy(snapshot, 0, grown, 0, snapshot.length);
        grown[index] = probe;
        this.probes = grown;
    }

    /**
     * The probe for an entry, or null if the list has grown since the last ping.
     *
     * The array is replaced wholesale rather than resized, so a card drawn between a
     * server being added and the next ping has nothing to look at. That is a missing
     * status line for one frame, not an exception.
     */
    private Probe probeFor(int index) {
        Probe[] snapshot = this.probes;
        return index >= 0 && index < snapshot.length ? snapshot[index] : null;
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
            this.pinger.pingPendingNetworks();
        } catch (Exception e) {
            // A failed ping tick is not worth a broken screen.
        }

        drawHeader();
        updateHover(mouseX, mouseY);
        updateHold();

        Draw.beginClip(this.gridX, this.gridY, this.gridWidth, this.gridHeight);
        drawAddCard(0);
        for (int i = 0; i < this.servers.countServers(); i++) {
            drawServerCard(i, i + 1);
        }
        Draw.endClip();

        drawShatter();
        drawEdgeFade();
    }

    /**
     * The shards, drawn over everything and outside the grid's clip.
     *
     * Unclipped for the world picker's reason: a card bursting apart should be allowed
     * to throw pieces past the edge of the list rather than have them vanish at a
     * boundary the player cannot see.
     */
    private void drawShatter() {
        if (this.shatter == null) {
            return;
        }
        this.shatter.advance(this.delta);
        this.shatter.draw(this.fadeAlpha);
        if (this.shatter.isFinished()) {
            this.shatter = null;
        }
    }

    private void drawHeader() {
        this.fontRenderer.drawString(I18n.format("multiplayer.title", new Object[0]),
                this.gridX, 26, Draw.withAlpha(Theme.text, this.fadeAlpha));
        Draw.gradientH(this.gridX, 40, this.gridX + this.gridWidth, 41,
                Draw.withAlpha(Theme.accent, 0.5F * this.fadeAlpha),
                Draw.withAlpha(Theme.accent, 0.0F));

        boolean over = isOverBack(this.lastMouseX, this.lastMouseY);
        int colour = Draw.withAlpha(over ? Theme.textHover : Theme.textDim, this.fadeAlpha);
        Icons.back(this.gridX + this.gridWidth - 10, 30, 9, colour);
        this.fontRenderer.drawString(I18n.format("gui.back", new Object[0]),
                this.gridX + this.gridWidth - 46, 26, colour);

        boolean overRefresh = isOverRefresh(this.lastMouseX, this.lastMouseY);
        Icons.refresh(this.gridX + this.gridWidth - 78, 30, 11,
                Draw.withAlpha(overRefresh ? Theme.accent : Theme.textDim, this.fadeAlpha));

        // Direct connect lives here rather than on the plus tile, which now does what
        // a plus should: add a server to the list and keep it.
        boolean overDirect = isOverDirect(this.lastMouseX, this.lastMouseY);
        String direct = I18n.format("selectServer.direct", new Object[0]);
        this.fontRenderer.drawString(direct,
                this.gridX + this.gridWidth - 96 - this.fontRenderer.getStringWidth(direct), 26,
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
        int width = this.fontRenderer.getStringWidth(
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
        int w = this.fontRenderer.getStringWidth(label);
        this.fontRenderer.drawString(label, (int) (cx - w / 2.0F), (int) (cy + 16), colour);
    }

    private void drawServerCard(int index, int slot) {
        int x = slotX(slot);
        float y = slotY(slot);
        if (y + this.tileHeight < this.gridY || y > this.gridY + this.gridHeight) {
            return;
        }

        ServerData data = this.servers.getServerData(index);
        // The hover array is sized by the layout; a server added since then is drawn
        // unhighlighted rather than taking the screen down on an index.
        float hover = index < this.hoverAmount.length ? this.hoverAmount[index] : 0.0F;
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

        String name = fit(data.serverName, this.tileWidth - 12);
        this.fontRenderer.drawString(name, x + 6, (int) (y2 - 28),
                Draw.withAlpha(hover > 0.5F ? Theme.textHover : Theme.text, alpha));

        // Blank until the server has actually said something. The pinger writes its
        // own untranslated "Pinging..." into the MOTD the moment a probe starts, and
        // that is the status line's job to say, in the player's language.
        String motd = stateOf(data, index) == PING_ONLINE
                ? strip(data.serverMOTD).replace('\n', ' ')
                : "";
        this.fontRenderer.drawString(
                fit(motd, this.tileWidth - 12),
                x + 6, (int) (y2 - 18), Draw.withAlpha(Theme.textDim, 0.85F * alpha));

        drawStatus(data, index, x + 6, (int) (y2 - 9), alpha);

        if (this.holdCard == index && this.holdProgress > 0.0F && this.cracks != null) {
            this.cracks.draw(x, y, this.tileWidth, this.tileHeight,
                    Ease.clamp01(this.holdProgress), alpha);
            Draw.border(x, y, x2, y2, 1.0F,
                    Draw.withAlpha(Theme.danger,
                            (0.3F + 0.7F * Ease.clamp01(this.holdProgress)) * alpha));
        }

        if (hover > 0.02F) {
            drawCardActions(index, x, y, hover, alpha);
            Draw.border(x, y, x2, y2, 1.0F, Draw.withAlpha(Theme.accent, hover * alpha));
            Draw.glow(x, y, x2, y2, 5.0F, Draw.withAlpha(Theme.accent, 0.25F * hover * alpha), 4);
        }
    }

    /**
     * Ping and population, with a dot coloured by how healthy the ping is.
     *
     * Every failure says what actually went wrong, in the player's own language.
     * This used to ask for {@code multiplayer.status.cannot_connect}, which is a 1.8
     * key: 1.7.10 has no {@code multiplayer.status.*} at all, so what a player saw
     * on a server that was merely still being asked was the literal untranslated
     * string "multiplayer.status.cannot_connect" across the card.
     */
    private void drawStatus(ServerData data, int index, int x, int y, float alpha) {
        String text;
        int dot;
        int state = stateOf(data, index);

        switch (state) {
            case PING_ONLINE:
                text = data.pingToServer + " ms";
                String population = strip(data.populationInfo);
                if (!population.isEmpty()) {
                    text = text + "   " + population;
                }
                dot = data.pingToServer < 150L ? 0xFF6ECB63
                        : (data.pingToServer < 400L ? Theme.accent : Theme.danger);
                break;
            case PING_UNRESOLVED:
                text = I18n.format("uky.server.status.unresolved", new Object[0]);
                dot = Theme.danger;
                break;
            case PING_TIMED_OUT:
                text = I18n.format("uky.server.status.timedOut", new Object[0]);
                dot = Theme.danger;
                break;
            case PING_UNREACHABLE:
                text = I18n.format("uky.server.status.unreachable", new Object[0]);
                dot = Theme.danger;
                break;
            default:
                text = I18n.format("uky.server.status.pinging", new Object[0]);
                dot = Theme.textDim;
                break;
        }

        Draw.rect(x, y + 1, x + 3, y + 4, Draw.withAlpha(dot, alpha));
        this.fontRenderer.drawString(fit(text, this.tileWidth - 18), x + 7, y,
                Draw.withAlpha(Theme.textDim, 0.8F * alpha));
    }

    /**
     * What this entry's ping amounts to right now.
     *
     * A successful reply is recognised by the ping going non-negative rather than by
     * the worker reporting it: the reply is handled on the netty thread inside
     * {@code OldServerPinger}, which has no idea this screen exists.
     */
    private int stateOf(ServerData data, int index) {
        if (data.pingToServer >= 0L) {
            return PING_ONLINE;
        }
        Probe probe = probeFor(index);
        if (probe == null) {
            return PING_PENDING;
        }
        if (probe.state != PING_PENDING) {
            return probe.state;
        }
        long startedAt = probe.startedAt;
        return startedAt != 0L && System.currentTimeMillis() - startedAt > PING_TIMEOUT_MS
                ? PING_TIMED_OUT
                : PING_PENDING;
    }

    /** Colour codes are section signs in this font; the card wants the words only. */
    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
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

        float binX = x + this.tileWidth - box - 4;
        boolean overBin = this.hoveredAction == HIT_DELETE && this.hoveredCard == index;
        drawIconButton(binX, y + 4, box, a, overBin, true);
        if (overBin && isShiftKeyDown()) {
            // Full, because that is what Shift means: the next click is the one that
            // does it, and the button already looks the way it looks a moment before
            // a card breaks.
            drawBinFill(binX, y + 4, box, alpha, 1.0F);
        } else if (this.holdCard == index && this.holdProgress > 0.0F) {
            drawBinFill(binX, y + 4, box, alpha, Ease.clamp01(this.holdProgress));
        }
    }

    /**
     * Advances or unwinds the hold on the bin.
     *
     * Driven from the button being physically down rather than from a click: the
     * gesture is the confirmation, so the only thing that can go on meaning "yes" is
     * going on holding. Moving off the bin unwinds it, which makes sliding away the
     * natural cancel.
     */
    private void updateHold() {
        boolean down = org.lwjgl.input.Mouse.isButtonDown(0);
        if (!down) {
            this.holdSuppressed = false;
        }
        boolean holding = down
                && !this.holdSuppressed
                // Shift already means "now", and the click has already done it.
                && !isShiftKeyDown()
                && this.hoveredAction == HIT_DELETE
                && this.hoveredCard >= 0
                && this.hoveredCard < this.servers.countServers()
                && (this.holdCard == -1 || this.holdCard == this.hoveredCard);

        if (holding) {
            if (this.holdCard != this.hoveredCard || this.cracks == null) {
                this.cracks = new TileCracks();
            }
            this.holdCard = this.hoveredCard;
            // Started here rather than on the first frame of the press, so the sound
            // and the fill begin together. Repeated calls after the first do nothing.
            UkySounds.startDeleteHold();
            this.holdProgress += this.delta / DELETE_HOLD_SECONDS;
            if (this.holdProgress >= 1.0F) {
                int index = this.holdCard;
                this.holdCard = -1;
                this.holdProgress = 0.0F;
                // The rise has arrived; the break takes over from here.
                UkySounds.stopDeleteHold();
                destroyServer(index);
            }
            return;
        }

        if (this.holdProgress > 0.0F) {
            // Let go before the end: the rise is cut off, because it is a rise towards
            // something that is now not going to happen.
            UkySounds.stopDeleteHold();
        }

        this.holdProgress -= this.delta / DELETE_UNWIND_SECONDS;
        if (this.holdProgress <= 0.0F) {
            this.holdProgress = 0.0F;
            this.holdCard = -1;
            // Dropped with the press it belonged to; see the world picker.
            this.cracks = null;
        }
    }

    /**
     * Removes the entry at {@code index} and breaks its card apart where it stood.
     *
     * <p>The two parallel arrays are spliced rather than rebuilt, and that is the whole
     * difficulty here. {@link #probes} and {@link #hoverAmount} are indexed by position
     * in the server list, so removing an entry without removing theirs leaves every
     * card below it reading the one above's ping and the one above's hover — a list
     * where deleting the second server makes the third claim the second's latency.
     * Re-pinging everything would fix it too, and would throw away every answer already
     * received to solve a bookkeeping problem.
     */
    private void destroyServer(int index) {
        if (index < 0 || index >= this.servers.countServers()) {
            return;
        }
        UkySounds.play(UkySounds.DELETE_BREAK);

        ServerData data = this.servers.getServerData(index);
        ResourceLocation icon = iconFor(data);
        float x = slotX(index + 1);
        float y = slotY(index + 1);

        this.servers.removeServerData(index);
        this.servers.saveServerList();

        this.probes = removeAt(this.probes, index);
        this.hoverAmount = removeAt(this.hoverAmount, index);

        TileCracks pattern = this.cracks != null ? this.cracks : new TileCracks();
        this.cracks = null;
        this.shatter = new TileShatter(pattern, icon, x, y, this.tileWidth, this.tileHeight);
        clampScroll();
    }

    private static Probe[] removeAt(Probe[] array, int index) {
        if (index < 0 || index >= array.length) {
            return array;
        }
        Probe[] out = new Probe[array.length - 1];
        System.arraycopy(array, 0, out, 0, index);
        System.arraycopy(array, index + 1, out, index, array.length - index - 1);
        return out;
    }

    private static float[] removeAt(float[] array, int index) {
        if (index < 0 || index >= array.length) {
            return array;
        }
        float[] out = new float[array.length - 1];
        System.arraycopy(array, 0, out, 0, index);
        System.arraycopy(array, index + 1, out, index, array.length - index - 1);
        return out;
    }

    /**
     * The hold filling the bin button up; the world picker's, to the pixel.
     *
     * @param sweep how full, 0 to 1 — a Shift-click draws it at 1 without any hold
     */
    private void drawBinFill(float x, float y, float box, float alpha, float sweep) {
        Draw.rect(x, y + box * (1.0F - sweep), x + box, y + box,
                Draw.withAlpha(Theme.danger, 0.55F * alpha));
        // A brighter line riding the top of the fill, so the movement stays legible
        // over the last few percent where the fill itself barely grows.
        float edge = y + box * (1.0F - sweep);
        Draw.rect(x, edge, x + box, edge + 1.0F, Draw.withAlpha(Theme.danger, alpha));
        Draw.border(x, y, x + box, y + box, 1.0F, Draw.withAlpha(Theme.danger, alpha));

        Icons.trash(x + box / 2.0F, y + box / 2.0F, box * 0.58F,
                Draw.withAlpha(Draw.mix(Theme.danger, 0xFFFFFF, sweep), alpha));
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
        // containsKey, not a null check: a failed decode is cached as null on purpose,
        // and asking again would re-run ImageIO and re-log the warning every frame for
        // as long as the screen is open.
        if (this.icons.containsKey(key)) {
            return this.icons.get(key);
        }
        try {
            byte[] bytes = Base64.decodeBase64(encoded.getBytes("UTF-8"));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                this.icons.put(key, null);
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
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
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
                    // Nothing on click. The bin is a hold, and a dialogue on top of a
                    // hold would be two confirmations for one action.
                    //
                    // Shift stands in for the hold, as it does in the world picker.
                    // A server entry is a name and an address rather than months of
                    // play, so this is the list where the shortcut gets used — but it
                    // is still the same key doing the same thing, which is the point of
                    // it being the same key.
                    if (isShiftKeyDown()) {
                        this.holdSuppressed = true;
                        destroyServer(this.hoveredCard);
                    }
                    return;
                default:
                    join(data);
                    return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scrollTarget -= (wheel > 0 ? 1 : -1) * (this.tileHeight + TILE_GAP) * 0.6F;
            clampScroll();
        }
    }

    /**
     * Joins a server the way FML requires, rather than the way it looks like it works.
     *
     * Constructing {@code GuiConnecting} and displaying it is what vanilla appears to
     * do and is not enough. {@code FMLClientHandler.connectToServer} creates the
     * {@code playClientBlock} latch as well, and FML's handshake waits on that latch
     * from the Netty thread the moment login succeeds. Without it,
     * {@code waitForPlayClient} dereferences null: the handshake dies with an NPE that
     * never reaches the player, FML falls back to "Unexpected packet during modded
     * negotiation - assuming vanilla", and the client sits on the connecting screen
     * until it times out. Every modded server, every time.
     *
     * <p>It also does the blocked-server check that puts up {@code GuiAccessDenied},
     * which going around it silently skipped, and it displays the screen itself — so
     * there is nothing left for this method to do but hand over.
     */
    private void join(ServerData data) {
        FMLClientHandler.instance().connectToServer(this, data);
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
    private static final int MODE_ADD = 2;
    private static final int MODE_EDIT = 3;
    private static final int MODE_DIRECT = 4;

    private int mode = MODE_NONE;
    private int editIndex = -1;
    private ServerData draft;

    private void beginAddServer() {
        this.mode = MODE_ADD;
        this.draft = new ServerData(
                I18n.format("selectServer.defaultName", new Object[0]), "", false);
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
        this.draft = new ServerData(existing.serverName, existing.serverIP, false);
        this.draft.setResourceMode(existing.getResourceMode());
        this.mc.displayGuiScreen(new GuiServerEditScreen(this, GuiServerEditScreen.Mode.EDIT, this.draft));
    }

    private void beginDirectConnect() {
        this.mode = MODE_DIRECT;
        this.draft = new ServerData(
                I18n.format("selectServer.defaultName", new Object[0]), "", false);
        this.mc.displayGuiScreen(new GuiServerEditScreen(this, GuiServerEditScreen.Mode.DIRECT, this.draft));
    }

    @Override
    public void confirmClicked(boolean confirmed, int id) {
        int finished = this.mode;
        this.mode = MODE_NONE;

        if (!confirmed) {
            this.editIndex = -1;
            this.draft = null;
            this.mc.displayGuiScreen(this);
            return;
        }

        switch (finished) {
            case MODE_ADD:
                this.servers.addServerData(this.draft);
                this.servers.saveServerList();
                // Asked for on its own rather than by re-pinging the list: appending
                // keeps every answer already received, and the entry goes on the end,
                // so its index is the one past what the array currently holds.
                setProbe(this.servers.countServers() - 1, ping(this.draft));
                break;
            case MODE_EDIT:
                if (this.editIndex >= 0 && this.editIndex < this.servers.countServers()) {
                    ServerData live = this.servers.getServerData(this.editIndex);
                    live.serverName = this.draft.serverName;
                    live.serverIP = this.draft.serverIP;
                    live.setResourceMode(this.draft.getResourceMode());
                    this.servers.saveServerList();
                    // The address may be the thing that was edited, which makes the
                    // answer on the card an answer about somewhere else.
                    setProbe(this.editIndex, ping(live));
                }
                break;
            case MODE_DIRECT:
                // Straight in, deliberately not saved: that is the whole point of a
                // one-off connection.
                join(this.draft);
                this.editIndex = -1;
                this.draft = null;
                return;
            default:
                break;
        }

        this.editIndex = -1;
        this.draft = null;
        this.mc.displayGuiScreen(this);
    }

    private void back() {
        Transitions.emerge();
        this.mc.displayGuiScreen(this.parent);
    }

    /**
     * Leaving mid-hold must not leave the rise playing.
     *
     * The screen can go while the button is still down — Escape, or a server being
     * joined from underneath — and nothing else would ever stop the sound.
     */
    @Override
    public void onGuiClosed() {
        UkySounds.stopDeleteHold();
        super.onGuiClosed();
        if (this.pinger != null) {
            this.pinger.clearPendingNetworks();
        }
    }

    @Override
    protected void onAction(GuiButton button) {
        // Every control on this screen is drawn, not a GuiButton.
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
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

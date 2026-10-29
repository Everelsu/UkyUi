package com.console.uky.client.gui;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import com.console.uky.compat.FontRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The player list, sized to the players in it.
 *
 * Vanilla builds its box from {@code currentServerMaxPlayers}, not from how many
 * people are actually on: a server with room for sixty and one player online draws
 * sixty rows of empty grey across the middle of the screen, and the one name sits
 * alone at the top of it. That is the thing worth fixing here — the panel is as tall
 * as the names it has, and no taller.
 *
 * <p>Everything else follows the rest of this interface: dark panel, gold rail down
 * the left, a rule under the heading, ping as a number rather than as five bars whose
 * meaning has to be learned. Sorted by name, because the order the server sends is
 * join order and nobody is looking for the fourth person who joined.
 */
public final class PlayerListOverlay {

    /** Rows before a second column is opened rather than growing further down. */
    private static final int MAX_ROWS = 20;

    private static final int ROW_HEIGHT = 10;
    private static final int PADDING = 8;
    private static final int HEADER = 22;
    /** Air between a name and the ping that follows it. */
    private static final int PING_GAP = 14;
    private static final int COLUMN_GAP = 12;

    private PlayerListOverlay() {
    }

    /** Takes the Tab list's turn in the HUD; registered on NeoForge's bus. */
    public static final class Handler {
        @net.neoforged.bus.api.SubscribeEvent
        public void onLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
            if (!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.TAB_LIST)
                    || !com.console.uky.config.UiConfig.replacePlayerList) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            // Drawn only while the key is held, as vanilla's is.
            if (!mc.options.keyPlayerList.isDown() || !shouldDraw(mc)) {
                return;
            }
            event.setCanceled(true);
            event.getGuiGraphics().flush();
            draw(mc, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
        }
    }

    /**
     * Whether this should draw at all, matching vanilla's own condition: a single
     * player on their own world has nothing to look at.
     */
    public static boolean shouldDraw(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        return !mc.isLocalServer()
                || mc.player.connection.getListedOnlinePlayers().size() > 1
                || objective(mc) != null;
    }

    /** The objective a server puts in the list, or null when there is none. */
    private static Objective objective(Minecraft mc) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        return scoreboard == null ? null : scoreboard.getDisplayObjective(DisplaySlot.LIST);
    }

    @SuppressWarnings("unchecked")
    public static void draw(Minecraft mc, int screenWidth, int screenHeight) {
        ClientPacketListener connection = mc.player.connection;
        List<PlayerInfo> players =
                new ArrayList<PlayerInfo>(connection.getListedOnlinePlayers());
        if (players.isEmpty()) {
            return;
        }
        Collections.sort(players, BY_NAME);

        FontRenderer font = com.console.uky.compat.FontRenderer.get();
        Objective objective = objective(mc);

        int rows = Math.min(players.size(), MAX_ROWS);
        int columns = (players.size() + MAX_ROWS - 1) / MAX_ROWS;

        // One width for every column, from the widest entry anywhere: ragged columns
        // read as a broken layout rather than as a deliberate one.
        int columnWidth = 0;
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo info = players.get(i);
            int width = font.getStringWidth(nameOf(info)) + PING_GAP
                    + font.getStringWidth(pingText(info));
            String score = scoreText(mc, objective, info);
            if (!score.isEmpty()) {
                width += PING_GAP + font.getStringWidth(score);
            }
            columnWidth = Math.max(columnWidth, width);
        }
        columnWidth = Math.max(columnWidth, 96);

        String heading = heading(players.size(), -1);
        int bodyWidth = columnWidth * columns + COLUMN_GAP * (columns - 1);
        int panelWidth = Math.max(bodyWidth, font.getStringWidth(heading)) + PADDING * 2;
        int panelHeight = HEADER + rows * ROW_HEIGHT + PADDING;

        int x = (screenWidth - panelWidth) / 2;
        int y = Math.max(4, screenHeight / 8);

        drawPanel(x, y, panelWidth, panelHeight);

        font.drawString(heading, x + PADDING, y + PADDING,
                Draw.withAlpha(Theme.text, 0.95F));
        Draw.rect(x + PADDING, y + HEADER - 6, x + panelWidth - PADDING, y + HEADER - 5,
                Draw.withAlpha(Theme.separator, 0.7F));

        for (int i = 0; i < players.size(); i++) {
            int column = i / MAX_ROWS;
            int row = i % MAX_ROWS;
            int rowX = x + PADDING + column * (columnWidth + COLUMN_GAP);
            int rowY = y + HEADER + row * ROW_HEIGHT;
            drawRow(mc, font, players.get(i), objective, rowX, rowY, columnWidth);
        }
    }

    private static void drawPanel(int x, int y, int width, int height) {
        Draw.rect(x, y, x + width, y + height, Draw.withAlpha(Theme.background, 0.82F));
        Draw.border(x, y, x + width, y + height, 1.0F, Draw.withAlpha(Theme.text, 0.10F));
        // The same gold rail every panel in this interface carries.
        Draw.gradientV(x, y, x + 2, y + height,
                Draw.withAlpha(Theme.accent, 0.75F), Draw.withAlpha(Theme.accent, 0.0F));
    }

    private static void drawRow(Minecraft mc, FontRenderer font, PlayerInfo info,
                                Objective objective, int x, int y, int width) {
        boolean self = mc.player != null
                && info.getProfile().getId().equals(mc.player.getUUID());

        if (self) {
            // You, marked. In a list of twenty names finding your own row otherwise
            // means reading all of them.
            Draw.rect(x - 4, y - 1, x + width + 4, y + ROW_HEIGHT - 1,
                    Draw.withAlpha(Theme.accent, 0.10F));
        }

        font.drawString(nameOf(info), x, y,
                Draw.withAlpha(self ? Theme.textHover : Theme.text, 0.92F));

        String ping = pingText(info);
        int pingWidth = font.getStringWidth(ping);
        font.drawString(ping, x + width - pingWidth, y,
                Draw.withAlpha(pingColour(info.getLatency()), 0.9F));

        String score = scoreText(mc, objective, info);
        if (!score.isEmpty()) {
            int scoreWidth = font.getStringWidth(score);
            font.drawString(score, x + width - pingWidth - PING_GAP - scoreWidth, y,
                    Draw.withAlpha(Theme.textDim, 0.85F));
        }
    }

    /**
     * The ping as milliseconds.
     *
     * A negative response time is what the server sends for a player it has not timed
     * yet, which is not the same as a bad connection and should not be drawn as one.
     */
    private static String pingText(PlayerInfo info) {
        return info.getLatency() < 0 ? "--" : info.getLatency() + "ms";
    }

    private static int pingColour(int responseTime) {
        if (responseTime < 0) {
            return Theme.textDim;
        }
        if (responseTime < 150) {
            return 0x6ECB63;
        }
        return responseTime < 400 ? Theme.accent : Theme.danger;
    }

    /** The player's score in the list objective, or empty when the server sets none. */
    private static String scoreText(Minecraft mc, Objective objective, PlayerInfo info) {
        if (objective == null) {
            return "";
        }
        try {
            ReadOnlyScoreInfo score = mc.level.getScoreboard().getPlayerScoreInfo(
                    ScoreHolder.forNameOnly(info.getProfile().getName()), objective);
            return score == null ? "" : String.valueOf(score.value());
        } catch (Throwable t) {
            // A scoreboard is server-authored data; a malformed one is not worth the HUD.
            return "";
        }
    }

    /** The name the server shows in the list, which may be styled; the account name otherwise. */
    private static String nameOf(PlayerInfo info) {
        return info.getTabListDisplayName() != null
                ? info.getTabListDisplayName().getString() : info.getProfile().getName();
    }

    private static String heading(int online, int max) {
        return max > 0 ? online + " / " + max : String.valueOf(online);
    }

    private static final Comparator<PlayerInfo> BY_NAME = new Comparator<PlayerInfo>() {
        @Override
        public int compare(PlayerInfo a, PlayerInfo b) {
            return nameOf(a).compareToIgnoreCase(nameOf(b));
        }
    };
}

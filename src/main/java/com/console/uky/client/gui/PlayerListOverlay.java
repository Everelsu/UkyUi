package com.console.uky.client.gui;

import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

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

    /**
     * Whether this should draw at all, matching vanilla's own condition: a single
     * player on their own world has nothing to look at.
     */
    public static boolean shouldDraw(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        return !mc.isIntegratedServerRunning()
                || mc.thePlayer.sendQueue.playerInfoList.size() > 1
                || objective(mc) != null;
    }

    /** The objective a server puts in the list, or null when there is none. */
    private static ScoreObjective objective(Minecraft mc) {
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        return scoreboard == null ? null : scoreboard.func_96539_a(0);
    }

    @SuppressWarnings("unchecked")
    public static void draw(Minecraft mc, ScaledResolution resolution) {
        NetHandlerPlayClient connection = mc.thePlayer.sendQueue;
        List<GuiPlayerInfo> players =
                new ArrayList<GuiPlayerInfo>(connection.playerInfoList);
        if (players.isEmpty()) {
            return;
        }
        Collections.sort(players, BY_NAME);

        FontRenderer font = mc.fontRenderer;
        ScoreObjective objective = objective(mc);

        int rows = Math.min(players.size(), MAX_ROWS);
        int columns = (players.size() + MAX_ROWS - 1) / MAX_ROWS;

        // One width for every column, from the widest entry anywhere: ragged columns
        // read as a broken layout rather than as a deliberate one.
        int columnWidth = 0;
        for (int i = 0; i < players.size(); i++) {
            GuiPlayerInfo info = players.get(i);
            int width = font.getStringWidth(info.name) + PING_GAP
                    + font.getStringWidth(pingText(info));
            String score = scoreText(mc, objective, info);
            if (!score.isEmpty()) {
                width += PING_GAP + font.getStringWidth(score);
            }
            columnWidth = Math.max(columnWidth, width);
        }
        columnWidth = Math.max(columnWidth, 96);

        String heading = heading(players.size(), connection.currentServerMaxPlayers);
        int bodyWidth = columnWidth * columns + COLUMN_GAP * (columns - 1);
        int panelWidth = Math.max(bodyWidth, font.getStringWidth(heading)) + PADDING * 2;
        int panelHeight = HEADER + rows * ROW_HEIGHT + PADDING;

        int x = (resolution.getScaledWidth() - panelWidth) / 2;
        int y = Math.max(4, resolution.getScaledHeight() / 8);

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

    private static void drawRow(Minecraft mc, FontRenderer font, GuiPlayerInfo info,
                                ScoreObjective objective, int x, int y, int width) {
        boolean self = mc.thePlayer != null
                && info.name.equals(mc.thePlayer.getCommandSenderName());

        if (self) {
            // You, marked. In a list of twenty names finding your own row otherwise
            // means reading all of them.
            Draw.rect(x - 4, y - 1, x + width + 4, y + ROW_HEIGHT - 1,
                    Draw.withAlpha(Theme.accent, 0.10F));
        }

        font.drawString(info.name, x, y,
                Draw.withAlpha(self ? Theme.textHover : Theme.text, 0.92F));

        String ping = pingText(info);
        int pingWidth = font.getStringWidth(ping);
        font.drawString(ping, x + width - pingWidth, y,
                Draw.withAlpha(pingColour(info.responseTime), 0.9F));

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
    private static String pingText(GuiPlayerInfo info) {
        return info.responseTime < 0 ? "--" : info.responseTime + "ms";
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
    private static String scoreText(Minecraft mc, ScoreObjective objective, GuiPlayerInfo info) {
        if (objective == null) {
            return "";
        }
        try {
            Score score = mc.theWorld.getScoreboard().func_96529_a(info.name, objective);
            return String.valueOf(score.getScorePoints());
        } catch (Throwable t) {
            // A scoreboard is server-authored data; a malformed one is not worth the HUD.
            return "";
        }
    }

    private static String heading(int online, int max) {
        return max > 0 ? online + " / " + max : String.valueOf(online);
    }

    private static final Comparator<GuiPlayerInfo> BY_NAME = new Comparator<GuiPlayerInfo>() {
        @Override
        public int compare(GuiPlayerInfo a, GuiPlayerInfo b) {
            return a.name.compareToIgnoreCase(b.name);
        }
    };
}

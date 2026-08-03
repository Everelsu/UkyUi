package com.console.uky.client.gui.screen;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.MenuScreen;
import com.console.uky.client.gui.Transitions;
import com.console.uky.client.gui.widget.LinkButton;
import com.console.uky.client.gui.widget.MenuButton;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.client.sound.UkySounds;
import com.console.uky.client.splash.UkySplash;
import com.console.uky.config.UiConfig;
import cpw.mods.fml.client.GuiModList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Replacement title screen: artwork backdrop with parallax, a glowing wordmark
 * and a single column of buttons that stagger in on open.
 *
 * The logo image is optional. Packs that ship
 * {@code assets/uky/textures/gui/logo.png} get it above the wordmark; without it
 * the screen falls back to type only, which is why nothing here assumes the
 * texture exists.
 */
public class GuiTitleScreen extends MenuScreen implements GuiYesNoCallback {

    private static final ResourceLocation LOGO =
            new ResourceLocation("uky", "textures/gui/logo.png");

    private static final int ID_SINGLEPLAYER = 1;
    private static final int ID_MULTIPLAYER = 2;
    private static final int ID_OPTIONS = 3;
    private static final int ID_MODS = 4;
    private static final int ID_QUIT = 5;
    /** Link buttons get ids from here up, indexed into {@link #linkTargets}. */
    private static final int ID_LINK_BASE = 100;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 5;

    /** Logo edge length as a fraction of screen height, and its hard bounds. */
    private static final float LOGO_HEIGHT_RATIO = 0.22F;
    private static final int LOGO_MIN = 48;
    private static final int LOGO_MAX = 112;

    /** Smoothed pointer position, used for the background parallax. */
    private float parallaxX;
    private float parallaxY;

    /** Pointer from the previous frame; the backdrop draws before drawContent runs. */
    private int lastMouseX;
    private int lastMouseY;

    /** Resolved once per screen: whether the optional logo texture is present. */
    private boolean hasLogo;
    private int logoSize;
    private int logoCenterY;
    /** Baseline of the wordmark / tagline block, resolved in buildLayout. */
    private int captionY;

    /** Height reserved for the rendered black hole, 0 when disabled. */
    private int holeBlock;
    private float holeRadius;
    private float holeCenterX;
    private float holeCenterY;

    /** URLs for the link row, indexed by {@code button.id - ID_LINK_BASE}. */
    private final List<String> linkTargets = new ArrayList<String>();
    /** URL awaiting confirmation from the vanilla "open link?" dialog. */
    private String pendingLink;

    /** Bounds of the menu column, used to draw the rail behind it. */
    private int railX;
    private int railTop = Integer.MAX_VALUE;
    private int railBottom = Integer.MIN_VALUE;

    private final TitleIntro intro = new TitleIntro();
    /** Entrance fade combined with the intro's progress; drives all foreground art. */
    private float contentAlpha = 1.0F;

    public GuiTitleScreen() {
        super(null);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.intro.begin();
    }

    @Override
    protected void buildLayout() {
        this.linkTargets.clear();
        this.railTop = Integer.MAX_VALUE;
        this.railBottom = Integer.MIN_VALUE;
        if (isLeftLayout()) {
            buildLeftLayout();
        } else {
            buildCenteredLayout();
        }
    }

    private boolean isLeftLayout() {
        return "left".equals(UiConfig.layout);
    }

    private int menuEntryCount() {
        // options + quit are always present; the rest are opt-in
        return 2
                + (UiConfig.showSingleplayer ? 1 : 0)
                + (UiConfig.showMultiplayer ? 1 : 0)
                + (UiConfig.showModList ? 1 : 0);
    }

    /**
     * Wordmark across the top, menu down the left, black hole filling the right —
     * the composition the pack is going for.
     */
    private void buildLeftLayout() {
        int rowHeight = 18;
        int rowGap = 3;
        int count = menuEntryCount();
        int stackHeight = count * rowHeight + (count - 1) * rowGap;

        this.hasLogo = resourceExists(LOGO);
        this.logoSize = this.hasLogo ? clamp((int) (this.height * 0.26F), 48, 150) : 0;

        // Header sits in the top band; the caption hangs directly under it.
        int headerTop = (int) (this.height * 0.06F);
        this.logoCenterY = headerTop + this.logoSize / 2;
        this.captionY = headerTop + this.logoSize + 4;

        // The hole owns the right two-thirds and is deliberately large: it is the
        // artwork here, not a backdrop.
        this.holeBlock = (int) (Math.min(this.width, this.height) * 0.62F);
        this.holeRadius = this.holeBlock * 0.30F;
        this.holeCenterX = this.width * 0.62F;
        this.holeCenterY = this.height * 0.58F;

        int columnX = (int) Math.max(12, this.width * 0.07F);
        int columnWidth = (int) Math.max(110, this.width * 0.26F);
        int y = (int) (this.height * 0.52F - stackHeight / 2.0F);
        // Keep the column clear of the wordmark above and the footer below.
        y = Math.max(y, this.captionY + 30);
        y = Math.min(y, this.height - stackHeight - 46);

        int index = 0;
        if (UiConfig.showSingleplayer) {
            addRow(ID_SINGLEPLAYER, columnX, y, columnWidth, rowHeight, index++,
                    I18n.format("menu.singleplayer", new Object[0]), MenuButton.Style.GHOST);
            y += rowHeight + rowGap;
        }
        if (UiConfig.showMultiplayer) {
            addRow(ID_MULTIPLAYER, columnX, y, columnWidth, rowHeight, index++,
                    I18n.format("menu.multiplayer", new Object[0]), MenuButton.Style.GHOST);
            y += rowHeight + rowGap;
        }
        addRow(ID_OPTIONS, columnX, y, columnWidth, rowHeight, index++,
                I18n.format("menu.options", new Object[0]), MenuButton.Style.GHOST);
        y += rowHeight + rowGap;
        if (UiConfig.showModList) {
            addRow(ID_MODS, columnX, y, columnWidth, rowHeight, index++,
                    I18n.format("uky.menu.mods", new Object[0]), MenuButton.Style.GHOST);
            y += rowHeight + rowGap;
        }
        // Ghost like the rest of the column, just coloured as destructive — a
        // panelled Quit button was the only box in an otherwise text-only menu.
        addRow(ID_QUIT, columnX, y, columnWidth, rowHeight, index++,
                I18n.format("menu.quit", new Object[0]), MenuButton.Style.GHOST).destructive();
        y += rowHeight + rowGap;

        // Lined up with the "01" of the menu entries above rather than with the
        // buttons' own left edge, which is twelve units further out and made the
        // links look like they had slipped off the column. Three units back on top
        // of that, because a mark is drawn centred in its box and its ink starts
        // about that far in, while the "01" above starts at its own first pixel.
        int linkX = columnX + 9;
        // Wider than the menu column: the links sit below it, where there is room
        // out to the hole, and a two-word label does not fit in a column sized for
        // one-word menu entries.
        int linkWidth = Math.max(columnWidth, (int) (this.width * 0.36F));
        buildLinkRow(linkX, y + 12, index, linkWidth, false);
    }

    private MenuButton addRow(int id, int x, int y, int width, int height, int index,
                              String label, MenuButton.Style style) {
        MenuButton button = new MenuButton(id, x, y, width, height, label, style);
        button.align(MenuButton.Align.LEFT);
        button.ordinal(String.format("%02d", index + 1));
        button.entrance(0.12F + index * 0.055F);
        this.buttonList.add(button);
        this.railTop = Math.min(this.railTop, y);
        this.railBottom = Math.max(this.railBottom, y + height);
        this.railX = x;
        return button;
    }

    /**
     * Vertical rail behind the menu column, with a node tracking whichever entry
     * the pointer is on. It is what ties the separate labels into one control
     * surface instead of leaving them as loose text over the artwork.
     */
    private void drawMenuRail() {
        if (this.railBottom <= this.railTop) {
            return;
        }
        float alpha = this.contentAlpha;
        float top = this.railTop - 6.0F;
        float bottom = this.railBottom + 6.0F;

        // Fades out at both ends so the line has no hard stops.
        Draw.gradientV(this.railX, top, this.railX + 1.0F, (top + bottom) / 2.0F,
                Draw.withAlpha(Theme.textDim, 0.0F), Draw.withAlpha(Theme.textDim, 0.30F * alpha));
        Draw.gradientV(this.railX, (top + bottom) / 2.0F, this.railX + 1.0F, bottom,
                Draw.withAlpha(Theme.textDim, 0.30F * alpha), Draw.withAlpha(Theme.textDim, 0.0F));

        for (int i = 0; i < this.buttonList.size(); i++) {
            Object o = this.buttonList.get(i);
            // Links are on this list too, and they are not on the rail: hovering one
            // lit a node up beside the menu entries, pointing at a row that was not
            // there. The rail belongs to the column above them.
            if (!(o instanceof MenuButton) || o instanceof LinkButton) {
                continue;
            }
            MenuButton button = (MenuButton) o;
            float hover = button.hoverAmount();
            if (hover <= 0.02F) {
                continue;
            }
            float cy = button.yPosition + button.height / 2.0F;
            int accent = button.isDestructive() ? Theme.danger : Theme.accent;
            Draw.rect(this.railX - 1.0F, cy - 5.0F * hover, this.railX + 2.0F, cy + 5.0F * hover,
                    Draw.fade(accent, alpha));
            Draw.radialGlow(this.railX + 0.5F, cy, 10.0F * hover,
                    Draw.fade(accent, 0.35F * hover * alpha), Draw.withAlpha(accent, 0.0F));
        }
    }

    /** Height of one link tile, and so the size of its mark. */
    private static final int LINK_HEIGHT = 16;
    private static final int LINK_GAP = 5;

    /**
     * The link row, driven entirely by {@code mainmenu.links}.
     *
     * Each line of that setting is {@code Label|URL} with an optional third field
     * naming a mark — one of ours, or a PNG in the icon folder. What does not fit on
     * a line wraps to the next one rather than running off into the black hole, so a
     * pack can list as many as it likes without having to know how wide the window
     * will be.
     *
     * @param rowWidth space the row may use
     * @param centred  whether each line is centred on {@code x} instead of starting there
     */
    private void buildLinkRow(int x, int y, int index, int rowWidth, boolean centred) {
        String[] entries = UiConfig.links;
        if (entries == null || entries.length == 0) {
            return;
        }

        // Laid out into lines first, then placed: a centred line cannot be positioned
        // until it is known what else is on it.
        List<List<LinkButton>> lines = new ArrayList<List<LinkButton>>();
        List<LinkButton> line = new ArrayList<LinkButton>();
        List<Integer> lineWidths = new ArrayList<Integer>();
        int used = 0;

        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split("\\|");
            if (parts.length < 2) {
                continue; // malformed line in the config; skip rather than crash
            }
            String label = parts[0].trim();
            String url = parts[1].trim();
            String icon = parts.length > 2 ? parts[2].trim() : "";
            if (label.isEmpty() || url.isEmpty()) {
                continue;
            }

            int width = LinkButton.widthFor(this.fontRendererObj, label, LINK_HEIGHT);
            if (!line.isEmpty() && used + LINK_GAP + width > rowWidth) {
                lines.add(line);
                lineWidths.add(Integer.valueOf(used));
                line = new ArrayList<LinkButton>();
                used = 0;
            }

            LinkButton button = LinkButton.of(ID_LINK_BASE + this.linkTargets.size(),
                    0, 0, width, LINK_HEIGHT, label, url, icon);
            button.entrance(0.16F + (index + i) * 0.045F);
            line.add(button);
            this.linkTargets.add(url);
            used += (used == 0 ? 0 : LINK_GAP) + width;
        }
        if (!line.isEmpty()) {
            lines.add(line);
            lineWidths.add(Integer.valueOf(used));
        }

        for (int row = 0; row < lines.size(); row++) {
            int cursor = centred ? x - lineWidths.get(row).intValue() / 2 : x;
            int rowY = y + row * (LINK_HEIGHT + 4);
            List<LinkButton> placed = lines.get(row);
            for (int i = 0; i < placed.size(); i++) {
                LinkButton button = placed.get(i);
                button.xPosition = cursor;
                button.yPosition = rowY;
                this.buttonList.add(button);
                cursor += button.width + LINK_GAP;
            }
        }
    }

    private void buildCenteredLayout() {
        int centerX = this.width / 2;

        int count = menuEntryCount();
        int stackHeight = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP;

        int captionHeight = 0;
        boolean hasTitle = UiConfig.title != null && !UiConfig.title.isEmpty();
        boolean hasTagline = UiConfig.tagline != null && !UiConfig.tagline.isEmpty();
        if (hasTitle) {
            captionHeight += 26; // wordmark is drawn at 2x plus its rule
        }
        if (hasTagline) {
            captionHeight += 12;
        }

        int gap = 18;
        // Whatever is left once the caption, gap, stack and the corner text are
        // accounted for is all the header may use. Sizing the header first and
        // clamping the stack afterwards is what let the wordmark collide with the
        // top button on short windows.
        int headerBudget = this.height - (captionHeight + gap + stackHeight + 36);

        // The logo scales with the window: a fixed pixel size swallows the screen
        // at GUI scale 4 and disappears at scale 1.
        this.hasLogo = resourceExists(LOGO);
        this.logoSize = this.hasLogo
                ? clamp(Math.min((int) (this.height * LOGO_HEIGHT_RATIO), headerBudget), 0, LOGO_MAX)
                : 0;

        // With the black hole backdrop the hole takes the logo's place in the
        // vertical flow, so the wordmark and buttons sit under it rather than on it.
        this.holeBlock = isBlackHoleBackground()
                ? clamp(Math.min((int) (this.height * 0.34F), headerBudget), 0, 190)
                : 0;
        int headerBlock = Math.max(this.logoSize, this.holeBlock);

        // Lay the whole block out as one unit, then centre it: header, caption, gap, stack.
        int blockHeight = headerBlock + captionHeight + gap + stackHeight;
        int blockTop = Math.max(12, (this.height - blockHeight) / 2 - 8);

        this.logoCenterY = blockTop + headerBlock / 2;
        this.captionY = blockTop + headerBlock + 4;
        this.holeCenterX = this.width * 0.5F;
        this.holeCenterY = blockTop + headerBlock * 0.5F;
        this.holeRadius = this.holeBlock * 0.21F;

        int y = blockTop + headerBlock + captionHeight + gap;
        // Guarantee the stack clears the footer even on very short windows.
        y = Math.min(y, this.height - stackHeight - 24);

        int index = 0;
        if (UiConfig.showSingleplayer) {
            add(ID_SINGLEPLAYER, centerX, y, index++, I18n.format("menu.singleplayer", new Object[0]),
                    MenuButton.Style.PRIMARY);
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        if (UiConfig.showMultiplayer) {
            add(ID_MULTIPLAYER, centerX, y, index++, I18n.format("menu.multiplayer", new Object[0]),
                    MenuButton.Style.NORMAL);
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        add(ID_OPTIONS, centerX, y, index++, I18n.format("menu.options", new Object[0]),
                MenuButton.Style.NORMAL);
        y += BUTTON_HEIGHT + BUTTON_GAP;
        if (UiConfig.showModList) {
            // 1.7.10's vanilla menu hardcodes "Mods", so this one needs our own key.
            add(ID_MODS, centerX, y, index++, I18n.format("uky.menu.mods", new Object[0]),
                    MenuButton.Style.NORMAL);
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        add(ID_QUIT, centerX, y, index, I18n.format("menu.quit", new Object[0]), MenuButton.Style.DANGER);
        y += BUTTON_HEIGHT;

        buildLinkRow(centerX, y + 10, index + 1, (int) (this.width * 0.7F), true);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private boolean resourceExists(ResourceLocation location) {
        try {
            this.mc.getResourceManager().getResource(location);
            return true;
        } catch (java.io.IOException e) {
            // 1.7.10 has no "does this exist" query; a failed read is the check.
            return false;
        }
    }

    private void add(int id, int centerX, int y, int index, String label, MenuButton.Style style) {
        MenuButton button = new MenuButton(id, centerX - BUTTON_WIDTH / 2, y,
                BUTTON_WIDTH, BUTTON_HEIGHT, label, style);
        // 55 ms between entries reads as one flowing cascade rather than separate pops.
        button.entrance(0.12F + index * 0.055F);
        this.buttonList.add(button);
    }

    // --------------------------------------------------------------- drawing --

    @Override
    protected float blackHoleCenterY() {
        return this.holeCenterY;
    }

    @Override
    protected float blackHoleCenterX() {
        return this.holeCenterX;
    }

    @Override
    protected float blackHoleRadius() {
        return this.holeRadius;
    }

    @Override
    protected float blackHoleIntensity() {
        return 1.0F;
    }

    @Override
    protected float widgetFade() {
        // The menu goes with the light as the hole closes in.
        return this.fadeAlpha * this.intro.uiAlpha() * (1.0F - Transitions.blackout());
    }

    /** How long the black carried over from the loading screen takes to lift. */
    private static final float ARRIVAL_SECONDS = 0.9F;
    /** Only the first title screen of the session arrives from the splash. */
    private static boolean arrived;

    /**
     * Continues the loading screen's black instead of starting a new fade under it.
     *
     * The splash releases the GL context and then a variable number of frames belong
     * to nobody — Minecraft finishing start-up, the resource reload, the main menu
     * being built and swapped for this one. Timing the lift from the instant the
     * splash ended rather than from when this screen opened covers that gap whatever
     * its length, so the hand-off is one continuous darkness rather than a fade that
     * happens to begin after an unpredictable flash.
     */
    @Override
    protected void drawOverlay() {
        super.drawOverlay();

        if (arrived) {
            return;
        }
        long splashEnd = UkySplash.finishedAtNanos();
        if (splashEnd == 0L) {
            // No splash ran; there is nothing to continue from.
            arrived = true;
            return;
        }
        float since = (System.nanoTime() - splashEnd) / 1_000_000_000.0F;
        if (since >= ARRIVAL_SECONDS) {
            arrived = true;
            return;
        }
        // inCubic: holds the dark a moment, then clears quickly, so the menu does not
        // sit behind a grey film for the whole of it.
        float veil = 1.0F - Ease.inCubic(since / ARRIVAL_SECONDS);
        Draw.rect(0, 0, this.width, this.height, Draw.withAlpha(0x000000, veil));
    }

    /** Falls into the hole, then opens {@code target} on the other side. */
    private void diveTo(final net.minecraft.client.gui.GuiScreen target) {
        Transitions.dive(new Runnable() {
            @Override
            public void run() {
                GuiTitleScreen.this.mc.displayGuiScreen(target);
            }
        });
    }

    @Override
    protected void drawBackgroundArt() {
        // Pointer parallax on top of the base drift: the artwork leans away from
        // the cursor by up to a few percent of the hidden overscan.
        float targetX = (this.mouseXNormalized() - 0.5F) * 2.0F;
        float targetY = (this.mouseYNormalized() - 0.5F) * 2.0F;
        this.parallaxX = Ease.approach(this.parallaxX, targetX, 0.25F, this.delta);
        this.parallaxY = Ease.approach(this.parallaxY, targetY, 0.25F, this.delta);

        if (isBlackHoleBackground()) {
            this.intro.update(this.delta);
            blackHole.update(this.delta);
            // This screen overrides the whole backdrop, so it has to aim the camera
            // itself — without this the hole kept whatever angle the settings screen
            // left it at and never swung back.
            blackHole.lookFrom(blackHolePose());
            // Shared camera, so arriving from another screen glides into place.
            advanceCamera(blackHoleCenterX(), blackHoleCenterY(), blackHoleRadius());
            // Pointer parallax rides on top without disturbing that easing.
            blackHole.render(
                    cameraX + this.parallaxX * 6.0F,
                    cameraY + this.parallaxY * 4.0F,
                    cameraRadius * Transitions.holeScale(),
                    this.fadeAlpha * this.intro.holeIntensity(),
                    this.intro.warp());
            return;
        }

        this.intro.update(this.delta);
        if ("solid".equals(UiConfig.background)) {
            return;
        }

        float zoom = 1.10F;
        float panX = this.parallaxX * 0.35F;
        float panY = this.parallaxY * 0.25F;
        if (UiConfig.backgroundDrift) {
            zoom += (float) Math.sin(this.elapsed * 0.06F) * 0.03F;
            panX += (float) Math.sin(this.elapsed * 0.041F) * 0.25F;
            panY += (float) Math.cos(this.elapsed * 0.029F) * 0.18F;
        }
        Draw.textureCover(BACKGROUND, 0, 0, this.width, this.height,
                BACKGROUND_W, BACKGROUND_H, zoom,
                clampPan(panX), clampPan(panY),
                Draw.withAlpha(0xFFFFFF, this.fadeAlpha));
    }

    private static float clampPan(float v) {
        return v < -1.0F ? -1.0F : (v > 1.0F ? 1.0F : v);
    }

    private float mouseXNormalized() {
        return this.width == 0 ? 0.5F : lastMouseX / (float) this.width;
    }

    private float mouseYNormalized() {
        return this.height == 0 ? 0.5F : lastMouseY / (float) this.height;
    }

    @Override
    protected void drawContent(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        this.intro.render(this.width, this.height,
                blackHoleCenterX(), blackHoleCenterY(), Math.max(8.0F, blackHoleRadius()));

        if (!this.intro.isActive()) {
            startMenuMusic();
        }

        // Everything below the backdrop shares one opacity: the screen's entrance
        // fade multiplied by however far the intro has got.
        this.contentAlpha = this.fadeAlpha * this.intro.uiAlpha();
        if (this.contentAlpha > 0.01F) {
            drawLogo();
            if (isLeftLayout()) {
                drawMenuRail();
            }
            drawCorners();
        }
    }

    private void startMenuMusic() {
        if (UiConfig.menuMusic) {
            UkySounds.startMusic((float) UiConfig.menuMusicVolume);
        }
    }

    private void drawLogo() {
        int centerX = this.width / 2;

        // The mark eases up into place slightly ahead of the buttons.
        float rise = (1.0F - Ease.outCubic(this.elapsed / 0.7F)) * 12.0F;
        float breathe = (float) Math.sin(this.elapsed * 0.9F) * 0.015F + 1.0F;

        float cy = this.logoCenterY - rise;
        float lx = centerX - this.logoSize / 2.0F;
        float ly = cy - this.logoSize / 2.0F;

        if (this.hasLogo) {
            Draw.radialGlow(centerX, cy, this.logoSize * 0.95F,
                    Draw.withAlpha(Theme.accent, 0.16F * this.contentAlpha),
                    Draw.withAlpha(Theme.accent, 0.0F));

            GL11.glPushMatrix();
            GL11.glTranslatef(centerX, cy, 0.0F);
            GL11.glScalef(breathe, breathe, 1.0F);
            GL11.glTranslatef(-centerX, -cy, 0.0F);
            Draw.texture(LOGO, lx, ly, this.logoSize, this.logoSize,
                    Draw.withAlpha(0xFFFFFF, this.contentAlpha));
            GL11.glPopMatrix();
        }

        int textY = (int) (this.captionY - rise);

        if (UiConfig.title != null && !UiConfig.title.isEmpty()) {
            drawWordmark(UiConfig.title, centerX, textY);
            textY += 22;
        }
        if (UiConfig.tagline != null && !UiConfig.tagline.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, UiConfig.tagline, centerX, textY,
                    Draw.withAlpha(Theme.textDim, 0.9F * this.contentAlpha));
        }
    }

    /** Title text at 2x with a letter-spaced, shadowed look. */
    private void drawWordmark(String text, int centerX, int y) {
        final float scale = 2.0F;
        final int tracking = 2; // extra pixels between glyphs, in unscaled units

        int totalWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            totalWidth += this.fontRendererObj.getCharWidth(text.charAt(i)) + tracking;
        }
        totalWidth -= tracking;

        // Without a logo image the wordmark carries the whole header, so give it
        // the halo the logo would otherwise have provided.
        if (!this.hasLogo) {
            Draw.radialGlow(centerX, y + 8 * scale / 2.0F, totalWidth * scale * 0.7F,
                    Draw.withAlpha(Theme.accent, 0.14F * this.contentAlpha),
                    Draw.withAlpha(Theme.accent, 0.0F));
        }

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);

        float cursor = centerX / scale - totalWidth / 2.0F;
        float baseY = y / scale;
        int shadow = Draw.withAlpha(0x000000, 0.6F * this.contentAlpha);
        int color = Draw.withAlpha(Theme.text, this.contentAlpha);

        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            this.fontRendererObj.drawString(ch, (int) (cursor + 1), (int) (baseY + 1), shadow, false);
            this.fontRendererObj.drawString(ch, (int) cursor, (int) baseY, color, false);
            cursor += this.fontRendererObj.getCharWidth(text.charAt(i)) + tracking;
        }
        GL11.glPopMatrix();

        // Accent rule under the wordmark, growing outwards from the centre.
        float grow = Ease.outCubic((this.elapsed - 0.25F) / 0.8F);
        float ruleHalf = totalWidth * scale * 0.5F * grow;
        float ruleY = y + 8 * scale + 4;
        Draw.gradientH(centerX - ruleHalf, ruleY, centerX, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.0F), Draw.withAlpha(Theme.accent, 0.75F * this.contentAlpha));
        Draw.gradientH(centerX, ruleY, centerX + ruleHalf, ruleY + 1,
                Draw.withAlpha(Theme.accent, 0.75F * this.contentAlpha), Draw.withAlpha(Theme.accent, 0.0F));
    }

    private void drawCorners() {
        int dim = Draw.withAlpha(Theme.textDim, 0.75F * this.contentAlpha);

        String left = "Minecraft 1.7.10";
        this.fontRendererObj.drawString(left, 6, this.height - 20, dim);
        if (UiConfig.footer != null && !UiConfig.footer.isEmpty()) {
            this.fontRendererObj.drawString(UiConfig.footer, 6, this.height - 11, dim);
        }

        if (UiConfig.footerRight != null && !UiConfig.footerRight.isEmpty()) {
            String right = UiConfig.footerRight.replace("%version%", UkyUI.VERSION);
            this.fontRendererObj.drawString(right,
                    this.width - this.fontRendererObj.getStringWidth(right) - 6,
                    this.height - 11, dim);
        }
    }

    // ----------------------------------------------------------------- input --

    @Override
    protected void onAction(GuiButton button) {
        switch (button.id) {
            case ID_SINGLEPLAYER:
                diveTo(new GuiWorldsScreen(this));
                break;
            case ID_MULTIPLAYER:
                diveTo(new GuiServersScreen(this));
                break;
            case ID_OPTIONS:
                switchTo(new GuiSettingsScreen(GuiTitleScreen.this, mc.gameSettings));
                break;
            case ID_MODS:
                switchTo(new GuiModsScreen(GuiTitleScreen.this));
                break;
            case ID_QUIT:
                this.mc.shutdown();
                break;
            default:
                openLink(button.id);
                break;
        }
    }

    private void openLink(int buttonId) {
        int index = buttonId - ID_LINK_BASE;
        if (index < 0 || index >= this.linkTargets.size()) {
            return;
        }
        this.pendingLink = this.linkTargets.get(index);
        // Vanilla's confirmation screen: never send a player to a URL unprompted.
        this.mc.displayGuiScreen(new GuiConfirmOpenLink(this, this.pendingLink, 0, false));
    }

    @Override
    public void confirmClicked(boolean confirmed, int id) {
        if (confirmed && this.pendingLink != null) {
            try {
                Class<?> desktop = Class.forName("java.awt.Desktop");
                Object instance = desktop.getMethod("getDesktop").invoke(null);
                desktop.getMethod("browse", URI.class).invoke(instance, new URI(this.pendingLink));
            } catch (Throwable t) {
                // Headless JREs and some Linux setups have no Desktop; not worth a crash.
                UkyUI.LOGGER.warn("Could not open link " + this.pendingLink, t);
            }
        }
        this.pendingLink = null;
        this.mc.displayGuiScreen(this);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Escape must not close the title screen into a black void.
        this.intro.skip();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.intro.isActive()) {
            // While the intro runs the buttons are hidden, so a click can only mean
            // "get on with it".
            this.intro.skip();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

package com.console.uky.client.gui.screen;

import com.console.uky.client.death.DeathScene;
import com.console.uky.client.death.DeathTheme;
import com.console.uky.client.render.Draw;
import com.console.uky.client.render.Ease;
import com.console.uky.client.render.Theme;
import com.console.uky.config.UiConfig;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Dying, as a blackout rather than a menu.
 *
 * The picture cuts out, the tape starts failing — scanlines, a rolling band,
 * dropouts, tracking tears — and a line resolves out of the noise. There is
 * nothing to click: after the scene has played, any key or any click comes back,
 * and if the player does neither the game comes back on its own.
 *
 * <p>Extends {@link GuiGameOver} and not {@code MenuScreen}, for one specific
 * reason: when the respawn packet comes back, the game closes the death screen by
 * testing {@code currentScreen instanceof GuiGameOver}. Anything else stays up
 * forever over a living player. The state that has to outlive the screen — the
 * clock, the cause, the sounds — is in {@link DeathScene}, because the screen is
 * torn down and rebuilt while the respawn is in flight.
 */
public class GuiDeathScreen extends GuiGameOver {

    /** Seconds the picture takes to go out completely. */
    private static final float BLACKOUT = 0.30F;
    /** When the title starts resolving out of the noise. */
    private static final float TITLE_AT = 0.30F;
    /** When the line under it arrives. */
    private static final float MESSAGE_AT = 1.25F;
    /** How long the way out takes: the tape tears, then the picture snaps off. */
    private static final float EXIT = 0.42F;

    /** Glyphs the title is corrupted with while it resolves. */
    private static final char[] STATIC_GLYPHS =
            "#%&@$/\\|<>*+=~^:;!?01".toCharArray();

    /**
     * How long one glitched frame is held, in seconds.
     *
     * The corruption and the shake used to be redrawn every frame, which at a
     * hundred frames a second is not a glitch — it is a strobe, and the headline
     * under it could not be read at all. Tape damage happens at tape speed: a
     * broken frame sits there long enough to be seen before the next one replaces
     * it. Holding each sample for a tenth of a second is that, and it also stops
     * the effect getting worse the faster the machine is.
     */
    private static final float SAMPLE_SECONDS = 0.10F;

    /**
     * 1.12.2's {@code GuiGameOver} takes the death message; 1.7.10's took nothing.
     * Null on purpose — the base class only ever reads it from the drawing and input
     * methods this screen replaces outright, and the line shown here comes from
     * {@link com.console.uky.client.death.DeathTracker} instead, which is what makes
     * the scene match the way the player died rather than merely report it.
     */
    public GuiDeathScreen() {
        super(null);
    }

    private final Random noise = new Random();

    private long lastFrameNanos = System.nanoTime();
    private float delta;
    /** Runs on its own clock so the beat keeps its phase across a frame rate change. */
    private float beatPhase;
    private float pulse;
    /** Seconds left of the current tracking error, or 0. */
    private float glitch;
    private float nextGlitchIn = 1.6F;

    // ---- held glitch sample; see SAMPLE_SECONDS ----
    private float sampleAge = SAMPLE_SECONDS;
    private String sampledTitle = "";
    private String sampledFrom = "";
    private float sampledShake;

    private boolean hardcore;
    /** Seconds into the exit animation, or -1 while the scene is simply running. */
    private float leaving = -1.0F;
    /**
     * Hardcore only: the player asked to stay and watch rather than leave.
     *
     * The respawn packet is what does it — on a hardcore server the respawn handler
     * puts the player straight into spectator instead of reviving them — so this is
     * the same request the non-hardcore path makes, and only the wording differs.
     */
    private boolean spectating;

    // ------------------------------------------------------------- lifecycle --

    @Override
    public void initGui() {
        // Opening this screen is the first the client knows about the death — but it
        // may also be the second, because respawning re-opens it while the server
        // answers. begin() is a no-op in that case, which is what keeps the scene
        // from restarting under the player.
        DeathScene.begin();

        this.buttonList.clear();
        this.hardcore = this.mc.world != null
                && this.mc.world.getWorldInfo().isHardcoreModeEnabled();

        // Rebuilt while the respawn is in flight: the picture has already switched
        // off, so this instance opens on the last frame of that rather than playing
        // the whole scene again for the few frames the server takes to answer.
        if (DeathScene.isReturning()) {
            this.leaving = EXIT;
        }
    }

    /** Seconds of scene before anything will listen, or come back on its own. */
    private static float sceneSeconds() {
        return (float) UiConfig.deathSceneSeconds;
    }

    /** True once the player is allowed to leave, and has not already asked. */
    private boolean canLeave() {
        return this.leaving < 0.0F && !DeathScene.isReturning()
                && DeathScene.elapsed() >= sceneSeconds();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Comes back on its own if the player does nothing.
     *
     * On the tick rather than the frame: a minimised window stops drawing, and a
     * respawn that only happens while someone is looking is not a respawn.
     *
     * <p>Also replaces {@code GuiGameOver}'s twenty-tick button lockout, which has
     * nothing left to unlock.
     */
    @Override
    public void updateScreen() {
        if (!UiConfig.deathAutoRespawn || this.hardcore || !canLeave()) {
            return;
        }
        if (DeathScene.elapsed() >= (float) UiConfig.deathAutoRespawnSeconds) {
            this.leaving = 0.0F;
        }
    }

    // ---------------------------------------------------------------- drawing --

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        this.delta = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        float t = DeathScene.elapsed();
        DeathTheme theme = DeathScene.theme();
        advanceBeat(t);
        advanceGlitch();

        float intensity = (float) UiConfig.deathIntensity;
        float exit = advanceExit();

        drawBlackout(theme, t);
        drawTape(theme, t, intensity, exit);
        drawTitle(theme, t, intensity, exit);
        drawMessage(t, exit);
        drawPrompt(theme, t, exit);

        // Over everything, including the tape: this is the eye closing, not a filter.
        Draw.vignette(this.width, this.height,
                (0.72F + 0.24F * this.pulse) * (1.0F - exit * 0.85F), 0xFF000000);

        drawExit(exit);
    }

    /**
     * The picture switching off: the frame collapses to a bright line and goes.
     *
     * Ends fully black rather than on the world, and deliberately so — the respawn
     * packet has not come back yet at that point, and this screen is rebuilt once
     * more while it is in flight. Holding black through that is what makes the
     * handover invisible; see {@link #initGui()}.
     */
    private void drawExit(float exit) {
        float collapse = Ease.clamp01((exit - 0.5F) / 0.5F);
        if (collapse <= 0.0F) {
            return;
        }
        float half = this.height * 0.5F * collapse;
        Draw.rect(0, 0, this.width, half, 0xFF000000);
        Draw.rect(0, this.height - half, this.width, this.height, 0xFF000000);

        float cy = this.height * 0.5F;
        float thickness = Math.max(0.5F, this.height * (1.0F - collapse) * 0.06F);
        float bright = collapse < 0.85F ? collapse : (1.0F - (collapse - 0.85F) / 0.15F);
        Draw.rect(0, cy - thickness, this.width, cy + thickness,
                Draw.withAlpha(0xFFFFFF, 0.85F * bright));
    }

    /**
     * Drives the way out, and fires the respawn at the end of it.
     *
     * The screen used to vanish on the frame the key was pressed, which after three
     * seconds of a scene closing in read as the game being yanked back rather than
     * as coming round. So the input starts this instead: the tape tears once, the
     * text is pulled apart, the black lifts, and only then does the packet go.
     *
     * @return 0 while the scene is running, rising to 1 as it is torn down
     */
    private float advanceExit() {
        if (this.leaving < 0.0F) {
            return 0.0F;
        }
        this.leaving += this.delta;
        if (this.leaving >= EXIT) {
            commitLeave();
            return 1.0F;
        }
        return Ease.clamp01(this.leaving / EXIT);
    }

    /**
     * Advances the heartbeat.
     *
     * Integrated rather than evaluated from {@code t}, because the interval itself
     * lengthens as the scene goes on: sampling {@code sin(t / period(t))} would slide
     * the phase around every time the period moved and the beat would stumble.
     */
    private void advanceBeat(float t) {
        float period = 0.62F + 0.55F * Ease.outCubic(t / 9.0F);
        this.beatPhase += this.delta / period;

        // Two knocks, the second softer and close behind — a single thump reads as a
        // drum, and this is supposed to be a chest.
        float first = thump(this.beatPhase % 1.0F);
        float second = thump(((this.beatPhase - 0.17F) % 1.0F + 1.0F) % 1.0F) * 0.55F;
        this.pulse = Math.min(1.0F, Math.max(first, second) * DeathScene.theme().heartbeat);
    }

    /** A short, sharp rise and fall — zero for most of the cycle. */
    private static float thump(float phase) {
        if (phase > 0.22F) {
            return 0.0F;
        }
        float shaped = (float) Math.sin(phase / 0.22F * Math.PI);
        return shaped * shaped * shaped;
    }

    /** Occasional tape tracking errors, on their own irregular schedule. */
    private void advanceGlitch() {
        if (this.glitch > 0.0F) {
            this.glitch -= this.delta;
            return;
        }
        this.nextGlitchIn -= this.delta;
        if (this.nextGlitchIn <= 0.0F) {
            this.glitch = 0.06F + this.noise.nextFloat() * 0.10F;
            this.nextGlitchIn = 1.2F + this.noise.nextFloat() * 2.6F;
        }
    }

    /**
     * The picture going out.
     *
     * Not a slow scrim over the world: this is a blackout, and the last frame of the
     * world is gone within a third of a second. What is left is the cause's colour
     * bleeding through the black, which is the only thing that distinguishes drowning
     * from burning once the picture has stopped.
     */
    private void drawBlackout(DeathTheme theme, float t) {
        float closed = Ease.outCubic(t / BLACKOUT);
        Draw.rect(0, 0, this.width, this.height,
                Draw.withAlpha(Theme.background, 0.88F + 0.12F * closed));

        // A cast of the cause's colour over the black, strongest at the hit and never
        // quite gone. These colours are dark by design — this is meant to read as
        // black that is slightly the wrong colour, not as a coloured screen.
        float wash = 0.55F + 0.35F * Ease.clamp01(1.0F - t / 0.8F);
        Draw.gradientV(0, 0, this.width, this.height,
                Draw.withAlpha(theme.bleed, wash),
                Draw.withAlpha(theme.bleed, wash * 0.35F));

        // The hit itself, in the bright half of the palette so it actually lands.
        float flash = Ease.clamp01(1.0F - t / 0.35F);
        if (flash > 0.01F) {
            Draw.rect(0, 0, this.width, this.height,
                    Draw.withAlpha(theme.chromaRed, 0.32F * flash * flash));
        }
    }

    /** Scanlines, a rolling band, dropouts and grain — scaled by the cause. */
    private void drawTape(DeathTheme theme, float t, float intensity, float exit) {
        // The tape gives up as the screen does: the last thing it does is tear.
        float amount = intensity * theme.vhs * (1.0F + exit * 2.5F);
        if (amount <= 0.01F) {
            return;
        }

        Draw.scanlines(this.width, this.height, 3.0F,
                Draw.withAlpha(0x000000, 0.22F * amount));

        // One bright band travelling down the picture, wrapping with a pause so it
        // does not read as a metronome.
        float travel = (t * 0.22F) % 1.45F;
        float bandY = (travel - 0.2F) * this.height;
        float bandH = 16.0F + 12.0F * this.pulse;
        if (bandY > -bandH && bandY < this.height) {
            int edge = Draw.withAlpha(theme.accent, 0.0F);
            int core = Draw.withAlpha(theme.accent, 0.075F * amount);
            Draw.gradientV(0, bandY, this.width, bandY + bandH * 0.5F, edge, core);
            Draw.gradientV(0, bandY + bandH * 0.5F, this.width, bandY + bandH, core, edge);
            Draw.rect(0, bandY + bandH * 0.5F, this.width, bandY + bandH * 0.5F + 0.6F,
                    Draw.withAlpha(0xFFFFFF, 0.08F * amount));
        }

        // Dropouts: a few short horizontal tears per frame, and a burst of them
        // during a tracking error.
        int tears = (int) (3 * amount) + (this.glitch > 0.0F ? 9 : 0);
        for (int i = 0; i < tears; i++) {
            float y = this.noise.nextInt(Math.max(1, this.height));
            float w = 20.0F + this.noise.nextFloat() * this.width * 0.45F;
            float x = this.noise.nextFloat() * (this.width - w);
            float h = 1.0F + this.noise.nextInt(2);
            Draw.rect(x, y, x + w, y + h,
                    Draw.withAlpha(0xFFFFFF, (0.04F + this.noise.nextFloat() * 0.08F) * amount));
        }

        // Grain. Budgeted off the screen area so it does not thin out on a big window.
        int specks = (int) ((this.width * this.height) / 1100 * amount);
        for (int i = 0; i < specks; i++) {
            int gx = this.noise.nextInt(Math.max(1, this.width));
            int gy = this.noise.nextInt(Math.max(1, this.height));
            Draw.rect(gx, gy, gx + 1, gy + 1,
                    Draw.withAlpha(0xFFFFFF, 0.03F + this.noise.nextFloat() * 0.07F));
        }
    }

    /**
     * The headline, resolving out of the noise and split into its colour channels.
     *
     * There is no post-processing pass to run a real aberration through — this is a
     * GUI drawn straight over the frame — so the split is done the honest way, by
     * drawing the same string three times with the outer two offset and tinted. The
     * corruption works the same way: while the title is arriving, and for a moment
     * during a tracking error, some of its characters are replaced with junk. It is
     * measured from the clean string so the line stays put while its letters change.
     */
    private void drawTitle(DeathTheme theme, float t, float intensity, float exit) {
        float in = Ease.outCubic((t - TITLE_AT) / 0.85F) * (1.0F - exit);
        if (in <= 0.01F) {
            return;
        }

        String title = this.hardcore
                ? I18n.format("deathScreen.title.hardcore")
                : I18n.format("deathScreen.title");

        // Corruption is for the arrival and for the way out, and it is over well
        // before the title has finished resolving — this is the one thing on the
        // screen that has to be read, so the damage clears off it first and stays
        // off. A tracking error touches it, lightly.
        float settling = Ease.clamp01((0.55F - in) / 0.55F);
        float corruption = settling * 0.30F
                + (this.glitch > 0.0F ? 0.12F : 0.0F)
                + exit * 0.85F;
        float shake = (this.pulse * 0.6F + (this.glitch > 0.0F ? 1.4F : 0.0F))
                * theme.jitter * intensity;
        String shown = sample(title, corruption * Math.max(0.4F, intensity), shake);

        float scale = 2.55F - 0.45F * in;
        float centerX = this.width / 2.0F;
        float centerY = this.height * 0.36F;

        // The split settles to under a pixel. It used to sit at three and jump on
        // every heartbeat, which left the headline permanently doubled.
        float split = (2.4F * (1.0F - in) + 0.5F + this.pulse * 0.8F + exit * 14.0F)
                * theme.jitter * Math.max(0.35F, intensity);

        GL11.glPushMatrix();
        GL11.glTranslatef(centerX + this.sampledShake, centerY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        int half = this.fontRenderer.getStringWidth(title) / 2;

        // The channel copies are faint: they are a fringe on the white, not two more
        // headlines competing with it.
        this.fontRenderer.drawString(shown, (int) (-half - split), 0,
                Draw.withAlpha(theme.chromaRed, 0.45F * in));
        this.fontRenderer.drawString(shown, (int) (-half + split), 0,
                Draw.withAlpha(theme.chromaCyan, 0.45F * in));
        this.fontRenderer.drawString(shown, -half, 0, Draw.withAlpha(Theme.text, in));
        GL11.glPopMatrix();

        // The rule under it opens outwards from the centre, the same gesture every
        // heading in this interface uses.
        float ruleWidth = half * scale * 1.15F * Ease.outCubic((t - TITLE_AT) / 1.2F);
        float ruleY = centerY + 12.0F * scale;
        int lit = Draw.withAlpha(theme.accent, 0.85F * in);
        int gone = Draw.withAlpha(theme.accent, 0.0F);
        Draw.gradientH(centerX - ruleWidth, ruleY, centerX, ruleY + 1.0F, gone, lit);
        Draw.gradientH(centerX, ruleY, centerX + ruleWidth, ruleY + 1.0F, lit, gone);
    }

    /**
     * The corrupted headline and the shake that goes with it, held for a while.
     *
     * Both are re-rolled on {@link #SAMPLE_SECONDS} rather than per frame, which is
     * what turns this from a strobe into damage — see the note on that constant.
     *
     * @return the string to draw; {@link #sampledShake} holds the matching offset
     */
    private String sample(String text, float amount, float shake) {
        if (amount <= 0.01F) {
            this.sampledShake = 0.0F;
            this.sampledTitle = text;
            this.sampledFrom = text;
            return text;
        }

        this.sampleAge += this.delta;
        // A language change or the hardcore/normal swap has to take effect at once,
        // whatever the sample clock says.
        if (this.sampleAge < SAMPLE_SECONDS && text.equals(this.sampledFrom)) {
            return this.sampledTitle;
        }
        this.sampleAge = 0.0F;
        this.sampledFrom = text;
        this.sampledShake = shake > 0.01F ? (this.noise.nextFloat() - 0.5F) * shake : 0.0F;

        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] != ' ' && this.noise.nextFloat() < amount) {
                chars[i] = STATIC_GLYPHS[this.noise.nextInt(STATIC_GLYPHS.length)];
            }
        }
        this.sampledTitle = new String(chars);
        return this.sampledTitle;
    }

    /** The themed line, and the hardcore warning under it. */
    private void drawMessage(float t, float exit) {
        // Goes first and quickly: by the time the picture tears there should be
        // nothing left to read.
        float in = Ease.outCubic((t - MESSAGE_AT) / 0.9F) * Ease.clamp01(1.0F - exit * 2.5F);
        if (in <= 0.01F) {
            return;
        }
        int centerX = this.width / 2;
        int y = (int) (this.height * 0.36F + 40.0F);

        String message = DeathScene.message();
        if (!message.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, message, centerX, y,
                    Draw.withAlpha(Theme.text, 0.82F * in));
        }
        if (this.hardcore) {
            // Our own key. 1.7.10's deathScreen.hardcoreInfo does not exist in 1.12.2
            // — the whole line went when the death screen was rebuilt — so borrowing a
            // vanilla one is not an option here.
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("uky.death.hardcore"), centerX, y + 16,
                    Draw.withAlpha(DeathScene.theme().chromaRed, 0.85F * in));
        }
    }

    /**
     * The way out, once there is one.
     *
     * Breathes rather than blinks — a hard blink on a screen this dark reads as a
     * fault, and the whole scene is already pretending to be one.
     */
    private void drawPrompt(DeathTheme theme, float t, float exit) {
        float in = Ease.outCubic((t - sceneSeconds()) / 0.8F)
                * Ease.clamp01(1.0F - exit * 4.0F);
        if (in <= 0.01F) {
            return;
        }
        float breathe = 0.62F + 0.30F * (float) Math.sin(t * 2.0F);
        int y = (int) (this.height * 0.78F);
        String prompt = I18n.format(this.hardcore ? "uky.death.promptLeave" : "uky.death.prompt");
        this.drawCenteredString(this.fontRenderer, prompt, this.width / 2, y,
                Draw.withAlpha(Theme.textDim, breathe * in));

        // Hardcore has no way back into the body, but it does have a way to stay and
        // look. Offered as a second line rather than folded into the first: leaving is
        // still what any key does, and the one that does something else has to name
        // itself. Not breathing with the line above — two things pulsing out of phase
        // at the bottom of the screen reads as a fault rather than as a prompt.
        if (this.hardcore) {
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("uky.death.promptSpectate"), this.width / 2, y + 12,
                    Draw.withAlpha(Theme.textDim, 0.55F * in));
        }
    }

    // ------------------------------------------------------------------ input --

    /**
     * Any key, once the scene has played. Nothing before that — including Escape,
     * which vanilla also swallows here.
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (!canLeave()) {
            return;
        }
        // Enter is the one key that means something other than "leave", and only in
        // hardcore. Everything else keeps the contract the prompt states.
        if (this.hardcore && (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN
                || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER)) {
            this.spectating = true;
        }
        this.leaving = 0.0F;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        if (canLeave()) {
            this.leaving = 0.0F;
        }
    }

    /**
     * Back to the world, or out of it.
     *
     * Hardcore has nothing to respawn into, so there the way on is out — unless the
     * player asked to spectate, which takes the same respawn path. The server is what
     * makes the difference: its respawn handler drops a hardcore player into spectator
     * rather than reviving them, so the client asks for the same thing either way.
     * The scene itself is not ended here: the player is still dead until the server
     * says otherwise, and {@code DeathTracker} is what notices that it has.
     */
    private void commitLeave() {
        if (DeathScene.isReturning()) {
            return;
        }
        DeathScene.requestReturn();

        if ((!this.hardcore || this.spectating) && this.mc.player != null) {
            this.mc.player.respawnPlayer();
            this.mc.displayGuiScreen(null);
            return;
        }

        DeathScene.end();
        if (this.mc.world != null) {
            this.mc.world.sendQuittingDisconnectingPacket();
        }
        this.mc.loadWorld((WorldClient) null);
        this.mc.displayGuiScreen(null);
    }
}

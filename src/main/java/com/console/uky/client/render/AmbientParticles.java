package com.console.uky.client.render;

import com.console.uky.config.UiConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Screen-space dust and ember field drawn behind the menu content.
 *
 * Deliberately not tied to Minecraft's particle system: menus run while no world
 * exists, and these need to be driven by wall-clock delta time so they keep
 * moving smoothly regardless of tick rate.
 */
public final class AmbientParticles {

    private static final int DUST = 0;
    private static final int EMBER = 1;
    private static final int MOTE = 2;

    private static final class P {
        float x, y, vx, vy, size, life, maxLife, phase;
        int type;
        int color;
    }

    private final List<P> particles = new ArrayList<P>();
    private final Random random = new Random();

    private int width;
    private int height;

    /** Rebuilds the field for a new screen size, seeding it as if already running. */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        particles.clear();
        if (!UiConfig.ambientParticles) {
            return;
        }
        int budget = UiConfig.ambientParticleBudget;
        for (int i = 0; i < budget; i++) {
            P p = spawn(true);
            // stagger initial lifetimes so nothing pops in all at once
            p.life = random.nextFloat() * p.maxLife;
            particles.add(p);
        }
    }

    public void update(float deltaSeconds) {
        if (!UiConfig.ambientParticles || width == 0) {
            return;
        }
        Iterator<P> it = particles.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.phase += deltaSeconds;
            // gentle horizontal sway makes the drift feel like air, not gravity
            float sway = (float) Math.sin(p.phase * 0.8F + p.x * 0.01F) * 3.0F;
            p.x += (p.vx + sway * 0.2F) * deltaSeconds;
            p.y += p.vy * deltaSeconds;
            p.life -= deltaSeconds;

            if (p.type == EMBER) {
                p.vy -= 2.0F * deltaSeconds; // embers accelerate upward as they cool
            }

            boolean offScreen = p.x < -40 || p.x > width + 40 || p.y < -40 || p.y > height + 40;
            if (p.life <= 0.0F || offScreen) {
                it.remove();
            }
        }

        int budget = UiConfig.ambientParticleBudget;
        while (particles.size() < budget) {
            particles.add(spawn(false));
        }
    }

    public void render(float alpha) {
        if (!UiConfig.ambientParticles || alpha <= 0.01F) {
            return;
        }
        for (P p : particles) {
            float lifeRatio = p.life / p.maxLife;
            // fade in over the first 15% of life and out over the last 25%
            float fade = Math.min(1.0F, Math.min((1.0F - lifeRatio) / 0.15F, lifeRatio / 0.25F));
            float a = fade * alpha;
            if (a <= 0.01F) {
                continue;
            }
            switch (p.type) {
                case EMBER: {
                    Draw.radialGlow(p.x, p.y, p.size * 3.0F, Draw.fade(p.color, a * 0.35F), Draw.withAlpha(p.color, 0.0F));
                    Draw.circle(p.x, p.y, p.size, Draw.fade(p.color, a));
                    break;
                }
                case MOTE: {
                    // long thin streak along the direction of travel
                    float len = p.size * 4.0F;
                    float ang = (float) Math.atan2(p.vy, p.vx);
                    Draw.line(p.x, p.y,
                            p.x + (float) Math.cos(ang) * len,
                            p.y + (float) Math.sin(ang) * len,
                            1.0F, Draw.fade(p.color, a * 0.8F));
                    break;
                }
                default: {
                    Draw.rect(p.x, p.y, p.x + p.size, p.y + p.size, Draw.fade(p.color, a));
                }
            }
        }
    }

    private P spawn(boolean anywhere) {
        P p = new P();
        int roll = random.nextInt(100);
        if (roll < 68) {
            p.type = DUST;
        } else if (roll < 92) {
            p.type = MOTE;
        } else {
            p.type = EMBER;
        }

        p.x = random.nextFloat() * width;
        // fresh particles enter from below the screen; the initial seed fills it
        p.y = anywhere ? random.nextFloat() * height : height + random.nextFloat() * 30.0F;
        p.phase = random.nextFloat() * 10.0F;

        switch (p.type) {
            case EMBER: {
                p.vx = (random.nextFloat() - 0.5F) * 8.0F;
                p.vy = -(6.0F + random.nextFloat() * 10.0F);
                p.size = 0.7F + random.nextFloat() * 0.9F;
                p.maxLife = 6.0F + random.nextFloat() * 5.0F;
                p.color = Draw.mix(Theme.accent, Theme.accentAlt, random.nextFloat());
                break;
            }
            case MOTE: {
                p.vx = (random.nextFloat() - 0.5F) * 6.0F;
                p.vy = -(2.0F + random.nextFloat() * 4.0F);
                p.size = 0.8F + random.nextFloat() * 1.2F;
                p.maxLife = 8.0F + random.nextFloat() * 8.0F;
                p.color = Theme.textDim;
                break;
            }
            default: {
                p.vx = (random.nextFloat() - 0.5F) * 4.0F;
                p.vy = -(1.0F + random.nextFloat() * 3.0F);
                p.size = 1.0F + random.nextFloat() * 1.0F;
                p.maxLife = 10.0F + random.nextFloat() * 10.0F;
                p.color = Theme.text;
            }
        }
        p.life = p.maxLife;
        return p;
    }
}

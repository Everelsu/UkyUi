package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;

import java.lang.reflect.Field;

/**
 * How far the block under the crosshair has been mined.
 *
 * Vanilla shows this as the cracks spreading over the block, which is a poor readout:
 * it says "some" and "nearly", and on a modded block that takes forty seconds with the
 * wrong tool the difference between those two is most of a minute. The number itself
 * exists — the client integrates it every tick to know when to tell the server the
 * block is gone — it simply is not shown anywhere. This is where the tooltip gets it.
 *
 * <p>The two fields are private, so they are looked up by name, and by <em>both</em>
 * names: a development workspace has the readable one and a production client has the
 * searge one, and which of the two applies is not something this code can know. That
 * is exactly what FML's {@code ReflectionHelper} takes a list for.
 */
public final class WailaMining {

    private static Field damage;
    private static Field hitting;
    private static boolean searched;

    private WailaMining() {
    }

    /**
     * How far the current block has got, 0 to 1.
     *
     * @return 0 whenever nothing is being mined, which is also what a client that has
     *         moved these fields gets — the bar simply never appears
     */
    public static float progress() {
        Minecraft mc = Minecraft.getMinecraft();
        PlayerControllerMP controller = mc.playerController;
        if (controller == null || mc.thePlayer == null) {
            return 0.0F;
        }
        search();
        if (damage == null) {
            return 0.0F;
        }
        try {
            if (!hitting.getBoolean(controller)) {
                return 0.0F;
            }
            float value = damage.getFloat(controller);
            return value <= 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        } catch (Throwable t) {
            // Reading it failed once and will fail every frame; stop asking.
            damage = null;
            UkyUI.LOGGER.warn("Could not read the block breaking progress", t);
            return 0.0F;
        }
    }

    private static void search() {
        if (searched) {
            return;
        }
        searched = true;
        try {
            damage = ReflectionHelper.findField(PlayerControllerMP.class,
                    "curBlockDamageMP", "field_78770_f");
            hitting = ReflectionHelper.findField(PlayerControllerMP.class,
                    "isHittingBlock", "field_78778_j");
        } catch (Throwable t) {
            damage = null;
            UkyUI.LOGGER.info("Block breaking progress is not where we expected it;"
                    + " the tooltip will do without the bar");
        }
    }
}

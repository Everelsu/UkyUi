package com.console.uky.client.mods;

import com.console.uky.config.UiConfig;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The blocks Waila is asked to keep quiet about.
 *
 * <p>Waila's tooltip earns its place over a machine and earns nothing over the ground.
 * A player crossing a hillside spends the whole crossing with a box in the corner of
 * the screen naming the stone they are standing on, and it is never once read — but the
 * same box over a tank, a cable or a multiblock is half of why the mod is installed. So
 * the answer is neither "on" nor "off": it is a list, kept in the config, of what is not
 * worth a tooltip. See {@code UiConfig.wailaHiddenBlocks}.
 *
 * <h2>Our own ray trace, not Waila's</h2>
 *
 * <p>What is being looked at could be read out of Waila's {@code RayTracing} singleton,
 * which is what Waila itself uses, and deliberately is not: that would mean naming
 * another mod's class to answer a question the game already answers.
 * {@code Minecraft.objectMouseOver} is the same hit, from the same trace the game does
 * every tick before any of this runs, and it costs no reflection and no mod to be
 * present. The one difference is that Waila can be configured to see through liquids
 * where the vanilla trace does not — a look at water through which a hidden block is
 * visible, which resolves to the block rather than the water, and so shows the tooltip
 * Waila was going to show anyway. Erring towards showing is the right way for a filter
 * to be wrong.
 */
public final class WailaHidden {

    /**
     * The parsed list, and the array it was parsed from.
     *
     * Kept side by side rather than behind a flag: {@code UiConfig.reload} assigns a
     * new array, so an identity comparison is all it takes to notice that the config
     * has been edited in game and the set needs building again. No listener, nothing to
     * remember to call, and nothing that can be forgotten when a new caller turns up.
     */
    private static Set<String> hidden = Collections.emptySet();
    private static String[] parsedFrom;

    private WailaHidden() {
    }

    /**
     * Whether the block under the crosshair is one the player asked not to be told
     * about.
     *
     * @return true to suppress Waila entirely for this frame
     */
    public static boolean hideTarget() {
        if (!UiConfig.wailaHideListed) {
            return false;
        }
        Set<String> list = entries();
        if (list.isEmpty()) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return false;
        }
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            // An entity, or nothing at all. This list is about blocks; anything else is
            // Waila's business as it always was.
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        if (pos == null) {
            return false;
        }
        net.minecraft.block.state.IBlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        // 1.12 keeps the registry name as a ResourceLocation rather than a bare
        // string, and it is the same "domain:path" either way once it is written out.
        Object registered = Block.REGISTRY.getNameForObject(block);
        if (registered == null) {
            return false;
        }
        // Lowered to match the entries, which were lowered when they were read: a
        // registry name is conventionally lower case and a handful of mods do not know
        // that, and the config should not have to.
        String name = registered.toString().toLowerCase(Locale.ROOT);
        int meta = block.getMetaFromState(state);
        return matches(list, name, meta);
    }

    /**
     * Whether a name is on the list, in any of the four shapes it can be written in.
     *
     * A bare name covers every variant of the block; a name with a metadata value
     * covers exactly one. Both may be written with the domain or without it, because
     * "grass" is what somebody types and "minecraft:grass" is what the game calls it,
     * and being made to look up which of the two this file wanted would be the whole
     * cost of the feature.
     */
    private static boolean matches(Set<String> list, String name, int meta) {
        if (list.contains(name) || list.contains(name + ':' + meta)) {
            return true;
        }
        int domain = name.indexOf(':');
        if (domain < 0) {
            return false;
        }
        String path = name.substring(domain + 1);
        return list.contains(path) || list.contains(path + ':' + meta);
    }

    /** The list as a set, rebuilt when the config has handed over a new array. */
    private static Set<String> entries() {
        String[] configured = UiConfig.wailaHiddenBlocks;
        if (configured == parsedFrom) {
            return hidden;
        }
        parsedFrom = configured;
        if (configured == null || configured.length == 0) {
            hidden = Collections.emptySet();
            return hidden;
        }
        Set<String> built = new HashSet<String>(configured.length * 2);
        for (int i = 0; i < configured.length; i++) {
            String entry = configured[i];
            if (entry == null) {
                continue;
            }
            // Lower case because a registry name is, and an entry typed in title case
            // should not silently do nothing.
            entry = entry.trim().toLowerCase(Locale.ROOT);
            if (!entry.isEmpty()) {
                built.add(entry);
            }
        }
        hidden = built;
        return hidden;
    }
}

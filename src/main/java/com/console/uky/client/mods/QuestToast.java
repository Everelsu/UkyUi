package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.AchievementToast;
import com.console.uky.config.UiConfig;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * BetterQuesting's "quest complete" notice, shown as this mod's own panel instead.
 *
 * <p>The quest book already announces a completed quest with a title across the middle
 * of the screen, and this mod already announces an earned achievement with a panel that
 * cuts in from the right. They are the same event described twice — a thing was
 * finished, here is what it was called — and a player who finishes both at once sees
 * two different shapes, in two different places, running for two different lengths of
 * time. That is what makes a pack look assembled rather than made.
 *
 * <h2>Read rather than hooked</h2>
 *
 * <p>The obvious interception is {@code QuestNotification.ScheduleNotice}, which is
 * where a notice is created, and it cannot be used: the overload BetterQuesting's own
 * network handler calls takes a {@code NoticeConfig}, and a Mixin callback has to spell
 * out its target's parameter types exactly. Naming a class from an optional mod at
 * compile time is the thing this integration exists to avoid.
 *
 * <p>So the drawing is what gets cancelled — {@code onDrawScreen} takes a Forge event
 * and nothing else — and the notices are read out of the list behind it. Reflection
 * rather than {@code @Shadow} deliberately, for the reason {@code QuestBookTheme} gives
 * at length: a field that has been renamed should cost this feature and nothing else. A
 * shadow that fails takes the whole mixin down with it; a lookup that fails logs once
 * and hands the notice back to the quest book, which draws it exactly as it always did.
 */
public final class QuestToast {

    private static final String NOTIFICATION = "betterquesting.client.QuestNotification";

    private static boolean unavailable;
    private static Field noticesField;
    private static Field mainField;
    private static Field subField;
    private static Field iconField;

    private static boolean announced;
    /** {@code QuestTranslation.translate}, or null once it has been looked for and missed. */
    private static Method translate;
    private static boolean translateSearched;

    private QuestToast() {
    }

    /**
     * Takes the quest book's pending notices, if there are any and we can read them.
     *
     * <p><b>The list is emptied, not merely read.</b> That method is not only drawing:
     * it is what starts a notice's clock, plays its sound, opens a screen the notice
     * asked for, and drops the notice once its time is up. Cancelling it and leaving
     * the list alone — which is what this did at first — meant nothing was ever removed
     * from it, so the first quest completed sat at the head of the list for the rest of
     * the session and every quest after it went unseen behind it.
     *
     * <p>Taking the notices out is what makes the two halves consistent: what we have
     * shown is gone, so the frame after this the list is empty, this hands the frame
     * back, and the quest book resumes its own housekeeping with nothing left to draw.
     * The only frames it loses are the ones where a notice was actually taken.
     *
     * @return whether the caller should stop drawing — false leaves BetterQuesting's
     *         own notice on screen, which is what the config switch does and what
     *         happens whenever anything here cannot be resolved
     */
    public static boolean takeOver() {
        if (unavailable || !UiConfig.restyleQuestToast) {
            return false;
        }
        try {
            List<?> notices = notices();
            // Empty, or unreadable: nothing to take and nothing to suppress. Handing the
            // frame back matters more than it looks — see the note above about what else
            // that method is responsible for.
            if (notices == null || notices.isEmpty()) {
                return false;
            }
            for (int i = 0; i < notices.size(); i++) {
                Object notice = notices.get(i);
                String name = text(subField, notice);
                AchievementToast.show(name, translate(text(mainField, notice)), name,
                        icon(notice));
            }
            notices.clear();
            if (!announced) {
                announced = true;
                UkyUI.LOGGER.info("Quest notices are shown as UKY panels");
            }
            return true;
        } catch (Throwable t) {
            unavailable = true;
            UkyUI.LOGGER.warn("Could not read BetterQuesting's quest notice;"
                    + " it keeps its own", t);
            return false;
        }
    }

    /**
     * Runs a line through BetterQuesting's own translation, as its renderer does.
     *
     * <p>The upper line of a notice is a lang key rather than a sentence —
     * {@code betterquesting.notice.complete} — and the quest book resolves it at the
     * moment it draws. Taking the notice without taking that step put the key itself on
     * screen, which is what it looked like.
     *
     * <p>Only this line. The quest's own name arrives already resolved, and the
     * renderer does not translate it either; putting it through here as well would be
     * inventing a step BetterQuesting does not take.
     *
     * <p>A failure here costs the translation and nothing else — the raw text is still
     * a good deal more use on screen than no notice at all — so it does not mark the
     * whole integration unavailable the way an unreadable notice does.
     */
    private static String translate(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        try {
            if (!translateSearched) {
                translateSearched = true;
                translate = Class.forName("betterquesting.api2.utils.QuestTranslation")
                        .getMethod("translate", String.class, Object[].class);
            }
            if (translate == null) {
                return key;
            }
            Object out = translate.invoke(null, key, new Object[0]);
            return out == null ? key : out.toString();
        } catch (Throwable t) {
            translate = null;
            return key;
        }
    }

    private static List<?> notices() throws Exception {
        if (noticesField == null) {
            Class<?> type = Class.forName(NOTIFICATION);
            noticesField = type.getDeclaredField("notices");
            noticesField.setAccessible(true);

            Class<?> notice = Class.forName(NOTIFICATION + "$QuestNotice");
            mainField = field(notice, "mainTxt");
            subField = field(notice, "subTxt");
            iconField = field(notice, "icon");
        }
        return (List<?>) noticesField.get(null);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static String text(Field f, Object notice) {
        try {
            Object value = f.get(notice);
            return value == null ? "" : value.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static ItemStack icon(Object notice) {
        try {
            Object value = iconField.get(notice);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable t) {
            return null;
        }
    }
}

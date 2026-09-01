package com.console.uky.client.mods;

import com.console.uky.UkyUI;
import com.console.uky.client.gui.AchievementToast;
import com.console.uky.config.UiConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

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

    /** Where the quest book's translation helper has lived, newest first. */
    private static final String[] TRANSLATION_CLASSES = {
        "betterquesting.api2.utils.QuestTranslation",
        "betterquesting.api.utils.QuestTranslation",
    };

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
     * <p>Three attempts, in the order of who has the best answer:
     *
     * <ol>
     *   <li>{@code QuestTranslation}, which is what the quest book's own renderer
     *       calls. Two package names are tried, because the class moved into
     *       {@code api2} and a pack can be running either side of that.</li>
     *   <li>The game's own table. BetterQuesting's keys are ordinary lang keys, so its
     *       file answers just as well read directly, and vanilla already falls back to
     *       English for a language whose own file is missing the line.</li>
     *   <li>Our own wording, under {@code uky.quest.notice.*}. Only reached when the
     *       quest book is a version nothing above could read, and it is the difference
     *       between a sentence and a raw lang key on screen — which is exactly what
     *       was on screen.</li>
     * </ol>
     *
     * <p>Failing all three the key is returned, which is what BetterQuesting itself
     * would have drawn. A failure here costs the translation and nothing else — the raw
     * text is still a good deal more use on screen than no notice at all — so it does
     * not mark the whole integration unavailable the way an unreadable notice does.
     *
     * <p>Only this line. The quest's own name arrives already resolved, and the
     * renderer does not translate it either; putting it through here as well would be
     * inventing a step BetterQuesting does not take.
     */
    private static String translate(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String questBook = viaQuestBook(key);
        if (questBook != null && !key.equals(questBook)) {
            return questBook;
        }
        String vanilla = StatCollector.translateToLocal(key);
        if (!key.equals(vanilla)) {
            return vanilla;
        }
        return ourOwn(key);
    }

    /** {@code QuestTranslation.translate}, or null when there is no reaching it. */
    private static String viaQuestBook(String key) {
        try {
            if (!translateSearched) {
                translateSearched = true;
                translate = findTranslate();
            }
            if (translate == null) {
                return null;
            }
            Object out = translate.invoke(null, key, new Object[0]);
            return out == null ? null : out.toString();
        } catch (Throwable t) {
            translate = null;
            return null;
        }
    }

    /**
     * The helper, wherever this version of the quest book keeps it.
     *
     * {@code api2} is where it lives now and where it has lived for every version this
     * mod is likely to meet; the older package is tried after it rather than instead of
     * it, so the current one costs one lookup and only the fallback costs a failed one.
     */
    private static Method findTranslate() {
        for (int i = 0; i < TRANSLATION_CLASSES.length; i++) {
            try {
                return Class.forName(TRANSLATION_CLASSES[i])
                        .getMethod("translate", String.class, Object[].class);
            } catch (Throwable missing) {
                // Next candidate. All of them missing is a quest book we cannot ask,
                // which the caller answers out of the game's own table instead.
            }
        }
        return null;
    }

    /**
     * Our own wording for a notice nothing else could name.
     *
     * Keyed on the last segment — {@code complete}, {@code unlock}, {@code update} —
     * rather than on the whole key, so what has to be recognised is the kind of notice
     * rather than the exact string a given version of the mod spells it with.
     */
    private static String ourOwn(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return key;
        }
        String ours = "uky.quest.notice." + key.substring(dot + 1);
        String out = StatCollector.translateToLocal(ours);
        return ours.equals(out) ? key : out;
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

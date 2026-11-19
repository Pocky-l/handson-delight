package com.pockyl.handson_delight;

import java.util.Locale;

/**
 * The recipe book's arithmetic, kept apart from the client so that the game tests can check it on a server: turning pages, leafing
 * through them with a held arrow, and what a search matches.
 */
public final class BookMath {
    /** A held arrow turns one page at once, then waits this long, seconds, before it goes on by itself. */
    public static final float REPEAT_DELAY = 0.35F;
    /** Then it turns this many pages a second, and from {@link #FAST_FROM} seconds on the faster rate. */
    public static final float REPEAT_RATE = 8.0F;
    public static final float FAST_FROM = 1.5F;
    public static final float FAST_RATE = 16.0F;

    private BookMath() {
    }

    /**
     * The page after turning {@code by} from {@code page} in a list of {@code pages}: kept within the list, or, with {@code wrap},
     * going round from the last page to the first and back.
     */
    public static int turned(int page, int by, int pages, boolean wrap) {
        if (pages <= 1) {
            return 0;
        }
        int next = page + by;
        if (wrap) {
            return Math.floorMod(next, pages);
        }
        return Math.max(0, Math.min(pages - 1, next));
    }

    /** How many pages a held arrow has turned once it has been held {@code seconds}: one at once, then more and more quickly. */
    public static int turnsDue(float seconds) {
        if (seconds < REPEAT_DELAY) {
            return 1;
        }
        float slow = Math.min(seconds, FAST_FROM) - REPEAT_DELAY;
        float fast = Math.max(0.0F, seconds - FAST_FROM);
        return 2 + (int) (slow * REPEAT_RATE) + (int) (fast * FAST_RATE);
    }

    /** Whether a held arrow that has turned {@code done} pages turns one more now. */
    public static boolean repeatDue(float seconds, int done) {
        return turnsDue(seconds) > done;
    }

    /**
     * Whether a recipe called {@code name}, with the id {@code id}, is found by {@code query}: each word typed is somewhere in the
     * name or the id, case aside. Nothing typed finds everything.
     */
    public static boolean matches(String query, String name, String id) {
        String typed = query.trim().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) {
            return true;
        }
        String in = name.toLowerCase(Locale.ROOT) + "\n" + id.toLowerCase(Locale.ROOT);
        for (String word : typed.split("\\s+")) {
            if (!in.contains(word)) {
                return false;
            }
        }
        return true;
    }
}

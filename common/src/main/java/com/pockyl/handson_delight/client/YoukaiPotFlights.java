package com.pockyl.handson_delight.client;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * What is still on its way into a Youkai's Feasts pot: the pot already holds it, but it is coming down from the cook, so the pot's
 * own renderer does not draw it floating yet. Counted from the last thing in, per pot; it lets go by itself after a while, should
 * the station close in the middle of a flight. Also how far the stirring has turned what floats in a pot, degrees. Both are of the
 * level the player is in: a pot at the same place in another dimension or world starts afresh.
 */
public final class YoukaiPotFlights {
    private static final Map<BlockPos, Flights> FLYING = new HashMap<>();
    private static final Map<BlockPos, Float> SWIRLS = new HashMap<>();
    private static WeakReference<Level> kept = new WeakReference<>(null);

    private record Flights(int count, long until) {
    }

    private YoukaiPotFlights() {
    }

    /** {@code count} more things are coming down into the pot at {@code pos}, all in within {@code seconds}. */
    static void coming(BlockPos pos, int count, float seconds) {
        forgetOtherLevels();
        Flights now = live(pos);
        long until = Util.getMillis() + (long) (seconds * 1000.0F) + 250L;
        FLYING.put(pos.immutable(), new Flights((now == null ? 0 : now.count()) + count, Math.max(until, now == null ? 0L : now.until())));
    }

    /** One of them is in. */
    static void landed(BlockPos pos) {
        Flights now = live(pos);
        if (now == null || now.count() <= 1) {
            FLYING.remove(pos);
        } else {
            FLYING.put(pos.immutable(), new Flights(now.count() - 1, now.until()));
        }
    }

    static void clear(BlockPos pos) {
        FLYING.remove(pos);
    }

    /** What floats in the pot at {@code pos} has been stirred round {@code degrees}; 0 lets it be. */
    static void stirred(BlockPos pos, float degrees) {
        forgetOtherLevels();
        if (degrees == 0.0F) {
            SWIRLS.remove(pos);
        } else {
            SWIRLS.put(pos.immutable(), degrees);
        }
    }

    /** How far what floats in the pot at {@code pos} has been stirred round, degrees. */
    public static float swirl(BlockPos pos) {
        forgetOtherLevels();
        Float degrees = SWIRLS.get(pos);
        return degrees == null ? 0.0F : degrees;
    }

    /** How many of the last things in the pot at {@code pos} are not to be drawn floating yet. */
    public static int flying(BlockPos pos) {
        forgetOtherLevels();
        Flights now = live(pos);
        return now == null ? 0 : now.count();
    }

    /** The player went to another level: what was kept belongs to pots of the one left. */
    private static void forgetOtherLevels() {
        Level level = Minecraft.getInstance().level;
        if (level != kept.get()) {
            kept = new WeakReference<>(level);
            FLYING.clear();
            SWIRLS.clear();
        }
    }

    private static Flights live(BlockPos pos) {
        Flights now = FLYING.get(pos);
        if (now != null && Util.getMillis() > now.until()) {
            FLYING.remove(pos);
            return null;
        }
        return now;
    }
}

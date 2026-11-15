package com.pockyl.handson_delight.client;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * What is still on its way into a Youkai's Feasts pot: the pot already holds it, but it is coming down from the cook, so the pot's
 * own renderer does not draw it floating yet. Counted from the last thing in, per pot; it lets go by itself after a while, should
 * the station close in the middle of a flight. Also how far the stirring has turned what floats in a pot, degrees.
 */
public final class YoukaiPotFlights {
    private static final Map<BlockPos, Flights> FLYING = new HashMap<>();
    private static final Map<BlockPos, Float> SWIRLS = new HashMap<>();

    private record Flights(int count, long until) {
    }

    private YoukaiPotFlights() {
    }

    /** {@code count} more things are coming down into the pot at {@code pos}, all in within {@code seconds}. */
    static void coming(BlockPos pos, int count, float seconds) {
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
        if (degrees == 0.0F) {
            SWIRLS.remove(pos);
        } else {
            SWIRLS.put(pos.immutable(), degrees);
        }
    }

    /** How far what floats in the pot at {@code pos} has been stirred round, degrees. */
    public static float swirl(BlockPos pos) {
        Float degrees = SWIRLS.get(pos);
        return degrees == null ? 0.0F : degrees;
    }

    /** How many of the last things in the pot at {@code pos} are not to be drawn floating yet. */
    public static int flying(BlockPos pos) {
        Flights now = live(pos);
        return now == null ? 0 : now.count();
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

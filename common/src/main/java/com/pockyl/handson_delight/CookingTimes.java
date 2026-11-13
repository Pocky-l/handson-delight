package com.pockyl.handson_delight;

import net.minecraft.nbt.CompoundTag;

/**
 * The cooking times of the server a client plays on ({@link HandsOnConfig}), for what the client shows: the recipe book's seconds.
 * The server sends them with the board's dishes as a player joins; until then, and on the server itself, the own config's.
 */
public final class CookingTimes {
    private static final String POT = "cooking_pot";
    private static final String SKILLET = "skillet";
    private static final String STOVE = "stove";

    private static volatile CompoundTag received;

    private CookingTimes() {
    }

    /** The server's times, for the packet. */
    public static CompoundTag write() {
        CompoundTag times = new CompoundTag();
        times.putFloat(POT, HandsOnConfig.cookingPotTime());
        times.putFloat(SKILLET, HandsOnConfig.skilletTime());
        times.putFloat(STOVE, HandsOnConfig.stoveTime());
        return times;
    }

    /** Called on the network thread. A server of an older version sends none: its times are the usual ones. */
    public static void receive(CompoundTag times) {
        received = times;
    }

    public static void clear() {
        received = null;
    }

    public static float cookingPot() {
        return read(POT, HandsOnConfig.cookingPotTime());
    }

    public static float skillet() {
        return read(SKILLET, HandsOnConfig.skilletTime());
    }

    public static float stove() {
        return read(STOVE, HandsOnConfig.stoveTime());
    }

    private static float read(String key, float own) {
        CompoundTag times = received;
        if (times == null) {
            return own;
        }
        return times.contains(key) ? times.getFloat(key) : 1.0F;
    }
}

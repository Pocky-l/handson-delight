package com.pockyl.handson_delight.station;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.utility.ItemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Catches the items Farmer's Delight throws into the world ({@code ItemUtils.spawnItemEntity}) while a catch runs, so that the station
 * can hand them to the cook or lay them where the cook looks. Stations let Farmer's Delight's own code make what it makes (the cut,
 * the fried or grilled food), so that what other mods add to that code (experience, quality, extra drops) still happens; only where
 * the result lands is the station's. A quiet catch makes no sound: the rest of a board's batch, after its first item.
 */
public final class CaughtDrops {
    /** One item Farmer's Delight threw, and how: enough to throw it again just so. */
    public record Drop(ItemStack stack, double x, double y, double z, double dx, double dy, double dz) {
        /** Throws it into the world after all, as Farmer's Delight would have. */
        public void spawn(Level level) {
            ItemUtils.spawnItemEntity(level, stack, x, y, z, dx, dy, dz);
        }
    }

    /** Per thread: the client of a single-player game spawns its own items in its own thread meanwhile. */
    private static final ThreadLocal<List<Drop>> CAUGHT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> QUIET = ThreadLocal.withInitial(() -> false);

    private CaughtDrops() {
    }

    /** Runs {@code action} and returns what it would have thrown into the world, or null when it did nothing. */
    public static List<ItemStack> catching(boolean quiet, BooleanSupplier action) {
        List<Drop> previous = CAUGHT.get();
        List<Drop> caught = new ArrayList<>();
        CAUGHT.set(caught);
        QUIET.set(quiet);
        try {
            return action.getAsBoolean() ? caught.stream().map(Drop::stack).toList() : null;
        } finally {
            CAUGHT.set(previous);
            QUIET.set(false);
        }
    }

    /** Starts catching for code this mod does not call itself; {@link #end} returns the drops in the order they came. */
    public static void begin() {
        CAUGHT.set(new ArrayList<>());
    }

    public static List<Drop> end() {
        List<Drop> caught = CAUGHT.get();
        CAUGHT.remove();
        return caught == null ? List.of() : caught;
    }

    /** Called for every item Farmer's Delight spawns: true when it was caught. */
    public static boolean take(ItemStack stack, double x, double y, double z, double dx, double dy, double dz) {
        List<Drop> caught = CAUGHT.get();
        if (caught == null) {
            return false;
        }
        caught.add(new Drop(stack.copy(), x, y, z, dx, dy, dz));
        return true;
    }

    public static boolean isQuiet() {
        return QUIET.get();
    }
}

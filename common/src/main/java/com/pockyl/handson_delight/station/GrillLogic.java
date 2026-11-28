package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import com.pockyl.handson_delight.ItemNbt;

/**
 * The grill's actions at the station: a skewer on, turned, seasoned, taken. The grilling itself is the grill's: it ticks its skewers
 * on the server and on every client near it, so this only tells the clients how far each one is.
 */
public final class GrillLogic implements StationLogic {
    /** Doneness is told to the clients in steps this fine: the look moves smoothly between them. */
    private static final float STEP = 1.0F / 32.0F;

    private final KitchenGrill grill;

    public GrillLogic(StationContext station) {
        grill = KitchenGrill.of(station.blockEntity());
        station.state().bindSlots(GrillStation.GRILL, new SlotStorage() {
            @Override
            public int size() {
                return KitchenGrill.PLACES;
            }

            @Override
            public ItemStack get(int index) {
                return grill == null ? ItemStack.EMPTY : grill.getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (grill != null) {
                    grill.setItem(index, stack);
                }
            }
        });
    }

    /** Nothing goes on by the library's own moves: every stack comes through {@code place}, the hand's slot stays empty. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    @Override
    public void tick(StationContext station) {
        publish(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (grill == null) {
            return;
        }
        int place = data.contains(GrillStation.PLACE) ? data.getIntOr(GrillStation.PLACE, 0) : -1;
        int from = data.contains(GrillStation.FROM) ? data.getIntOr(GrillStation.FROM, 0) : -1;
        switch (action) {
            case GrillStation.ACTION_PLACE -> place(station, player, data.getBooleanOr(GrillStation.ONE, false) ? place : -1, from);
            case GrillStation.ACTION_TURN -> turn(station, place);
            case GrillStation.ACTION_TAKE -> take(station, player, place);
            case GrillStation.ACTION_SEASON -> season(station, player, place, from);
            default -> {
            }
        }
        publish(station);
    }

    /** The stack on the cursor, or the one in inventory slot {@code from}, goes onto the grill: every free place, or onto {@code place}. */
    private void place(StationContext station, Player player, int place, int from) {
        ItemStack carried = held(player, from);
        if (carried.isEmpty()) {
            return;
        }
        int laid = 0;
        if (!KitchenGrill.grills(station.level(), carried)) {
            refused(station, carried);
            return;
        }
        for (int at = 0; at < KitchenGrill.PLACES && !carried.isEmpty(); at++) {
            if ((place < 0 || at == place) && grill.lay(at, carried)) {
                laid++;
            }
        }
        if (laid == 0) {
            refused(station, carried);
            return;
        }
        putBack(player, from, carried);
        station.effect(GrillStation.EFFECT_PLACED, "grill", new CompoundTag());
    }

    private void refused(StationContext station, ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.put(GrillStation.STACK, ItemNbt.save(stack.copyWithCount(1), station.level().registryAccess()));
        station.effect(GrillStation.EFFECT_REFUSED, "grill", data);
    }

    private void turn(StationContext station, int place) {
        CompoundTag data = new CompoundTag();
        data.putInt(GrillStation.PLACE, place);
        boolean turned = grill.canTurn(place) && grill.turn(place);
        station.effect(turned ? GrillStation.EFFECT_TURNED : GrillStation.EFFECT_UNTURNED, "grill", data);
    }

    private void take(StationContext station, Player player, int place) {
        ItemStack taken = grill.take(place);
        if (taken.isEmpty()) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt(GrillStation.PLACE, place);
        data.put(GrillStation.STACK, ItemNbt.save(taken, station.level().registryAccess()));
        station.effect(GrillStation.EFFECT_TAKEN, "grill", data);
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
    }

    /** The seasoning on the cursor, or in inventory slot {@code from}, over the skewer on {@code place}, or the first that takes it. */
    private void season(StationContext station, Player player, int place, int from) {
        ItemStack seasoning = held(player, from);
        if (!KitchenGrill.isSeasoning(seasoning)) {
            return;
        }
        int at = place;
        if (at < 0) {
            for (int free = 0; free < KitchenGrill.PLACES && at < 0; free++) {
                at = KitchenGrill.canSeason(seasoning, grill.getItem(free)) ? free : -1;
            }
        }
        if (at < 0 || at >= KitchenGrill.PLACES) {
            return;
        }
        ItemStack before = seasoning.copy();
        if (!grill.season(at, seasoning, player)) {
            return;
        }
        putBack(player, from, seasoning);
        CompoundTag data = new CompoundTag();
        data.putInt(GrillStation.PLACE, at);
        data.put(GrillStation.STACK, ItemNbt.save(before, station.level().registryAccess()));
        station.effect(GrillStation.EFFECT_SEASONED, "grill", data);
    }

    /** What the cook works with: the stack on the cursor, or the one in inventory slot {@code from}. */
    private static ItemStack held(Player player, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    /** Puts back what is left of what the cook worked with, nothing once it is used up. */
    private static void putBack(Player player, int from, ItemStack left) {
        ItemStack kept = left.isEmpty() ? ItemStack.EMPTY : left;
        if (from >= 0) {
            player.getInventory().setItem(from, kept);
        } else {
            player.inventoryMenu.setCarried(kept);
        }
    }

    /** How far each skewer is, for the clients; sent only when it changes. */
    private void publish(StationContext station) {
        if (grill == null) {
            return;
        }
        StationState state = station.state();
        CompoundTag data = new CompoundTag();
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            if (grill.getItem(place).isEmpty()) {
                continue;
            }
            float half = Math.max(1, grill.duration(place)) / 2.0F;
            int time = grill.time(place);
            boolean turned = grill.turned(place);
            data.putBoolean(GrillStation.TURNED + place, turned);
            data.putFloat(GrillStation.LOWER + place, steps(Mth.clamp((turned ? time - half : time) / half, 0.0F, 1.0F)));
            data.putBoolean(GrillStation.WAITS + place, grill.canTurn(place));
            data.putFloat(GrillStation.BURN + place, grill.burnt(place) ? 1.0F : steps(Mth.clamp((time - 2.0F * half) / (2.0F * half), 0.0F, 1.0F)));
        }
        data.putBoolean(GrillStation.LIT, grill.heated());
        if (!data.equals(state.data())) {
            state.setData(data);
        }
    }

    private static float steps(float done) {
        return done >= 1.0F ? 1.0F : (float) Math.floor(done / STEP) * STEP;
    }
}

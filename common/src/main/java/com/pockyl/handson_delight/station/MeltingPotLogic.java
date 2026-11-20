package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * The melting pot's menu by hand: its input slot is the pot's own, the melting is left to it. What melts goes in, what has melted
 * is drawn into the cook's bottles and buckets. Also runs on the clients near a pot nobody works at; there it only reads.
 */
public final class MeltingPotLogic implements StationLogic {
    /** What has melted, as the scene draws it; its colour is the client's, from the fluid in the tank. */
    public static final net.minecraft.resources.Identifier MELTED_LIQUID = HandsOnDelight.id("melted");

    private final KitchenMeltingPot pot;

    public MeltingPotLogic(StationContext station) {
        pot = KitchenMeltingPot.of(station.blockEntity());
        station.state().bindSlots(MeltingPotStation.INPUT, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return pot == null ? ItemStack.EMPTY : pot.handson$input();
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (pot != null) {
                    pot.handson$setInput(stack);
                }
            }
        });
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return KitchenMeltingPot.melting(KitchenMeltingPot.recipes(station.level()), stack).isPresent();
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (pot == null) {
            return;
        }
        int from = data.contains(MeltingPotStation.FROM) ? data.getIntOr(MeltingPotStation.FROM, 0) : -1;
        switch (action) {
            case MeltingPotStation.ACTION_PUT -> put(station, player, from);
            case MeltingPotStation.ACTION_DRAW -> draw(player, from, data.getBooleanOr(MeltingPotStation.ALL, false));
            case MeltingPotStation.ACTION_FILL -> fill(station, player, data);
            default -> {
            }
        }
        refresh(station);
    }

    private static ItemStack held(Player player, int from) {
        if (from < 0) {
            return player.inventoryMenu.getCarried();
        }
        return from < Inventory.INVENTORY_SIZE ? player.getInventory().getItem(from) : ItemStack.EMPTY;
    }

    private static void putBack(Player player, int from, ItemStack left) {
        if (from < 0) {
            player.inventoryMenu.setCarried(left);
        } else {
            player.getInventory().setItem(from, left);
            player.getInventory().setChanged();
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** What the cook brings goes in to melt, onto what melts there of the same kind; an empty container draws what has melted. */
    private void put(StationContext station, Player player, int from) {
        ItemStack stack = held(player, from);
        if (stack.isEmpty()) {
            return;
        }
        if (KitchenMeltingPot.melting(KitchenMeltingPot.recipes(station.level()), stack).isEmpty()) {
            draw(player, from, false);
            return;
        }
        putBack(player, from, moveIn(stack));
    }

    private ItemStack moveIn(ItemStack stack) {
        ItemStack there = pot.handson$input();
        if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
            return stack;
        }
        int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
        if (moved <= 0) {
            return stack;
        }
        pot.handson$setInput(there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        return stack.copyWithCount(stack.getCount() - moved);
    }

    /** What has melted goes into the containers the cook brings, one, or as many as there are with {@code all}. */
    private void draw(Player player, int from, boolean all) {
        ItemStack containers = held(player, from);
        int times = all ? containers.getCount() : 1;
        for (int i = 0; i < times && !containers.isEmpty(); i++) {
            ItemStack full = pot.handson$draw(containers.copyWithCount(1));
            if (full.isEmpty()) {
                return;
            }
            containers = containers.copyWithCount(containers.getCount() - 1);
            if (containers.isEmpty() && from < 0) {
                putBack(player, from, full);
                return;
            }
            putBack(player, from, containers);
            give(player, full);
        }
    }

    /** What melts into the picked recipe goes in from the inventory: one, or with shift a whole stack, onto what is there. */
    private void fill(StationContext station, Player player, CompoundTag data) {
        String id = data.getStringOr(MeltingPotStation.DISH, "");
        KitchenMeltingPot.recipes(station.level()).stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst().ifPresent(recipe -> {
            Inventory inventory = player.getInventory();
            boolean all = data.getBooleanOr(MeltingPotStation.ALL, false);
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || !recipe.input().test(stack)) {
                    continue;
                }
                ItemStack there = pot.handson$input();
                if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                    // Something else melts there: it goes back to the cook to make room.
                    give(player, there);
                    pot.handson$setInput(ItemStack.EMPTY);
                }
                ItemStack left = moveIn(all ? stack : stack.copyWithCount(1));
                int moved = (all ? stack.getCount() : 1) - left.getCount();
                inventory.setItem(slot, stack.copyWithCount(stack.getCount() - moved));
                if (!all && moved > 0 || pot.handson$input().getCount() >= pot.handson$input().getMaxStackSize()) {
                    break;
                }
            }
            inventory.setChanged();
        });
    }

    private void refresh(StationContext station) {
        if (pot == null) {
            return;
        }
        StationState state = station.state();
        int time = pot.handson$cookTime();
        int total = pot.handson$cookTimeTotal();
        state.setControl(MeltingPotStation.PROGRESS, time > 0 && total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        int amount = Math.round(pot.handson$amount() * 100.0F / KitchenMeltingPot.TANK);
        TankContents melt = amount > 0 || pot.handson$amount() > 0 ? TankContents.of(MELTED_LIQUID, Math.max(1, Math.min(100, amount)))
                : TankContents.EMPTY;
        if (!state.getTank(MeltingPotStation.MELT).equals(melt)) {
            state.setTank(MeltingPotStation.MELT, melt);
        }
        CompoundTag own = state.data().copy();
        int left = time > 0 && total > time ? Mth.ceil((total - time) / 20.0F) * 20 : 0;
        String fluid = pot.handson$fluid() == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(pot.handson$fluid()).toString();
        boolean heated = pot.handson$isHeated();
        if (own.getIntOr(MeltingPotStation.LEFT, 0) != left || !own.getStringOr(MeltingPotStation.FLUID, "").equals(fluid)
                || own.getBooleanOr(MeltingPotStation.HEATED, false) != heated) {
            own.putInt(MeltingPotStation.LEFT, left);
            own.putString(MeltingPotStation.FLUID, fluid);
            own.putBoolean(MeltingPotStation.HEATED, heated);
            state.setData(own);
        }
    }
}

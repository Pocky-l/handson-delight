package com.pockyl.handson_delight.station;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Youkai's Feasts pot's actions at the station: food in, the last thing back out, a dish from the book. Each goes through the
 * pot's own code ({@link KitchenYoukaiPot}); this only says what happened, for everybody to see. While somebody works at the pot it
 * knows them: a dish done is told, and one that is no block goes to them.
 */
public final class YoukaiPotLogic implements StationLogic {
    /** The most one click with shift puts in: the pot holds twelve things. */
    private static final int MOST = 12;
    /** A stir is seen for this long after the hand last moved the ladle, ticks, as at the cooking pot. */
    private static final int STIR_TICKS = 40;

    /** The pot's block entity, looked up again at each call: an empty pot has none until water turns it into the pot with water. */
    private KitchenYoukaiPot pot;
    private long stirredAt = -STIR_TICKS;
    private UUID stirrer;

    public YoukaiPotLogic(StationContext station) {
        pot = KitchenYoukaiPot.of(station.blockEntity());
    }

    private void lookUp(StationContext station) {
        pot = KitchenYoukaiPot.of(station.blockEntity());
    }

    /** Nothing goes in by the library's own moves: every stack comes through {@code put}, the hand's slot stays empty. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        lookUp(station);
        if (pot != null && !station.isClientSide()) {
            pot.handson$setCook(cook(station, player));
        }
    }

    /** The one who left was the cook: whoever still works there becomes it, or nobody. */
    @Override
    public void onClose(StationContext station, Player player) {
        lookUp(station);
        if (pot == null || station.isClientSide()) {
            return;
        }
        KitchenYoukaiPot.Cook now = pot.handson$cook();
        if (now != null && now.player() != player) {
            return;
        }
        Player next = station.users().stream().filter(user -> user != player).findFirst().orElse(null);
        pot.handson$setCook(next == null ? null : cook(station, next));
    }

    private static KitchenYoukaiPot.Cook cook(StationContext station, Player player) {
        return new KitchenYoukaiPot.Cook() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public void done(ItemStack dish) {
                CompoundTag data = new CompoundTag();
                data.put(YoukaiPotStation.STACK, dish.saveOptional(station.level().registryAccess()));
                station.effect(YoukaiPotStation.EFFECT_DONE, "pot", data);
            }

            @Override
            public void served(ItemStack dish) {
                CompoundTag data = new CompoundTag();
                data.put(YoukaiPotStation.STACK, dish.saveOptional(station.level().registryAccess()));
                data.putBoolean(YoukaiPotStation.SERVED, true);
                station.effect(YoukaiPotStation.EFFECT_DONE, "pot", data);
            }
        };
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        lookUp(station);
        if (pot == null) {
            if (YoukaiPotStation.ACTION_PUT.equals(action)) {
                water(station, player, data.contains(YoukaiPotStation.FROM) ? data.getInt(YoukaiPotStation.FROM) : -1);
            } else if (YoukaiPotStation.ACTION_FILL.equals(action)) {
                // A dish picked in the book of an empty pot: water goes in first, and the bucket says so.
                refused(station, new ItemStack(Items.WATER_BUCKET));
            }
            return;
        }
        if (pot.handson$cook() == null || !station.users().contains(pot.handson$cook().player())) {
            pot.handson$setCook(cook(station, player));
        }
        int from = data.contains(YoukaiPotStation.FROM) ? data.getInt(YoukaiPotStation.FROM) : -1;
        switch (action) {
            case YoukaiPotStation.ACTION_PUT -> put(station, player, from, data.getBoolean(YoukaiPotStation.ALL));
            case YoukaiPotStation.ACTION_BACK -> back(station, player);
            case YoukaiPotStation.ACTION_FILL -> fill(station, player, data.getString(YoukaiPotStation.DISH));
            case YoukaiPotStation.ACTION_STIR -> stir(station, player, data);
            default -> {
            }
        }
    }

    /** The ladle moved in a cook's hand: everybody near sees it where it is. One cook stirs at a time. */
    private void stir(StationContext station, Player player, CompoundTag data) {
        long now = station.level().getGameTime();
        if (isStirring(now) && stirrer != null && !player.getUUID().equals(stirrer)) {
            return;
        }
        stirredAt = now;
        stirrer = player.getUUID();
        station.state().setControl(YoukaiPotStation.STIRRING, 1.0F);
        station.state().setControl(YoukaiPotStation.SPOON_X, spoon(data.getFloat(YoukaiPotStation.STIR_X)));
        station.state().setControl(YoukaiPotStation.SPOON_Z, spoon(data.getFloat(YoukaiPotStation.STIR_Z)));
    }

    private boolean isStirring(long now) {
        return now - stirredAt < STIR_TICKS;
    }

    private static float spoon(float at) {
        return Float.isFinite(at) ? Math.max(-1.0F, Math.min(1.0F, at)) : 0.0F;
    }

    /** Left alone, the ladle goes back to its rest for everybody. */
    @Override
    public void tick(StationContext station) {
        lookUp(station);
        if (station.isClientSide() || isStirring(station.level().getGameTime())
                || station.state().getControl(YoukaiPotStation.STIRRING) <= 0.5F) {
            return;
        }
        station.state().setControl(YoukaiPotStation.STIRRING, 0.0F);
        station.state().setControl(YoukaiPotStation.SPOON_X, 0.0F);
        station.state().setControl(YoukaiPotStation.SPOON_Z, 0.0F);
    }

    /**
     * Water poured into an empty pot, from the cursor or inventory slot {@code from}: through the pot's own click with it in the main
     * hand, which makes the pot with water and empties the container as it always does. The station stays open on the new block.
     */
    private void water(StationContext station, Player player, int from) {
        ItemStack held = held(player, from);
        BlockState before = station.level().getBlockState(station.pos());
        if (!YoukaiPotStation.isWater(held) || !YoukaiPotStation.isEmptyPot(before)) {
            if (!held.isEmpty()) {
                refused(station, held);
            }
            return;
        }
        ItemStack poured = held.copyWithCount(1);
        ItemStack inHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        before.useItemOn(held, station.level(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(station.pos()), Direction.UP, station.pos(), false));
        ItemStack after = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        putBack(player, from, held, after);
        lookUp(station);
        if (pot == null) {
            refused(station, poured);
            return;
        }
        pot.handson$setCook(cook(station, player));
        ListTag went = new ListTag();
        went.add(poured.saveOptional(station.level().registryAccess()));
        in(station, went, false);
    }

    /** One of the stack on the cursor, or in inventory slot {@code from}; with {@code all} as many as the pot takes of it. */
    private void put(StationContext station, Player player, int from, boolean all) {
        ItemStack held = held(player, from);
        if (held.isEmpty()) {
            return;
        }
        ListTag went = new ListTag();
        for (int i = 0; i < (all ? MOST : 1) && !held.isEmpty(); i++) {
            ItemStack one = held.copyWithCount(1);
            if (!pot.handson$put(player, held)) {
                break;
            }
            went.add(one.saveOptional(station.level().registryAccess()));
        }
        putBack(player, from, held);
        if (went.isEmpty()) {
            refused(station, held);
            return;
        }
        in(station, went, false);
    }

    /** The last thing in comes back out to the cook. */
    private void back(StationContext station, Player player) {
        List<ItemStack> in = pot.handson$contents();
        ItemStack back = pot.handson$takeBack(player);
        if (back == null) {
            if (!in.isEmpty()) {
                refused(station, in.get(in.size() - 1));
            }
            return;
        }
        // What does not fit drops, and only that: the inventory keeps the part it took.
        ItemStack given = back.copy();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
        CompoundTag data = new CompoundTag();
        data.put(YoukaiPotStation.STACK, back.saveOptional(station.level().registryAccess()));
        station.effect(YoukaiPotStation.EFFECT_BACK, "pot", data);
    }

    /**
     * A dish picked in the book goes in from the inventory: what is in the pot already counts when it is part of that dish, the rest
     * comes from the first stacks that fit. Something else in the pot, or nothing of the dish in the inventory: refused. Without
     * everything it puts in what there is, and the pot waits for the rest.
     */
    private void fill(StationContext station, Player player, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        KitchenYoukaiPot.Dish dish = YoukaiPotStation.dishes(pot).stream().filter(it -> it.id().equals(key)).findFirst().orElse(null);
        if (dish == null) {
            return;
        }
        List<Ingredient> missing = missing(dish, pot.handson$contents());
        if (missing == null) {
            List<ItemStack> in = pot.handson$contents();
            refused(station, in.isEmpty() ? dish.result() : in.get(in.size() - 1));
            return;
        }
        Inventory inventory = player.getInventory();
        ListTag went = new ListTag();
        for (Ingredient ingredient : missing) {
            int slot = slotFor(inventory, ingredient);
            if (slot < 0) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            ItemStack one = stack.copyWithCount(1);
            if (pot.handson$put(player, stack)) {
                inventory.setChanged();
                went.add(one.saveOptional(station.level().registryAccess()));
            }
        }
        if (went.isEmpty()) {
            if (!missing.isEmpty()) {
                refused(station, missing.get(0).getItems().length > 0 ? missing.get(0).getItems()[0] : dish.result());
            }
            return;
        }
        in(station, went, true);
    }

    private int slotFor(Inventory inventory, Ingredient ingredient) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty() && ingredient.test(stack) && pot.handson$takes(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * What of {@code dish} is not in the pot yet, when everything in it is part of that dish (each thing matched to an ingredient of
     * its own, as the pot's recipes match them); null when something in it is not.
     */
    public static List<Ingredient> missing(KitchenYoukaiPot.Dish dish, List<ItemStack> in) {
        List<Ingredient> left = new ArrayList<>(dish.ingredients());
        for (ItemStack stack : in) {
            Ingredient matched = left.stream().filter(ingredient -> ingredient.test(stack)).findFirst().orElse(null);
            if (matched == null) {
                return null;
            }
            left.remove(matched);
        }
        return left;
    }

    private void in(StationContext station, ListTag went, boolean fromBook) {
        CompoundTag data = new CompoundTag();
        data.put(YoukaiPotStation.STACKS, went);
        data.putBoolean(YoukaiPotStation.BOOK, fromBook);
        station.effect(YoukaiPotStation.EFFECT_IN, "pot", data);
    }

    private static void refused(StationContext station, ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.put(YoukaiPotStation.STACK, stack.copyWithCount(1).saveOptional(station.level().registryAccess()));
        station.effect(YoukaiPotStation.EFFECT_REFUSED, "pot", data);
    }

    /** What the cook works with: the stack on the cursor, or the one in inventory slot {@code from}. */
    private static ItemStack held(Player player, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    private static void putBack(Player player, int from, ItemStack left) {
        putBack(player, from, left, left);
    }

    /**
     * What is left of {@code taken} goes back where it came from. A slot it left empty keeps what came into it meanwhile: the bottle
     * or bowl the last one gave back.
     */
    private static void putBack(Player player, int from, ItemStack taken, ItemStack left) {
        ItemStack kept = left.isEmpty() ? ItemStack.EMPTY : left;
        if (from >= 0) {
            ItemStack now = player.getInventory().getItem(from);
            if (!left.isEmpty() || now == taken || now.isEmpty()) {
                player.getInventory().setItem(from, kept);
            }
        } else {
            player.inventoryMenu.setCarried(kept);
        }
    }
}

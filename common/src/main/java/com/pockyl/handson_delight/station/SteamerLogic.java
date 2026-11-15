package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The steamer's actions at the station: things on and off its racks, a rack opened, a rack, the lid or water added, a recipe from
 * the book. Each goes through the steamer's own code ({@link KitchenSteamer}); this only says what happened, for everybody to see.
 */
public final class SteamerLogic implements StationLogic {
    /** How many things the cook laid are remembered to come back off one by one. */
    private static final int REMEMBERED = 72;

    /** Where the cook laid things, the last one first: rack and place. */
    private final Deque<int[]> laid = new ArrayDeque<>();

    public SteamerLogic(StationContext station) {
    }

    private static SteamerTower tower(StationContext station) {
        return SteamerTower.of(station.level(), station.pos());
    }

    /** Nothing goes on by the library's own moves: every stack comes through {@code put}, the hand's slot stays empty. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    /** The first cook finds the top rack open; whoever comes later sees what the others opened. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (!station.isClientSide() && station.users().size() <= 1) {
            station.state().setControl(SteamerStation.OPEN, SteamerStation.TOP);
            laid.clear();
        }
    }

    /** The last cook gone, the racks go back on the tower. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.isClientSide() && station.users().stream().noneMatch(user -> user != player)) {
            station.state().setControl(SteamerStation.OPEN, SteamerStation.TOP);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        SteamerTower tower = tower(station);
        if (tower == null) {
            return;
        }
        int rack = data.contains(SteamerStation.RACK) ? data.getInt(SteamerStation.RACK) : -1;
        int place = data.contains(SteamerStation.PLACE) ? data.getInt(SteamerStation.PLACE) : -1;
        int from = data.contains(SteamerStation.FROM) ? data.getInt(SteamerStation.FROM) : -1;
        boolean all = data.getBoolean(SteamerStation.ALL);
        switch (action) {
            case SteamerStation.ACTION_PUT -> put(station, tower, player, rack, place, from, all, false);
            case SteamerStation.ACTION_TAKE -> take(station, tower, player, rack >= 0 ? rack : openRack(station, tower), place, all);
            case SteamerStation.ACTION_BACK -> back(station, tower, player);
            case SteamerStation.ACTION_OPEN -> open(station, tower, rack);
            case SteamerStation.ACTION_RACK -> rack(station, tower, player, from);
            case SteamerStation.ACTION_LID -> lid(station, tower, player, from);
            case SteamerStation.ACTION_WATER -> water(station, tower, player, from);
            case SteamerStation.ACTION_FILL -> fill(station, tower, player, data.getString(SteamerStation.DISH), all);
            default -> {
            }
        }
    }

    /** The open rack as the station has it: the one the cook opened, the top one when that is gone or nothing was. */
    public static int openRack(StationContext station, SteamerTower tower) {
        int open = Math.round(station.state().getControl(SteamerStation.OPEN));
        return open < 0 || open >= tower.racks() ? tower.racks() - 1 : open;
    }

    /**
     * One of what the cook works with onto {@code place} of {@code rack} (any free place of it with -1); with {@code all} onto every
     * free place of that rack. Only the rack the cook sees takes things: the closed ones under it and on the stack aside stay as
     * they are.
     */
    private void put(StationContext station, SteamerTower tower, Player player, int rack, int place, int from, boolean all,
            boolean book) {
        ItemStack held = held(player, from);
        if (held.isEmpty() || tower.racks() == 0) {
            return;
        }
        if (!tower.potBlock().handson$takes(held)) {
            refused(station, held);
            return;
        }
        int at = rack >= 0 && rack < tower.racks() ? rack : openRack(station, tower);
        ListTag went = new ListTag();
        boolean aimed = !all && place >= 0 && place < KitchenSteamer.PLACES;
        for (int spot : aimed ? new int[] {place} : new int[] {0, 1, 2, 3}) {
            if (held.isEmpty() || !tower.item(at, spot).isEmpty()) {
                continue;
            }
            ItemStack one = held.copyWithCount(1);
            int target = spot;
            boolean on = tower.blockOf(at).handson$put(tower.localOf(at), spot, held, player);
            // A feast block fills a whole rack from its first place, wherever on the empty rack it was let go.
            if (!on && aimed && spot != 0 && tower.isEmpty(at)) {
                target = 0;
                on = tower.blockOf(at).handson$put(tower.localOf(at), 0, held, player);
            }
            if (on) {
                went.add(laid(station, at, target, one));
                remember(at, target);
                if (!all) {
                    break;
                }
            }
        }
        putBack(player, from, held);
        if (went.isEmpty()) {
            refused(station, held.isEmpty() ? ItemStack.EMPTY : held);
            return;
        }
        CompoundTag data = new CompoundTag();
        data.put(SteamerStation.LAID, went);
        data.putBoolean(SteamerStation.BOOK, book);
        station.effect(SteamerStation.EFFECT_IN, "tower", data);
    }

    private static CompoundTag laid(StationContext station, int rack, int place, ItemStack stack) {
        CompoundTag one = new CompoundTag();
        one.putInt(SteamerStation.RACK, rack);
        one.putInt(SteamerStation.PLACE, place);
        one.put(SteamerStation.STACK, stack.saveOptional(station.level().registryAccess()));
        return one;
    }

    private void remember(int rack, int place) {
        laid.removeIf(it -> it[0] == rack && it[1] == place);
        laid.push(new int[] {rack, place});
        while (laid.size() > REMEMBERED) {
            laid.removeLast();
        }
    }

    /** What lies on {@code place} of {@code rack} comes off to the cook; with {@code all} everything on that rack that is done. */
    private void take(StationContext station, SteamerTower tower, Player player, int rack, int place, boolean all) {
        ListTag went = new ListTag();
        for (int at = 0; at < tower.racks(); at++) {
            for (int spot = 0; spot < KitchenSteamer.PLACES; spot++) {
                boolean wanted = at == rack && (all ? !tower.item(at, spot).isEmpty() && tower.steamed(at, spot) < 0.0F : spot == place);
                if (!wanted) {
                    continue;
                }
                ItemStack taken = tower.blockOf(at).handson$take(tower.localOf(at), spot, player);
                if (!taken.isEmpty()) {
                    went.add(laid(station, at, spot, taken));
                    int a = at;
                    int b = spot;
                    laid.removeIf(it -> it[0] == a && it[1] == b);
                }
            }
        }
        if (!went.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.put(SteamerStation.LAID, went);
            station.effect(SteamerStation.EFFECT_TAKEN, "tower", data);
        }
    }

    /** The last thing the cook laid that is still there comes back off. */
    private void back(StationContext station, SteamerTower tower, Player player) {
        while (!laid.isEmpty()) {
            int[] last = laid.pop();
            if (last[0] < tower.racks() && !tower.item(last[0], last[1]).isEmpty()) {
                take(station, tower, player, last[0], last[1], false);
                return;
            }
        }
    }

    private static void open(StationContext station, SteamerTower tower, int rack) {
        int open = rack < 0 || rack >= tower.racks() - 1 ? SteamerStation.TOP : rack;
        station.state().setControl(SteamerStation.OPEN, open);
    }

    /** A rack onto the top of the tower, which becomes the open one. */
    private static void rack(StationContext station, SteamerTower tower, Player player, int from) {
        ItemStack held = held(player, from);
        if (!SteamerStation.isRack(held) || !tower.hasRoom() || !tower.top().handson$addRack()) {
            refused(station, held);
            return;
        }
        use(player, held);
        putBack(player, from, held);
        station.state().setControl(SteamerStation.OPEN, SteamerStation.TOP);
        CompoundTag data = new CompoundTag();
        data.putInt(SteamerStation.RACK, tower.racks());
        station.effect(SteamerStation.EFFECT_RACK, "tower", data);
    }

    private static void lid(StationContext station, SteamerTower tower, Player player, int from) {
        ItemStack held = held(player, from);
        if (!SteamerStation.isLid(held) || tower.racks() == 0 || tower.isCapped() || !tower.top().handson$cap()) {
            refused(station, held);
            return;
        }
        use(player, held);
        putBack(player, from, held);
        station.effect(SteamerStation.EFFECT_LID, "tower", new CompoundTag());
    }

    private static void water(StationContext station, SteamerTower tower, Player player, int from) {
        ItemStack held = held(player, from);
        if (!held.is(Items.WATER_BUCKET) || !tower.potBlock().handson$fill()) {
            refused(station, held);
            return;
        }
        ItemStack bucket = player.getAbilities().instabuild ? held : new ItemStack(Items.BUCKET);
        putBack(player, from, bucket);
        station.effect(SteamerStation.EFFECT_WATER, "tower", new CompoundTag());
    }

    /**
     * What a recipe picked in the book steams from goes onto the open rack from the inventory, one; with {@code all} as many as
     * there are free places on it and things to lay.
     */
    private void fill(StationContext station, SteamerTower tower, Player player, String id, boolean all) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        SteamerStation.Steaming recipe = SteamerStation.recipes(station.level()).stream().filter(it -> it.id().equals(key)).findFirst()
                .orElse(null);
        if (recipe == null || tower.racks() == 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.items.size() && slot < 0; i++) {
            ItemStack stack = inventory.items.get(i);
            slot = !stack.isEmpty() && recipe.ingredient().test(stack) ? i : -1;
        }
        if (slot < 0) {
            ItemStack[] options = recipe.ingredient().getItems();
            refused(station, options.length > 0 ? options[0] : recipe.result());
            return;
        }
        put(station, tower, player, -1, -1, slot, all, true);
        inventory.setChanged();
    }

    private static void use(Player player, ItemStack held) {
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
    }

    private static void refused(StationContext station, ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.put(SteamerStation.STACK, stack.copyWithCount(1).saveOptional(station.level().registryAccess()));
        station.effect(SteamerStation.EFFECT_REFUSED, "tower", data);
    }

    /** What the cook works with: the stack on the cursor, or the one in inventory slot {@code from}. */
    private static ItemStack held(Player player, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    private static void putBack(Player player, int from, ItemStack left) {
        ItemStack kept = left.isEmpty() ? ItemStack.EMPTY : left;
        if (from >= 0) {
            player.getInventory().setItem(from, kept);
        } else {
            player.inventoryMenu.setCarried(kept);
        }
    }
}

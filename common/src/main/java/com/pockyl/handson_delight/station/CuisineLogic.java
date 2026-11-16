package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * The cuisine board's actions at the station: a step on, a step by hand, the dish taken or cut, the last step back, a recipe from
 * the book. Each goes through the board's own code ({@link KitchenCuisine}); this only says what happened, for everybody to see.
 */
public final class CuisineLogic implements StationLogic {
    /** The most steps one click with shift puts on, and the most dishes one book pick with shift makes. */
    private static final int MOST = 16;

    private final KitchenCuisine board;
    /** The finished dish whose marks are being cut, and how many are cut: they start over when another dish lies there. */
    private ItemStack cutting = ItemStack.EMPTY;
    private int cuts;

    public CuisineLogic(StationContext station) {
        board = KitchenCuisine.of(station.blockEntity());
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
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (board == null) {
            return;
        }
        int from = data.contains(CuisineBoardStation.FROM) ? data.getInt(CuisineBoardStation.FROM) : -1;
        boolean all = data.getBoolean(CuisineBoardStation.ALL);
        followDish(station);
        switch (action) {
            case CuisineBoardStation.ACTION_PUT -> put(station, player, from, all);
            case CuisineBoardStation.ACTION_SHAPE -> shape(station, player);
            case CuisineBoardStation.ACTION_TAKE -> take(station, player);
            case CuisineBoardStation.ACTION_BACK -> back(station, player);
            case CuisineBoardStation.ACTION_CUT -> cut(station, player, from, data.getInt(CuisineBoardStation.MARK), all);
            case CuisineBoardStation.ACTION_FILL -> fill(station, player, data.getString(CuisineBoardStation.DISH), all);
            default -> {
            }
        }
        followDish(station);
    }

    /** The marks start over when the finished dish lying there is not the one they were cut on. */
    private void followDish(StationContext station) {
        ItemStack finished = board.handson$finished();
        if (!ItemStack.matches(finished, cutting)) {
            cutting = finished.copy();
            setCuts(station, 0);
        }
    }

    private void setCuts(StationContext station, int value) {
        cuts = value;
        CompoundTag data = station.state().data();
        if (!data.contains(CuisineBoardStation.CUTS) || data.getInt(CuisineBoardStation.CUTS) != value) {
            data.putInt(CuisineBoardStation.CUTS, value);
            station.state().setData(data);
        }
    }

    /** The stack on the cursor, or in inventory slot {@code from}, as the next step; with {@code all} as many as it takes in a row. */
    private void put(StationContext station, Player player, int from, boolean all) {
        ItemStack held = held(player, from);
        if (held.isEmpty()) {
            return;
        }
        List<CompoundTag> steps = new ArrayList<>();
        for (int i = 0; i < (all ? MOST : 1) && !held.isEmpty(); i++) {
            if (board.handson$put(player, held) <= 0) {
                break;
            }
            steps.add(step(lastOn()));
        }
        putBack(player, from, held);
        if (steps.isEmpty()) {
            refused(station, held);
            return;
        }
        laid(station, steps, false);
    }

    private void shape(StationContext station, Player player) {
        if (board.handson$shape(player)) {
            laid(station, List.of(step(ItemStack.EMPTY)), false);
        }
    }

    private void take(StationContext station, Player player) {
        ItemStack finished = board.handson$finished();
        if (!finished.isEmpty() && board.handson$take(player)) {
            taken(station, finished, false);
        }
    }

    /** The last step comes off, back to the cook: food as it went on (into its container), nothing for a shaping or rolling. */
    private void back(StationContext station, Player player) {
        List<ItemStack> on = board.handson$contents();
        ItemStack back = board.handson$takeBack(player);
        if (back == null) {
            if (!on.isEmpty()) {
                refused(station, on.get(on.size() - 1));
            }
            return;
        }
        // What does not fit drops, and only that: the inventory keeps the part it took.
        ItemStack given = back.copy();
        if (!given.isEmpty() && !player.getInventory().add(given)) {
            player.drop(given, false);
        }
        CompoundTag data = new CompoundTag();
        data.put(CuisineBoardStation.STACK, back.saveOptional(station.level().registryAccess()));
        data.put(CuisineBoardStation.AFTER, contents());
        station.effect(CuisineBoardStation.EFFECT_BACK, "board", data);
    }

    /**
     * Cuts the finished dish at its next mark with the knife on the cursor or in inventory slot {@code from}, or (an empty hand is
     * offered what the inventory holds) the first in the inventory that cuts it; with {@code all} at every mark left. At the last
     * mark the board's own cut makes the pieces, which go to the cook, and the knife wears as that cut wears it.
     */
    private void cut(StationContext station, Player player, int from, int mark, boolean all) {
        ItemStack finished = board.handson$finished();
        if (finished.isEmpty()) {
            return;
        }
        int slot = from;
        ItemStack tool = held(player, from);
        if (!CuisineBoardStation.cuts(station.level(), board, tool)) {
            if (!tool.isEmpty()) {
                refused(station, tool);
                return;
            }
            slot = Cutting.toolSlot(station.level(), finished, player.getInventory());
            tool = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        }
        if (tool.isEmpty()) {
            return;
        }
        int marks = Cutting.plan(station.level(), finished).marks();
        if (!all && mark != cuts) {
            // Not the mark that is next (two clicks at once, or a mark cut by somebody else meanwhile).
            return;
        }
        if (!all && cuts + 1 < marks) {
            setCuts(station, cuts + 1);
            CompoundTag chopped = new CompoundTag();
            chopped.putInt(CuisineBoardStation.MARK, mark);
            station.effect(CuisineBoardStation.EFFECT_CHOPPED, "board", chopped);
            return;
        }
        ItemStack knife = tool;
        List<ItemStack> results = CaughtDrops.catching(false, () -> board.handson$cut(knife));
        if (results == null) {
            refused(station, knife);
            return;
        }
        ItemStack shown = knife.copyWithCount(1);
        if (!player.getAbilities().instabuild && knife.isDamageableItem()) {
            if (player.level() instanceof ServerLevel server) {
                knife.hurtAndBreak(1, server, player instanceof ServerPlayer cook ? cook : null, broken -> {
                });
            }
        }
        putBack(player, slot, knife);
        ListTag pieces = new ListTag();
        for (ItemStack result : results) {
            pieces.add(result.saveOptional(station.level().registryAccess()));
            ItemStack given = result.copy();
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
        }
        CompoundTag data = new CompoundTag();
        data.put(CuisineBoardStation.STACK, finished.saveOptional(station.level().registryAccess()));
        data.put(CuisineBoardStation.TOOL, shown.saveOptional(station.level().registryAccess()));
        data.put(CuisineBoardStation.RESULTS, pieces);
        data.putInt(CuisineBoardStation.MARK, marks - 1);
        station.effect(CuisineBoardStation.EFFECT_CUT, "board", data);
    }

    /**
     * A recipe picked in the book is laid up from the inventory, step after step as its recipe wants them, the hand steps done too.
     * What lies on the board already counts when it is the start of that dish; a finished dish lying there goes to the cook first;
     * anything else stays and the pick is refused. Without enough for the whole dish it lays what it can and the board waits for the
     * rest. With {@code all} each dish made goes to the cook while the inventory holds another whole set, up to {@link #MOST}; the
     * last stays on the board.
     */
    private void fill(StationContext station, Player player, String id, boolean all) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        KitchenCuisine.Dish dish = board.handson$dishes().stream().filter(it -> it.id().equals(key)).findFirst().orElse(null);
        if (dish == null) {
            return;
        }
        int done = done(dish, board.handson$contents());
        if (done < 0 || done == dish.steps().size()) {
            ItemStack finished = board.handson$finished();
            if (finished.isEmpty() || !board.handson$take(player)) {
                List<ItemStack> on = board.handson$contents();
                refused(station, on.isEmpty() ? dish.result() : on.get(on.size() - 1));
                return;
            }
            taken(station, finished, false);
            done = 0;
        }
        List<CompoundTag> steps = new ArrayList<>();
        int made = 0;
        ItemStack lastMade = ItemStack.EMPTY;
        while (true) {
            boolean whole = lay(player, dish, done, made == 0 ? steps : null);
            if (!whole) {
                break;
            }
            made++;
            done = 0;
            if (!all || made >= MOST || !hasSet(board, player, dish, board.handson$contents())) {
                break;
            }
            // Another dish to make: this one goes to the cook, and the board starts again.
            ItemStack finished = board.handson$finished();
            if (finished.isEmpty() || !board.handson$take(player)) {
                break;
            }
            lastMade = finished;
        }
        if (!steps.isEmpty()) {
            laid(station, steps, true);
        }
        if (!lastMade.isEmpty()) {
            taken(station, lastMade.copyWithCount(lastMade.getCount() * (made - 1)), true);
        }
    }

    /**
     * Lays {@code dish}'s steps from {@code from} on: each from the first inventory stack the step takes and the board takes now,
     * or by hand; what went on is told in {@code told} when it is not null. True when every step went on.
     */
    private boolean lay(Player player, KitchenCuisine.Dish dish, int from, List<CompoundTag> told) {
        Inventory inventory = player.getInventory();
        for (int i = from; i < dish.steps().size(); i++) {
            Ingredient step = dish.steps().get(i);
            if (step.isEmpty()) {
                if (!board.handson$shape(player)) {
                    return false;
                }
                if (told != null) {
                    told.add(step(ItemStack.EMPTY));
                }
                continue;
            }
            int slot = slotFor(inventory, step);
            if (slot < 0 || board.handson$put(player, inventory.getItem(slot)) <= 0) {
                return false;
            }
            inventory.setChanged();
            if (told != null) {
                told.add(step(lastOn()));
            }
        }
        return true;
    }

    private int slotFor(Inventory inventory, Ingredient step) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty() && step.test(board.handson$asStep(stack)) && board.handson$takes(stack) > 0) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Whether the inventory holds each step of {@code dish} as many times as it went onto the board for the dish lying there,
     * {@code laid} (the first step from the bare board may take several, a roll its pieces), each from one stack not counted for
     * another step already: the board takes a step from a single stack.
     */
    private static boolean hasSet(KitchenCuisine board, Player player, KitchenCuisine.Dish dish, List<ItemStack> laid) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            // A flask of soy sauce counts as the bottles it gives: each set takes one more serving of it.
            left.add(board.handson$asStep(stack).copy());
        }
        for (int i = 0; i < dish.steps().size(); i++) {
            Ingredient step = dish.steps().get(i);
            if (step.isEmpty()) {
                continue;
            }
            int need = i < laid.size() && step.test(laid.get(i)) ? Math.max(1, laid.get(i).getCount()) : 1;
            ItemStack found = left.stream().filter(stack -> step.test(stack) && stack.getCount() >= need).findFirst().orElse(null);
            if (found == null) {
                return false;
            }
            found.shrink(need);
        }
        return true;
    }

    /**
     * How many of {@code dish}'s steps lie on the board already: what lies there, matched to its first steps (a step done by hand to
     * a hand step, food to a step that takes it, in any order, as a mixed recipe takes its fillings); -1 when it is not the start of
     * that dish.
     */
    public static int done(KitchenCuisine.Dish dish, List<ItemStack> on) {
        if (on.size() > dish.steps().size()) {
            return -1;
        }
        boolean[] used = new boolean[on.size()];
        for (ItemStack laid : on) {
            boolean matched = false;
            for (int i = 0; i < on.size() && !matched; i++) {
                Ingredient step = dish.steps().get(i);
                if (!used[i] && (laid.isEmpty() ? step.isEmpty() : !step.isEmpty() && step.test(laid))) {
                    used[i] = true;
                    matched = true;
                }
            }
            if (!matched) {
                return -1;
            }
        }
        return on.size();
    }

    private ItemStack lastOn() {
        List<ItemStack> on = board.handson$contents();
        return on.isEmpty() ? ItemStack.EMPTY : on.get(on.size() - 1);
    }

    private CompoundTag step(ItemStack laid) {
        CompoundTag step = new CompoundTag();
        step.put(CuisineBoardStation.STACK, laid.saveOptional(board.handson$blockEntity().getLevel().registryAccess()));
        step.put(CuisineBoardStation.AFTER, contents());
        return step;
    }

    private ListTag contents() {
        ListTag list = new ListTag();
        for (ItemStack stack : board.handson$contents()) {
            list.add(stack.saveOptional(board.handson$blockEntity().getLevel().registryAccess()));
        }
        return list;
    }

    private void laid(StationContext station, List<CompoundTag> steps, boolean fromBook) {
        ListTag list = new ListTag();
        list.addAll(steps);
        CompoundTag data = new CompoundTag();
        data.put(CuisineBoardStation.STEPS, list);
        data.putBoolean(CuisineBoardStation.BOOK, fromBook);
        station.effect(CuisineBoardStation.EFFECT_LAID, "board", data);
    }

    private void taken(StationContext station, ItemStack dish, boolean batch) {
        CompoundTag data = new CompoundTag();
        data.put(CuisineBoardStation.STACK, dish.saveOptional(station.level().registryAccess()));
        if (batch) {
            data.put(CuisineBoardStation.AFTER, contents());
        }
        station.effect(CuisineBoardStation.EFFECT_TAKEN, "board", data);
    }

    private static void refused(StationContext station, ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.put(CuisineBoardStation.STACK, stack.copyWithCount(1).saveOptional(station.level().registryAccess()));
        station.effect(CuisineBoardStation.EFFECT_REFUSED, "board", data);
    }

    /** What the cook works with: the stack on the cursor, or the one in inventory slot {@code from}. */
    private static ItemStack held(Player player, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    /**
     * What is left of the stack goes back where it came from. A slot it left empty keeps what came into it meanwhile: the bottle or
     * bowl the last one gave back.
     */
    private static void putBack(Player player, int from, ItemStack left) {
        ItemStack kept = left.isEmpty() ? ItemStack.EMPTY : left;
        if (from >= 0) {
            if (!left.isEmpty() || player.getInventory().getItem(from) == left) {
                player.getInventory().setItem(from, kept);
            }
        } else {
            player.inventoryMenu.setCarried(kept);
        }
    }
}

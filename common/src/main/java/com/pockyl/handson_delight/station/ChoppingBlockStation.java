package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.mixin.RecipeManagerAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sable Trees' chopping block, the stump of a felled tree, as a station: the board's cutting on a stump, with the stump's own rules
 * ({@link KitchenStump}). Food is cut along its marks as on the board, a log stands on it to be split with an axe, and no dish is
 * put together there. Its own type, as the stump stands half a block high: the scene is the board's, raised to its top.
 */
public final class ChoppingBlockStation {
    public static final Identifier ID = HandsOnDelight.id("chopping_block");

    /** Stumps that work as Sable Trees' (its chopping block, optional in the tag). */
    public static final TagKey<Block> STUMPS = TagKey.create(Registries.BLOCK, HandsOnDelight.id("chopping_blocks"));

    /** Sable Trees splits a log into its planks and half as many again: the axe wastes less than the crafting table. */
    public static final double PLANKS_YIELD = 1.5;
    /**
     * A log is split in three blows, each on its own dashed mark across its top as the knife's marks on the board, through its
     * middle as a pie is cut: six wedges. The planks come with the last blow, as the board's pieces come with the last cut.
     */
    public static final Cutting.Plan SPLIT_PLAN = new Cutting.Plan(3, Cutting.Style.WEDGES);

    /** Opens from any side, turned to the cook, as the board; the board's slots, rules and look. */
    public static final StationType TYPE = Diegesis.station(ID)
            .tag(STUMPS)
            .schema(CuttingBoardStation.SCHEMA)
            .logic(BoardLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(ChoppingBlockStation::opens)
            .idleLogic()
            .build();

    /** What each log splits into, per recipe map: the book and the axe over the log ask every frame. */
    private static final Map<Object, Map<Item, ItemStack>> PLANKS = new WeakHashMap<>();

    private ChoppingBlockStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    /**
     * Sneaking keeps the stump's own use, and so does a shovel on a stump that still has its roots, which digs them out. Everything
     * else opens the station.
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || KitchenStump.of(player.level().getBlockEntity(pos)) == null) {
            return false;
        }
        boolean rooted = state.getProperties().stream().anyMatch(property -> property.getName().equals("rooted")
                && Boolean.TRUE.equals(state.getValue(property)));
        return !(rooted && player.getMainHandItem().is(ItemTags.SHOVELS));
    }

    /** Whether {@code log} is split on the stump: a log the crafting table makes planks of. */
    public static boolean splits(Level level, ItemStack log) {
        return !planks(level, log).isEmpty();
    }

    /** Whether {@code tool} splits logs: an axe. */
    public static boolean isAxe(ItemStack tool) {
        return !tool.isEmpty() && tool.is(ItemTags.AXES);
    }

    /** What {@code log} splits into on the stump: its planks, half as many again as the crafting table gives; empty for no log. */
    @SuppressWarnings("unchecked")
    public static ItemStack planks(Level level, ItemStack log) {
        if (log.isEmpty() || !log.is(ItemTags.LOGS) || !(log.getItem() instanceof BlockItem)) {
            return ItemStack.EMPTY;
        }
        synchronized (PLANKS) {
            return PLANKS.computeIfAbsent(KitchenRecipes.source(level), key -> new HashMap<>()).computeIfAbsent(log.getItem(), item -> {
                CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(item)));
                return KitchenRecipes.find(level, RecipeType.CRAFTING, input).map(recipe -> {
                    ItemStack planks = recipe.value().assemble(input, level.registryAccess());
                    planks.setCount((int) Math.round(planks.getCount() * PLANKS_YIELD));
                    return planks;
                }).orElse(ItemStack.EMPTY);
            }).copy();
        }
    }

    /** The inventory slot of an axe: the one in hand first, then the off hand, the hotbar and the rest; -1 without one. */
    public static int axeSlot(Inventory inventory) {
        if (isAxe(inventory.getSelectedItem())) {
            return inventory.getSelectedSlot();
        }
        if (isAxe(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (isAxe(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }
}

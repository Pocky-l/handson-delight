package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.mixin.CookingPotBlockEntityAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules the pot's menu has, the values its screen read and what is new: nothing cooks unless it is stirred. It also runs on the
 * clients around a pot nobody works at, to show what lies in it; there it only reads.
 */
public final class PotLogic implements StationLogic {
    /** How full the pot looks: there is always water, and a little more for every stack in it. */
    private static final int BROTH_BASE = 58;
    private static final int BROTH_PER_STACK = 6;
    /** A finished soup stands high in the pot and sinks serving by serving. */
    private static final int SOUP_BASE = 74;
    private static final int SOUP_PER_SERVING = 5;
    /** How long a stir of the spoon keeps the pot cooking, in ticks: the soup goes on turning for that long. */
    private static final int STIR_TICKS = 40;

    private final CookingPotBlockEntity pot;
    private boolean unsorted;

    public PotLogic(StationContext station) {
        pot = (CookingPotBlockEntity) station.blockEntity();
        ItemStackHandler items = pot.getInventory();
        StationState state = station.state();
        state.bindSlots(CookingPotStation.INGREDIENTS, storage(items, 0, CookingPotBlockEntity.MEAL_DISPLAY_SLOT));
        state.bindSlots(CookingPotStation.MEAL, storage(items, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, 1));
        state.bindSlots(CookingPotStation.SERVED, storage(items, CookingPotBlockEntity.OUTPUT_SLOT, 1));
    }

    private static SlotStorage storage(ItemStackHandler items, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return items.getStackInSlot(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                items.setStackInSlot(start + index, stack);
            }
        };
    }

    /** Whether a player works at the pot through Farmer's Delight's own menu: a client without this mod cannot stir. */
    public static boolean isInMenu(CookingPotBlockEntity pot) {
        if (pot.getLevel() != null) {
            for (Player player : pot.getLevel().players()) {
                if (player.containerMenu instanceof CookingPotMenu menu && menu.blockEntity == pot) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        unsorted = true;
    }

    @Override
    public void tick(StationContext station) {
        if (unsorted && !station.level().isClientSide()) {
            unsorted = false;
            shareOut(station);
        }
        refresh(station);
    }

    /**
     * A recipe wants two tomatoes in two slots, and a stack thrown into the pot lies in one: when what lies in the pot makes a recipe that way,
     * the stacks are shared out over as many slots as the recipe asks for.
     */
    private void shareOut(StationContext station) {
        Level level = station.level();
        ItemStackHandler items = pot.getInventory();
        if (level.getRecipeManager().getRecipeFor(ModRecipeTypes.COOKING.get(), new RecipeWrapper(items), level).isPresent()) {
            return;
        }
        List<ItemStack> kinds = new ArrayList<>();
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                kinds.stream().filter(kind -> ItemStack.isSameItemSameTags(kind, stack)).findFirst()
                        .ifPresentOrElse(kind -> kind.grow(stack.getCount()), () -> kinds.add(stack.copy()));
            }
        }
        for (CookingPotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            int[] parts = parts(recipe, kinds);
            if (parts != null) {
                lay(items, kinds, parts);
                return;
            }
        }
    }

    /** In how many slots every kind must lie for the recipe; null when what is there does not make it, all of it and nothing else. */
    private static int[] parts(CookingPotRecipe recipe, List<ItemStack> kinds) {
        int[] parts = new int[kinds.size()];
        for (Ingredient ingredient : recipe.getIngredients()) {
            int fits = -1;
            for (int kind = 0; kind < parts.length; kind++) {
                boolean free = parts[kind] < kinds.get(kind).getCount();
                if (free && ingredient.test(kinds.get(kind)) && (fits < 0 || parts[kind] < parts[fits])) {
                    fits = kind;
                }
            }
            if (fits < 0) {
                return null;
            }
            parts[fits]++;
        }
        for (int kind = 0; kind < parts.length; kind++) {
            // What will not go into the slots it gets stays as it is.
            if (parts[kind] == 0 || (kinds.get(kind).getCount() + parts[kind] - 1) / parts[kind] > kinds.get(kind).getMaxStackSize()) {
                return null;
            }
        }
        return parts;
    }

    private static void lay(ItemStackHandler items, List<ItemStack> kinds, int[] parts) {
        int slot = 0;
        for (int kind = 0; kind < parts.length; kind++) {
            int left = kinds.get(kind).getCount();
            for (int part = parts[kind]; part > 0; part--) {
                int share = (left + part - 1) / part;
                items.setStackInSlot(slot++, kinds.get(kind).copyWithCount(share));
                left -= share;
            }
        }
        while (slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT) {
            items.setStackInSlot(slot++, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return !slot.equals(CookingPotStation.MEAL_SLOT);
    }

    /** What a soup is served in does not go into the water. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        for (CookingPotRecipe recipe : station.level().getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            if (ItemStack.isSameItem(recipe.getOutputContainer(), stack)) {
                return false;
            }
        }
        return true;
    }

    /** How many times the ingredients in the pot make the recipe: every slot gives one item to a serving. */
    private int servingsLeft(StationState state) {
        int left = Integer.MAX_VALUE;
        for (ItemStack stack : state.getItems(CookingPotStation.INGREDIENTS)) {
            if (!stack.isEmpty()) {
                left = Math.min(left, stack.getCount());
            }
        }
        return left == Integer.MAX_VALUE ? 0 : left;
    }

    /** What the menu's result slot does when a player takes a serving: experience, recipes unlocked. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(CookingPotStation.SERVED_SLOT)) {
            served(station, player, stack);
        }
    }

    private void served(StationContext station, Player player, ItemStack stack) {
        stack.onCraftedBy(station.level(), player, stack.getCount());
        pot.awardUsedRecipes(player, pot.getDroppableInventory());
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (CookingPotStation.ACTION_SCOOP.equals(action)) {
            scoop(station, player, data.getBoolean(CookingPotStation.SCOOP_ALL));
        } else if (CookingPotStation.ACTION_STIR.equals(action)) {
            ((PotStirring) pot).handson$stir(station.level().getGameTime() + STIR_TICKS);
            StationState state = station.state();
            state.setControl(CookingPotStation.SPOON_X, Mth.clamp(data.getFloat(CookingPotStation.STIR_X), -1.0F, 1.0F));
            state.setControl(CookingPotStation.SPOON_Z, Mth.clamp(data.getFloat(CookingPotStation.STIR_Z), -1.0F, 1.0F));
        }
    }

    /** A container on the player's cursor takes a serving, or all of them do while there is soup; the filled ones go to the inventory. */
    private void scoop(StationContext station, Player player, boolean all) {
        int scooped = 0;
        boolean filled;
        do {
            ItemStack carried = player.inventoryMenu.getCarried();
            ItemStack serving = pot.useHeldItemOnMeal(carried);
            filled = !serving.isEmpty();
            if (filled) {
                scooped++;
                served(station, player, serving);
                if (carried.isEmpty()) {
                    player.inventoryMenu.setCarried(serving);
                } else if (!player.getInventory().add(serving)) {
                    player.drop(serving, false);
                }
            }
        } while (all && filled);
        if (scooped > 0) {
            station.effect(CookingPotStation.EFFECT_SCOOPED, "broth", new CompoundTag());
        }
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        ContainerData data = ((CookingPotBlockEntityAccessor) pot).handson$data();
        int time = data.get(0);
        int total = data.get(1);
        // More servings cook faster, by the square root: four take twice the time of one, not four times, and a pot of them is still work.
        // The dial shows the whole of what fits in the pot.
        int left = servingsLeft(state);
        ItemStack meal = pot.getMeal();
        float speed = (float) Math.sqrt(Math.max(1, left + meal.getCount()));
        ((PotStirring) pot).handson$setSpeed(left > 0 ? speed : 1.0F);
        int fits = Math.min(left + meal.getCount(), meal.isEmpty() ? left : meal.getMaxStackSize());
        float serving = total > 0 ? Math.min(1.0F, (float) time / total) : 0.0F;
        float progress = left > 0 && fits > 0 ? Math.min(1.0F, (meal.getCount() + serving) / fits) : 0.0F;
        state.setControl(CookingPotStation.PROGRESS, progress);
        state.setControl(CookingPotStation.HEATED, pot.isHeated() ? 1.0F : 0.0F);
        boolean stirred = ((PotStirring) pot).handson$isStirred(station.level().getGameTime());
        state.setControl(CookingPotStation.STIRRING, stirred ? 1.0F : 0.0F);
        if (!stirred) {
            state.setControl(CookingPotStation.SPOON_X, 0.0F);
            state.setControl(CookingPotStation.SPOON_Z, 0.0F);
        }
        CompoundTag own = new CompoundTag();
        own.putInt(CookingPotStation.COOK_LEFT, Math.round(total * fits * (1.0F - progress) / speed));
        state.setData(own);
        int stacks = 0;
        for (ItemStack stack : state.getItems(CookingPotStation.INGREDIENTS)) {
            stacks += stack.isEmpty() ? 0 : 1;
        }
        int level = BROTH_BASE + BROTH_PER_STACK * stacks;
        if (!pot.getMeal().isEmpty()) {
            level = Math.max(level, Math.min(100, SOUP_BASE + SOUP_PER_SERVING * pot.getMeal().getCount()));
        }
        state.setTank(CookingPotStation.BROTH, TankContents.of(HandsOnDelight.id("broth"), level));
    }
}

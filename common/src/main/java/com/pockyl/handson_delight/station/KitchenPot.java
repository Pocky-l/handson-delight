package com.pockyl.handson_delight.station;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.platform.Platform;

import java.util.List;
import java.util.Optional;

/**
 * A pot that cooks Farmer's Delight's recipes: Farmer's Delight's own and the ones other mods copy from it (Miner's Delight's copper
 * pot, Dungeons Delight's monster pot with recipes of its own). Mixed into each pot's block entity, so that the station, the stirring and the batches work the same at all of them.
 * Slots are counted as the pot's own inventory has them: the ingredients, then the meal, the container and the served dish.
 */
public interface KitchenPot {
    /** What this pot knows about stirring, batches and stars. */
    PotCooking handson$cooking();

    /**
     * The recipes this pot cooks: Farmer's Delight's, or for a pot with recipes of its own (Dungeons Delight's monster cooking) those,
     * read as Farmer's Delight's, which they copy field for field.
     */
    default List<CookingPotRecipe> handson$recipes(Level level) {
        return Platform.INSTANCE.cookingRecipes(level);
    }

    /** The recipe that {@code items}, the pot's ingredient slots, make in this pot. */
    default Optional<CookingPotRecipe> handson$findRecipe(Level level, List<ItemStack> items) {
        return Platform.INSTANCE.findRecipe(level, items);
    }

    /** The recipes the pot of {@code entity} cooks, or Farmer's Delight's where there is none. */
    static List<CookingPotRecipe> recipes(BlockEntity entity, Level level) {
        KitchenPot pot = of(entity);
        return pot == null ? Platform.INSTANCE.cookingRecipes(level) : pot.handson$recipes(level);
    }

    /** What {@code items} make in the pot of {@code entity}, or in Farmer's Delight's pot where there is none. */
    static Optional<CookingPotRecipe> findRecipe(BlockEntity entity, Level level, List<ItemStack> items) {
        KitchenPot pot = of(entity);
        return pot == null ? Platform.INSTANCE.findRecipe(level, items) : pot.handson$findRecipe(level, items);
    }

    /** How many ingredient slots the pot has; the meal lies in the slot after them. */
    int handson$slots();

    ItemStack handson$getItem(int slot);

    void handson$setItem(int slot, ItemStack stack);

    ItemStack handson$meal();

    boolean handson$isContainerValid(ItemStack container);

    /** Whether the pot serves its dishes in {@code stack} besides what the recipes say (copper cups): it does not go into the water. */
    boolean handson$servesIn(ItemStack stack);

    /** The container takes a serving of the meal: the filled dish, or nothing. */
    ItemStack handson$serve(ItemStack container);

    boolean handson$isHeated();

    /** The cook time the pot's menu reads: 0 = ticks done, 1 = ticks the recipe takes. */
    ContainerData handson$data();

    /** What the result slot of the pot's menu gives a player who takes a serving: experience, recipes unlocked. */
    void handson$awardRecipes(Player player);

    /** Whether a player works at this pot through its own menu, which has no spoon. */
    boolean handson$isInMenu();

    /**
     * Whether the block is the pot the station opens. Other mods may build pots on the same class; those keep cooking their own
     * way, with no spoon to stir them.
     */
    boolean handson$isOurs();

    /** What the recipe makes in this pot: a pot may serve a dish of its own instead (soups in copper cups). */
    ItemStack handson$resultOf(CookingPotRecipe recipe, RegistryAccess access);

    /** What the recipe's dish is served in at this pot. */
    ItemStack handson$containerOf(CookingPotRecipe recipe, RegistryAccess access);

    /** What {@code recipe} makes at the pot of {@code entity}, or as the recipe says where there is none. */
    static ItemStack resultOf(BlockEntity entity, CookingPotRecipe recipe, RegistryAccess access) {
        KitchenPot pot = of(entity);
        return pot == null ? recipe.getResultItem(access) : pot.handson$resultOf(recipe, access);
    }

    /** What {@code recipe}'s dish is served in at the pot of {@code entity}, or as the recipe says where there is none. */
    static ItemStack containerOf(BlockEntity entity, CookingPotRecipe recipe, RegistryAccess access) {
        KitchenPot pot = of(entity);
        return pot == null ? recipe.getOutputContainer() : pot.handson$containerOf(recipe, access);
    }

    int handson$cookTime();

    void handson$setCookTime(int ticks);

    int handson$cookTimeTotal();

    /** The pot's own cooking step for one serving, with the pot's own checks skipped: it serves when the time is up. */
    boolean handson$cookOnce(CookingPotRecipe recipe);

    /** Throws out what an ingredient left (a milk bucket's bucket), as the pot does without a cook at it. */
    void handson$throwOut(ItemStack remainder);

    BlockEntity handson$blockEntity();

    /** The pot of a block entity, or null for anything else. */
    static KitchenPot of(BlockEntity entity) {
        return entity instanceof KitchenPot pot ? pot : null;
    }
}

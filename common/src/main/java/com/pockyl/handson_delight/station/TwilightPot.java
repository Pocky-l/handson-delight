package com.pockyl.handson_delight.station;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;

import java.util.Set;

/**
 * Twilight's Flavor & Delight hurries Farmer's Delight's pot along (its mixin into the pot's step) for the dishes of the Twilight Forest
 * and of its own mod: its fiery cooking pot cooks those that take longer in ten seconds, and a pot on its maze stove cooks them in half the
 * time. It does so by adding to the time cooked every step, which the station's stirring adds to as well; so the station's badge and book
 * count those steps in, as this reads them.
 */
public final class TwilightPot {
    private static final ResourceLocation MAZE_STOVE = ResourceLocation.fromNamespaceAndPath(FarmersDelightContent.TWILIGHT_DELIGHT,
            "maze_stove");
    private static final Set<String> FAST = Set.of("twilightforest", FarmersDelightContent.TWILIGHT_DELIGHT);
    /** The most a Twilight dish takes in the fiery pot, in ticks. */
    private static final int FIERY_MOST = 200;

    private TwilightPot() {
    }

    /**
     * How much Twilight's Flavor & Delight adds to the time {@code pot} has cooked {@code recipe} every step, besides the step's own tick:
     * its own sum, 0 for anything it leaves alone.
     */
    public static int extraPerStep(BlockEntity pot, CookingPotRecipe recipe, RegistryAccess access) {
        Level level = pot == null ? null : pot.getLevel();
        if (level == null || recipe == null || !Platform.INSTANCE.isLoaded(FarmersDelightContent.TWILIGHT_DELIGHT)) {
            return 0;
        }
        boolean fiery = pot.getBlockState().is(FarmersDelightContent.FIERY_POT.get());
        boolean maze = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pot.getBlockPos().below()).getBlock()).equals(MAZE_STOVE);
        ItemStack result = recipe.getResultItem(access);
        if (!fiery && !maze || result.isEmpty() || !FAST.contains(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace())) {
            return 0;
        }
        int time = recipe.getCookTime();
        if (fiery) {
            time = Math.min(FIERY_MOST, time);
        }
        if (maze) {
            time /= 2;
        }
        return recipe.getCookTime() / Math.max(1, time) - 1;
    }
}

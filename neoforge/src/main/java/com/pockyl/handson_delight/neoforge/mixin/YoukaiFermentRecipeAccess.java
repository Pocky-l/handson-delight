package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

/**
 * A fermenting recipe of Youkai's Feasts' tank: what goes in (one a slot), the liquid it wants and the one it makes, the things it
 * makes and its time. Reached by name: the class builds on the L2 libraries nested in that mod's jar.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe", remap = false)
public interface YoukaiFermentRecipeAccess {
    @Accessor("ingredients")
    ArrayList<Ingredient> handson$ingredients();

    @Accessor("results")
    ArrayList<ItemStack> handson$results();

    @Accessor("inputFluid")
    FluidStack handson$inputFluid();

    @Accessor("outputFluid")
    FluidStack handson$outputFluid();

    @Accessor("time")
    int handson$time();
}

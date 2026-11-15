package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A brewing recipe of Youkai's Feasts' kettle: what steeps, for how long once the water boils, and the drink it makes. */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.kettle.KettleRecipe", remap = false)
public interface YoukaiKettleRecipeAccess {
    @Accessor("input")
    List<Ingredient> handson$input();

    @Accessor("time")
    int handson$time();

    @Accessor("result")
    FluidStack handson$result();
}

package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.workstations.vat.recipes.VatRecipe;
import com.lance5057.extradelight.workstations.vat.recipes.VatRecipeWrapper;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.ArrayList;
import java.util.List;

/**
 * An aging recipe of Extra Delight's vat matches with no containers in the vat: what it makes then waits in the vat as servings
 * ({@code EdVatMixin}), drawn into the containers when it is done. With containers in their slot it matches as it always does.
 */
@Pseudo
@Mixin(value = VatRecipe.class, remap = false)
abstract class EdVatRecipeMixin {
    @Shadow
    @Final
    NonNullList<Ingredient> ingredients;
    @Shadow
    @Final
    SizedFluidIngredient fluid;

    @Inject(method = "matches(Lcom/lance5057/extradelight/workstations/vat/recipes/VatRecipeWrapper;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true)
    private void handson$withoutContainers(VatRecipeWrapper input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !HandsOnConfig.edVat() || !input.getItem(KitchenEdVat.CONTAINER_SLOT).isEmpty()) {
            return;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < KitchenEdVat.INGREDIENTS; slot++) {
            if (!input.getItem(slot).isEmpty()) {
                inputs.add(input.getItem(slot));
            }
        }
        cir.setReturnValue(inputs.size() == ingredients.size() && RecipeMatcher.findMatches(inputs, ingredients) != null
                && fluid.test(input.getTank().getFluid()));
    }
}

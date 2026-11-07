package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlocks;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * The cooking pot as a station. Its slots are windows on the pot's own inventory: hoppers reach it and it keeps its items whoever is
 * at it. The block is the pot, open in the world too (the mod's blockstate): the scene only adds what is in it, for everybody around.
 */
public final class CookingPotStation {
    public static final ResourceLocation ID = HandsOnDelight.id("cooking_pot");

    public static final String INGREDIENTS = "ingredients";
    public static final String MEAL = "meal";
    public static final String SERVED = "served";
    public static final String BROTH = "broth";
    public static final String PROGRESS = "progress";
    public static final String HEATED = "heated";
    /** On while the pot counts as stirred; where the spoon's end is, -1 to 1 of its reach in the pot's own axes. */
    public static final String STIRRING = "stirring";
    public static final String SPOON_X = "spoon_x";
    public static final String SPOON_Z = "spoon_z";
    /** What the logic tells the clients in the station's data: how long cooking still takes, in ticks. */
    public static final String COOK_LEFT = "cook_left";

    public static final SlotRef MEAL_SLOT = SlotRef.of(MEAL, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);

    /** A custom action: the container on the player's cursor takes a serving of the soup. */
    public static final String ACTION_SCOOP = "scoop";
    /** A custom action: the spoon moved; data: where its end is now, {@link #STIR_X} and {@link #STIR_Z}. */
    public static final String ACTION_STIR = "stir";
    /** Data of {@link #ACTION_SCOOP}: the whole stack on the cursor, not one container. */
    public static final String SCOOP_ALL = "all";
    public static final String STIR_X = "x";
    public static final String STIR_Z = "z";
    /** Played when a serving leaves the pot, for everybody who sees it. */
    public static final ResourceLocation EFFECT_SCOOPED = HandsOnDelight.id("scooped");

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, SlotRules.DEFAULT.kept().accepts(stack -> !isDish(stack)))
            .slots(MEAL, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(BROTH, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(HEATED, ControlSpec.toggle(false).locked())
            .control(STIRRING, ControlSpec.toggle(false).locked())
            .control(SPOON_X, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .control(SPOON_Z, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .build();

    public static final StationType TYPE = Diegesis.station(ID)
            .block(ModBlocks.COOKING_POT)
            .schema(SCHEMA)
            .logic(PotLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .idleLogic()
            .build();

    private CookingPotStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    /** Dishes are for scooping, they do not go into the water. Modded ones are found in the recipes, by {@code PotLogic.mayPlace}. */
    private static boolean isDish(ItemStack stack) {
        return stack.is(Items.BOWL) || stack.is(Items.GLASS_BOTTLE);
    }

    /** Sneaking keeps the pot's handle toggle; a bowl in the hand keeps its quick serving. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return !(player.level().getBlockEntity(pos) instanceof CookingPotBlockEntity pot) || pot.getMeal().isEmpty() || !pot.isContainerValid(held);
    }
}

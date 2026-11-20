package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * Extra Delight's mixing bowl as a station: what is mixed flies in from the cook and lies in the bowl, liquids are poured in from
 * their bottles and buckets, eggs cracked over the rim; the spoon or whisk from the cook's inventory stands in the bowl, a click
 * takes it in hand and every circle it goes round the bowl is a stir. When the recipe's stirs are done, the bowl gives the result as
 * it always does, in its container from the cook's inventory. A whole bowlful is mixed at once. Sneaking keeps the bowl's own menu.
 */
public final class BowlStation {
    public static final Identifier ID = HandsOnDelight.id("mixing_bowl");

    public static final String INGREDIENTS = "ingredients";
    /** How far the stirring is, 0 to 1 of the stirs the bowlful needs. */
    public static final String PROGRESS = "progress";
    /** Where the end of the spoon is in the bowl, -1 to 1 of its reach across and along, as the cook stirring it last told it. */
    public static final String SPOON_X = "spoon_x";
    public static final String SPOON_Z = "spoon_z";
    /** On (1) while somebody stirs: the spoon is held in the bowl for everybody, what lies in it goes round. */
    public static final String STIRRING = "stirring";
    /** What the bowl holds as liquid for the scene: how full, in hundredths of its inside; the colour is the client's. */
    public static final String BATTER = "batter";
    public static final Identifier BATTER_LIQUID = HandsOnDelight.id("batter");

    /**
     * A custom action: the stack in an inventory slot ({@link #FROM}; -1 the cursor) goes into the bowl: a liquid is poured, anything
     * else goes in as it is, the whole stack or with {@link #ONE} one of it. With {@link #SEPARATE} eggs are cracked so that only their
     * white goes in and the yolk comes back, as the bowl's own click cracks them.
     */
    public static final String ACTION_PUT = "put";
    public static final String FROM = "from";
    public static final String ONE = "one";
    public static final String SEPARATE = "separate";
    /** A custom action: a liquid drawn back out of the bowl into the empty container at {@link #FROM}; with {@link #ALL} as many as go. */
    public static final String ACTION_DRAW = "draw";
    /** A custom action: a recipe picked in the book ({@link #DISH}); its food and liquids go in from the inventory. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /**
     * A custom action: the spoon held by the cook is at {@link #X}, {@link #Z} in the bowl (-1 to 1 of its reach); the utensil is the
     * one at {@link #FROM} in their inventory, -1 the one on the cursor.
     */
    public static final String ACTION_STIR = "stir";
    public static final String X = "x";
    public static final String Z = "z";

    /** In the station's data: the stirs the bowlful needs, those done, the sets it makes, the recipe it makes (its id). */
    public static final String NEED = "need";
    public static final String STIRRED = "stirred";
    public static final String SETS = "sets";
    public static final String MAKING = "making";
    /**
     * In the station's data: a count that goes up whenever something is poured or cracked in, and what it was poured from (item ids,
     * {@code minecraft:egg} for an egg cracked for its white), for every client to show it.
     */
    public static final String POURED_COUNT = "poured_n";
    public static final String POURED = "poured";
    /** In the station's data: a count that goes up whenever the bowl gives what it made, what it was (an item id) and how many. */
    public static final String MADE_COUNT = "made_n";
    public static final String MADE = "made";
    public static final String MADE_HOW_MANY = "made_count";
    /** In the station's data: the container the stirred bowlful waits for, when the cook has none (an item id). */
    public static final String WANTS = "wants";

    public static final Supplier<Block> BOWL = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(KitchenBowl.MOD_ID,
            "mixing_bowl"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, KitchenBowl.PLACES, SlotRules.DEFAULT.kept())
            .tank(BATTER, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(SPOON_X, ControlSpec.range(-1.0F, 1.0F, 0.0F).locked())
            .control(SPOON_Z, ControlSpec.range(-1.0F, 1.0F, 0.0F).locked())
            .control(STIRRING, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
            .build();

    /** The bowl is open in the world: the scene adds what lies in it and the spoon, its own model stays the block's. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(BOWL)
            .schema(SCHEMA)
            .logic(BowlLogic::new)
            // Round from every side: worked from where the cook stands.
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(BowlStation::opens)
            .build();

    private BowlStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenBowl.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    /** Sneaking keeps the bowl's own menu. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenBowl.of(player.level().getBlockEntity(pos)) != null;
    }
}

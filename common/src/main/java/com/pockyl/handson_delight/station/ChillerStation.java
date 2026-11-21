package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * Extra Delight's chiller as a station: an old ice box whose three painted doors are real. Behind the tall door the cold chamber,
 * with a rack for the container (a mold, a tin, a bowl, sticks) and what goes into it, and a rack above for what has set; behind the
 * upper small door the ice, behind the lower one the drip pan the melt water runs into. Liquids are poured into the container, ice
 * is laid in the chest, and a full pan is emptied with a bucket or tipped out. Sneaking keeps its own menu.
 */
public final class ChillerStation {
    public static final ResourceLocation ID = HandsOnDelight.id("chiller");

    public static final String INGREDIENTS = "ingredients";
    public static final String CONTAINER = "container";
    public static final String SERVED = "served";
    public static final String ICE = "ice";
    /** The liquid in the container on the rack, for the scene: hundredths of what one setting takes. */
    public static final String LIQUID = "liquid";
    /** The melt water in the drip pan, hundredths of the pan. */
    public static final String MELT = "melt";
    /** How far what is in it has set, 0 to 1. */
    public static final String PROGRESS = "progress";

    /**
     * A custom action: the stack in inventory slot {@link #FROM} (-1: on the cursor) goes where it belongs: ice into the chest, a
     * container onto the rack, a liquid into the tank, an empty bottle or bucket draws the liquid back, anything else into the four
     * places. {@link #ALL}: the whole stack (of a liquid: every container that fits), else one.
     */
    public static final String ACTION_PUT = "put";
    /** A custom action: a recipe picked in the book; its container, liquid, things and ice go in from the inventory. */
    public static final String ACTION_FILL = "fill";
    /**
     * A custom action at the drip pan: the container in slot {@link #FROM} (-1: on the cursor) is filled from it; with -2 a bucket
     * from the inventory when the pan is full, else the pan is tipped out.
     */
    public static final String ACTION_PAN = "pan";
    public static final String FROM = "from";
    public static final String ALL = "all";
    public static final String DISH = "dish";

    /** The station's data for the clients: the liquid and its millibuckets, the cold left and of the piece, the pan, ticks to set. */
    public static final String FLUID = "fluid";
    public static final String AMOUNT = "amount";
    public static final String CHILL = "chill";
    public static final String CHILL_TOTAL = "chill_total";
    public static final String PAN = "pan";
    public static final String LEFT = "left";
    /** The recipe being set up, by id, empty for none; {@link #SETS}: whether all it needs but cold is there. */
    public static final String MAKING = "making";
    public static final String SETS = "sets";
    /** Counts the times the pan was tipped out, so that every client sees it go. */
    public static final String TIPPED = "tipped";

    public static final SlotRef CONTAINER_SLOT = SlotRef.of(CONTAINER, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final SlotRef ICE_SLOT = SlotRef.of(ICE, 0);

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, KitchenChiller.PLACES, SlotRules.DEFAULT.kept())
            .slots(CONTAINER, 1, SlotRules.DEFAULT.kept())
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(ICE, 1, SlotRules.DEFAULT.kept())
            .tank(LIQUID, TankSpec.of(100))
            .tank(MELT, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .build();

    /**
     * The chiller's block is hidden while somebody works at it: the scene shows the ice box with its doors open. While nobody does,
     * the block is the box with its doors shut; Extra Delight sends its content to the clients near it, and each of them reads it
     * with its own copy of the logic for the mist and the drips the scene shows of it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(KitchenChiller::block)
            .schema(SCHEMA)
            .logic(ChillerLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(ChillerStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private ChillerStation() {
    }

    public static void register() {
        if (KitchenChiller.access() != null) {
            Diegesis.register(TYPE);
        }
    }

    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenChiller.of(player.level().getBlockEntity(pos)) != null;
    }
}

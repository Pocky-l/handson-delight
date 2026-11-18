package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.List;

/**
 * Extra Delight's vat as a station: what ages is poured and dropped into it, the thing a stage wants is shaken in when its time comes,
 * and Extra Delight's lid block is lifted off and put back on as each stage wants it. What is made waits in the vat until the cook
 * draws it into its containers. A click on the lid on top opens it too; sneaking keeps Extra Delight's own uses (its menu, the lid
 * taken up).
 */
public final class EdVatStation {
    public static final ResourceLocation ID = HandsOnDelight.id("ed_vat");

    public static final String INGREDIENTS = "ingredients";
    /** Extra Delight's container slot: the station never puts containers there, a cook draws what is made into them. */
    public static final String CONTAINER = "container";
    /** The place of what the stage under way wants (salt, yeast): one at a time, as the vat uses one a stage. */
    public static final String STAGE = "stage";
    public static final String SERVED = "served";
    /** What was made without its containers, waiting in the vat to be drawn into them. */
    public static final String SERVINGS = "servings";
    /** The liquid in the vat, and what was made waiting in it, for the scene to draw: hundredths of its inside. */
    public static final String BREW = "brew";
    /** How far the stage under way has aged, 0 to 1. */
    public static final String PROGRESS = "progress";
    /** Where Extra Delight's lid is: {@link #LID_NONE}, {@link #LID_ASIDE} (lifted off by the station) or {@link #LID_ON}. */
    public static final String LID = "lid";
    public static final int LID_NONE = 0;
    public static final int LID_ASIDE = 1;
    public static final int LID_ON = 2;

    /** A custom action: the lid comes off, or goes on (the one set aside, else one the cook brings). */
    public static final String ACTION_LID = "lid";
    /**
     * A custom action: the stack in inventory slot {@link #FROM} (-1: on the cursor) goes into the vat: a liquid is poured into its
     * tank, the thing of a stage into the stage place, the lid onto the vat, anything else into the six places; {@link #ALL} the
     * whole stack, else one.
     */
    public static final String ACTION_PUT = "put";
    /** A custom action: a recipe picked in the book; its liquid, ingredients and first stage's thing go in from the inventory. */
    public static final String ACTION_FILL = "fill";
    /** A custom action: what was made goes to the cook, a serving drawn into each container they bring ({@link #FROM}). */
    public static final String ACTION_TAKE = "take";
    /** A custom action: an empty container ({@link #FROM}) draws the liquid back out of the vat. */
    public static final String ACTION_SCOOP = "scoop";
    public static final String FROM = "from";
    public static final String ALL = "all";
    public static final String DISH = "dish";

    /** The station's data for the clients: the stage under way and how many, ticks left of it, the liquid and its millibuckets. */
    public static final String STAGE_NOW = "stage";
    public static final String STAGES = "stages";
    public static final String LEFT = "left";
    public static final String FLUID = "fluid";
    public static final String AMOUNT = "amount";
    /** The recipe under way, by id, empty for none. */
    public static final String MAKING = "making";

    public static final SlotRef CONTAINER_SLOT = SlotRef.of(CONTAINER, 0);
    public static final SlotRef STAGE_SLOT = SlotRef.of(STAGE, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final SlotRef SERVINGS_SLOT = SlotRef.of(SERVINGS, 0);

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, KitchenEdVat.INGREDIENTS, SlotRules.DEFAULT.kept())
            .slots(CONTAINER, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(STAGE, 1, SlotRules.DEFAULT.kept().maxStack(1))
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(SERVINGS, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(BREW, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(LID, ControlSpec.stepped(0.0F, LID_ON, LID_ON, 0.0F).locked())
            .build();

    /**
     * The vat's block is hidden while somebody works at it, and the lid block on it with it: the scene shows the open vat and moves
     * the lid. While nobody does, both are the blocks themselves; Extra Delight sends the vat's content to the clients near it, and
     * each of them reads it with its own copy of the logic for what the scene shows of it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(KitchenEdVat::vat)
            .block(KitchenEdVat::lid)
            .anchor(EdVatStation::vatUnder)
            .hidesBlocks(EdVatStation::lidAbove)
            .schema(SCHEMA)
            .logic(EdVatLogic::new)
            // Worked from where the cook stands; the vat's handles stay where they are in the world.
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(EdVatStation::opens)
            .idleLogic()
            .build();

    private EdVatStation() {
    }

    public static void register() {
        if (KitchenEdVat.access() != null) {
            Diegesis.register(TYPE);
        }
    }

    /** A lid opens the station of the vat it stands on; a vat its own. */
    private static BlockPos vatUnder(BlockGetter level, BlockPos pos, BlockState state) {
        return KitchenEdVat.isLid(state) && level.getBlockState(pos.below()).is(KitchenEdVat.vat()) ? pos.below() : pos;
    }

    private static List<BlockPos> lidAbove(BlockGetter level, BlockPos vat) {
        return KitchenEdVat.lidOn(level, vat) ? List.of(vat.above()) : List.of();
    }

    /** Sneaking keeps the vat's own menu, and a lid's own use: taken up into the hand. A lid on nothing is only a lid. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenEdVat.of(player.level().getBlockEntity(pos)) != null;
    }
}

package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * Extra Delight's melting pot as a station: a double boiler over the heat, its upper pot open. Chocolate dropped in melts and swirls
 * into the syrup that stands in it, and a bottle or bucket brought to it dips in and comes up full. Sneaking keeps its own menu.
 */
public final class MeltingPotStation {
    public static final Identifier ID = HandsOnDelight.id("melting_pot");

    /** What melts: one stack, a piece at a time. */
    public static final String INPUT = "input";
    /** What has melted, for the scene: how full the upper pot is, in hundredths. */
    public static final String MELT = "melt";
    public static final String PROGRESS = "progress";
    /** A custom action: the stack on the cursor ({@link #FROM} -1) or in an inventory slot goes in to melt. */
    public static final String ACTION_PUT = "put";
    public static final String FROM = "from";
    /** A custom action: a recipe picked in the book; what melts into it goes in from the inventory. Data: {@link #DISH}, {@link #ALL}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /** A custom action: what has melted is drawn into the containers on the cursor or in slot {@link #FROM}, one or all. */
    public static final String ACTION_DRAW = "draw";
    /** What the logic tells the clients in the station's data: ticks still to melt, the liquid, whether it is heated. */
    public static final String LEFT = "left";
    public static final String FLUID = "fluid";
    public static final String HEATED = "heated";
    public static final SlotRef INPUT_SLOT = SlotRef.of(INPUT, 0);
    public static final Supplier<Block> POT = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(KitchenMeltingPot.MOD_ID,
            "melting_pot"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INPUT, 1, SlotRules.DEFAULT.kept())
            .tank(MELT, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .build();

    public static final StationType TYPE = Diegesis.station(ID)
            .block(POT)
            .schema(SCHEMA)
            .logic(MeltingPotLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(MeltingPotStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private MeltingPotStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenMeltingPot.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenMeltingPot.of(player.level().getBlockEntity(pos)) != null;
    }
}

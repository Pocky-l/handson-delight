package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * Barbeque's Delight's ingredients basin as a station: food goes into its two compartments, and a stick laid over them takes its
 * pieces one after another, the food from one compartment and the side from the other, into a skewer for the cook. Its compartments
 * are windows on the basin's own: what lies in them stays the basin's.
 */
public final class BasinStation {
    public static final Identifier ID = HandsOnDelight.id("basin");

    /** The basin's two compartments, one stack each. */
    public static final String COMPARTMENTS = "compartments";
    /** Where a stack let go over the basin is caught: one slot that never keeps anything. */
    public static final String HAND = "hand";

    /**
     * Custom actions. {@code put}: the stack on the cursor, or in inventory slot {@link #FROM}, goes into compartment {@link #PLACE}
     * (with no place, the one holding the same or a free one); {@link #ALL} puts the whole stack, else one.
     */
    public static final String ACTION_PUT = "put";
    /** {@code take}: compartment {@link #PLACE}'s stack goes back to the inventory. */
    public static final String ACTION_TAKE = "take";
    /** {@code skewer}: a stick from the cursor or inventory slot {@link #FROM} makes a skewer; {@link #ALL} as many as it can. */
    public static final String ACTION_SKEWER = "skewer";
    /**
     * {@code fill}: a recipe picked in the book ({@link #DISH}): its food and side go into the compartments from the inventory, and a
     * stick from the inventory makes one skewer of them, or with {@link #ALL} as many as there is for.
     */
    public static final String ACTION_FILL = "fill";
    public static final String FROM = "from";
    public static final String PLACE = "place";
    public static final String ALL = "all";
    public static final String DISH = "dish";

    /**
     * A skewer was made: {@link #STACK} what came out, {@link #FOOD} and {@link #SIDE} one piece of each (side empty for none), how many
     * of each go on it ({@link #FOOD_COUNT}, {@link #SIDE_COUNT}), from which compartment the food came ({@link #PLACE}), and how
     * many skewers ({@link #MADE}).
     */
    public static final Identifier EFFECT_SKEWERED = HandsOnDelight.id("basin_skewered");
    public static final Identifier EFFECT_PUT = HandsOnDelight.id("basin_put");
    public static final Identifier EFFECT_REFUSED = HandsOnDelight.id("basin_refused");
    public static final Identifier EFFECT_TAKEN = HandsOnDelight.id("basin_taken");
    public static final String STACK = "stack";
    public static final String FOOD = "food";
    public static final String SIDE = "side";
    public static final String FOOD_COUNT = "food_count";
    public static final String SIDE_COUNT = "side_count";
    public static final String MADE = "made";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(COMPARTMENTS, KitchenBasin.PLACES, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    public static final Supplier<Block> BASIN = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(KitchenGrill.MOD_ID,
            "basin"));

    /** The basin is the block itself, nothing on it opens: the scene draws what lies in its compartments, also for passers-by. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(BASIN)
            .schema(SCHEMA)
            .logic(BasinLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(BasinStation::opens)
            .idleLogic()
            .build();

    private BasinStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenGrill.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    public static SlotRef compartment(int index) {
        return SlotRef.of(COMPARTMENTS, index);
    }

    /** With an empty hand, a stick, food it skewers, or a kitchen thing it has no use for. Sneaking keeps the basin's own use. */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || KitchenBasin.of(player.level().getBlockEntity(pos)) == null) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || KitchenBasin.isTool(player.level(), held) || KitchenBasin.isFood(player.level(), held)
                || StoveStation.isKitchenThing(player.level(), held);
    }
}

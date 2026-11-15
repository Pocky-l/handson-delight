package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * Barbeque's Delight's grill as a station: two skewers lie side by side over its fire, each turned by the cook once its lower side is
 * done, seasoned with a shake of powder or a drizzle of sauce, taken off when done or before they burn. Its two places are windows on
 * the grill's own: it grills them its way, the station shows them and does by hand what clicks on the grill did.
 */
public final class GrillStation {
    public static final ResourceLocation ID = HandsOnDelight.id("grill");

    /** What lies on the grill: its two places, one skewer each. */
    public static final String GRILL = "grill";
    /** Where a stack let go over the grill is caught: one slot that never keeps anything, as at the stove. */
    public static final String HAND = "hand";

    /**
     * Data for the clients, per place {@code i}: whether the skewer was turned ({@link #TURNED}), how far its lower side is done
     * ({@link #LOWER}, 0 to 1, again from 0 once turned), whether it waits to be turned ({@link #WAITS}), and how near it is to
     * burning once its time is up ({@link #BURN}, 0 to 1). {@link #LIT}: the grill has its heat.
     */
    public static final String TURNED = "turned";
    public static final String LOWER = "lower";
    public static final String WAITS = "waits";
    public static final String BURN = "burn";
    public static final String LIT = "lit";

    /**
     * Custom actions. {@code place}: the stack on the cursor goes onto the grill, one onto every free place, or with {@link #ONE} one
     * onto {@link #PLACE}; with {@link #FROM}, the stack in that inventory slot.
     */
    public static final String ACTION_PLACE = "place";
    /** {@code turn}: the skewer on {@link #PLACE} is turned over. */
    public static final String ACTION_TURN = "turn";
    /** {@code take}: the skewer on {@link #PLACE} goes to the inventory. */
    public static final String ACTION_TAKE = "take";
    /**
     * {@code season}: the seasoning on the cursor, or in inventory slot {@link #FROM}, goes over the skewer on {@link #PLACE}, or with
     * no place over the first that takes it.
     */
    public static final String ACTION_SEASON = "season";
    public static final String ONE = "one";
    public static final String FROM = "from";
    public static final String PLACE = "place";

    /** Effects for everybody who sees the grill. Data: {@link #PLACE}, and {@link #STACK} of what it is about. */
    public static final ResourceLocation EFFECT_PLACED = HandsOnDelight.id("grill_placed");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnDelight.id("grill_refused");
    public static final ResourceLocation EFFECT_TURNED = HandsOnDelight.id("grill_turned");
    /** A turn the grill did not take (the lower side is not done yet): the skewer only twitches. */
    public static final ResourceLocation EFFECT_UNTURNED = HandsOnDelight.id("grill_unturned");
    public static final ResourceLocation EFFECT_TAKEN = HandsOnDelight.id("grill_taken");
    /** A seasoning went over a skewer: {@link #STACK} the seasoning, as it was before the use. */
    public static final ResourceLocation EFFECT_SEASONED = HandsOnDelight.id("grill_seasoned");
    public static final String STACK = "stack";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(GRILL, KitchenGrill.PLACES, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    public static final Supplier<Block> GRILL_BLOCK = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(KitchenGrill.MOD_ID,
            "grill"));

    /**
     * The grill is the block itself, nothing on it opens: the scene draws only the skewers on it, also while nobody works at it, from
     * the grill's own content that it sends to the clients near it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(GRILL_BLOCK)
            .schema(SCHEMA)
            .logic(GrillLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(GrillStation::opens)
            .idleLogic()
            .build();

    private GrillStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenGrill.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    public static SlotRef place(int index) {
        return SlotRef.of(GRILL, index);
    }

    /**
     * With an empty hand, a skewer the grill takes, a seasoning, or a kitchen thing the grill has no use for (a knife, a bowl). Sneaking
     * keeps the grill's own use (its quick turn), and so does what it has a use for: a campfire goes into it, a flint and steel lights it.
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || KitchenGrill.of(player.level().getBlockEntity(pos)) == null) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || KitchenGrill.grills(player.level(), held) || KitchenGrill.isSeasoning(held)
                || StoveStation.isKitchenThing(player.level(), held);
    }
}

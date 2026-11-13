package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * The stove's top as a station: six pieces grill on it, each turned by the cook when its lower side is done (as the skillet's food,
 * but piece by piece, as on a grill). Its six slots are windows on Farmer's Delight's own: what is put on its way cooks its way.
 */
public final class StoveStation {
    public static final ResourceLocation ID = HandsOnDelight.id("stove");

    /** What lies on the grill: Farmer's Delight's six slots, one item each. */
    public static final String GRILL = "grill";
    /** Where a stack let go over the grill is caught: one slot that never keeps anything, as at the board. */
    public static final String HAND = "hand";

    /** Data for the clients, per place {@code i}: how far each side is done ({@link #DOWN}{@code i}, {@link #UP}{@code i}), by hand. */
    public static final String DOWN = "down";
    public static final String UP = "up";
    public static final String BY_HAND = "by_hand";
    public static final String LIT = "lit";

    /**
     * Custom actions. {@code place}: the stack on the cursor goes onto the grill, one onto every free place, or with {@link #ONE} one
     * onto {@link #PLACE}; with {@link #FROM}, the stack in that inventory slot (shift + click in the inventory).
     */
    public static final String ACTION_PLACE = "place";
    /** {@code turn}: the piece on {@link #PLACE} is turned over. */
    public static final String ACTION_TURN = "turn";
    /** {@code take}: the piece on {@link #PLACE} goes back to the inventory. */
    public static final String ACTION_TAKE = "take";
    public static final String ONE = "one";
    public static final String FROM = "from";
    public static final String PLACE = "place";

    /** Effects for everybody who sees the stove. Data: {@link #PLACE}, and {@link #STACK} of what is about. */
    public static final ResourceLocation EFFECT_PLACED = HandsOnDelight.id("stove_placed");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnDelight.id("stove_refused");
    public static final ResourceLocation EFFECT_TURNED = HandsOnDelight.id("stove_turned");
    /** A turn the server did not take (the piece was still in the air): the cook's client lays it back. */
    public static final ResourceLocation EFFECT_UNTURNED = HandsOnDelight.id("stove_unturned");
    public static final ResourceLocation EFFECT_TAKEN = HandsOnDelight.id("stove_taken");
    /** A piece is done on both sides: {@link #STACK} what it became, still on the grill. */
    public static final ResourceLocation EFFECT_GRILLED = HandsOnDelight.id("grilled");
    public static final String STACK = "stack";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(GRILL, Grill.PLACES, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    /**
     * Stoves that open as this station: Farmer's Delight's and addons' built on its class (End's Delight's End stove, My Nether's
     * Delight's Nether stove). A pack adds its own.
     */
    public static final TagKey<Block> STOVES = TagKey.create(Registries.BLOCK, HandsOnDelight.id("stoves"));

    public static final StationType TYPE = Diegesis.station(ID)
            .block(FarmersDelightContent.STOVE)
            .tag(STOVES)
            .schema(SCHEMA)
            .logic(StoveLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(StoveStation::opens)
            .idleLogic()
            .build();

    private StoveStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    public static SlotRef place(int index) {
        return SlotRef.of(GRILL, index);
    }

    /**
     * With food, an empty hand or a kitchen thing the stove has no use for: the grill. Sneaking keeps the stove's own use, and so
     * does what it has a use for: a flint and steel lights it, a shovel or a water bucket puts it out, a block (a pot, a skillet) is
     * set on it. A pot or skillet standing on it covers the grill: it is theirs to open.
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || !(player.level().getBlockEntity(pos) instanceof AbstractStoveBlockEntity stove)
                || StoveCooking.isCovered(stove)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || SkilletCooking.recipe(player.level(), held).isPresent() || isKitchenThing(player.level(), held);
    }

    /**
     * Whether {@code held} is what a cook carries from station to station and the stove has no use of its own for: a knife from
     * the board, food that is not grilled, a bowl or a bottle. It opens the grill as an empty hand does; before, nothing happened
     * at all, and a cook with the knife in hand could not go over to the stove.
     */
    static boolean isKitchenThing(Level level, ItemStack held) {
        Item item = held.getItem();
        if (item instanceof BlockItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem || item instanceof ShovelItem
                || item instanceof BucketItem) {
            return false;
        }
        return held.has(DataComponents.FOOD) || Cutting.isTool(level, held) || held.is(Items.BOWL) || held.is(Items.GLASS_BOTTLE);
    }
}

package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Brewin' and Chewin's keg as a station: its lid opens on the liquid and what ferments in it, buckets pour in at the opening, drinks
 * are drawn at the tap. It ferments as Brewin' and Chewin' ferments it, whoever is at it; the station only shows it and does by hand
 * what its menu did. Youkai's Feasts' fermentation tank ({@link #TANK_TYPE}) is the same station: eight things, one each, in water or
 * a juice under its own lid, its drinks dipped out at the opening, as it has no tap.
 */
public final class KegStation {
    public static final String MOD_ID = "brewinandchewin";
    public static final Identifier ID = HandsOnDelight.id("keg");
    /** The keg stood on its end: the same station, its own scene. */
    public static final Identifier STANDING_ID = HandsOnDelight.id("standing_keg");
    public static final String TANK_MOD_ID = "youkaisfeasts";
    public static final Identifier TANK_ID = HandsOnDelight.id("youkai_tank");
    /** The tank holds eight things, one in each slot, as its own container does. */
    public static final int TANK_INGREDIENTS = 8;

    public static final String INGREDIENTS = "ingredients";
    /** What fermented into an item (cheese, fudge) waits in the keg's output slot. */
    public static final String SERVED = "served";
    public static final String DRINK = "drink";
    public static final String PROGRESS = "progress";
    /** The keg's temperature, 1 (cold) to 5 (hot), and what the recipe in it wants, 0 for none. */
    public static final String TEMPERATURE = "temperature";
    public static final String WANTED = "wanted";
    /**
     * Whether the lid is off (1) or on (0). The cook takes it off and puts it back with a click on it ({@link #ACTION_LID}); only
     * with the lid off does anything go in at the top, and the keg ferments only with it on, as a brew is kept shut.
     */
    public static final String LID = "lid";
    /** A custom action: the lid is taken off or put back; data {@link #OPEN}. */
    public static final String ACTION_LID = "lid";
    public static final String OPEN = "open";
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);

    /**
     * A custom action: the stack on the cursor is used at {@link #AT}: {@link #MOUTH} pours it in, {@link #TAP} fills it with the
     * drink. {@link #ALL}: the whole stack; {@link #FROM_INVENTORY}: the hand is empty, a container comes from the inventory;
     * {@link #FROM}: the stack in that inventory slot instead of the cursor's (Shift + click in the inventory).
     */
    public static final String ACTION_USE = "use";
    public static final String AT = "at";
    public static final String MOUTH = "mouth";
    public static final String TAP = "tap";
    public static final String ALL = "all";
    public static final String FROM_INVENTORY = "from_inventory";
    public static final String FROM = "from";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    /** What the logic tells the clients in the station's data: the liquid's id and how long fermenting still takes, in ticks. */
    public static final String FLUID = "fluid";
    public static final String LEFT = "left";
    /** In a poured or tapped effect, with {@link #FLUID}: how many millibuckets ran. */
    public static final String AMOUNT = "amount";
    /** In a tapped effect: how many containers were filled, one drink each whatever its size (a bucket is one). */
    public static final String COUNT = "count";
    /** In a poured or tapped effect: the container used, as it was before (a water bucket poured, a tankard filled). */
    public static final String VESSEL = "vessel";
    /** Played for everybody who sees it: something poured in at the opening, a drink drawn at the tap. */
    public static final Identifier EFFECT_POURED = HandsOnDelight.id("poured");
    public static final Identifier EFFECT_TAPPED = HandsOnDelight.id("tapped");

    public static final Supplier<Block> KEG = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MOD_ID, "keg"));
    public static final Supplier<Block> TANK = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(TANK_MOD_ID, "fermentation_tank"));

    public static final StationSchema SCHEMA = schema(KitchenKeg.INGREDIENTS, SlotRules.DEFAULT.kept());
    public static final StationSchema TANK_SCHEMA = schema(TANK_INGREDIENTS, SlotRules.DEFAULT.kept().maxStack(1));

    public static final StationType TYPE = type(ID, KEG, SCHEMA, state -> !isStanding(state));
    public static final StationType STANDING_TYPE = type(STANDING_ID, KEG, SCHEMA, KegStation::isStanding);
    public static final StationType TANK_TYPE = type(TANK_ID, TANK, TANK_SCHEMA, state -> true);

    private KegStation() {
    }

    private static StationSchema schema(int ingredients, SlotRules rules) {
        return StationSchema.builder()
                .slots(INGREDIENTS, ingredients, rules)
                .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
                .tank(DRINK, TankSpec.of(100))
                .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
                .control(TEMPERATURE, ControlSpec.stepped(0.0F, 5.0F, 5, 3.0F).locked())
                .control(WANTED, ControlSpec.stepped(0.0F, 5.0F, 5, 0.0F).locked())
                .control(LID, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
                .build();
    }

    /** The keg's block is hidden while somebody works at it: the scene shows it with its lid open. */
    private static StationType type(Identifier id, Supplier<Block> block, StationSchema schema, Predicate<BlockState> when) {
        return Diegesis.station(id)
                .block(block)
                .when(when)
                .schema(schema)
                .logic(KegLogic::new)
                .orientation(StationType.Orientation.PLAYER)
                .openWhen(KegStation::opens)
                .hidesBlock()
                .idleLogic()
                .build();
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(MOD_ID)) {
            Diegesis.register(TYPE);
            Diegesis.register(STANDING_TYPE);
        }
    }

    public static void registerTank() {
        if (Platform.INSTANCE.isLoaded(TANK_MOD_ID)) {
            Diegesis.register(TANK_TYPE);
        }
    }

    /** Whether the keg stands on its end: Brewin' and Chewin's {@code vertical} property, read by name. */
    public static boolean isStanding(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("vertical") && property instanceof BooleanProperty vertical) {
                return state.getValue(vertical);
            }
        }
        return false;
    }

    /**
     * Sneaking keeps the keg's own menu (the tank's: its contents tipped out); a container that fills or pours in the hand keeps its
     * quick use in the world.
     */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) {
            return false;
        }
        KitchenKeg keg = KitchenKeg.of(player.level().getBlockEntity(pos));
        ItemStack held = player.getMainHandItem();
        // No keg of ours: a version of the addon its station does not fit, which keeps its own use.
        return keg != null && !keg.handson$canTap(held) && !keg.handson$canPour(held);
    }
}

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
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * A kettle as a station: its lid comes off onto the stove beside it, water is poured in at the opening and the leaves dropped in
 * after it steep there over the heat, and the drink is poured out of the spout into a cup. Farmer's Respite's kettle ({@link #TYPE})
 * and Youkai's Feasts' ({@link #YOUKAI_TYPE}, which boils its water before it brews) are the same station on their own blocks. Each
 * brews as its mod brews, whoever is at it; the station only shows it and does by hand what its menu or its clicks did.
 */
public final class KettleStation {
    public static final String MOD_ID = "farmersrespite";
    public static final Identifier ID = HandsOnDelight.id("kettle");
    public static final String YOUKAI_MOD_ID = "youkaisfeasts";
    public static final Identifier YOUKAI_ID = HandsOnDelight.id("youkai_kettle");

    public static final String INGREDIENTS = "ingredients";
    public static final String DRINK = "drink";
    public static final String PROGRESS = "progress";
    /** Whether something under the kettle heats it (1) or not (0): nothing brews cold. */
    public static final String HEATED = "heated";
    /** How far the water has come to the boil, 0 to 1, in a kettle that boils it before it brews; 1 in any other. */
    public static final String BOIL = "boil";
    /**
     * Whether the lid is off (1) or on (0): Farmer's Respite's kettle block's own {@code lid} state, which it draws in the world too
     * (a kettle left open stays open); Youkai's Feasts' kettle has none, its lid goes back on when the last cook leaves. The cook takes
     * it off and puts it back with a click on it ({@link #ACTION_LID}); only with the lid off does anything go in at the top.
     */
    public static final String LID = "lid";
    /** A custom action: the lid is taken off or put back; data {@link #OPEN}. */
    public static final String ACTION_LID = "lid";
    public static final String OPEN = "open";

    /**
     * A custom action: the stack on the cursor is used at {@link #AT}: {@link #MOUTH} pours it in, {@link #SPOUT} fills it with the
     * drink. {@link #ALL}: the whole stack, or every serving; {@link #FROM_INVENTORY}: the hand is empty, a cup comes from the inventory;
     * {@link #FROM}: the stack in that inventory slot instead of the cursor's (Shift + click in the inventory).
     */
    public static final String ACTION_USE = "use";
    public static final String AT = "at";
    public static final String MOUTH = "mouth";
    public static final String SPOUT = "spout";
    public static final String ALL = "all";
    public static final String FROM_INVENTORY = "from_inventory";
    public static final String FROM = "from";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    /** What the logic tells the clients in the station's data: the liquid's id and how long brewing still takes, in ticks. */
    public static final String FLUID = "fluid";
    public static final String LEFT = "left";
    /** In a poured or served effect, with {@link #FLUID}: how many millibuckets ran. */
    public static final String AMOUNT = "amount";
    /** In a served effect: how many containers were filled, one drink each whatever its size (a bucket is one). */
    public static final String COUNT = "count";
    /** In a poured or served effect: the container used, as it was before (a water bucket poured, a bottle filled). */
    public static final String VESSEL = "vessel";
    /** Played for everybody who sees it: something poured in at the opening, a drink poured out of the spout. */
    public static final Identifier EFFECT_POURED = HandsOnDelight.id("kettle_poured");
    public static final Identifier EFFECT_SERVED = HandsOnDelight.id("kettle_served");

    public static final Supplier<Block> KETTLE = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MOD_ID, "kettle"));
    public static final Supplier<Block> YOUKAI_KETTLE = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(YOUKAI_MOD_ID, "kettle"));

    public static final StationSchema SCHEMA = schema(KitchenKettle.INGREDIENTS, SlotRules.DEFAULT.kept());
    /** Youkai's Feasts' kettle steeps four things, one in each of its slots. */
    public static final StationSchema YOUKAI_SCHEMA = schema(KitchenKettle.MOST_INGREDIENTS, SlotRules.DEFAULT.kept().maxStack(1));

    /** The kettle's block is hidden while somebody works at it: the scene shows it, its lid off or tipped to pour. */
    public static final StationType TYPE = type(ID, KETTLE, SCHEMA);
    public static final StationType YOUKAI_TYPE = type(YOUKAI_ID, YOUKAI_KETTLE, YOUKAI_SCHEMA);

    private KettleStation() {
    }

    private static StationSchema schema(int ingredients, SlotRules rules) {
        return StationSchema.builder()
                .slots(INGREDIENTS, ingredients, rules)
                .tank(DRINK, TankSpec.of(100))
                .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
                .control(HEATED, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
                .control(LID, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
                .control(BOIL, ControlSpec.stepped(0.0F, 1.0F, 100, 1.0F).locked())
                .build();
    }

    private static StationType type(Identifier id, Supplier<Block> block, StationSchema schema) {
        return Diegesis.station(id)
                .block(block)
                .schema(schema)
                .logic(KettleLogic::new)
                .orientation(StationType.Orientation.PLAYER)
                .openWhen(KettleStation::opens)
                .hidesBlock()
                .idleLogic()
                .build();
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(MOD_ID)) {
            Diegesis.register(TYPE);
        }
        if (Platform.INSTANCE.isLoaded(YOUKAI_MOD_ID)) {
            Diegesis.register(YOUKAI_TYPE);
        }
    }

    /** Whether {@code id} is one of the kettle stations. */
    public static boolean isKettle(Identifier id) {
        return ID.equals(id) || YOUKAI_ID.equals(id);
    }

    /** Farmer's Respite's {@code lid} property of the kettle block, read by name; null on anything else. */
    public static BooleanProperty lidProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("lid") && property instanceof BooleanProperty lid) {
                return lid;
            }
        }
        return null;
    }

    /** Whether the kettle stands with its lid off. */
    public static boolean isOpen(BlockState state) {
        BooleanProperty lid = lidProperty(state);
        return lid != null && !state.getValue(lid);
    }

    /**
     * Sneaking keeps the kettle's own use (Farmer's Respite: its lid by an empty hand, its menu; Youkai's Feasts: tipping it out); a cup
     * that fills or a bucket that pours in the hand keeps its quick use in the world.
     */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) {
            return false;
        }
        KitchenKettle kettle = KitchenKettle.of(player.level().getBlockEntity(pos));
        ItemStack held = player.getMainHandItem();
        // No kettle of ours: a version of the addon its station does not fit, which keeps its own use.
        return kettle != null && !kettle.handson$canServe(held) && !kettle.handson$canPour(held);
    }
}

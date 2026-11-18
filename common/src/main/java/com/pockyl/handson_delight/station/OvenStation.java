package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * An oven as a station: its door opens, the tray or rack slides out with what bakes laid on it, the door shuts and it bakes as the oven
 * always does. One station for every oven ({@link KitchenOven}); each mod's oven has its type and scene. Cooking for Blockheads' oven
 * ({@link #CFB_TYPE}): the door drops on its hinge, the wire rack slides out over it with its nine places, what is baked waits on the
 * open door, the fuel burns at the back of the oven floor and the tools stand on top. Cultural Delights' oven ({@link #TYPE}): its
 * grated door swings open in two leaves, the baking tray slides out, what bakes is laid on it in the oven's 3 x 3 grid as one set, and
 * it bakes shut over the heat under it.
 */
public final class OvenStation {
    /** Cultural Delights' oven. */
    public static final ResourceLocation ID = HandsOnDelight.id("oven");
    /** Cooking for Blockheads' oven, of every colour. */
    public static final ResourceLocation CFB_ID = HandsOnDelight.id("cfb_oven");

    public static final String INGREDIENTS = "ingredients";
    /** What is baked waits in the oven's output slots. */
    public static final String SERVED = "served";
    /** The oven's fuel, for an oven that burns it. */
    public static final String FUEL = "fuel";
    /** The tool places on top of the oven. */
    public static final String TOOLS = "tools";
    public static final String PROGRESS = "progress";
    /**
     * Whether the door is open (1) or shut (0). The cook opens and shuts it with a click on it ({@link #ACTION_DOOR}); things go on the
     * tray and come off it only with it open.
     */
    public static final String DOOR = "door";
    public static final String ACTION_DOOR = "door";
    public static final String OPEN = "open";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}, {@link #ALL}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /**
     * A custom action: the stack on the cursor, or the one in an inventory slot ({@link #FROM}), is laid on the tray: where alike lies
     * or into free places, one a place for an oven that bakes each alone. {@link #PLACE}: onto that place only, one ({@link #ONE});
     * {@link #ALL}: the inventory's alike things follow once the stack is laid.
     */
    public static final String ACTION_LAY = "lay";
    public static final String FROM = "from";
    public static final String PLACE = "place";
    public static final String ONE = "one";
    /**
     * A custom action: fuel goes into the oven, the stack on the cursor, or with an empty hand ({@link #FROM_INVENTORY}) a stack of fuel
     * from the inventory.
     */
    public static final String ACTION_FUEL = "fuel";
    public static final String FROM_INVENTORY = "from_inventory";
    /**
     * What the logic tells the clients in the station's data: the ticks until the next thing is done ({@link #LEFT}), how far each
     * place has baked in hundredths ({@link #BAKED}), whether the oven is hot ({@link #HOT}) and whether its things wait for heat
     * ({@link #COLD}).
     */
    public static final String LEFT = "left";
    public static final String BAKED = "baked";
    public static final String HOT = "hot";
    public static final String COLD = "cold";
    public static final SlotRef FUEL_SLOT = SlotRef.of(FUEL, 0);
    /** The book's tabs: the oven's furnace food; Farmer's Delight's campfire dishes; dishes baked in bakeware. */
    public static final String KIND_FOOD = "food";
    public static final String KIND_CAMPFIRE = "campfire";
    public static final String KIND_BAKEWARE = "bakeware";

    /** Cooking for Blockheads' ovens, its sixteen colours: on 1.21.1 it tags them as items only. */
    public static final TagKey<Block> CFB_OVENS = TagKey.create(Registries.BLOCK, HandsOnDelight.id("cfb_ovens"));
    public static final Supplier<Block> OVEN = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(CulturalOven.MOD_ID, "oven"));

    public static final StationSchema CFB_SCHEMA = schema(CfbOven.PLACES, 1, CfbOven.OUTPUTS, true, CfbOven.TOOLS);

    /**
     * Cooking for Blockheads' oven. Its block is hidden while somebody works at it and the scene shows it with its door open; while
     * nobody does, the block and its own renderer are the oven, and each client near it reads it with its own copy of the logic for
     * the puff of steam when something is done.
     */
    public static final StationType CFB_TYPE = Diegesis.station(CFB_ID)
            .tag(CFB_OVENS)
            .schema(CFB_SCHEMA)
            .logic(OvenLogic::new)
            // Worked from its door: the scene stands as the oven faces, whichever side the cook comes from.
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(OvenStation::opensCfb)
            .hidesBlock()
            .idleLogic()
            .build();

    /**
     * Cultural Delights' oven. Its block is hidden while somebody works at it: the scene shows it with its door open. While nobody
     * does, the block is the oven and the scene shows only what lies on its tray: its block entity sends its content to the clients
     * near it ({@code OvenMixin}), and each of them reads it with its own copy of the logic.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(OVEN)
            .schema(schema(CulturalOven.PLACES, CulturalOven.PER_PLACE, 1, false, 0))
            .logic(OvenLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(OvenStation::opensCultural)
            .hidesBlock()
            .idleLogic()
            .build();

    private OvenStation() {
    }

    /** The slots of an oven with {@code places} places holding {@code perPlace} each, its outputs, fuel and tool places. */
    public static StationSchema schema(int places, int perPlace, int outputs, boolean fuel, int tools) {
        StationSchema.Builder builder = StationSchema.builder()
                .slots(INGREDIENTS, places, SlotRules.DEFAULT.kept().maxStack(perPlace))
                .slots(SERVED, outputs, SlotRules.DEFAULT.kept().accepts(stack -> false));
        if (fuel) {
            builder.slots(FUEL, 1, SlotRules.DEFAULT.kept());
        }
        if (tools > 0) {
            builder.slots(TOOLS, tools, SlotRules.DEFAULT.kept().maxStack(1));
        }
        return builder
                .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
                .control(DOOR, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
                .build();
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(CulturalOven.MOD_ID)) {
            Diegesis.register(TYPE);
        }
        if (Platform.INSTANCE.isLoaded(CfbOven.MOD_ID)) {
            Diegesis.register(CFB_TYPE);
        }
    }

    /** Sneaking keeps Cultural Delights' oven's own menu. */
    private static boolean opensCultural(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && CulturalOven.of(player.level().getBlockEntity(pos)) != null;
    }

    /**
     * Sneaking keeps the oven's own use (its menu, from the front its door held open); so do its heating unit in the hand, and a click
     * on its top with a tool, a skillet or a pot, or with an empty hand on what stands there ({@code CfbOvenTopMixin}).
     */
    private static boolean opensCfb(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (CfbOven.HEATING_UNIT.equals(BuiltInRegistries.ITEM.getKey(held.getItem()))) {
            return false;
        }
        CfbOven oven = CfbOven.of(player.level().getBlockEntity(pos));
        if (oven == null) {
            return false;
        }
        // On the top face: a tool, a skillet or a pot goes onto the burner, an empty hand takes back what stands on it.
        BlockHitResult top = topHit(player, pos);
        return top == null || !CfbOven.isToolItem(held) && !CfbOven.standsOnBurner(held)
                && !(held.isEmpty() && !oven.tool(CfbOven.burnerAt(state, pos, top.getLocation())).isEmpty());
    }

    /** Where the player aims at the oven's top face, or null. */
    private static BlockHitResult topHit(Player player, BlockPos pos) {
        HitResult hit = player.pick(player.isCreative() ? 5.0 : 4.5, 1.0F, false);
        return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(pos)
                && block.getDirection() == Direction.UP ? block : null;
    }
}

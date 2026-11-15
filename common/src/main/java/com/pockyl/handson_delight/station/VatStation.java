package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * Cultural Delights' vat as a station: its lid swings up on its hinge, what ages is poured, shaken and dropped into it; the lid shuts
 * and it ages at the warmth under it, as the vat always does. A drink stays in the vat until the cook draws it into a bottle from their
 * inventory. A vat lying on its side or upside down keeps its own menu.
 */
public final class VatStation {
    public static final ResourceLocation ID = HandsOnDelight.id("vat");

    public static final String INGREDIENTS = "ingredients";
    /** The containers the result goes into: bottles, wafers. */
    public static final String CONTAINER = "container";
    /** What is aged without a container waits in the vat's output slot (pickles, cheese). */
    public static final String SERVED = "served";
    /** A drink aged without its container waits in the vat as servings, drawn into containers from the cook's inventory. */
    public static final String SERVINGS = "servings";
    public static final String PROGRESS = "progress";
    /**
     * Whether the lid is open (1) or shut (0). The cook opens and shuts it with a click on it ({@link #ACTION_LID}); things go in and
     * come out only with it open, and the vat ages only with it shut.
     */
    public static final String LID = "lid";
    public static final String ACTION_LID = "lid";
    public static final String OPEN = "open";
    /** What the vat holds as liquid, for the scene to draw: how full, in hundredths of what it holds. */
    public static final String BREW = "brew";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}, {@link #ALL}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /**
     * A custom action: the stack in an inventory slot ({@link #FROM}) goes into the vat, onto the same thing or into the first free
     * place, a container onto the rim: the item the cook came with, or one shift-clicked in the inventory.
     */
    public static final String ACTION_PUT = "put";
    public static final String FROM = "from";
    /**
     * A custom action: what is aged goes to the cook, one, or all of it with {@link #ALL}. A drink waiting in the vat goes into the
     * containers the cook draws it with, one a serving: those on their cursor ({@link #FROM} -1) or in an inventory slot ({@link #FROM});
     * anything else is taken as it lies.
     */
    public static final String ACTION_TAKE = "take";
    /** What the logic tells the clients in the station's data: how long aging still takes, in ticks. */
    public static final String LEFT = "left";
    public static final SlotRef CONTAINER_SLOT = SlotRef.of(CONTAINER, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final SlotRef SERVINGS_SLOT = SlotRef.of(SERVINGS, 0);
    public static final Supplier<Block> VAT = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(KitchenVat.MOD_ID, "vat"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, KitchenVat.INGREDIENTS, SlotRules.DEFAULT.kept())
            .slots(CONTAINER, 1, SlotRules.DEFAULT.kept())
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(SERVINGS, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(BREW, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(LID, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
            .build();

    /**
     * The vat's block is hidden while somebody works at it: the scene shows it with its lid up. While nobody does, the block is the
     * vat, its lid shut: its block entity sends its content to the clients near it ({@code VatMixin}), and each of them reads it with
     * its own copy of the logic for the sounds and steam of what ages.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(VAT)
            .schema(SCHEMA)
            .logic(VatLogic::new)
            // Round from every side: worked from where the cook stands, its lid hinged on the far side.
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(VatStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private VatStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenVat.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    /** Sneaking keeps the vat's own menu, and so does a vat that is not standing on its bottom. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenVat.isUpright(state) && KitchenVat.of(player.level().getBlockEntity(pos)) != null;
    }
}

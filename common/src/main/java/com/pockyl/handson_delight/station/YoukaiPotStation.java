package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.List;
import java.util.function.Supplier;

/**
 * Youkai's Feasts' cooking pots as stations: its small iron pot, short iron pot and stockpot once water is in them. Food comes in
 * from the cook one piece after another while a recipe of that pot still has a place for it (what it would not take stays dim in
 * the drawer), a dish picked in the book goes in from the inventory, the last thing comes back out with a right click. The pot
 * cooks by itself over heat as before; the badge tells what it makes and how long it still takes. Done, the dish stands where the
 * pot stood (a pot of a feast, a bowl of soup), as before; a dish that is no block (a modpack's recipe) goes to the cook at the pot
 * rather than out over it. Every step goes through the pot's own code.
 */
public final class YoukaiPotStation {
    public static final ResourceLocation SMALL_ID = HandsOnDelight.id("youkai_small_pot");
    public static final ResourceLocation SHORT_ID = HandsOnDelight.id("youkai_short_pot");
    public static final ResourceLocation STOCKPOT_ID = HandsOnDelight.id("youkai_stockpot");

    /** Where a stack let go over the pot is caught: one slot that never keeps anything. */
    public static final String HAND = "hand";

    /**
     * Custom actions. {@code put}: one of the stack on the cursor, or in inventory slot {@link #FROM}, goes in; with {@link #ALL} as
     * many as the pot takes of it.
     */
    public static final String ACTION_PUT = "put";
    /** {@code back}: the last thing in comes back out to the cook. */
    public static final String ACTION_BACK = "back";
    /** {@code fill}: a dish picked in the book ({@link #DISH}) goes in from the inventory, what is in the pot already counting. */
    public static final String ACTION_FILL = "fill";
    /**
     * {@code stir}: the stockpot's ladle moved in the cook's hand; data: where its end is now, {@link #STIR_X} and {@link #STIR_Z},
     * -1 to 1 of its reach. Everybody near sees it stir; the pot cooks by its own time all the same.
     */
    public static final String ACTION_STIR = "stir";
    public static final String STIR_X = "x";
    public static final String STIR_Z = "z";
    /** Controls: somebody is stirring now, and where the ladle's end is (-1 to 1 of its reach), for everybody near. */
    public static final String STIRRING = "stirring";
    public static final String SPOON_X = "spoon_x";
    public static final String SPOON_Z = "spoon_z";
    public static final String FROM = "from";
    public static final String ALL = "all";
    public static final String DISH = "dish";

    /** Things went in: {@link #STACKS} one after the other, {@link #BOOK} when the book put them in from the inventory. */
    public static final ResourceLocation EFFECT_IN = HandsOnDelight.id("youkai_pot_in");
    /** The last thing came back out: {@link #STACK}. */
    public static final ResourceLocation EFFECT_BACK = HandsOnDelight.id("youkai_pot_back");
    /** What the pot would not take: {@link #STACK}. */
    public static final ResourceLocation EFFECT_REFUSED = HandsOnDelight.id("youkai_pot_refused");
    /** The pot has finished a dish ({@link #STACK}); with {@link #SERVED} it went to the cook rather than out over the pot. */
    public static final ResourceLocation EFFECT_DONE = HandsOnDelight.id("youkai_pot_done");
    public static final String STACKS = "stacks";
    public static final String STACK = "stack";
    public static final String BOOK = "book";
    public static final String SERVED = "served";

    /**
     * The soup the scene draws in the pot, in place of the pot's own flickering soup textures. The server never
     * fills it: each client shows the pot's soup in it by code, in that soup's colour.
     */
    public static final String SOUP = "soup";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(HAND, 1, SlotRules.DEFAULT)
            .tank(SOUP, TankSpec.of(100))
            .control(STIRRING, ControlSpec.toggle(false).locked())
            .control(SPOON_X, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .control(SPOON_Z, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .build();

    public static final Supplier<Block> SMALL = block("cooking_small_iron_pot");
    public static final Supplier<Block> SHORT = block("cooking_short_iron_pot");
    public static final Supplier<Block> STOCKPOT = block("cooking_stockpot");

    /**
     * The pots are the blocks themselves, open on top: the pot's own renderer goes on drawing the soup and what floats in it. The
     * scene stands on the side the cook comes from. The stockpot's ladle stirs what goes in: the scene draws the stockpot while
     * somebody works at it.
     */
    public static final StationType SMALL_TYPE = type(SMALL_ID, SMALL).build();
    public static final StationType SHORT_TYPE = type(SHORT_ID, SHORT).build();
    public static final StationType STOCKPOT_TYPE = type(STOCKPOT_ID, STOCKPOT).hidesBlock().build();
    public static final List<StationType> TYPES = List.of(SMALL_TYPE, SHORT_TYPE, STOCKPOT_TYPE);

    private YoukaiPotStation() {
    }

    @SuppressWarnings("removal")
    private static Supplier<Block> block(String path) {
        return () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(KitchenYoukaiPot.MOD_ID, path));
    }

    private static StationType.Builder type(ResourceLocation id, Supplier<Block> block) {
        return Diegesis.station(id)
                .block(block)
                .schema(SCHEMA)
                .logic(YoukaiPotLogic::new)
                .orientation(StationType.Orientation.PLAYER)
                .openWhen(YoukaiPotStation::opens);
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenYoukaiPot.MOD_ID)) {
            TYPES.forEach(Diegesis::register);
        }
    }

    /**
     * With an empty hand, food a recipe of this pot uses, or a kitchen thing it has no use for. Sneaking keeps the pot's own use (it
     * tips everything out and the water with it).
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        KitchenYoukaiPot pot = KitchenYoukaiPot.of(player.level().getBlockEntity(pos));
        if (player.isSecondaryUseActive() || pot == null) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || pot.handson$takes(held) || isIngredient(pot, held) || StoveStation.isKitchenThing(player.level(), held);
    }

    /** The recipes of this pot. */
    public static List<KitchenYoukaiPot.Dish> dishes(KitchenYoukaiPot pot) {
        return pot.handson$dishes().stream().filter(dish -> dish.pot() == pot.handson$pot()).toList();
    }

    /** Whether {@code stack} goes into some dish of this pot. */
    public static boolean isIngredient(KitchenYoukaiPot pot, ItemStack stack) {
        return !stack.isEmpty() && dishes(pot).stream()
                .anyMatch(dish -> dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }
}

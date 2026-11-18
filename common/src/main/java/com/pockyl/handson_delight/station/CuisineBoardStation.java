package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * Youkai's Feasts' cuisine board as a station: sushi and rolls are laid up on it step by step in the order its recipes want (rice,
 * then shaped by hand into nigiri and topped; kelp, rice, a sauce poured and fillings, then rolled up by hand), the finished roll is
 * cut with a knife, a dish is taken off. Nothing on the board opens: the scene draws the board's own models of what lies on it,
 * also for passers-by, and every step goes through the board's own code.
 */
public final class CuisineBoardStation {
    public static final Identifier ID = HandsOnDelight.id("youkai_board");

    /** Where a stack let go over the board is caught: one slot that never keeps anything. */
    public static final String HAND = "hand";

    /**
     * Custom actions. {@code put}: the stack on the cursor, or in inventory slot {@link #FROM}, goes on as the next step; with
     * {@link #ALL} as many steps of it as the board takes in a row.
     */
    public static final String ACTION_PUT = "put";
    /** {@code shape}: the next step done by hand: rice shaped into nigiri, a sheet rolled up, a bamboo tube closed. */
    public static final String ACTION_SHAPE = "shape";
    /** {@code take}: the finished dish goes to the cook. */
    public static final String ACTION_TAKE = "take";
    /** {@code back}: the last step comes off the board, back to the cook. */
    public static final String ACTION_BACK = "back";
    /**
     * {@code cut}: the knife on the cursor, in inventory slot {@link #FROM}, or the first in the inventory that cuts it, cuts the
     * finished dish at mark {@link #MARK}, the next one, as the board cuts at its dashed marks; the last mark cuts it into its
     * pieces. With {@link #ALL} every mark left is cut at once.
     */
    public static final String ACTION_CUT = "cut";
    /**
     * {@code fill}: a recipe picked in the book ({@link #DISH}) is laid up from the inventory; {@link #ALL} makes as many as there
     * is for.
     */
    public static final String ACTION_FILL = "fill";
    public static final String FROM = "from";
    public static final String ALL = "all";
    public static final String DISH = "dish";
    public static final String MARK = "mark";
    /** Station data: how many of the finished dish's marks are cut. */
    public static final String CUTS = "cuts";

    /**
     * Steps went on: {@link #STEPS} (each a compound with {@link #STACK}, empty for the hand, and {@link #AFTER}, what lay on the
     * board after it), {@link #BOOK} when the book laid them, from the inventory rather than the cursor.
     */
    public static final Identifier EFFECT_LAID = HandsOnDelight.id("youkai_board_laid");
    /** The dish went to the cook: {@link #STACK}. With {@link #AFTER} the dish was one of a batch and the board starts again. */
    public static final Identifier EFFECT_TAKEN = HandsOnDelight.id("youkai_board_taken");
    /** The last step came off: {@link #STACK} what went back, {@link #AFTER} what lies there now. */
    public static final Identifier EFFECT_BACK = HandsOnDelight.id("youkai_board_back");
    /** A mark was cut ({@link #MARK}), not the last one. */
    public static final Identifier EFFECT_CHOPPED = HandsOnDelight.id("youkai_board_chopped");
    /** The dish was cut through at its last mark: {@link #STACK} the dish, {@link #TOOL} the knife, {@link #RESULTS} the pieces. */
    public static final Identifier EFFECT_CUT = HandsOnDelight.id("youkai_board_cut");
    /** What the board would not take: {@link #STACK}. */
    public static final Identifier EFFECT_REFUSED = HandsOnDelight.id("youkai_board_refused");
    public static final String STEPS = "steps";
    public static final String STACK = "stack";
    public static final String AFTER = "after";
    public static final String BOOK = "book";
    public static final String TOOL = "tool";
    public static final String RESULTS = "results";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    public static final Supplier<Block> BOARD = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(KitchenCuisine.MOD_ID,
            "cuisine_board"));

    /**
     * The board is the block itself, nothing on it opens: the scene draws what lies on it, also for passers-by. It turns to the side
     * the cook comes from (the board's own facing points away from whoever placed it); its models keep the board's facing.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(BOARD)
            .schema(SCHEMA)
            .logic(CuisineLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CuisineBoardStation::opens)
            .build();

    private CuisineBoardStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenCuisine.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    /**
     * With an empty hand, a step of some dish, a knife, or a kitchen thing it has no use for. Sneaking keeps the board's own use (it
     * clears the board).
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        KitchenCuisine board = KitchenCuisine.of(player.level().getBlockEntity(pos));
        if (player.isSecondaryUseActive() || board == null) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || board.handson$takes(held) > 0 || isStep(board, held) || Cutting.isTool(player.level(), held)
                || StoveStation.isKitchenThing(player.level(), held);
    }

    /** Whether {@code stack} is a step of any dish of the board. */
    public static boolean isStep(KitchenCuisine board, ItemStack stack) {
        ItemStack step = stack.isEmpty() ? stack : board.handson$asStep(stack);
        return !step.isEmpty() && board.handson$dishes().stream()
                .anyMatch(dish -> dish.steps().stream().anyMatch(each -> !each.isEmpty() && each.test(step)));
    }

    /** Whether {@code tool} cuts what lies finished on the board. */
    public static boolean cuts(Level level, KitchenCuisine board, ItemStack tool) {
        ItemStack finished = board.handson$finished();
        return !finished.isEmpty() && !tool.isEmpty() && Cutting.findWith(level, finished, tool).isPresent();
    }
}

package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * The cutting board as a station: what lies on it is cut with chops of the knife, and food that Farmer's Delight makes in the crafting
 * table is put together on it, layer by layer. The board's one slot is a window on Farmer's Delight's own: hoppers, Create's deployers
 * and players without this mod use it as before.
 */
public final class CuttingBoardStation {
    public static final ResourceLocation ID = HandsOnDelight.id("cutting_board");

    /** What lies on the board to be cut: Farmer's Delight's own slot, a stack. */
    public static final String BOARD = "board";
    /** What a dish is being put together from, bottom first: kept in the board (see {@link BoardLayers}). */
    public static final String LAYERS = "layers";
    /** Feasts, pies and the cake take nine things, as many as a crafting grid holds. */
    public static final int MOST_LAYERS = 9;
    /**
     * Where a stack let go on the board is caught: one slot that never keeps anything. The logic takes what it is given there, and the
     * board's own slots refuse stacks the library would move by itself: its guess (a shift-click from the inventory) is never shown.
     */
    public static final String HAND = "hand";
    public static final SlotRef BOARD_SLOT = SlotRef.of(BOARD, 0);

    /** Data the logic tells the clients: the dish the layers make. */
    public static final String DISH = "dish";
    /**
     * The board's own recipes, which a dedicated server sends nowhere else, for the book: {@link #EFFECT_BOOK} brings them when a cook
     * comes to the board, once, not with every change of the board's data.
     */
    public static final String BOOK = "book";
    /** How many items of the stack one cut goes through, and how many dishes one stack of layers makes at most. */
    public static final int BATCH = 16;

    /**
     * Custom actions. {@code place}: the stack on the cursor goes onto the board, {@link #ONE} of it or all; with {@link #FROM}, the
     * stack in that inventory slot instead (shift + click in the drawer, or the stack in hand when the board was opened by its use,
     * {@link #BY_USE}: what the board cannot take then stays in the hand without a word).
     */
    public static final String ACTION_PLACE = "place";
    public static final String BY_USE = "by_use";
    /** {@code carve}: the tool on the cursor is stuck into the bare board, as Farmer's Delight's sneaking use sticks it in. */
    public static final String ACTION_CARVE = "carve";
    /** {@code chop}: the knife on the cursor comes down on mark {@link #MARK} (the next one to cut), at {@link #AT}, scene pixels along z. */
    public static final String ACTION_CHOP = "chop";
    /** {@code take}: the top layer, or what lies to be cut, back into the inventory. */
    public static final String ACTION_TAKE = "take";
    /** {@code assemble}: the layers become the dish they make. */
    public static final String ACTION_ASSEMBLE = "assemble";
    /**
     * {@code pick}: the dish picked in the book ({@link #DISH}: its recipe id) is made as soon as the layers make it, and what it takes
     * is laid from the inventory: one set, or a batch with {@link #ALL}.
     */
    public static final String ACTION_PICK = "pick";
    public static final String ALL = "all";
    public static final String ONE = "one";
    public static final String FROM = "from";
    public static final String AT = "at";
    /** Across the board, scene pixels: where a tool met what it works, for the bits that fly off there. */
    public static final String AT_X = "at_x";
    public static final String MARK = "mark";
    /** Data: how many of the marks are cut; they are cut in order, and only the next one is shown. */
    public static final String MARKS = "marks";
    /**
     * Data: where the marks lie, chosen by the server for every item: where each crosses the item's middle, as fractions of its length
     * from its far end, the next one first to cut. The server cannot see the icon, so the client lays them over its solid pixels.
     */
    public static final String SPOTS = "spots";
    /** Data: how each mark lies, radians from straight across the item, random so diagonal cuts happen too. */
    public static final String ANGLES = "angles";
    /** Effect data of a chop: the spot and angle of the mark it cut. */
    public static final String SPOT = "spot";
    public static final String ANGLE = "angle";

    /** Effects for everybody who sees the board. Data: {@link #AT}, {@link #DONE}, {@link #TOOL}. */
    public static final ResourceLocation EFFECT_CHOPPED = HandsOnDelight.id("chopped");
    public static final ResourceLocation EFFECT_PLACED = HandsOnDelight.id("placed");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnDelight.id("refused");
    /**
     * Data: {@link #FINISH}, how the dish was finished ({@code AssemblyRecipes.Finish}); {@link #BLOCK}, whether it is a block;
     * {@link #LAYERS}, what it was made of, bottom first, one of each (a dish picked in the book is laid and made in one tick).
     */
    public static final ResourceLocation EFFECT_ASSEMBLED = HandsOnDelight.id("assembled");
    public static final String FINISH = "finish";
    public static final String BLOCK = "block";
    /** Data: {@link #BOOK}. */
    public static final ResourceLocation EFFECT_BOOK = HandsOnDelight.id("board_book");
    /**
     * Something comes off the board into the inventory: {@link #STACK} from layer {@link #AT}, or from the board itself with none;
     * {@link #CARVED} when it was a tool stuck into the board.
     */
    public static final ResourceLocation EFFECT_TAKEN = HandsOnDelight.id("taken");
    public static final String CARVED = "carved";
    /** A tool was stuck into the board: Farmer's Delight's sound of it. */
    public static final ResourceLocation EFFECT_CARVED = HandsOnDelight.id("carved");
    /** What a {@link #EFFECT_TAKEN} or {@link #EFFECT_REFUSED} is about, saved as a stack. */
    public static final String STACK = "stack";
    public static final String DONE = "done";
    public static final String TOOL = "tool";
    /** How many items the last chop cut. */
    public static final String COUNT = "count";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(BOARD, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(LAYERS, MOST_LAYERS, SlotRules.DEFAULT.maxStack(BATCH).kept().accepts(stack -> false))
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    /**
     * Boards of other mods that work as Farmer's Delight's (Chopper's Delight's of every wood): the same station, each in its own
     * look, as the block stays the world's. Chopper's Delight's decorated board is not one: its own renderer draws the whole board and
     * its banner, and the station hides a board's renderer.
     */
    public static final TagKey<Block> BOARDS = TagKey.create(Registries.BLOCK, HandsOnDelight.id("cutting_boards"));
    private static final ResourceLocation DECORATED = ResourceLocation.fromNamespaceAndPath("choppersdelight", "decorated_cutting_board");

    public static final StationType TYPE = Diegesis.station(ID)
            .block(FarmersDelightContent.CUTTING_BOARD)
            .tag(BOARDS)
            .when(state -> !DECORATED.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())))
            .schema(SCHEMA)
            .logic(BoardLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(CuttingBoardStation::opens)
            .idleLogic()
            .build();

    private CuttingBoardStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    /**
     * Sneaking keeps the board's own use: a tool stuck in with it, what lies there taken off with an empty hand. Everything else opens
     * the station, whatever is cut there: food, a log, a flower, leather armour. A tool stuck into the board opens it like a bare
     * board: it comes out by a click, or by itself for what comes to be cut.
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive();
    }
}

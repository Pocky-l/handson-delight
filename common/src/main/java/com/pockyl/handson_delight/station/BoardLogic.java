package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The board's rules. Cutting is Farmer's Delight's own, once the item has had its chops; what it makes goes to the cook instead of
 * onto the floor. A dish is put together from the bread up, and every layer must belong to some dish made that way. Also runs on
 * the clients around a board nobody works at, to show what lies on it; there it only reads.
 */
public final class BoardLogic implements StationLogic {
    /** Ticks between two chops the server takes: a click as fast as a hand can go, not a macro. */
    private static final int CHOP_GAP = 3;
    /**
     * Where marks may lie, as fractions of the item's length (never at its very ends), and how far apart two must be to read as two
     * cuts and to leave three pieces of a size one can see.
     */
    private static final float SPOT_FROM = 0.2F;
    private static final float SPOT_TO = 0.8F;
    private static final float SPOT_APART = 0.3F;
    /** How far a mark may lie from straight across the item: diagonal both ways, never along the item towards the cook. */
    private static final float MOST_TILT = 60.0F * Mth.DEG_TO_RAD;

    private final CuttingBoardBlockEntity board;
    /** How many of the marks the knife has come down on, in order. */
    private int marks;
    /** Where the marks of the item lying there are, see {@link CuttingBoardStation#SPOTS}. */
    private final float[] spots = new float[Cutting.CHOPS];
    private final float[] angles = new float[Cutting.CHOPS];
    private ItemStack cutting = ItemStack.EMPTY;
    private long lastChop = -CHOP_GAP;
    /** The dish the cook picked in the book: made at once when the layers make it. */
    private ResourceLocation picked;

    public BoardLogic(StationContext station) {
        board = (CuttingBoardBlockEntity) station.blockEntity();
        station.state().bindSlots(CuttingBoardStation.BOARD, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return Platform.INSTANCE.getItem(board);
            }

            @Override
            public void set(int index, ItemStack stack) {
                Platform.INSTANCE.setItem(board, stack);
            }
        });
        BoardLayers layers = (BoardLayers) board;
        station.state().bindSlots(CuttingBoardStation.LAYERS, new SlotStorage() {
            @Override
            public int size() {
                return CuttingBoardStation.MOST_LAYERS;
            }

            @Override
            public ItemStack get(int index) {
                return layers.handson$layers().get(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                layers.handson$layers().set(index, stack);
                layers.handson$layersChanged();
            }
        });
        restart(station);
    }

    /** The layers of the dish being put together, bottom first. */
    public static List<ItemStack> layers(StationState state) {
        List<ItemStack> layers = new ArrayList<>();
        for (ItemStack layer : state.getItems(CuttingBoardStation.LAYERS)) {
            if (!layer.isEmpty()) {
                layers.add(layer);
            }
        }
        return layers;
    }

    /** Nothing goes in by the library's own moves: every stack comes through {@link #place}, the hand's slot stays empty. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return slot.group().equals(CuttingBoardStation.BOARD);
    }

    @Override
    public void tick(StationContext station) {
        if (station.level().isClientSide()) {
            return;
        }
        ItemStack onBoard = Platform.INSTANCE.getItem(board);
        if (!ItemStack.isSameItemSameTags(onBoard, cutting)) {
            // Something else lies there now: a hopper took the item, or a deployer cut it.
            cutting = onBoard.copyWithCount(1);
            restart(station);
        }
        publish(station);
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (!AssemblyRecipes.book().isEmpty()) {
            CompoundTag book = new CompoundTag();
            book.put(CuttingBoardStation.BOOK, AssemblyRecipes.book());
            station.effect(CuttingBoardStation.EFFECT_BOOK, "surface", book);
        }
    }

    /** The marks and the dish picked start over when the last cook leaves; another one may still be cutting. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (station.users().isEmpty()) {
            restart(station);
            picked = null;
        }
        publish(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        switch (action) {
            case CuttingBoardStation.ACTION_PLACE -> place(station, player, data.getBoolean(CuttingBoardStation.ONE),
                    data.contains(CuttingBoardStation.FROM) ? data.getInt(CuttingBoardStation.FROM) : -1);
            case CuttingBoardStation.ACTION_CHOP -> chop(station, player, data.getInt(CuttingBoardStation.MARK), data.getFloat(CuttingBoardStation.AT));
            case CuttingBoardStation.ACTION_TAKE -> take(station, player);
            case CuttingBoardStation.ACTION_ASSEMBLE -> assemble(station, player);
            case CuttingBoardStation.ACTION_PICK ->
                    pick(station, player, ResourceLocation.tryParse(data.getString(CuttingBoardStation.DISH)));
            default -> {
            }
        }
        publish(station);
    }

    /**
     * The stack on the cursor, or the one in inventory slot {@code from}, goes onto the board: on the dish when it belongs to one, else
     * to be cut.
     */
    private void place(StationContext station, Player player, boolean one, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack carried = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        StationState state = station.state();
        List<ItemStack> layers = layers(state);
        ItemStack onBoard = Platform.INSTANCE.getItem(board);
        if (!layers.isEmpty() || onBoard.isEmpty() && AssemblyRecipes.isBase(carried)) {
            List<ItemStack> next = new ArrayList<>(layers);
            next.add(carried.copyWithCount(1));
            if (layers.size() >= CuttingBoardStation.MOST_LAYERS || !AssemblyRecipes.fits(next)) {
                refuse(station, carried);
                return;
            }
            // A batch: the bread sets how many, every layer after it as many as the hand has, up to that.
            int batch = layers.isEmpty() ? CuttingBoardStation.BATCH : layers.get(0).getCount();
            state.setItem(SlotRef.of(CuttingBoardStation.LAYERS, layers.size()), carried.split(one ? 1 : Math.min(carried.getCount(), batch)));
            putBack(player, from, carried);
            CompoundTag where = new CompoundTag();
            where.putInt(CuttingBoardStation.AT, layers.size());
            station.effect(CuttingBoardStation.EFFECT_PLACED, "surface", where);
            finishIfWanted(station, player, next);
            return;
        }
        boolean fits = onBoard.isEmpty() ? !Cutting.recipesFor(station.level(), carried).isEmpty()
                : ItemStack.isSameItemSameTags(onBoard, carried);
        int room = Math.min(carried.getMaxStackSize(), board.getMaxStackSize()) - onBoard.getCount();
        int moved = Math.min(one ? 1 : carried.getCount(), room);
        if (!fits || moved <= 0) {
            refuse(station, carried);
            return;
        }
        ItemStack put = onBoard.isEmpty() ? carried.copyWithCount(moved) : onBoard.copyWithCount(onBoard.getCount() + moved);
        carried.shrink(moved);
        putBack(player, from, carried);
        Platform.INSTANCE.setItem(board, put);
        station.effect(CuttingBoardStation.EFFECT_PLACED, "surface", new CompoundTag());
    }

    /**
     * The layers make a dish at once when it is the one picked in the book, or when they could become nothing bigger. A dish that is
     * also the start of a bigger one waits for a click on the stack or a layer more: nothing is made that the cook did not mean.
     */
    private void finishIfWanted(StationContext station, Player player, List<ItemStack> layers) {
        Optional<AssemblyRecipes.Assembly> dish = AssemblyRecipes.complete(layers);
        if (dish.isPresent() && (dish.get().id().equals(picked) || !AssemblyRecipes.canGrow(layers))) {
            assemble(station, player);
        }
    }

    private void pick(StationContext station, Player player, ResourceLocation dish) {
        if (dish != null && AssemblyRecipes.all().stream().anyMatch(assembly -> assembly.id().equals(dish))) {
            picked = dish;
            finishIfWanted(station, player, layers(station.state()));
        }
    }

    /**
     * The knife comes down on the item; with its last chop Farmer's Delight cuts a batch of the stack, one item after the other as
     * its own cut does (each wears the knife), and the pieces go to the cook.
     */
    private void chop(StationContext station, Player player, int mark, float at) {
        Level level = station.level();
        ItemStack onBoard = Platform.INSTANCE.getItem(board);
        long now = level.getGameTime();
        // Only the next mark is shown, so only it can be cut. The cook's client drew the cut at once: it hears that there was none.
        if (onBoard.isEmpty() || mark != marks || now - lastChop < CHOP_GAP) {
            refuse(station);
            return;
        }
        lastChop = now;
        // The knife is the one the cook took on the cursor.
        ItemStack tool = player.inventoryMenu.getCarried();
        if (Cutting.findWith(level, onBoard, tool).isEmpty()) {
            refuse(station);
            return;
        }
        CompoundTag chopped = new CompoundTag();
        chopped.putFloat(CuttingBoardStation.AT, Mth.clamp(at, 0.0F, 16.0F));
        chopped.putFloat(CuttingBoardStation.SPOT, spots[mark]);
        chopped.putFloat(CuttingBoardStation.ANGLE, angles[mark]);
        marks++;
        chopped.putString(CuttingBoardStation.TOOL, BuiltInRegistries.ITEM.getKey(tool.getItem()).toString());
        if (marks < Cutting.CHOPS) {
            station.effect(CuttingBoardStation.EFFECT_CHOPPED, "item", chopped);
            return;
        }
        // The next item of the stack comes with marks of its own.
        restart(station);
        int done = 0;
        while (done < CuttingBoardStation.BATCH && !Platform.INSTANCE.getItem(board).isEmpty() && !tool.isEmpty()) {
            List<ItemStack> results = BoardResults.catching(done > 0, () -> board.processStoredItemUsingTool(tool, player));
            if (results == null) {
                break;
            }
            results.forEach(result -> give(player, result));
            done++;
        }
        player.inventoryMenu.setCarried(tool.isEmpty() ? ItemStack.EMPTY : tool);
        if (done == 0) {
            refuse(station);
            return;
        }
        chopped.putInt(CuttingBoardStation.COUNT, done);
        chopped.putBoolean(CuttingBoardStation.DONE, true);
        station.effect(CuttingBoardStation.EFFECT_CHOPPED, "item", chopped);
    }

    /** The top layer comes off the dish; with none, what lies to be cut goes back. */
    private void take(StationContext station, Player player) {
        StationState state = station.state();
        List<ItemStack> layers = layers(state);
        if (!layers.isEmpty()) {
            SlotRef top = SlotRef.of(CuttingBoardStation.LAYERS, layers.size() - 1);
            ItemStack taken = state.getItem(top);
            taken(station, taken, layers.size() - 1);
            give(player, taken);
            state.setItem(top, ItemStack.EMPTY);
            return;
        }
        ItemStack onBoard = Platform.INSTANCE.getItem(board);
        if (!onBoard.isEmpty()) {
            Platform.INSTANCE.setItem(board, ItemStack.EMPTY);
            restart(station);
            taken(station, onBoard, -1);
            give(player, onBoard);
        }
    }

    /**
     * The layers become their dish, as the crafting table made it, as many times as the thinnest layer allows; it lies on the board
     * (Farmer's Delight's slot, so a hopper can take it as well as a click), and what is left of the other layers goes back.
     */
    private void assemble(StationContext station, Player player) {
        StationState state = station.state();
        Optional<AssemblyRecipes.Assembly> dish = AssemblyRecipes.complete(layers(state));
        if (dish.isEmpty()) {
            refuse(station);
            return;
        }
        int times = layers(state).stream().mapToInt(ItemStack::getCount).min().orElse(0);
        ItemStack one = dish.get().recipe().getResultItem(station.level().registryAccess());
        ItemStack result = one.copyWithCount(one.getCount() * times);
        for (int i = 0; i < CuttingBoardStation.MOST_LAYERS; i++) {
            SlotRef slot = SlotRef.of(CuttingBoardStation.LAYERS, i);
            ItemStack left = state.getItem(slot).copy();
            left.shrink(times);
            state.setItem(slot, ItemStack.EMPTY);
            if (!left.isEmpty()) {
                give(player, left);
            }
        }
        result.onCraftedBy(station.level(), player, result.getCount());
        ItemStack onBoard = Platform.INSTANCE.getItem(board);
        if (onBoard.isEmpty() || ItemStack.isSameItemSameTags(onBoard, result)) {
            int room = result.getMaxStackSize() - onBoard.getCount();
            int there = Math.min(room, result.getCount());
            Platform.INSTANCE.setItem(board, result.copyWithCount(onBoard.getCount() + there));
            result.shrink(there);
        }
        if (!result.isEmpty()) {
            give(player, result);
        }
        station.effect(CuttingBoardStation.EFFECT_ASSEMBLED, "item", new CompoundTag());
    }

    private static void refuse(StationContext station) {
        refuse(station, ItemStack.EMPTY);
    }

    /** The board will not have it: with a {@code stack}, that stack bounces back to the cook's hand. */
    private static void refuse(StationContext station, ItemStack stack) {
        CompoundTag data = new CompoundTag();
        if (!stack.isEmpty()) {
            data.put(CuttingBoardStation.STACK, stack.copyWithCount(1).save(new CompoundTag()));
        }
        station.effect(CuttingBoardStation.EFFECT_REFUSED, "surface", data);
    }

    /** What came off the board, from which layer (-1: what lay to be cut, or the dish), for the clients to lift it off. */
    private static void taken(StationContext station, ItemStack stack, int layer) {
        CompoundTag data = new CompoundTag();
        data.put(CuttingBoardStation.STACK, stack.save(new CompoundTag()));
        data.putInt(CuttingBoardStation.AT, layer);
        station.effect(CuttingBoardStation.EFFECT_TAKEN, "surface", data);
    }

    /**
     * No cuts on the item yet, and two new marks for it: at random places and angles, apart enough to read as two cuts, and never
     * crossing on the item (that would make four pieces of no sense).
     */
    private void restart(StationContext station) {
        marks = 0;
        RandomSource random = station.level().getRandom();
        spots[0] = Mth.nextFloat(random, SPOT_FROM, SPOT_TO);
        angles[0] = Mth.nextFloat(random, -MOST_TILT, MOST_TILT);
        for (int attempt = 0; ; attempt++) {
            spots[1] = Mth.nextFloat(random, SPOT_FROM, SPOT_TO);
            angles[1] = attempt < 30 ? Mth.nextFloat(random, -MOST_TILT, MOST_TILT) : angles[0];
            if (Math.abs(spots[1] - spots[0]) >= SPOT_APART && !crossOnTheItem(spots[0], angles[0], spots[1], angles[1])) {
                break;
            }
        }
    }

    /**
     * Whether two marks meet on the item, taken as a square: each runs through the middle of the item's width at its spot, the
     * angle from straight across.
     */
    public static boolean crossOnTheItem(float spotA, float angleA, float spotB, float angleB) {
        float ax = Mth.cos(angleA);
        float az = Mth.sin(angleA);
        float bx = Mth.cos(angleB);
        float bz = Mth.sin(angleB);
        float across = ax * bz - az * bx;
        if (Math.abs(across) < 1.0e-4F) {
            return false;
        }
        // Where along mark A it meets mark B.
        float t = (0.0F * bz - (spotB - spotA) * bx) / across;
        float x = 0.5F + t * ax;
        float z = spotA + t * az;
        return x >= 0.0F && x <= 1.0F && z >= 0.0F && z <= 1.0F;
    }

    /** Where the marks of the item lying there are now, fractions from its far end. */
    public float[] spots() {
        return spots.clone();
    }

    /** How the marks lie, radians from straight across. */
    public float[] angles() {
        return angles.clone();
    }

    /** What is left of the stack goes back where it came from: the cursor, or its inventory slot. */
    private static void putBack(Player player, int from, ItemStack stack) {
        if (from >= 0) {
            player.getInventory().setItem(from, stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** The cuts and the dish the layers make, for the clients; sent only when they change. */
    private void publish(StationContext station) {
        StationState state = station.state();
        CompoundTag data = new CompoundTag();
        if (marks > 0) {
            data.putInt(CuttingBoardStation.MARKS, marks);
        }
        if (!Platform.INSTANCE.getItem(board).isEmpty() && layers(state).isEmpty()) {
            ListTag where = new ListTag();
            ListTag how = new ListTag();
            for (int mark = 0; mark < Cutting.CHOPS; mark++) {
                where.add(FloatTag.valueOf(spots[mark]));
                how.add(FloatTag.valueOf(angles[mark]));
            }
            data.put(CuttingBoardStation.SPOTS, where);
            data.put(CuttingBoardStation.ANGLES, how);
        }
        AssemblyRecipes.complete(layers(state)).ifPresent(dish -> data.putString(CuttingBoardStation.DISH,
                BuiltInRegistries.ITEM.getKey(dish.recipe().getResultItem(station.level().registryAccess()).getItem()).toString()));
        if (!data.equals(state.data())) {
            state.setData(data);
        }
    }
}

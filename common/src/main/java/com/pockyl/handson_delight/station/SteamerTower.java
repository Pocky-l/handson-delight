package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Youkai's Feasts' steamer as one tower: its pot and the blocks of racks stacked on it, as many as the pot sends its steam through
 * (four), racks counted from the pot up across them: the pot's own two first, then four to a block, eighteen at most. A lid lies on
 * the top rack, or stands as a block of its own over a full block.
 */
public final class SteamerTower {
    /** Rack blocks the pot's steam goes up through, over the pot itself. */
    public static final int RACK_BLOCKS = 4;
    /** Racks in the tallest tower: two on the pot, four in each block over it. */
    public static final int MOST_RACKS = 2 + 4 * RACK_BLOCKS;

    private final BlockPos pot;
    private final List<KitchenSteamer> blocks;
    private final int[] firstRack;
    private final int racks;

    private SteamerTower(BlockPos pot, List<KitchenSteamer> blocks) {
        this.pot = pot;
        this.blocks = blocks;
        this.firstRack = new int[blocks.size()];
        int count = 0;
        for (int i = 0; i < blocks.size(); i++) {
            firstRack[i] = count;
            count += blocks.get(i).handson$racks();
        }
        this.racks = count;
    }

    /** The tower standing on the pot at {@code pot}, or null when no steamer pot is there. */
    public static SteamerTower of(BlockGetter level, BlockPos pot) {
        KitchenSteamer base = KitchenSteamer.of(level.getBlockEntity(pot));
        if (base == null || !base.handson$isPot()) {
            return null;
        }
        List<KitchenSteamer> blocks = new ArrayList<>();
        blocks.add(base);
        for (int up = 1; up <= RACK_BLOCKS; up++) {
            BlockPos at = pot.above(up);
            KitchenSteamer block = isRack(level.getBlockState(at)) ? KitchenSteamer.of(level.getBlockEntity(at)) : null;
            if (block == null) {
                break;
            }
            blocks.add(block);
        }
        return new SteamerTower(pot.immutable(), List.copyOf(blocks));
    }

    @SuppressWarnings("removal")
    private static boolean is(BlockState state, String path) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(Identifier.fromNamespaceAndPath(KitchenSteamer.MOD_ID, path));
    }

    public static boolean isPot(BlockState state) {
        return is(state, "steamer_pot");
    }

    public static boolean isRack(BlockState state) {
        return is(state, "steamer_rack");
    }

    public static boolean isLid(BlockState state) {
        return is(state, "steamer_lid");
    }

    @SuppressWarnings("removal")
    public static Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(KitchenSteamer.MOD_ID, path));
    }

    /**
     * The pot under a block of the tower: the block itself for the pot, the pot a rack block or the lid stands on, or the block itself
     * when it stands on none (racks stacked on something else steam nothing).
     */
    public static BlockPos potUnder(BlockGetter level, BlockPos pos, BlockState state) {
        if (isPot(state)) {
            return pos;
        }
        BlockPos at = pos;
        for (int down = 0; down <= RACK_BLOCKS; down++) {
            at = at.below();
            BlockState below = level.getBlockState(at);
            if (isPot(below)) {
                return at;
            }
            if (!isRack(below)) {
                break;
            }
        }
        return pos;
    }

    /** The blocks over the pot at {@code pot} that are the tower's: its blocks of racks and a lid block on the last. */
    public static List<BlockPos> above(BlockGetter level, BlockPos pot) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos at = pot;
        for (int up = 1; up <= RACK_BLOCKS + 1; up++) {
            at = at.above();
            BlockState state = level.getBlockState(at);
            if (isLid(state)) {
                found.add(at);
                break;
            }
            if (!isRack(state) || up > RACK_BLOCKS) {
                break;
            }
            found.add(at);
        }
        return found;
    }

    public BlockPos pot() {
        return pot;
    }

    public KitchenSteamer potBlock() {
        return blocks.get(0);
    }

    /** The top block of the tower, which a new rack or the lid goes onto. */
    public KitchenSteamer top() {
        return blocks.get(blocks.size() - 1);
    }

    /** Blocks in the tower, the pot one of them. */
    public int blocks() {
        return blocks.size();
    }

    /** Racks in the tower, from the pot up. */
    public int racks() {
        return racks;
    }

    /** Whether the tower has room for one more rack. */
    public boolean hasRoom() {
        return racks < MOST_RACKS;
    }

    /** The block rack {@code rack} of the tower is in. */
    public KitchenSteamer blockOf(int rack) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (rack >= firstRack[i]) {
                return blocks.get(i);
            }
        }
        return blocks.get(0);
    }

    /** Which rack of its own block rack {@code rack} of the tower is. */
    public int localOf(int rack) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (rack >= firstRack[i]) {
                return rack - firstRack[i];
            }
        }
        return rack;
    }

    public ItemStack item(int rack, int place) {
        return rack < 0 || rack >= racks ? ItemStack.EMPTY : blockOf(rack).handson$item(localOf(rack), place);
    }

    public float steamed(int rack, int place) {
        return rack < 0 || rack >= racks ? -1.0F : blockOf(rack).handson$steamed(localOf(rack), place);
    }

    public int steamTime(int rack, int place) {
        return rack < 0 || rack >= racks ? 0 : blockOf(rack).handson$steamTime(localOf(rack), place);
    }

    public float heat(int rack) {
        return rack < 0 || rack >= racks ? 0.0F : blockOf(rack).handson$heat(localOf(rack));
    }

    /** Whether the top rack is covered. */
    public boolean isCapped() {
        return racks > 0 && top().handson$isCapped();
    }

    /** Whether anything on the tower still steams. */
    public boolean isSteaming() {
        for (int rack = 0; rack < racks; rack++) {
            for (int place = 0; place < KitchenSteamer.PLACES; place++) {
                if (blockOf(rack).handson$isSteaming(localOf(rack), place)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether rack {@code rack} has nothing on it. */
    public boolean isEmpty(int rack) {
        for (int place = 0; place < KitchenSteamer.PLACES; place++) {
            if (!item(rack, place).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}

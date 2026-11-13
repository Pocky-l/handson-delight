package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A serving taken with a bowl in the hand, without opening the pot, counts as made, as one taken from the menu's result slot does:
 * the stars of a dish open by the servings a player has made, and the quickest way to serve must not leave them shut. Each pot's
 * block calls this before and after its use.
 */
public final class PotServing {
    /** The meal as it was before the use, per thread: the client of a single-player game uses the block in its own thread. */
    private static final ThreadLocal<ItemStack> MEAL_BEFORE = new ThreadLocal<>();

    private PotServing() {
    }

    public static void before(Level level, BlockPos pos) {
        KitchenPot pot = level.isClientSide() ? null : KitchenPot.of(level.getBlockEntity(pos));
        MEAL_BEFORE.set(pot == null ? ItemStack.EMPTY : pot.handson$meal().copy());
    }

    public static void after(Level level, BlockPos pos, Player player) {
        ItemStack before = MEAL_BEFORE.get();
        MEAL_BEFORE.remove();
        KitchenPot pot = KitchenPot.of(level.getBlockEntity(pos));
        if (before == null || before.isEmpty() || pot == null) {
            return;
        }
        ItemStack now = pot.handson$meal();
        int served = now.isEmpty() ? before.getCount() : ItemStack.isSameItemSameComponents(now, before) ? before.getCount() - now.getCount() : 0;
        if (served > 0) {
            ItemStack serving = before.copyWithCount(served);
            serving.onCraftedBy(level, player, served);
        }
    }
}

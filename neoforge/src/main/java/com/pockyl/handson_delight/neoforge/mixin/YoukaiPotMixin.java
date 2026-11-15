package com.pockyl.handson_delight.neoforge.mixin;

import dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem;
import dev.xkmc.youkaishomecoming.content.pot.cooking.soup.SoupHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Youkai's Feasts' cooking pots (small iron pot, short iron pot, stockpot: their common block entity) as a {@link KitchenYoukaiPot}.
 * Food goes in through its own {@code tryAddItem}, with what its click does around it (a bowl or bottle back, a slip bottle drained,
 * a tool worn); what is in it is read and taken out through the item handler it registers. It cooks as it always does: while somebody works
 * at it, a dish done is told to them before it comes out, and one that would come out over the pot goes to them. Its
 * classes build on the L2 libraries nested in its jar, which are not on the compile classpath: they are reached by name.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.cooking.core.CookingBlockEntity", remap = false)
abstract class YoukaiPotMixin implements KitchenYoukaiPot {
    @Unique
    private static final ResourceLocation HANDSON$COOKING = ResourceLocation.tryBuild(MOD_ID, "pot_cooking");
    /** How near the cook must be for the bowl to go to them rather than out over the pot. */
    @Unique
    private static final double HANDSON$REACH = 8.0;
    @Unique
    private static boolean handson$warned;
    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private List<Dish> handson$dishes = List.of();

    @Unique
    private Cook handson$cook;

    @Shadow
    public abstract boolean tryAddItem(ItemStack stack, boolean simulate);

    @Shadow
    public abstract void setLastPlayer(Player player);

    @Shadow
    public abstract Item container();

    @Shadow
    public abstract SoupHolder getSoup();

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private Level handson$level() {
        return handson$blockEntity().getLevel();
    }

    @Unique
    private IItemHandler handson$items() {
        BlockEntity self = handson$blockEntity();
        return self.getLevel() == null ? null
                : self.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, self.getBlockPos(), self.getBlockState(), self, null);
    }

    @Override
    public Item handson$pot() {
        return container();
    }

    @Override
    public List<ItemStack> handson$contents() {
        List<ItemStack> in = new ArrayList<>();
        IItemHandler items = handson$items();
        if (items == null) {
            return in;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                in.add(stack.copy());
            }
        }
        return in;
    }

    @Override
    public boolean handson$takes(ItemStack stack) {
        return !stack.isEmpty() && handson$level() != null && tryAddItem(stack, true);
    }

    @Override
    public boolean handson$put(Player player, ItemStack held) {
        Level level = handson$level();
        if (level == null || held.isEmpty() || !tryAddItem(held, true)) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        tryAddItem(held, false);
        setLastPlayer(player);
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (held.isDamageableItem()) {
            if (level instanceof ServerLevel server) {
                held.hurtAndBreak(1, server, player instanceof ServerPlayer cook ? cook : null, broken -> {
                });
            }
        } else if (SlipBottleItem.isSlipContainer(held)) {
            handson$give(player, SlipBottleItem.drain(held.split(1)));
        } else {
            ItemStack container = held.getCraftingRemainingItem();
            held.shrink(1);
            if (!container.isEmpty()) {
                handson$give(player, container.copyWithCount(1));
            }
        }
        return true;
    }

    @Unique
    private static void handson$give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public ItemStack handson$takeBack(Player player) {
        IItemHandler items = handson$items();
        Level level = handson$level();
        if (items == null || level == null) {
            return null;
        }
        int last = -1;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                last = slot;
            }
        }
        if (last < 0) {
            return null;
        }
        ItemStack stack = items.getStackInSlot(last);
        // A slip bottle's drink and a tool's wear cannot be put back: only a copy went in.
        if (SlipBottleItem.isSlipContainer(stack) || stack.isDamageableItem()) {
            return null;
        }
        // It went in out of its bowl or bottle, which the cook got back: it comes back into one of the cook's.
        ItemStack container = stack.getCraftingRemainingItem();
        if (!container.isEmpty() && !player.getAbilities().instabuild) {
            int slot = -1;
            for (int i = 0; i < player.getInventory().items.size() && slot < 0; i++) {
                if (ItemStack.isSameItem(player.getInventory().items.get(i), container)) {
                    slot = i;
                }
            }
            if (slot < 0) {
                return null;
            }
            if (!level.isClientSide()) {
                player.getInventory().getItem(slot).shrink(1);
            }
        }
        return level.isClientSide() ? stack.copy() : items.extractItem(last, 1, false);
    }

    @Override
    public ResourceLocation handson$cooking() {
        return ((YoukaiTimedAccess) this).handson$totalTime() > 0 ? ((YoukaiTimedAccess) this).handson$recipeId() : null;
    }

    @Override
    public int handson$cooked() {
        YoukaiTimedAccess timed = (YoukaiTimedAccess) this;
        return timed.handson$totalTime() > 0 ? Math.min(timed.handson$recipeProgress(), timed.handson$totalTime()) : 0;
    }

    @Override
    public int handson$cookTime() {
        return ((YoukaiTimedAccess) this).handson$totalTime();
    }

    @Override
    public Soup handson$soup() {
        SoupHolder soup = getSoup();
        ResourceLocation now = soup.current.id();
        ResourceLocation next = soup.next.id();
        float toNext = now.equals(next) || soup.next.time() == soup.current.time() ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, soup.getProgress(0.0F)));
        return new Soup(now, next, toNext);
    }

    @Override
    public boolean handson$isHeated() {
        Level level = handson$level();
        return level != null && ((HeatableBlockEntity) this).isHeated(level, handson$blockEntity().getBlockPos());
    }

    @Override
    public void handson$setCook(Cook cook) {
        handson$cook = cook;
    }

    @Override
    public Cook handson$cook() {
        return handson$cook;
    }

    /** The cook at the pot, when they are still there and near it. */
    @Unique
    private Cook handson$cookNear() {
        Cook cook = handson$cook;
        BlockPos pos = handson$blockEntity().getBlockPos();
        if (cook == null || !cook.player().isAlive() || cook.player().level() != handson$level()
                || cook.player().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > HANDSON$REACH * HANDSON$REACH) {
            return null;
        }
        return cook;
    }

    /** A dish is done: the cook hears of it before it stands where the pot stood (a pot of a feast, a bowl of soup). */
    @Redirect(method = "finishRecipe", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean handson$feastDone(Level level, BlockPos pos, BlockState state) {
        Cook cook = handson$cookNear();
        if (cook != null) {
            cook.done(new ItemStack(state.getBlock()));
        }
        handson$cook = null;
        return level.setBlockAndUpdate(pos, state);
    }

    /** A dish that is no block is done (a modpack's recipe): it goes to the cook at the pot, else out over the pot as before. */
    @Redirect(method = "finishRecipe", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
    private void handson$bowlDone(Level level, BlockPos pos, ItemStack dish) {
        Cook cook = handson$cookNear();
        if (cook == null) {
            Block.popResource(level, pos, dish);
            return;
        }
        ItemStack given = dish.copy();
        handson$give(cook.player(), given);
        cook.served(dish.copy());
    }

    @Override
    public List<Dish> handson$dishes() {
        Level level = handson$level();
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (manager != handson$readFrom) {
            handson$dishes = handson$read(manager);
            handson$readFrom = manager;
        }
        return handson$dishes;
    }

    /** Its pot recipes: what goes in, the dish, the time, and the pot the dish is served in, which is the pot it needs. */
    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Dish> handson$read(RecipeManager manager) {
        List<Dish> dishes = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(HANDSON$COOKING);
        if (type.isEmpty()) {
            return dishes;
        }
        for (RecipeHolder<?> holder : (List<RecipeHolder<?>>) (List) manager.getAllRecipesFor((RecipeType) type.get())) {
            Object recipe = holder.value();
            try {
                List<Ingredient> input = List.copyOf((List<Ingredient>) handson$call(recipe, "getInput"));
                ItemStack result = ((ItemStack) handson$call(recipe, "getResult")).copy();
                int ticks = (Integer) handson$call(recipe, "getProcessTime");
                ItemStack pot = result.getCraftingRemainingItem();
                if (!result.isEmpty() && !pot.isEmpty() && !input.isEmpty()) {
                    dishes.add(new Dish(holder.id(), result, input, ticks, pot.getItem()));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!handson$warned) {
                    handson$warned = true;
                    HandsOnDelight.LOGGER.warn("A pot recipe of Youkai's Feasts is not what the station knows: {}", holder.id(), e);
                }
            }
        }
        return List.copyOf(dishes);
    }

    @Unique
    private static Object handson$call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}

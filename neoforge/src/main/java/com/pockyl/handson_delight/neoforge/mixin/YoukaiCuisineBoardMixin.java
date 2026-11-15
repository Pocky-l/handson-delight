package com.pockyl.handson_delight.neoforge.mixin;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem;
import dev.xkmc.youkaishomecoming.content.pot.table.item.IngredientTableItem;
import dev.xkmc.youkaishomecoming.content.pot.table.item.TableItem;
import dev.xkmc.youkaishomecoming.content.pot.table.item.TableItemManager;
import dev.xkmc.youkaishomecoming.content.pot.table.item.VariantTableItemBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.KitchenCuisine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Youkai's Feasts' cuisine board as a {@link KitchenCuisine}. Every step goes through its own {@code addItem}, {@code addToPlayer}
 * and {@code performToolAction}, and what its block's click does around them (a sauce's bottle back, a slip bottle drained, its
 * advancement) is done as that click does it. Its recipes and its advancement trigger build on the L2 libraries nested in its jar,
 * which are not on the compile classpath: they are read by name.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.table.board.CuisineBoardBlockEntity", remap = false)
abstract class YoukaiCuisineBoardMixin implements KitchenCuisine {
    @Unique
    private static final TagKey<Item> HANDSON$WITH_CONTAINER = TagKey.create(Registries.ITEM,
            ResourceLocation.tryBuild(MOD_ID, "place_with_container"));
    @Unique
    private static final ResourceLocation HANDSON$CUISINE = ResourceLocation.tryBuild(MOD_ID, "cuisine");
    @Unique
    private static boolean handson$warned;

    @Shadow
    @Final
    private List<ItemStack> contents;
    @Shadow
    private TableItem model;

    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private List<Dish> handson$dishes = List.of();

    @Shadow
    public abstract int addItem(ItemStack stack);

    @Shadow
    public abstract boolean addToPlayer(Player player);

    @Shadow
    public abstract boolean performToolAction(ItemStack stack);

    @Shadow
    public abstract void notifyTile();

    @Shadow
    public abstract TableItem getModel();

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private Level handson$level() {
        return handson$blockEntity().getLevel();
    }

    @Override
    public List<ItemStack> handson$contents() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : contents) {
            copy.add(stack.copy());
        }
        return copy;
    }

    @Override
    public int handson$takes(ItemStack stack) {
        Level level = handson$level();
        if (level == null || stack.isEmpty()) {
            return 0;
        }
        ItemStack what = stack;
        if (SlipBottleItem.isSlipContainer(what)) {
            what = SlipBottleItem.getContentStack(what);
            if (what.isEmpty()) {
                return 0;
            }
        }
        TableItem now = getModel();
        Optional<TableItem> next = now.find(level, what);
        if (next.isEmpty()) {
            return 0;
        }
        int cost = next.get().getCost(now);
        return cost > what.getCount() ? 0 : cost;
    }

    @Override
    public boolean handson$byHand() {
        Level level = handson$level();
        return level != null && getModel().find(level, ItemStack.EMPTY).isPresent();
    }

    @Override
    public int handson$put(Player player, ItemStack held) {
        if (held.isEmpty() || handson$takes(held) <= 0) {
            return 0;
        }
        int cost = addItem(held);
        if (cost <= 0 || handson$level().isClientSide()) {
            return cost;
        }
        if (!player.getAbilities().instabuild) {
            if (SlipBottleItem.isSlipContainer(held)) {
                handson$give(player, SlipBottleItem.drain(held.split(1)));
            } else {
                boolean withContainer = held.is(HANDSON$WITH_CONTAINER);
                ItemStack container = held.getCraftingRemainingItem();
                held.shrink(cost);
                if (!withContainer && !container.isEmpty()) {
                    handson$give(player, container.copyWithCount(cost));
                }
            }
        }
        handson$rewardDone(player);
        return cost;
    }

    @Override
    public boolean handson$shape(Player player) {
        if (!handson$byHand() || addItem(ItemStack.EMPTY) <= 0) {
            return false;
        }
        handson$rewardDone(player);
        return true;
    }

    /** Its advancement for a dish made on the board, as its click gives it. */
    @Unique
    private void handson$rewardDone(Player player) {
        if (player instanceof ServerPlayer server && getModel().complete(handson$level()).isPresent()) {
            try {
                Object table = Class.forName("dev.xkmc.youkaishomecoming.init.registrate.YHCriteriaTriggers").getField("TABLE").get(null);
                if (handson$call(table, "get") instanceof PlayerTrigger trigger) {
                    trigger.trigger(server);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!handson$warned) {
                    handson$warned = true;
                    HandsOnDelight.LOGGER.warn("Youkai's Feasts' advancement for the cuisine board is not where the station looks", e);
                }
            }
        }
    }

    @Unique
    private static void handson$give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public ItemStack handson$finished() {
        Level level = handson$level();
        return level == null ? ItemStack.EMPTY : getModel().complete(level).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean handson$take(Player player) {
        return !handson$finished().isEmpty() && addToPlayer(player);
    }

    @Override
    public boolean handson$cut(ItemStack tool) {
        return !tool.isEmpty() && performToolAction(tool);
    }

    @Override
    public ItemStack handson$takeBack(Player player) {
        if (contents.isEmpty() || handson$level() == null) {
            return null;
        }
        ItemStack last = contents.get(contents.size() - 1);
        // It went on out of its container, which the cook got back: it goes back into one of the cook's.
        if (!last.isEmpty() && last.hasCraftingRemainingItem() && !last.is(HANDSON$WITH_CONTAINER)
                && !player.getAbilities().instabuild && !handson$level().isClientSide()) {
            ItemStack container = last.getCraftingRemainingItem();
            int need = last.getCount();
            if (player.getInventory().countItem(container.getItem()) < need) {
                return null;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize() && need > 0; slot++) {
                ItemStack there = player.getInventory().getItem(slot);
                if (ItemStack.isSameItem(there, container)) {
                    int used = Math.min(need, there.getCount());
                    there.shrink(used);
                    need -= used;
                }
            }
        }
        if (!handson$level().isClientSide()) {
            contents.remove(contents.size() - 1);
            model = null;
            getModel();
            notifyTile();
        }
        return last.copy();
    }

    @Override
    public List<ResourceLocation> handson$models() {
        return handson$level() == null ? List.of() : List.copyOf(getModel().getModels());
    }

    @Override
    public List<ResourceLocation> handson$modelsOf(List<ItemStack> laid) {
        Level level = handson$level();
        if (level == null) {
            return null;
        }
        Either<TableItem, Pair<TableItem, List<ItemStack>>> found = TableItemManager.TABLE.find(level, laid);
        return found.left().map(item -> List.copyOf(item.getModels())).orElse(null);
    }

    @Override
    public List<Ingredient> handson$hints() {
        Level level = handson$level();
        return level == null ? List.of() : List.copyOf(getModel().getHints(level));
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

    /**
     * Its recipes with every step from the empty board, as its recipe viewer page lays them out: the base's steps (rice, kelp, the
     * bamboo tube and what fills it, the hand that shapes or rolls), then the recipe's own, then the hand that rolls it up where the
     * base rolls. A recipe on a dish (a California roll topped) starts from that dish.
     */
    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Dish> handson$read(RecipeManager manager) {
        List<Dish> dishes = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(HANDSON$CUISINE);
        if (type.isEmpty()) {
            return dishes;
        }
        for (RecipeHolder<?> holder : (List<RecipeHolder<?>>) (List) manager.getAllRecipesFor((RecipeType) type.get())) {
            Object recipe = holder.value();
            try {
                ResourceLocation base = (ResourceLocation) handson$call(recipe, "base");
                List<Ingredient> own = new ArrayList<>((List<Ingredient>) handson$call(recipe, "getCustomIngredients"));
                ItemStack result = ((ItemStack) handson$call(recipe, "getResult")).copy();
                List<Ingredient> steps = new ArrayList<>();
                VariantTableItemBase variant = VariantTableItemBase.MAP.get(base);
                IngredientTableItem fixed = IngredientTableItem.FIXED.get(base);
                if (variant != null) {
                    variant.collectIngredients(steps, own);
                } else if (fixed != null) {
                    fixed.collectIngredients(steps);
                } else {
                    Item item = BuiltInRegistries.ITEM.get(base);
                    if (item == Items.AIR) {
                        continue;
                    }
                    steps.add(Ingredient.of(item));
                }
                steps.addAll(own);
                if (!result.isEmpty()) {
                    dishes.add(new Dish(holder.id(), result, List.copyOf(steps)));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!handson$warned) {
                    handson$warned = true;
                    HandsOnDelight.LOGGER.warn("A recipe of Youkai's Feasts' cuisine board is not what the station knows: {}",
                            holder.id(), e);
                }
            }
        }
        return dishes;
    }

    @Unique
    private static Object handson$call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}

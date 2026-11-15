package com.pockyl.handson_delight.neoforge.mixin;

import dev.xkmc.youkaishomecoming.content.block.food.ISteamerContentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;

import com.pockyl.handson_delight.neoforge.YoukaiSteamers;
import com.pockyl.handson_delight.station.KitchenSteamer;
import com.pockyl.handson_delight.station.SteamerTower;

import java.util.List;
import java.util.Optional;

/**
 * Youkai's Feasts' steamer block entity (its pot and its blocks of racks) as a {@link KitchenSteamer}. Things go on and come off
 * a rack through the rack's own methods, which its clicks call too (the stack split, the player kept for its advancements, what is
 * taken going back into the inventory); racks, the lid and water go on through its own rules. The pot steams as it always does;
 * when the last thing on the tower is done, everybody near hears a chime. Its classes build on the L2 libraries nested in its jar,
 * which are not on the compile classpath: they are reached by name ({@link YoukaiSteamers}).
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.steamer.SteamerBlockEntity", remap = false)
abstract class YoukaiSteamerMixin implements KitchenSteamer {
    @Unique
    private static final ResourceLocation HANDSON$STEAMING = ResourceLocation.tryBuild(MOD_ID, "steaming");
    /** Heat a thing takes of the steam going up past it and of the steam coming back down off a lid, as its racks count it. */
    @Unique
    private static final float HANDSON$UP = 0.01F;
    @Unique
    private static final float HANDSON$DOWN = 0.05F;

    @Shadow
    @Final
    public List<Object> racks;

    /** Steaming times by item, read from the recipes once (the client asks for them every frame). */
    @Unique
    private final java.util.Map<net.minecraft.world.item.Item, Integer> handson$times = new java.util.HashMap<>();
    @Unique
    private net.minecraft.world.item.crafting.RecipeManager handson$timesOf;

    /** Whether something on the tower steamed at the last tick of the pot. */
    @Unique
    private boolean handson$wasSteaming;

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private Level handson$level() {
        return handson$blockEntity().getLevel();
    }

    @Unique
    private BlockState handson$state() {
        return handson$blockEntity().getBlockState();
    }

    @Override
    public boolean handson$isPot() {
        return SteamerTower.isPot(handson$state());
    }

    @Override
    public int handson$racks() {
        BlockState state = handson$state();
        Property<?> property = state.getBlock().getStateDefinition().getProperty(handson$isPot() ? "pot_racks" : "racks");
        return property instanceof IntegerProperty racks && state.hasProperty(racks) ? state.getValue(racks) : 0;
    }

    /** The rack {@code rack} of this block as the block entity keeps it, or null: it catches up with the block state on its tick. */
    @Unique
    private Object handson$rack(int rack) {
        Level level = handson$level();
        if (racks.size() != handson$racks() && level != null && !level.isClientSide()) {
            // A rack put on a moment ago: its block entity counts its racks again on its next tick, as it would now.
            YoukaiSteamers.call(this, "checkRack");
        }
        return rack >= 0 && rack < racks.size() && rack < handson$racks() ? racks.get(rack) : null;
    }

    /** What lies on {@code place} of {@code rack}: its {@code RackItemData}, or null. */
    @Unique
    private Object handson$place(int rack, int place) {
        Object list = YoukaiSteamers.get(handson$rack(rack), "list");
        return list instanceof Object[] places && place >= 0 && place < places.length ? places[place] : null;
    }

    @Override
    public ItemStack handson$item(int rack, int place) {
        return YoukaiSteamers.get(handson$place(rack, place), "stack") instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    @Unique
    private Optional<AbstractCookingRecipe> handson$recipe(ItemStack stack) {
        Level level = handson$level();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(HANDSON$STEAMING);
        if (level == null || stack.isEmpty() || type.isEmpty()) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        RecipeType<AbstractCookingRecipe> steaming = (RecipeType<AbstractCookingRecipe>) type.get();
        return level.getRecipeManager().getRecipeFor(steaming, new SingleRecipeInput(stack), level).map(RecipeHolder::value)
                .filter(AbstractCookingRecipe.class::isInstance);
    }

    @Override
    public float handson$steamed(int rack, int place) {
        ItemStack stack = handson$item(rack, place);
        int time = handson$steamTime(rack, place);
        if (stack.isEmpty() || time <= 0) {
            return -1.0F;
        }
        Object progress = YoukaiSteamers.get(handson$place(rack, place), "progress");
        double done = progress instanceof Double value ? value : 0.0;
        return (float) Math.max(0.0, Math.min(1.0, done / time));
    }

    @Override
    public boolean handson$isSteaming(int rack, int place) {
        Object at = handson$place(rack, place);
        return at != null && YoukaiSteamers.get(at, "id") != null && !handson$item(rack, place).isEmpty();
    }

    @Override
    public int handson$steamTime(int rack, int place) {
        ItemStack stack = handson$item(rack, place);
        Level level = handson$level();
        if (stack.isEmpty() || level == null) {
            return 0;
        }
        if (level.getRecipeManager() != handson$timesOf) {
            handson$timesOf = level.getRecipeManager();
            handson$times.clear();
        }
        return handson$times.computeIfAbsent(stack.getItem(),
                item -> handson$recipe(stack).map(AbstractCookingRecipe::getCookingTime).orElse(0));
    }

    @Override
    public float handson$heat(int rack) {
        Object data = handson$rack(rack);
        Object up = YoukaiSteamers.get(data, "upwardHeat");
        Object down = YoukaiSteamers.get(data, "downwardHeat");
        double heat = (up instanceof Double value ? value * HANDSON$UP : 0.0) + (down instanceof Double value ? value * HANDSON$DOWN : 0.0);
        return (float) Math.max(0.0, heat);
    }

    @Override
    public float handson$clearance(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ISteamerContentBlock feast ? feast.clearance()
                : -1.0F;
    }

    @Override
    public boolean handson$takes(ItemStack stack) {
        Level level = handson$level();
        if (stack.isEmpty() || level == null) {
            return false;
        }
        Object rackData = racks.isEmpty() ? null : racks.get(0);
        Object valid = rackData == null ? null : YoukaiSteamers.call(rackData.getClass(), "isValid", level, stack);
        return valid instanceof Boolean value ? value : stack.has(DataComponents.FOOD) || handson$recipe(stack).isPresent();
    }

    /** The middle of a place, as a point a click on the rack's top hits: the rack picks the place by its quarter of the block. */
    @Unique
    private Vec3 handson$point(int place) {
        BlockPos pos = handson$blockEntity().getBlockPos();
        boolean east = place == 1 || place == 2;
        boolean south = place == 2 || place == 3;
        return new Vec3(pos.getX() + (east ? 0.75 : 0.25), pos.getY() + 0.5, pos.getZ() + (south ? 0.75 : 0.25));
    }

    @Override
    public boolean handson$put(int rack, int place, ItemStack held, Player player) {
        Object data = handson$rack(rack);
        Level level = handson$level();
        if (data == null || level == null || held.isEmpty() || !handson$takes(held)) {
            return false;
        }
        // A feast block fills its rack: nothing goes beside it (its own check lets things onto the other places).
        if (handson$clearance(handson$item(rack, 0)) >= 0.0F) {
            return false;
        }
        for (int at = 0; at < PLACES; at++) {
            if (place >= 0 && at != place) {
                continue;
            }
            if (Boolean.TRUE.equals(YoukaiSteamers.call(data, "tryAddItemAt", this, level, held, handson$point(at), player))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack handson$take(int rack, int place, Player player) {
        Object data = handson$rack(rack);
        Level level = handson$level();
        ItemStack there = handson$item(rack, place).copy();
        if (data == null || level == null || there.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Object taken = YoukaiSteamers.call(data, "tryTakeItemAt", this, level, handson$point(place), player, InteractionHand.MAIN_HAND);
        return Boolean.TRUE.equals(taken) ? there : ItemStack.EMPTY;
    }

    @Unique
    private Class<?> handson$rackInfo() {
        return YoukaiSteamers.type(this, YoukaiSteamers.RACK_INFO);
    }

    @Override
    public boolean handson$isCapped() {
        Level level = handson$level();
        return level != null && Boolean.TRUE.equals(YoukaiSteamers.call(handson$rackInfo(), "isCapped", level,
                handson$blockEntity().getBlockPos()));
    }

    @Override
    public boolean handson$addRack() {
        Level level = handson$level();
        BlockState state = handson$state();
        Object info = YoukaiSteamers.call(handson$rackInfo(), "getRackInfo", state);
        return level != null && Boolean.TRUE.equals(YoukaiSteamers.call(info, "tryAddRack", level, handson$blockEntity().getBlockPos(),
                state));
    }

    @Override
    public boolean handson$cap() {
        Level level = handson$level();
        return level != null && Boolean.TRUE.equals(YoukaiSteamers.call(handson$rackInfo(), "tryCap", level,
                handson$blockEntity().getBlockPos(), handson$state()));
    }

    @Unique
    private BooleanProperty handson$water() {
        return handson$state().getBlock().getStateDefinition().getProperty("water") instanceof BooleanProperty water ? water : null;
    }

    @Override
    public boolean handson$hasWater() {
        BooleanProperty water = handson$water();
        return water != null && handson$state().hasProperty(water) && handson$state().getValue(water);
    }

    @Override
    public boolean handson$fill() {
        BooleanProperty water = handson$water();
        Level level = handson$level();
        if (water == null || level == null || !handson$isPot() || handson$hasWater()) {
            return false;
        }
        if (!level.isClientSide()) {
            BlockPos pos = handson$blockEntity().getBlockPos();
            level.setBlockAndUpdate(pos, handson$state().setValue(water, true));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    @Override
    public boolean handson$isHeated() {
        Level level = handson$level();
        return level != null && ((HeatableBlockEntity) this).isHeated(level, handson$blockEntity().getBlockPos());
    }

    /** The pot sent its steam up the tower: when the last thing on it is done, a chime for everybody near, cook or passer-by. */
    @Inject(method = "cook", at = @At("RETURN"))
    private void handson$lastDone(Level level, CallbackInfoReturnable<Boolean> callback) {
        SteamerTower tower = SteamerTower.of(level, handson$blockEntity().getBlockPos());
        boolean steaming = tower != null && tower.isSteaming();
        if (handson$wasSteaming && !steaming && tower != null) {
            BlockPos top = handson$blockEntity().getBlockPos().above(tower.blocks() - 1);
            level.playSound(null, top, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5F, 1.26F);
            level.playSound(null, top, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4F, 1.5F);
        }
        handson$wasSteaming = steaming;
    }
}

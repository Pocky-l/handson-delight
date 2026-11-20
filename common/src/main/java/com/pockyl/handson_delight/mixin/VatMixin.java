package com.pockyl.handson_delight.mixin;

import com.baisylia.culturaldelights.block.entity.custom.VatBlockEntity;
import com.baisylia.culturaldelights.recipes.VatRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.ItemNbt;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.station.KitchenVat;

import java.util.Optional;

/**
 * Cultural Delights' vat with this mod:
 * <ul>
 * <li>It sends what lies in it to the clients near it, which it does not do itself: what ages in it is then shown by the scene for
 * everybody near, as at the station. Its data goes as it saves it, whenever what lies in it changes and every half second while it
 * ages.</li>
 * <li>A drink ages without its container (a bottle, a wafer) and stays in the vat as servings, drawn into the container when the
 * cook takes it, as from any vat: Cultural Delights wants the container in its slot before it starts. With a container in that
 * slot (its own menu, a hopper) it ages its own way; servings waiting in the vat go into containers put there the same way, so a
 * player without this mod still gets them.</li>
 * </ul>
 * Members inherited from Minecraft are overridden through Minecraft's own class or named as each loader names them.
 */
@Pseudo
@Mixin(value = VatBlockEntity.class, remap = false)
abstract class VatMixin extends BlockEntity implements KitchenVat.Servings {
    /** How often the clients hear how far it has aged, ticks. */
    @Unique
    private static final int HANDSON_AGING_EVERY = 10;
    @Unique
    private static final String HANDSON_SERVINGS = "handson_delight:servings";
    @Unique
    private int handson$sent;
    /** The drink aged without its container, waiting in the vat to be drawn. */
    @Unique
    private ItemStack handson$servings = ItemStack.EMPTY;
    /** The craft under way makes servings: there was no container in its slot when it started. */
    @Unique
    private boolean handson$intoServings;

    @Shadow
    private int maxProgress;
    @Shadow
    private VatRecipe currentRecipe;
    /** How warm the vat found itself this tick: its block state follows only when it changes. */
    @Shadow
    private com.baisylia.culturaldelights.util.VatTemperature cachedTemperature;

    private VatMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public ItemStack handson$servings() {
        return handson$servings;
    }

    @Override
    public void handson$setServings(ItemStack stack) {
        handson$servings = stack.copy();
        setChanged();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Inject(method = {"saveAdditional", "method_11007"}, at = @At("TAIL"))
    private void handson$saveServings(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!handson$servings.isEmpty()) {
            tag.put(HANDSON_SERVINGS, ItemNbt.save(handson$servings, registries));
        }
    }

    @Inject(method = {"loadAdditional", "method_11014"}, at = @At("TAIL"))
    private void handson$loadServings(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        handson$servings = tag.contains(HANDSON_SERVINGS) ? ItemNbt.parse(registries, tag.getCompound(HANDSON_SERVINGS)) : ItemStack.EMPTY;
    }

    /**
     * What lies in the vat makes a drink that goes into a container, and no container stands in its slot: it ages all the same, at
     * the warmth the recipe wants, as long as what waits in the vat is the same drink with room for more.
     */
    @Inject(method = "hasRecipe", at = @At("HEAD"), cancellable = true)
    private static void handson$withoutContainer(VatBlockEntity entity, CallbackInfoReturnable<Boolean> cir) {
        VatMixin self = (VatMixin) (Object) entity;
        Level level = self.getLevel();
        if (!HandsOnConfig.vat() || level == null || !((Container) entity).getItem(KitchenVat.CONTAINER_SLOT).isEmpty()) {
            return;
        }
        ItemStack[] places = new ItemStack[KitchenVat.INGREDIENTS];
        for (int slot = 0; slot < places.length; slot++) {
            places[slot] = ((Container) entity).getItem(slot);
        }
        Optional<KitchenVat.Aging> found = KitchenVat.matchingPlaces(KitchenVat.recipes(level), places).filter(KitchenVat.Aging::hasContainer);
        if (found.isEmpty()) {
            return;
        }
        KitchenVat.Aging aging = found.get();
        ItemStack waiting = self.handson$servings;
        boolean room = waiting.isEmpty() || ItemStack.isSameItemSameComponents(waiting, aging.result())
                && waiting.getCount() + aging.result().getCount() <= waiting.getMaxStackSize();
        if (!aging.temperature().serializedName().equals(self.cachedTemperature.getSerializedName()) || !room) {
            cir.setReturnValue(false);
            return;
        }
        Optional<VatRecipe> recipe = KitchenRecipes.byKey(level, aging.id()).map(RecipeHolder::value)
                .filter(VatRecipe.class::isInstance).map(VatRecipe.class::cast);
        if (recipe.isEmpty()) {
            return;
        }
        if (self.currentRecipe != recipe.get()) {
            self.currentRecipe = recipe.get();
            self.maxProgress = aging.ticks();
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "craftItem", at = @At("HEAD"))
    private static void handson$craftStarts(VatBlockEntity entity, CallbackInfo ci) {
        VatMixin self = (VatMixin) (Object) entity;
        self.handson$intoServings = HandsOnConfig.vat() && self.currentRecipe != null && !self.currentRecipe.getContainer().isEmpty()
                && ((Container) entity).getItem(KitchenVat.CONTAINER_SLOT).isEmpty();
    }

    /** A drink made without its container is taken back out of the output: it stays in the vat as a serving. */
    @Inject(method = "craftItem", at = @At("TAIL"))
    private static void handson$craftEnds(VatBlockEntity entity, CallbackInfo ci) {
        VatMixin self = (VatMixin) (Object) entity;
        if (!self.handson$intoServings || self.getLevel() == null) {
            return;
        }
        self.handson$intoServings = false;
        Container container = (Container) entity;
        ItemStack output = container.getItem(KitchenVat.OUTPUT_SLOT);
        // No lambda here: one that captured this mixin's own type could not be loaded.
        Optional<KitchenVat.Aging> bottling = KitchenVat.bottling(KitchenVat.recipes(self.getLevel()), output);
        if (bottling.isEmpty()) {
            return;
        }
        int made = Math.min(output.getCount(), bottling.get().result().getCount());
        container.setItem(KitchenVat.OUTPUT_SLOT, output.copyWithCount(output.getCount() - made));
        ItemStack waiting = self.handson$servings;
        self.handson$setServings(waiting.isEmpty() ? output.copyWithCount(made) : waiting.copyWithCount(waiting.getCount() + made));
    }

    /** Broken, the vat lets its servings go as they are, drinks in their containers, as it drops everything else in it. */
    @Inject(method = "drops", at = @At("HEAD"))
    private void handson$spill(CallbackInfo ci) {
        if (!handson$servings.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), handson$servings.copy());
            handson$servings = ItemStack.EMPTY;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void handson$send(Level level, BlockPos pos, BlockState state, VatBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide() || !HandsOnConfig.vat()) {
            return;
        }
        KitchenVat vat = KitchenVat.of(entity);
        if (vat == null) {
            return;
        }
        VatMixin self = (VatMixin) (Object) entity;
        self.handson$bottle(level, vat);
        int key = vat.progress() / HANDSON_AGING_EVERY;
        for (int slot = 0; slot <= KitchenVat.OUTPUT_SLOT; slot++) {
            ItemStack stack = vat.getItem(slot);
            key = 31 * key + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 67 + stack.getCount());
        }
        key = 31 * key + (self.handson$servings.isEmpty() ? 0 : ItemStack.hashItemAndComponents(self.handson$servings) * 67
                + self.handson$servings.getCount());
        if (key != self.handson$sent) {
            self.handson$sent = key;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Servings waiting in the vat go into containers put in its container slot (its own menu, a hopper), one by one into its output. */
    @Unique
    private void handson$bottle(Level level, KitchenVat vat) {
        if (handson$servings.isEmpty()) {
            return;
        }
        ItemStack container = vat.getItem(KitchenVat.CONTAINER_SLOT);
        ItemStack output = vat.getItem(KitchenVat.OUTPUT_SLOT);
        Optional<KitchenVat.Aging> bottling = KitchenVat.bottling(KitchenVat.recipes(level), handson$servings);
        if (container.isEmpty() || bottling.isEmpty() || !bottling.get().container().test(container)
                || !output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, handson$servings) || output.getCount() >= output.getMaxStackSize())) {
            return;
        }
        vat.setItem(KitchenVat.CONTAINER_SLOT, container.copyWithCount(container.getCount() - 1));
        vat.setItem(KitchenVat.OUTPUT_SLOT, output.isEmpty() ? handson$servings.copyWithCount(1) : output.copyWithCount(output.getCount() + 1));
        handson$setServings(handson$servings.copyWithCount(handson$servings.getCount() - 1));
    }
}

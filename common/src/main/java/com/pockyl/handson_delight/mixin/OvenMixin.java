package com.pockyl.handson_delight.mixin;

import com.baisylia.culturaldelights.block.entity.custom.OvenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.CulturalOven;

/**
 * Cultural Delights' oven sends what lies in it to the clients near it, which it does not do itself: the tray and what bakes on it
 * are then seen from the world as they are at the station, and the station shows it while nobody works at it. The oven's own data
 * goes as it saves it, whenever what lies in it changes and every half second while it bakes. Members inherited from Minecraft are
 * overridden through Minecraft's own class, so that the Fabric jar names them as Fabric does.
 */
@Pseudo
@Mixin(value = OvenBlockEntity.class, remap = false)
abstract class OvenMixin extends BlockEntity {
    /** How often the clients hear how far it has baked, ticks. */
    @Unique
    private static final int HANDSON_BAKING_EVERY = 10;
    @Unique
    private int handson$sent;

    private OvenMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void handson$send(Level level, BlockPos pos, BlockState state, OvenBlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide() || !HandsOnConfig.oven()) {
            return;
        }
        CulturalOven oven = CulturalOven.of(entity);
        if (oven == null) {
            return;
        }
        int key = oven.ticks() / HANDSON_BAKING_EVERY;
        for (int slot = 0; slot <= CulturalOven.OUTPUT_SLOT; slot++) {
            ItemStack stack = oven.item(slot);
            key = 31 * key + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 67 + stack.getCount());
        }
        OvenMixin self = (OvenMixin) (Object) entity;
        if (key != self.handson$sent) {
            self.handson$sent = key;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }
}

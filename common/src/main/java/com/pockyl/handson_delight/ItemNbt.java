package com.pockyl.handson_delight;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Item stacks in station data, as {@code ItemStack.saveOptional} and {@code parseOptional} kept them before Minecraft 26.x: a
 * stack as its {@code id}, {@code count} and {@code components}, an empty stack as an empty tag. Data saved by older versions reads
 * the same.
 */
public final class ItemNbt {
    private ItemNbt() {
    }

    /** The stack as a tag; an empty one for an empty stack. */
    public static Tag save(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    /** The stack in {@code tag}; empty for an empty tag or one that names no known item. */
    public static ItemStack parse(HolderLookup.Provider registries, Tag tag) {
        if (tag instanceof CompoundTag compound && compound.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
    }

    /** {@link #parse(HolderLookup.Provider, Tag)} of a tag that may be missing. */
    public static ItemStack parse(HolderLookup.Provider registries, Optional<? extends Tag> tag) {
        return tag.map(found -> parse(registries, (Tag) found)).orElse(ItemStack.EMPTY);
    }
}

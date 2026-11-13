package com.pockyl.handson_delight.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.handson_delight.CookingTimes;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The board's dishes on a client, for recipe viewers: the server sends them as a player joins and after each reload, before the
 * recipes and tags, so a viewer that starts on those finds them here. A server without this mod sends nothing, and its sandwiches
 * stay crafting recipes. The same packet carries the server's cooking times ({@link CookingTimes}).
 */
public final class BoardDishes {
    public static final ResourceLocation CHANNEL = HandsOnDelight.id("board_dishes");

    /** The server's config keeps these dishes in the crafting grid as well: the viewers show both. */
    private static final String CRAFTED_TOO = "crafted_too";
    private static final String TIMES = "cooking_times";
    private static volatile ListTag received = new ListTag();
    private static volatile boolean craftedToo;
    private static ListTag readFrom;
    private static List<AssemblyRecipes.Assembly> dishes = List.of();

    private BoardDishes() {
    }

    /** The packet, on the server. */
    public static void write(FriendlyByteBuf buffer) {
        CompoundTag payload = new CompoundTag();
        // Without the board's station nothing is made there: the viewers show the crafting recipes alone.
        payload.put("dishes", HandsOnConfig.cuttingBoard() ? AssemblyRecipes.book() : new ListTag());
        payload.putBoolean(CRAFTED_TOO, !HandsOnConfig.removeCraftingRecipes());
        payload.put(TIMES, CookingTimes.write());
        buffer.writeNbt(payload);
    }

    public static CompoundTag read(FriendlyByteBuf buffer) {
        CompoundTag payload = buffer.readNbt();
        return payload == null ? new CompoundTag() : payload;
    }

    /** Called on the network thread: the dishes are only kept here, and read once the tags are bound. */
    public static void receive(CompoundTag payload) {
        craftedToo = payload.getBoolean(CRAFTED_TOO);
        received = payload.getList("dishes", Tag.TAG_COMPOUND);
        CookingTimes.receive(payload.getCompound(TIMES));
    }

    public static void clear() {
        received = new ListTag();
        craftedToo = false;
        CookingTimes.clear();
    }

    public static synchronized List<AssemblyRecipes.Assembly> all() {
        ListTag book = received;
        if (book != readFrom) {
            dishes = AssemblyRecipes.read(book);
            readFrom = book;
        }
        return dishes;
    }

    /** The crafting recipes a player does not make: the viewers hide them. */
    public static Set<ResourceLocation> ids() {
        if (craftedToo) {
            return Set.of();
        }
        return all().stream().map(AssemblyRecipes.Assembly::id).collect(Collectors.toUnmodifiableSet());
    }
}

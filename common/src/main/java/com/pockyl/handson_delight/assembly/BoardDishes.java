package com.pockyl.handson_delight.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.handson_delight.CookingTimes;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The board's dishes on a client, for recipe viewers: the server sends them as a player joins and after each reload, before the
 * recipes and tags, so a viewer that starts on those finds them here. A server without this mod sends nothing, and its sandwiches
 * stay crafting recipes. The same packet carries the server's cooking times ({@link CookingTimes}).
 */
public final class BoardDishes {
    public static final ResourceLocation CHANNEL = HandsOnDelight.id("board_dishes");
    /**
     * A channel a client names when its own config turns the board's station off: it has no board to make the dishes on, so the
     * server leaves them in its grid and its recipe book. Nothing is sent on it.
     */
    public static final ResourceLocation NO_BOARD = HandsOnDelight.id("no_board");

    /** The server's config keeps these dishes in the crafting grid as well: the viewers show both. */
    private static final String CRAFTED_TOO = "crafted_too";
    private static final String TIMES = "cooking_times";
    private static volatile ListTag received = new ListTag();
    private static volatile boolean craftedToo;
    private static ListTag readFrom;
    private static List<AssemblyRecipes.Assembly> dishes = List.of();
    private static ListTag namesFrom;
    private static Set<ResourceLocation> names = Set.of();

    private BoardDishes() {
    }

    /** The packet: the dishes as the server has them. A client without this mod never gets it. */
    public record Payload(CompoundTag book) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeNbt(payload.book()),
                buffer -> new Payload(Objects.requireNonNullElseGet(buffer.readNbt(), CompoundTag::new)));

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /** {@link #NO_BOARD} as a packet type, so that a client can name it. Never sent. */
    public record NoBoard() implements CustomPacketPayload {
        public static final Type<NoBoard> TYPE = new Type<>(NO_BOARD);
        public static final StreamCodec<FriendlyByteBuf, NoBoard> CODEC = StreamCodec.unit(new NoBoard());

        @Override
        public Type<NoBoard> type() {
            return TYPE;
        }
    }

    /** The packet, on the server. */
    public static Payload payload() {
        CompoundTag book = new CompoundTag();
        // Without the board's station nothing is made there: the viewers show the crafting recipes alone.
        book.put("dishes", HandsOnConfig.cuttingBoard() ? AssemblyRecipes.book() : new ListTag());
        book.putBoolean(CRAFTED_TOO, !HandsOnConfig.removeCraftingRecipes());
        book.put(TIMES, CookingTimes.write());
        return new Payload(book);
    }

    /** Called on the network thread: the dishes are only kept here, and read once the tags are bound. */
    public static void receive(Payload packet) {
        CompoundTag payload = packet.book();
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
        if (!HandsOnConfig.cuttingBoard()) {
            // This client cuts on Farmer's Delight's board: the server leaves it the crafting recipes, and its viewers show those.
            return List.of();
        }
        ListTag book = received;
        if (book != readFrom) {
            // Meals of free fillings have no page: the viewers show Modest Meals' own.
            dishes = AssemblyRecipes.read(book).stream().filter(AssemblyRecipes.Assembly::inBook).toList();
            readFrom = book;
        }
        return dishes;
    }

    /** The crafting recipes a player does not make: the viewers hide them. */
    public static Set<ResourceLocation> ids() {
        if (craftedToo || !HandsOnConfig.cuttingBoard()) {
            return Set.of();
        }
        return all().stream().map(AssemblyRecipes.Assembly::id).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The crafting recipes this client's own grid leaves to the board, as its server named them: a lookup of what the grid matches
     * here (Hands-On Vanilla's choice of recipes) leaves them out, as the server does. Read from the names alone, so it may be asked
     * before the tags are bound.
     */
    public static synchronized Set<ResourceLocation> notInTheGrid() {
        if (craftedToo || !HandsOnConfig.cuttingBoard()) {
            return Set.of();
        }
        ListTag book = received;
        if (book != namesFrom) {
            Set<ResourceLocation> read = new HashSet<>();
            for (int i = 0; i < book.size(); i++) {
                String name = book.getCompound(i).getString("id");
                ResourceLocation id = name.isEmpty() ? null : ResourceLocation.tryParse(name);
                if (id != null) {
                    read.add(id);
                }
            }
            names = Set.copyOf(read);
            namesFrom = book;
        }
        return names;
    }
}

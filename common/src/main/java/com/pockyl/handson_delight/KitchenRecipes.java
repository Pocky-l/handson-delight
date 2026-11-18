package com.pockyl.handson_delight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The recipes the stations read, on either side. Since Minecraft 1.21.2 a client is sent no recipes, while the stations' client
 * side shows what the server cooks (the pot's book and the dish it will make, what the pan and the grill take, the cooked pieces
 * in a soup): the server sends the kinds they read ({@link #SYNCED}) as a player joins and after each reload, with the board's
 * dishes. On a server level everything is asked of the server's recipes; on a client level, of what it was sent. A server without
 * this mod sends nothing, and its stations are not worked at on the client.
 */
public final class KitchenRecipes {
    public static final Identifier CHANNEL = HandsOnDelight.id("recipes");
    /** The kinds of recipe the stations' client side reads. */
    private static final List<Supplier<RecipeType<?>>> SYNCED = new ArrayList<>(List.of(
            () -> RecipeType.CAMPFIRE_COOKING, () -> RecipeType.SMELTING, () -> ModRecipeTypes.COOKING.get(), FarmersDelightContent.CUTTING));

    private static volatile List<RecipeHolder<?>> received = List.of();
    private static volatile Map<Identifier, RecipeHolder<?>> receivedByName = Map.of();
    /** Stands for the recipes a client was sent, a new one each time: see {@link #source}. */
    private static volatile Object receivedSource = new Object();
    /** Each server recipe manager's recipes by id, made once: the same map as long as the manager is the same. */
    private static final Map<RecipeManager, Map<Identifier, RecipeHolder<?>>> BY_NAME = new WeakHashMap<>();

    private KitchenRecipes() {
    }

    /** The packet: the server's recipes of the kinds the stations read. A client without this mod never gets it. */
    public record Payload(List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = RecipeHolder.STREAM_CODEC
                .apply(ByteBufCodecs.list())
                .map(Payload::new, Payload::recipes);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /** Another kind of recipe a station reads on the client too (an addon's station). */
    public static void sync(Supplier<RecipeType<?>> type) {
        SYNCED.add(type);
    }

    /** The packet, on the server. */
    public static Payload payload(ServerLevel level) {
        List<RecipeType<?>> types = SYNCED.stream().<RecipeType<?>>map(Supplier::get).toList();
        List<RecipeHolder<?>> recipes = level.recipeAccess().getRecipes().stream()
                .filter(holder -> types.contains(holder.value().getType()))
                .toList();
        return new Payload(recipes);
    }

    /** Called on the network thread. */
    public static void receive(Payload packet) {
        received = List.copyOf(packet.recipes());
        receivedByName = byName(received);
        receivedSource = new Object();
    }

    /** The player left the server: its recipes are forgotten. */
    public static void clear() {
        received = List.of();
        receivedByName = Map.of();
        receivedSource = new Object();
    }

    /**
     * The level's recipes by id: the server's, or on a client what it was sent. The same map as long as the recipes are the same
     * (until a reload), so it can key what is worked out from them.
     */
    public static Map<Identifier, RecipeHolder<?>> byName(Level level) {
        return level instanceof ServerLevel server ? byName(server.recipeAccess()) : receivedByName;
    }

    /**
     * What the level's recipes come from, compared by identity: the server's recipe manager (a new one after each reload), or on a
     * client a token for what it was sent. What is worked out from the recipes can be kept under it.
     */
    public static Object source(Level level) {
        return level instanceof ServerLevel server ? server.recipeAccess() : receivedSource;
    }

    /** {@code manager}'s recipes by id, the same map for as long as the manager lives. */
    public static Map<Identifier, RecipeHolder<?>> byName(RecipeManager manager) {
        synchronized (BY_NAME) {
            return BY_NAME.computeIfAbsent(manager, key -> byName(key.getRecipes()));
        }
    }

    /** Forgets what was read of {@code manager}'s recipes: a game test replaced them. */
    public static void forget(RecipeManager manager) {
        synchronized (BY_NAME) {
            BY_NAME.remove(manager);
        }
    }

    private static Map<Identifier, RecipeHolder<?>> byName(java.util.Collection<RecipeHolder<?>> recipes) {
        Map<Identifier, RecipeHolder<?>> map = new LinkedHashMap<>();
        recipes.forEach(holder -> map.put(holder.id().identifier(), holder));
        return Collections.unmodifiableMap(map);
    }

    /** Every recipe of {@code type}: the server's, or on a client what it was sent. */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> List<RecipeHolder<T>> all(Level level, RecipeType<T> type) {
        List<RecipeHolder<T>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes(level)) {
            if (holder.value().getType() == type) {
                found.add((RecipeHolder<T>) holder);
            }
        }
        return found;
    }

    /** The first recipe of {@code type} that takes {@code input}, as the game finds one. */
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(Level level, RecipeType<T> type, I input) {
        if (level instanceof ServerLevel server) {
            return server.recipeAccess().getRecipeFor(type, input, level);
        }
        return all(level, type).stream().filter(holder -> holder.value().matches(input, level)).findFirst();
    }

    /** The recipe of that id, of any kind the level knows. */
    public static Optional<RecipeHolder<?>> byKey(Level level, ResourceKey<Recipe<?>> id) {
        if (level instanceof ServerLevel server) {
            return server.recipeAccess().byKey(id);
        }
        return received.stream().filter(holder -> holder.id().equals(id)).findFirst();
    }

    /** {@link #byKey(Level, ResourceKey)} by the recipe's id as data packs name it. */
    public static Optional<RecipeHolder<?>> byId(Level level, Identifier id) {
        return byKey(level, ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
    }

    /** Every recipe the level knows: the server's, or on a client what it was sent. */
    public static java.util.Collection<RecipeHolder<?>> recipes(Level level) {
        return level instanceof ServerLevel server ? server.recipeAccess().getRecipes() : received;
    }
}

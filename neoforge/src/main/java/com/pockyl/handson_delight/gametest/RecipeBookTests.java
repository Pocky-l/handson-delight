package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.List;
import java.util.UUID;

/** The board's dishes in the recipe books of players with and without the mod, and of players that are no client. */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeBookTests {
    private RecipeBookTests() {
    }

    /**
     * Another mod's mock player, its connection without a network channel, crafts and learns a sandwich as a client without the mod
     * does: asking whether its client has the mod throws nothing.
     */
    @GameTest(template = "empty")
    public static void aPlayerWithoutANetworkChannelHasNoClientWithTheMod(GameTestHelper helper) {
        ServerPlayer npc = TestPlayers.serverPlayer(helper, "npc");
        helper.assertFalse(Platform.INSTANCE.hasThisMod(npc), "a connection without a channel is no client with the mod");
        helper.assertFalse(Platform.INSTANCE.hasBoard(npc), "nor one with the board");
        if (booksTellOtherMods()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(npc.getRecipeBook().addRecipes(List.of(hamburger(helper)), npc) == 1, "its book learns the hamburger");
        helper.succeed();
    }

    /** A client without the mod keeps the board's dishes in its book when it is loaded again and sent. */
    @GameTest(template = "empty")
    public static void aClientWithoutTheModKeepsTheBoardDishesInItsBook(GameTestHelper helper) {
        if (booksTellOtherMods()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = TestPlayers.serverPlayer(helper, "npc");
        RecipeHolder<?> hamburger = hamburger(helper);
        ServerRecipeBook book = player.getRecipeBook();
        book.addRecipes(List.of(hamburger), player);
        book.fromNbt(book.toNbt(), helper.getLevel().getRecipeManager());
        helper.assertTrue(book.contains(hamburger), "the saved book keeps the hamburger");
        book.sendInitialRecipeBook(player);
        helper.assertTrue(book.contains(hamburger), "and so does the book sent to the client");
        helper.succeed();
    }

    /** A book learnt before the mod came keeps the board's dishes as it loads; a client with the mod is sent it without them. */
    @GameTest(template = "empty")
    public static void aClientWithTheModForgetsTheBoardDishesAsItsBookIsSent(GameTestHelper helper) {
        if (booksTellOtherMods()) {
            helper.succeed();
            return;
        }
        ServerPlayer without = TestPlayers.serverPlayer(helper, "npc");
        RecipeHolder<?> hamburger = hamburger(helper);
        without.getRecipeBook().addRecipes(List.of(hamburger), without);
        ServerPlayer cook = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cook"));
        ServerRecipeBook book = cook.getRecipeBook();
        book.fromNbt(without.getRecipeBook().toNbt(), helper.getLevel().getRecipeManager());
        helper.assertTrue(book.contains(hamburger), "the book loads the hamburger");
        book.sendInitialRecipeBook(cook);
        helper.assertFalse(book.contains(hamburger), "and leaves it out as it is sent");
        helper.succeed();
    }

    /**
     * Brewin' and Chewin' (-Paddons) sends a packet of its own to every player whose recipe book is sent or learns, which a connection
     * without a channel cannot carry: the books of such players are tested without it.
     */
    private static boolean booksTellOtherMods() {
        return Platform.INSTANCE.isLoaded("brewinandchewin");
    }

    private static RecipeHolder<?> hamburger(GameTestHelper helper) {
        return helper.getLevel().getRecipeManager().byKey(FarmersDelightContent.id("hamburger")).orElseThrow();
    }
}

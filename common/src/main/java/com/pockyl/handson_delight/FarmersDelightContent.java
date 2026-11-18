package com.pockyl.handson_delight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Blocks and sounds of Farmer's Delight, by id. Its own registry fields are NeoForge's {@code DeferredHolder} in one build and a
 * plain {@code Supplier} in Refabricated, so the shared code does not read them.
 */
public final class FarmersDelightContent {
    public static final String MOD_ID = "farmersdelight";

    public static final Supplier<Block> COOKING_POT = () -> BuiltInRegistries.BLOCK.getValue(id("cooking_pot"));
    public static final Supplier<Block> CUTTING_BOARD = () -> BuiltInRegistries.BLOCK.getValue(id("cutting_board"));
    public static final Supplier<Block> SKILLET = () -> BuiltInRegistries.BLOCK.getValue(id("skillet"));
    public static final Supplier<Block> STOVE = () -> BuiltInRegistries.BLOCK.getValue(id("stove"));
    /** Miner's Delight's copper pot: Farmer's Delight's pot made smaller, a station too when that mod is there. */
    public static final String MINERS_DELIGHT = "minersdelight";
    @SuppressWarnings("removal")
    public static final Supplier<Block> COPPER_POT = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MINERS_DELIGHT, "copper_pot"));
    public static final String DUNGEONS_DELIGHT = "dungeonsdelight";
    /** Dungeons Delight's monster pot: Farmer's Delight's pot in its own textures, cooking its own recipes of monster food. */
    public static final Supplier<Block> MONSTER_POT = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(DUNGEONS_DELIGHT, "monster_pot"));
    public static final String FARMERS_SPELL = "farmers_spell";
    /**
     * Farmer's Spell's alchemist pot: Farmer's Delight's pot in a shape of its own, cooking its own recipes first and Farmer's Delight's
     * after, with an Iron's Spells scroll whose school some of its recipes need.
     */
    public static final Supplier<Block> ALCHEMIST_POT = () -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(FARMERS_SPELL, "alchemist_pot"));
    public static final Supplier<RecipeType<?>> CUTTING = () -> BuiltInRegistries.RECIPE_TYPE.getValue(id("cutting"));

    public static final SoundEvent BOIL_SOUP = sound("block.cooking_pot.boil_soup");
    public static final SoundEvent SKILLET_SIZZLE = sound("block.skillet.sizzle");
    public static final SoundEvent SKILLET_ADD_FOOD = sound("block.skillet.add_food");
    public static final SoundEvent KNIFE_CUT = sound("block.cutting_board.knife_cut");
    public static final SoundEvent BOARD_PLACE = sound("block.cutting_board.place_item");
    public static final SoundEvent BOARD_REMOVE = sound("block.cutting_board.remove_item");
    public static final SoundEvent BOARD_CARVE = sound("block.cutting_board.carve_tool");

    private FarmersDelightContent() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** A sound event plays by its id, so one made here sounds the same as the registered one. */
    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(id(path));
    }
}

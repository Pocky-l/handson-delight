package com.pockyl.handson_delight;

import net.minecraft.util.Mth;

import com.pockyl.handson_delight.platform.Platform;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a modpack can turn off or tune, read once at startup from {@code config/handson_delight.properties} (the same file on every
 * loader). On a server it decides for everybody: a station turned off there opens on no client, its block keeps Farmer's Delight's own
 * use, and its cooking times reach the clients for their recipe books ({@link CookingTimes}).
 */
public final class HandsOnConfig {
    private static final String FILE = HandsOnDelight.MOD_ID + ".properties";
    /** The range of a cooking time: a tenth of the usual time at the shortest, ten times it at the longest. */
    private static final float SHORTEST_TIME = 0.1F;
    private static final float LONGEST_TIME = 10.0F;
    private static final String HEADER = String.join("\n",
            "Hands-On Delight. Restart the game or the server after a change.",
            "cooking_pot, cutting_board, skillet, stove: the station of that block. Off, the block works as in Farmer's Delight.",
            "keg: Brewin' and Chewin's keg as a station, when that mod is there. Off, the keg keeps its own menu.",
            "remove_crafting_recipes: Farmer's Delight's food crafts (sandwiches, salads, rolls, pies, feasts) are made on the",
            "  cutting board only, not in a crafting grid.",
            "  Machines (Create, AE2, Refined Storage) craft them either way.",
            "stirring_required: food a cook puts into the pot waits for the spoon while the cook is at it. Off, it also cooks",
            "  unstirred, Farmer's Delight's way; stirring still cooks the whole pot at once and earns stars.",
            "recipe_book: the recipe book beside the stations. Each player's own: read on their client.",
            "Those are true or false. The cooking times are numbers from 0.1 to 10:",
            "cooking_pot_time: how long food takes in the pot, as a multiple of the usual time: 1.0 as it is, 2.0 twice as",
            "  long, 0.5 half. Every way the pot cooks follows it: stirred, left alone, fed by a hopper or Farmer's Delight's",
            "  menu (Miner's Delight's copper pot too).",
            "skillet_time, stove_time: the same for food fried in the skillet and grilled on the stove at the station.",
            "  Food put in with Farmer's Delight's own right click (sneaking, a player without this mod) keeps its time.",
            "The recipe book shows the times as they are on the server.");

    private static boolean cookingPot = true;
    private static boolean cuttingBoard = true;
    private static boolean skillet = true;
    private static boolean stove = true;
    private static boolean keg = true;
    private static boolean removeCraftingRecipes = true;
    private static boolean stirringRequired = true;
    private static boolean recipeBook = true;
    private static float cookingPotTime = 1.0F;
    private static float skilletTime = 1.0F;
    private static float stoveTime = 1.0F;

    private HandsOnConfig() {
    }

    static void load() {
        Path path = Platform.INSTANCE.configDir().resolve(FILE);
        Properties values = new Properties();
        boolean unread = false;
        if (Files.exists(path)) {
            // Read leniently: a file saved in another encoding (a comment in Cyrillic in Windows-1251) keeps its values.
            try {
                values.load(new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
            } catch (IOException | IllegalArgumentException exception) {
                HandsOnDelight.LOGGER.warn("Could not read {}, using the defaults", path, exception);
                unread = true;
            }
        }
        cookingPot = read(values, "cooking_pot", cookingPot);
        cuttingBoard = read(values, "cutting_board", cuttingBoard);
        skillet = read(values, "skillet", skillet);
        stove = read(values, "stove", stove);
        keg = read(values, "keg", keg);
        removeCraftingRecipes = read(values, "remove_crafting_recipes", removeCraftingRecipes);
        stirringRequired = read(values, "stirring_required", stirringRequired);
        recipeBook = read(values, "recipe_book", recipeBook);
        cookingPotTime = readTime(values, "cooking_pot_time", cookingPotTime);
        skilletTime = readTime(values, "skillet_time", skilletTime);
        stoveTime = readTime(values, "stove_time", stoveTime);
        if (unread) {
            // Never write the defaults over a file the player wrote and the game could not read.
            return;
        }
        // Written back every start, so a file from an older version gets the new keys.
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                values.store(writer, HEADER);
            }
        } catch (IOException exception) {
            HandsOnDelight.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private static boolean read(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key, String.valueOf(fallback)).trim().toLowerCase(java.util.Locale.ROOT);
        boolean read = switch (value) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
        values.setProperty(key, String.valueOf(read));
        return read;
    }

    /** A cooking time: a number, kept between the shortest and longest a station allows; anything else is the default. */
    private static float readTime(Properties values, String key, float fallback) {
        float read = fallback;
        try {
            float value = Float.parseFloat(values.getProperty(key, String.valueOf(fallback)).trim().replace(',', '.'));
            if (Float.isFinite(value)) {
                read = Mth.clamp(value, SHORTEST_TIME, LONGEST_TIME);
            }
        } catch (NumberFormatException exception) {
            HandsOnDelight.LOGGER.warn("{} in {} is no number, using {}", key, FILE, fallback);
        }
        values.setProperty(key, String.valueOf(read));
        return read;
    }

    public static boolean cookingPot() {
        return cookingPot;
    }

    public static boolean cuttingBoard() {
        return cuttingBoard;
    }

    public static boolean skillet() {
        return skillet;
    }

    /** Turns the skillet's station on or off for a game test: what a restart with another file does to the cooking. */
    public static void setSkillet(boolean on) {
        skillet = on;
    }

    public static boolean stove() {
        return stove;
    }

    public static boolean keg() {
        return keg;
    }

    /** Whether board dishes leave players' crafting grids; never without the board to make them. */
    public static boolean removeCraftingRecipes() {
        return removeCraftingRecipes && cuttingBoard;
    }

    public static boolean stirringRequired() {
        return stirringRequired && cookingPot;
    }

    /**
     * How many times as long as usual food takes in the pot: a stirred batch, food left alone, food a hopper brought. 1 with the pot's
     * station off, when the pot is Farmer's Delight's own.
     */
    public static float cookingPotTime() {
        return cookingPot ? cookingPotTime : 1.0F;
    }

    /** How many times as long as usual food takes to fry in the skillet at the station. */
    public static float skilletTime() {
        return skillet ? skilletTime : 1.0F;
    }

    /** How many times as long as usual food takes to grill on the stove at the station. */
    public static float stoveTime() {
        return stove ? stoveTime : 1.0F;
    }

    /** Sets the cooking times for a game test: what a restart with another file does. */
    public static void setCookingTimes(float pot, float skillet, float stove) {
        cookingPotTime = pot;
        skilletTime = skillet;
        stoveTime = stove;
    }

    /** {@code ticks} as long as {@code times} makes them, never less than a tick. */
    public static int scaled(int ticks, float times) {
        return Math.max(1, Math.round(ticks * times));
    }

    /** Whether the recipe book hangs beside the stations; the client's own file decides, as it is a matter of taste. */
    public static boolean recipeBook() {
        return recipeBook;
    }
}

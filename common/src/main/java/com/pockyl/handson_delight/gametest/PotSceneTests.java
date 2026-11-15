package com.pockyl.handson_delight.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Every pot station's scene has what Farmer's Delight's pot does with it: the soup's surface that gives back what went in without
 * floating, and a hand and a spill for each ingredient slot. A pot whose scene misses one silently loses that part of the pot.
 */
public final class PotSceneTests {
    /** The scenes drawn by the pot's own behaviour. */
    private static final String[] POT_SCENES = {"cooking_pot", "copper_pot", "monster_pot", "alchemist_pot"};

    private PotSceneTests() {
    }

    public static void everyPotSceneHasEveryPart(GameTestHelper helper) {
        for (String scene : POT_SCENES) {
            Set<String> ids = nodeIds(helper, scene);
            int slots = 0;
            while (ids.contains("item_" + slots)) {
                slots++;
            }
            helper.assertTrue(slots > 0, scene + " has ingredient slots");
            helper.assertTrue(ids.contains("sunk"), scene + " has the soup's surface that gives back what sank");
            for (int i = 0; i < slots; i++) {
                helper.assertTrue(ids.contains("hand_" + i) && ids.contains("spill_" + i), scene + " has a hand and a spill for slot " + i);
            }
        }
        helper.succeed();
    }

    private static Set<String> nodeIds(GameTestHelper helper, String scene) {
        String path = "/assets/handson_delight/diegesis/scenes/" + scene + ".json";
        Set<String> ids = new HashSet<>();
        try (InputStream in = PotSceneTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "scene " + path + " is there");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray nodes = root.getAsJsonArray("nodes");
            for (JsonElement node : nodes) {
                ids.add(node.getAsJsonObject().get("id").getAsString());
            }
        } catch (java.io.IOException e) {
            helper.fail("scene " + path + " could not be read: " + e);
        }
        return ids;
    }
}

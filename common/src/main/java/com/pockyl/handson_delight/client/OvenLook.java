package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3f;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.CfbOven;
import com.pockyl.handson_delight.station.CulturalOven;

import java.util.Map;

/**
 * What differs between the ovens the oven station shows ({@link OvenBehavior}): how the door opens and sounds, how far the tray
 * comes out, where its places lie, where steam and smoke leave it, and the models that follow the block (a colour, a lit front).
 *
 * @param door             how the door opens: one door dropping on its hinge at the bottom, or two leaves swinging out like gates
 * @param doorOpen         how far the door turns open, degrees
 * @param doorTime         seconds the door takes to open or shut
 * @param trayOut          how far the tray slides out, pixels
 * @param trayTime         seconds the tray takes
 * @param columnX          the places' columns on the tray, pixels from its middle, column 0 on the cook's left
 * @param rowZ             the places' rows, row 0 at the back
 * @param onTray           how high the places lie above the tray node
 * @param foodScale        how big a thing on a place is drawn, of a block
 * @param mouth            where the shimmer and the steam leave the open oven, scene pixels
 * @param smokeFrom        where smoke rises while it bakes shut
 * @param openSound        played as the door starts to open
 * @param closeSound       played as the door shuts
 * @param blockParts       the scene's nodes the block draws as well, shown only while the station hides the block
 */
record OvenLook(Door door, float doorOpen, float doorTime, float trayOut, float trayTime, float[] columnX, float[] rowZ, float onTray,
        float foodScale, Vector3f mouth, Vector3f smokeFrom, SoundEvent openSound, SoundEvent closeSound, String[] blockParts, Models models) {

    enum Door {
        /** One door hinged at its bottom edge, node {@code door}, dropping towards the cook. */
        DROP,
        /** Two leaves hinged at the sides, nodes {@code door_left} and {@code door_right}, swinging out like gates. */
        LEAVES
    }

    /** The models of the scene's nodes that follow the block's state: node id to model, given the block and whether it burns. */
    @FunctionalInterface
    interface Models {
        Models NONE = (state, burning, doorShut) -> Map.of();

        Map<String, ResourceLocation> of(BlockState state, boolean burning, boolean doorShut);
    }

    /**
     * Cooking for Blockheads' oven: its door drops 72 degrees on its hinge in half a second, eased, as its {@code DoorAnimator} opens
     * it; the wire rack slides out over it. Places as its renderer lays them, a 3 x 3 grid a little under 4 pixels apart.
     */
    static final OvenLook CFB = new OvenLook(Door.DROP, 72.0F, 0.5F, 6.0F, 0.35F, new float[] {3.84F, 0.0F, -3.84F},
            new float[] {3.84F, 0.0F, -3.84F}, 6.3F, 0.3F, new Vector3f(8.0F, 9.0F, -1.0F), new Vector3f(8.0F, 11.6F, -0.6F),
            sound(CfbOven.MOD_ID, "oven_open"), sound(CfbOven.MOD_ID, "oven_close"),
            // While nobody works at it, the block and its own renderer are the oven, as without this mod (its renderer lights the tools
            // on top with the light inside its own solid block, so they are dark there; the scene lights them from above).
            new String[] {"oven"}, OvenLook::cfbModels);

    /**
     * Cultural Delights' brick oven: its grated door's two leaves swing out 105 degrees like gates, the baking tray slides out between
     * them; places in its 3 x 3 grid, smoke out of its chimney. While nobody works at it the block is the oven and the scene draws only
     * what lies on its tray.
     */
    static final OvenLook CULTURAL = new OvenLook(Door.LEAVES, 105.0F, 0.4F, 7.0F, 0.35F, new float[] {2.67F, 0.0F, -2.67F},
            new float[] {1.67F, -1.0F, -3.67F}, 2.0F, 0.5F, new Vector3f(8.0F, 10.5F, 0.5F), new Vector3f(8.0F, 16.3F, 8.0F),
            sound(CulturalOven.MOD_ID, "block.oven.open"), sound(CulturalOven.MOD_ID, "block.oven.close"),
            new String[] {"body", "door_left", "door_right", "tray_model"}, Models.NONE);

    private static SoundEvent sound(String namespace, String path) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * The oven's body as its block draws it, its door and handle bar as its renderer draws them: each in the oven's colour (on 1.21.1
     * every oven is one, white the first), the door lit through the glass while it burns and is shut.
     */
    private static Map<String, ResourceLocation> cfbModels(BlockState state, boolean burning, boolean doorShut) {
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        // Its ovens draw their lit front while not active, their plain one while active (its blockstates).
        String body = block + (flag(state, "active") ? "" : "_active");
        String door = block + "_door" + (burning && doorShut ? "_active" : "");
        return Map.of("body", HandsOnDelight.id("diegesis/cfb_oven/body/" + body), "door", HandsOnDelight.id("diegesis/cfb_oven/door/" + door),
                "door_handle", HandsOnDelight.id("diegesis/cfb_oven/door_handle/" + block + "_door_handle"));
    }

    private static boolean flag(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name) && property instanceof BooleanProperty flag) {
                return state.getValue(flag);
            }
        }
        return false;
    }
}

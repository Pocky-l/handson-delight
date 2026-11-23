package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_delight.HandsOnDelight;

import java.util.List;

/**
 * The station draws the pot's spoon and soup into the pot the world shows, so that pot has to be this mod's: no spoon of its own
 * and a floor low enough for the soup. Another mod or resource pack can still draw a different pot (a pack placed above this
 * mod's, a mod that swaps the baked model or draws the pot from a block entity renderer, as Vanilla Delights did). The first time
 * a pot's scene shows after the models load, this looks at what the world draws and, if it is not this mod's pot, names the
 * model, the pack and the renderer in the log, so a player's report says which mod it is.
 */
final class PotLookCheck {
    private static final Identifier STATE_FILE = Identifier.fromNamespaceAndPath("farmersdelight", "blockstates/cooking_pot.json");
    /** The top of this mod's floor ({@code body.json}, element {@code floor}), in block units. */
    private static final float FLOOR = 2.0F / 16.0F;
    private static final float EPSILON = 1.0E-4F;

    private static BlockStateModel checked;

    private PotLookCheck() {
    }

    static void check(BlockState state, BlockEntity pot) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = com.pockyl.diegesis.impl.client.render.SceneDraw.blockModel(state);
        if (model == checked) {
            return;
        }
        checked = model;
        BlockEntityRenderer<BlockEntity, ?> renderer = pot == null ? null : minecraft.getBlockEntityRenderDispatcher().getRenderer(pot);
        boolean ours = hasOurFloor(model, state);
        if (ours && renderer == null) {
            return;
        }
        List<Resource> stack = minecraft.getResourceManager().getResourceStack(STATE_FILE);
        String pack = stack.isEmpty() ? "none" : stack.get(stack.size() - 1).sourcePackId();
        HandsOnDelight.LOGGER.warn("Hands-On Delight: the cooking pot in the world is drawn by another mod or resource pack, so the station's "
                        + "spoon and soup may not fit it. Baked model: {} ({}), block state from pack: {}, block entity renderer: {}. "
                        + "Please report this with your mod list.",
                model.getClass().getName(), ours ? "this mod's floor found" : "this mod's floor missing", pack,
                renderer == null ? "none" : renderer.getClass().getName());
    }

    /** Whether the model has the up face of this mod's floor: a square at the floor's height. */
    private static boolean hasOurFloor(BlockStateModel model, BlockState state) {
        boolean[] found = {false};
        com.pockyl.diegesis.impl.client.render.SceneDraw.forEachQuad(model, quad -> {
            if (quad.direction() == Direction.UP && allAt(quad, FLOOR)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean allAt(BakedQuad quad, float y) {
        for (int vertex = 0; vertex < 4; vertex++) {
            if (Math.abs(quad.position(vertex).y() - y) > EPSILON) {
                return false;
            }
        }
        return true;
    }
}

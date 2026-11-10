package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The cooking pot in the world is this mod's pot: its body without Farmer's Delight's spoon, which the station moves, and with a
 * floor deep enough for the soup and what floats in it. The block state file that says so lies under Farmer's Delight's own name,
 * where any other copy of it can win: a resource pack that remodels Farmer's Delight, or Farmer's Delight's own file when the loader
 * lists it after this mod (development runs shuffle the mods). The pot then covered the soup and kept a second spoon that does
 * nothing. Its models are therefore read from this mod's copy of the file; a resource pack's textures still apply to them.
 */
final class PotModels {
    private static final String FILE = "assets/farmersdelight/blockstates/cooking_pot.json";

    private PotModels() {
    }

    static void register() {
        ModelLoadingPlugin.register(context -> {
            Block pot = FarmersDelightContent.COOKING_POT.get();
            if (pot == Blocks.AIR) {
                return;
            }
            try {
                context.registerBlockStateResolver(pot, resolver -> {
                    UnbakedModel model = read(pot).orElseGet(() -> resolver.getOrLoadModel(ModelBakery.MISSING_MODEL_LOCATION));
                    for (BlockState state : pot.getStateDefinition().getPossibleStates()) {
                        resolver.setModel(state, model);
                    }
                });
            } catch (IllegalArgumentException e) {
                // Another mod already decides how the pot looks: its choice stands.
                HandsOnDelight.LOGGER.warn("The cooking pot's models are resolved by another mod; this mod's pot body is not used", e);
            }
        });
    }

    /** The multipart of this mod's block state file, bound to the pot's properties. */
    private static Optional<UnbakedModel> read(Block pot) {
        Optional<Path> file = FabricLoader.getInstance().getModContainer(HandsOnDelight.MOD_ID).flatMap(mod -> mod.findPath(FILE));
        if (file.isEmpty()) {
            HandsOnDelight.LOGGER.error("{} is missing from the mod", FILE);
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file.get())) {
            BlockModelDefinition.Context context = new BlockModelDefinition.Context();
            context.setDefinition(pot.getStateDefinition());
            BlockModelDefinition definition = BlockModelDefinition.fromStream(context, reader);
            if (!definition.isMultiPart()) {
                HandsOnDelight.LOGGER.error("{} is no multipart", FILE);
                return Optional.empty();
            }
            return Optional.of(definition.getMultiPart());
        } catch (IOException | RuntimeException e) {
            HandsOnDelight.LOGGER.error("Cannot read {}", FILE, e);
            return Optional.empty();
        }
    }
}

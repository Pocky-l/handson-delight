package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.OvenStation;
import com.pockyl.handson_delight.station.VatStation;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The cooking pot in the world is this mod's pot: its body without Farmer's Delight's spoon, which the station moves, and with a
 * floor deep enough for the soup and what floats in it. The block state file that says so lies under Farmer's Delight's own name,
 * where any other copy of it can win: a resource pack that remodels Farmer's Delight, or Farmer's Delight's own file when the loader
 * lists it after this mod (development runs shuffle the mods). The pot then covered the soup and kept a second spoon that does
 * nothing. Its models are therefore read from this mod's copy of the file; a resource pack's textures still apply to them.
 * <p>
 * The files lie outside the mod's own {@code assets}, so each is used only while its station is on: with the station off the pot
 * is Farmer's Delight's again, with its own spoon, since no station draws one, and the oven and the vat are Cultural Delights' own.
 */
final class PotModels {
    private static final String FILE = "pot_pack/assets/farmersdelight/blockstates/cooking_pot.json";
    /** Cultural Delights' oven, the same way: a brick oven with a real door and a hearth inside instead of a painted front. */
    private static final String OVEN_FILE = "pot_pack/assets/culturaldelights/blockstates/oven.json";
    /** And its vat: hollow under a lid that lifts, when it stands on its bottom. */
    private static final String VAT_FILE = "pot_pack/assets/culturaldelights/blockstates/vat.json";

    private PotModels() {
    }

    static void register() {
        if (!HandsOnConfig.cookingPot() && !HandsOnConfig.oven() && !HandsOnConfig.vat()) {
            return;
        }
        ModelLoadingPlugin.register(context -> {
            if (HandsOnConfig.cookingPot()) {
                resolve(context, FarmersDelightContent.COOKING_POT.get(), FILE);
            }
            if (HandsOnConfig.oven()) {
                resolve(context, OvenStation.OVEN.get(), OVEN_FILE);
            }
            if (HandsOnConfig.vat()) {
                resolve(context, VatStation.VAT.get(), VAT_FILE);
            }
        });
    }

    /** Every state of {@code block} gets the multipart of this mod's {@code file}; nothing for a block that is not there. */
    private static void resolve(ModelLoadingPlugin.Context context, Block block, String file) {
        if (block == Blocks.AIR) {
            return;
        }
        // Read first: a file that cannot be read leaves the block its usual models instead of the missing one.
        Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> models = read(block, file);
        if (models.isEmpty()) {
            return;
        }
        try {
            context.registerBlockStateResolver(block, resolver -> models.get().forEach(resolver::setModel));
        } catch (IllegalArgumentException e) {
            // Another mod already decides how the block looks: its choice stands.
            HandsOnDelight.LOGGER.warn("The models of {} are resolved by another mod; this mod's are not used", block, e);
        }
    }

    /** The multipart of this mod's block state file, bound to the block's properties: a model for each of its states. */
    private static Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> read(Block block, String name) {
        Optional<Path> file = FabricLoader.getInstance().getModContainer(HandsOnDelight.MOD_ID).flatMap(mod -> mod.findPath(name));
        if (file.isEmpty()) {
            HandsOnDelight.LOGGER.error("{} is missing from the mod", name);
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file.get())) {
            BlockModelDefinition definition = BlockModelDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow();
            if (definition.multiPart().isEmpty()) {
                HandsOnDelight.LOGGER.error("{} is no multipart", name);
                return Optional.empty();
            }
            return Optional.of(definition.instantiate(block.getStateDefinition(), () -> name));
        } catch (IOException | RuntimeException e) {
            HandsOnDelight.LOGGER.error("Cannot read {}", name, e);
            return Optional.empty();
        }
    }
}

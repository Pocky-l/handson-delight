package com.pockyl.handson_delight.forge;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import com.pockyl.handson_delight.HandsOnDelight;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * The cooking pot in the world is this mod's pot: its body without Farmer's Delight's spoon, which the station moves, and with a
 * floor deep enough for the soup and what floats in it. The block state file that says so lies under Farmer's Delight's own name,
 * and a resource pack that remodels Farmer's Delight (Excalibur) sits above every mod: its file won, the pot covered the soup
 * and kept a second spoon that does nothing. This mod's file therefore also comes as a hidden built-in pack fixed above all
 * others, holding that one file; a pack's textures still apply to the pot's models.
 */
final class PotPack {
    private static final String ID = HandsOnDelight.MOD_ID + "_pot";
    private static final ResourceLocation STATE = new ResourceLocation("farmersdelight", "blockstates/cooking_pot.json");

    private PotPack() {
    }

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = ModList.get().getModFileById(HandsOnDelight.MOD_ID).getFile().getSecureJar().getRootPath();
        Pack.ResourcesSupplier resources = id -> new OnlyThePot(id, root);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Pack.Info info = new Pack.Info(Component.literal("Hands-On Delight: cooking pot"), format, format, FeatureFlagSet.of(), true);
        Pack pack = Pack.create(ID, Component.literal("Hands-On Delight: cooking pot"), true, resources, info, PackType.CLIENT_RESOURCES,
                Pack.Position.TOP, true, PackSource.BUILT_IN);
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /** The mod's files, of which only the pot's block state is shown. */
    private static final class OnlyThePot extends PathPackResources {
        OnlyThePot(String id, Path root) {
            super(id, root, true);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return type == PackType.CLIENT_RESOURCES && STATE.equals(location) ? super.getResource(type, location) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES && STATE.getNamespace().equals(namespace) && STATE.getPath().startsWith(path)) {
                super.listResources(type, namespace, path, (location, supplier) -> {
                    if (STATE.equals(location)) {
                        output.accept(location, supplier);
                    }
                });
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(STATE.getNamespace()) : Set.of();
        }
    }
}

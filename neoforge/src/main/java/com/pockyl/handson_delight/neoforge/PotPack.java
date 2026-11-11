package com.pockyl.handson_delight.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import com.pockyl.handson_delight.HandsOnDelight;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * The cooking pot in the world is this mod's pot: its body without Farmer's Delight's spoon, which the station moves, and with a
 * floor deep enough for the soup and what floats in it. The block state file that says so lies under Farmer's Delight's own name,
 * and a resource pack that remodels Farmer's Delight (Excalibur) sits above every mod: its file won, the pot covered the soup
 * and kept a second spoon that does nothing. This mod's file therefore also comes as a hidden built-in pack fixed above all
 * others, holding that one file; a pack's textures still apply to the pot's models.
 * <p>
 * Vanilla Delight dyes the pot: it empties the block's models and draws the pot from its block entity with its own models, one
 * per colour and support, which are Farmer's Delight's pot again. This mod's versions of them (its pot in Vanilla Delight's
 * colours) come in the same pack, so the dye stays and the spoon and floor are this mod's.
 */
final class PotPack {
    private static final ResourceLocation STATE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "blockstates/cooking_pot.json");
    private static final String DYED_NAMESPACE = "vanilladelight";
    private static final String DYED_MODELS = "models/block/cooking_pot_";
    private static final Set<String> NAMESPACES = Set.of(STATE.getNamespace(), DYED_NAMESPACE);

    private PotPack() {
    }

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = ModList.get().getModFileById(HandsOnDelight.MOD_ID).getFile().getSecureJar().getRootPath();
        PackLocationInfo location = new PackLocationInfo(HandsOnDelight.MOD_ID + "_pot", Component.literal("Hands-On Delight: cooking pot"),
                PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public OnlyThePot openPrimary(PackLocationInfo info) {
                return new OnlyThePot(info, root);
            }

            @Override
            public OnlyThePot openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new OnlyThePot(info, root);
            }
        };
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack.hidden()));
        }
    }

    /** The mod's files, of which only the pot's block state and the dyed pots' models are shown. */
    private static final class OnlyThePot extends PathPackResources {
        OnlyThePot(PackLocationInfo info, Path root) {
            super(info, root);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return type == PackType.CLIENT_RESOURCES && shown(location) ? super.getResource(type, location) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES && NAMESPACES.contains(namespace)) {
                super.listResources(type, namespace, path, (location, supplier) -> {
                    if (shown(location)) {
                        output.accept(location, supplier);
                    }
                });
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? NAMESPACES : Set.of();
        }

        private static boolean shown(ResourceLocation location) {
            return STATE.equals(location) || DYED_NAMESPACE.equals(location.getNamespace()) && location.getPath().startsWith(DYED_MODELS);
        }
    }
}

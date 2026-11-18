package com.pockyl.handson_delight.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The cooking pot in the world is this mod's pot: its body without Farmer's Delight's spoon, which the station moves, and with a
 * floor deep enough for the soup and what floats in it. The block state file that says so lies under Farmer's Delight's own name,
 * and a resource pack that remodels Farmer's Delight (Excalibur) sits above every mod: its file won, the pot covered the soup
 * and kept a second spoon that does nothing. This mod's file therefore also comes as a hidden built-in pack fixed above all
 * others, holding that one file; a pack's textures still apply to the pot's models. Miner's Delight's copper pot comes the same way:
 * open, its spoon in the station's hand, and this mod is not ordered after Miner's Delight, so only the pack is sure to win; so does
 * Dungeons Delight's monster pot, and Cultural Delights' oven, a brick oven with a real door and a hearth inside instead of a
 * painted front, and its vat, hollow under a lid that lifts. Farmer's Respite's kettle comes the same way: its opening under the lid is a
 * real one, and its tray is Farmer's Delight's heating tray, where its own model names tray textures Farmer's Delight no longer has. Farmer's Spell's alchemist pot comes the same way: its dark top is a real opening;
 * so does Youkai's Feasts' kettle: its painted hole is a real opening under its lid, which the station lifts. Extra Delight's
 * melting pot comes the same way: its upper pot is open on what melts in it.
 * <p>
 * Vanilla Delight dyes the pot: it empties the block's models and draws the pot from its block entity with its own models, one
 * per colour and support, which are Farmer's Delight's pot again. This mod's versions of them (its pot in Vanilla Delight's
 * colours) come in the same pack, so the dye stays and the spoon and floor are this mod's.
 * <p>
 * The files lie outside the mod's own {@code assets}, so only this pack shows them, and it shows each only while its station is on:
 * a pot whose station a modpack turned off is the other mod's pot again, with its own spoon, since no station draws one.
 */
final class PotPack {
    private static final String ID = HandsOnDelight.MOD_ID + "_pot";
    /** The folder of the mod's files that holds the pack's {@code assets}. */
    private static final String FOLDER = "pot_pack";
    /** Each block state file and the station that wants it. */
    private static final Map<ResourceLocation, BooleanSupplier> STATES = Map.of(
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "blockstates/cooking_pot.json"), HandsOnConfig::cookingPot,
            ResourceLocation.fromNamespaceAndPath("minersdelight", "blockstates/copper_pot.json"), HandsOnConfig::cookingPot,
            ResourceLocation.fromNamespaceAndPath("dungeonsdelight", "blockstates/monster_pot.json"), HandsOnConfig::cookingPot,
            ResourceLocation.fromNamespaceAndPath("culturaldelights", "blockstates/oven.json"), HandsOnConfig::oven,
            ResourceLocation.fromNamespaceAndPath("culturaldelights", "blockstates/vat.json"), HandsOnConfig::vat,
            ResourceLocation.fromNamespaceAndPath("farmersrespite", "blockstates/kettle.json"), HandsOnConfig::kettle,
            ResourceLocation.fromNamespaceAndPath("farmers_spell", "blockstates/alchemist_pot.json"), HandsOnConfig::cookingPot,
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "blockstates/kettle.json"), HandsOnConfig::kettle,
            ResourceLocation.fromNamespaceAndPath("extradelight", "blockstates/melting_pot.json"), HandsOnConfig::meltingPot);
    private static final String DYED_NAMESPACE = "vanilladelight";
    private static final String DYED_MODELS = "models/block/cooking_pot_";
    private static final Set<String> NAMESPACES = Set.of("farmersdelight", "minersdelight", "dungeonsdelight", "culturaldelights", "farmersrespite",
            "farmers_spell", "youkaisfeasts", "extradelight", DYED_NAMESPACE);

    private PotPack() {
    }

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || STATES.values().stream().noneMatch(BooleanSupplier::getAsBoolean)) {
            return;
        }
        Path root = ModList.get().getModFileById(HandsOnDelight.MOD_ID).getFile().getSecureJar().getRootPath().resolve(FOLDER);
        PackLocationInfo location = new PackLocationInfo(ID, Component.literal("Hands-On Delight: cooking pot"),
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
        // The folder has no pack.mcmeta of its own: the pack's metadata is made here.
        Pack.Metadata metadata = new Pack.Metadata(location.title(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        Pack pack = new Pack(location, resources, metadata, new PackSelectionConfig(true, Pack.Position.TOP, true));
        event.addRepositorySource(consumer -> consumer.accept(pack.hidden()));
    }

    /** The pack's files, of which only the block states and dyed pots' models of stations that are on are shown. */
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
            BooleanSupplier station = STATES.get(location);
            if (station != null) {
                return station.getAsBoolean();
            }
            return HandsOnConfig.cookingPot() && DYED_NAMESPACE.equals(location.getNamespace())
                    && location.getPath().startsWith(DYED_MODELS);
        }
    }
}

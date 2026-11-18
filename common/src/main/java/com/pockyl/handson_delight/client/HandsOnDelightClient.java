package com.pockyl.handson_delight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.BowlStation;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.OvenStation;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.MeltingPotLogic;
import com.pockyl.handson_delight.station.MeltingPotStation;
import com.pockyl.handson_delight.station.SkilletStation;
import com.pockyl.handson_delight.station.StoveStation;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.VatLogic;
import com.pockyl.handson_delight.station.VatStation;
import com.pockyl.handson_delight.station.EdVatStation;
import com.pockyl.handson_delight.station.YoukaiPotStation;

import java.util.ArrayList;
import java.util.List;

/** Client setup; only loaded on clients. */
public final class HandsOnDelightClient {
    public static final ResourceLocation OIL_POP = HandsOnDelight.id("oil_pop");
    public static final ResourceLocation FAT = HandsOnDelight.id("fat");
    public static final ResourceLocation PAN_SMOKE = HandsOnDelight.id("pan_smoke");
    /** A campfire's flame under a pot heated by a fire scroll: the game's flame, as big as a campfire's, glowing. */
    public static final ResourceLocation SCROLL_FLAME = HandsOnDelight.id("scroll_flame");
    public static final ResourceLocation HEAT = HandsOnDelight.id("heat");
    public static final ResourceLocation GRAIN = HandsOnDelight.id("grain");
    @SuppressWarnings("removal")
    private static final ResourceLocation DIEGESIS_BUBBLE = ResourceLocation.fromNamespaceAndPath("diegesis", "bubble");

    private HandsOnDelightClient() {
    }

    public static void init() {
        DiegesisClient.registerBehavior(CookingPotStation.ID, PotBehavior::new);
        DiegesisClient.registerBehavior(CookingPotStation.COPPER_ID, view -> new PotBehavior(view, PotBehavior.Shape.COPPER_POT));
        DiegesisClient.registerBehavior(CookingPotStation.MONSTER_ID, view -> new PotBehavior(view, PotBehavior.Shape.MONSTER_POT));
        DiegesisClient.registerBehavior(CookingPotStation.ALCHEMIST_ID, view -> new PotBehavior(view, PotBehavior.Shape.ALCHEMIST_POT));
        DiegesisClient.registerBehavior(KegStation.ID, KegBehavior::lying);
        DiegesisClient.registerBehavior(KegStation.STANDING_ID, KegBehavior::standing);
        DiegesisClient.registerBehavior(KegStation.TANK_ID, KegBehavior::tank);
        DiegesisClient.registerLiquid(HandsOnDelight.id("drink"), LiquidLook.of(0xC0B08850));
        KegLiquids.register();
        DiegesisClient.registerBehavior(OvenStation.ID, OvenBehavior::new);
        DiegesisClient.registerBehavior(VatStation.ID, VatBehavior::new);
        DiegesisClient.registerBehavior(EdVatStation.ID, EdVatBehavior::new);
        DiegesisClient.registerBehavior(KettleStation.ID, KettleBehavior::new);
        DiegesisClient.registerBehavior(KettleStation.YOUKAI_ID, KettleBehavior::new);
        DiegesisClient.registerBehavior(GrillStation.ID, GrillBehavior::new);
        DiegesisClient.registerBehavior(BasinStation.ID, BasinBehavior::new);
        DiegesisClient.registerLiquid(VatLogic.BREW_LIQUID, LiquidLook.of(0xD0B8D8F0));
        // Ice cream in the vat: opaque, grainy as snow, so thick that it hardly moves.
        DiegesisClient.registerLiquid(VatLogic.CREAM_LIQUID,
                new LiquidLook(0xFFF6EEDC, ResourceLocation.withDefaultNamespace("block/snow"), false, 0.95F));
        DiegesisClient.registerBehavior(MeltingPotStation.ID, MeltingPotBehavior::new);
        // What has melted in the melting pot (chocolate syrup, marshmallow fluff), coloured by the code; thick, so that it moves slowly.
        DiegesisClient.registerLiquid(MeltingPotLogic.MELTED_LIQUID, LiquidLook.of(0xF0FFFFFF).thick(0.8F));
        DiegesisClient.registerBehavior(CuisineBoardStation.ID, CuisineBehavior::new);
        DiegesisClient.registerLiquid(YoukaiPotBehavior.SOUP_LIQUID, LiquidLook.of(0x96FFFFFF));
        DiegesisClient.registerBehavior(YoukaiPotStation.SMALL_ID, view -> new YoukaiPotBehavior(view, YoukaiPotBehavior.Size.SMALL));
        DiegesisClient.registerBehavior(YoukaiPotStation.SHORT_ID, view -> new YoukaiPotBehavior(view, YoukaiPotBehavior.Size.SHORT));
        DiegesisClient.registerBehavior(YoukaiPotStation.STOCKPOT_ID, view -> new YoukaiPotBehavior(view, YoukaiPotBehavior.Size.STOCKPOT));
        DiegesisClient.registerBehavior(SteamerStation.ID, SteamerBehavior::new);
        DiegesisClient.registerBehavior(BowlStation.ID, BowlBehavior::new);
        // What is mixed in the bowl: thick, nearly opaque; its colour is set by the scene.
        DiegesisClient.registerLiquid(BowlStation.BATTER_LIQUID, LiquidLook.of(0xF0E8D8B8).thick(0.8F));
        DiegesisClient.registerBehavior(CuttingBoardStation.ID, BoardBehavior::new);
        DiegesisClient.registerBehavior(SkilletStation.ID, SkilletBehavior::new);
        DiegesisClient.registerBehavior(StoveStation.ID, StoveBehavior::new);
        DiegesisClient.registerLiquid(HandsOnDelight.id("broth"), LiquidLook.of(0xE0B0A070).thick(0.6F));
        registerParticles();
    }

    private static List<ResourceLocation> frames(String base, int count) {
        List<ResourceLocation> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            frames.add(HandsOnDelight.id(base + "_" + i));
        }
        return frames;
    }

    /** The skillet's heat: oil seething round the food, fat spitting off it, smoke of a side that waits, shimmer over the hot pan. */
    @SuppressWarnings("removal")
    private static void registerParticles() {
        List<ResourceLocation> popping = new ArrayList<>(List.of(DIEGESIS_BUBBLE, DIEGESIS_BUBBLE));
        for (int i = 0; i < 5; i++) {
            popping.add(ResourceLocation.fromNamespaceAndPath("diegesis", "bubble_pop_" + i));
        }
        DiegesisClient.registerParticle(OIL_POP, ParticleStyle.builder(popping).color(0xE6F7C27A).size(0.8F, 1.3F).life(0.25F, 0.5F)
                .drag(1.0F).grow(0.4F).pop().build());
        DiegesisClient.registerParticle(FAT, ParticleStyle.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "drip_fall")).color(0xFFFFE7A8)
                .size(0.3F, 0.45F).life(0.3F, 0.6F).gravity(75.0F).drag(0.05F).stretch().pop().build());
        List<ResourceLocation> steam = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            steam.add(ResourceLocation.fromNamespaceAndPath("diegesis", "steam_" + i));
        }
        DiegesisClient.registerParticle(PAN_SMOKE, ParticleStyle.builder(steam).color(0x58C8C6C2).size(1.8F, 2.6F).life(1.6F, 2.4F)
                .gravity(-4.0F).drag(0.75F).grow(3.0F).wobble(1.0F).fadeIn(0.3F).build());
        DiegesisClient.registerParticle(HEAT, ParticleStyle.builder(frames("heat", 4)).color(0x48FFFFFF)
                .size(3.0F, 4.0F).life(0.8F, 1.3F).gravity(-5.0F).drag(0.6F).grow(0.4F).wobble(0.3F).fadeIn(0.4F).build());
        DiegesisClient.registerParticle(SCROLL_FLAME, ParticleStyle.builder(ResourceLocation.withDefaultNamespace("flame")).color(0xFFFFFFFF)
                .size(1.6F, 2.4F).life(0.35F, 0.6F).gravity(-6.0F).drag(0.8F).emissive().grow(0.35F).wobble(0.2F).build());
        // Sugar, cocoa and the like shaken out over a pot: fine grains that fall straight and are gone in the soup.
        DiegesisClient.registerParticle(GRAIN, ParticleStyle.builder(ResourceLocation.withDefaultNamespace("generic_0")).color(0xFFFFFFFF)
                .size(0.28F, 0.42F).life(0.35F, 0.6F).gravity(55.0F).drag(0.2F).pop().build());
    }

    /**
     * The player left the server: what it told this client of its board dishes is forgotten. The next server may not have the mod (its
     * sandwiches are crafted), or has other dishes, and a book kept from this one would list dishes it cannot make.
     */
    public static void loggedOut() {
        BoardDishes.clear();
        BoardBook.received(new ListTag());
    }

    /** Whether a scene stands in for the block at {@code pos} on this client. */
    public static boolean isShown(BlockPos pos) {
        return DiegesisClient.station(pos).isPresent();
    }

    /** Whether the scene at {@code pos} is drawn this frame: then it stands in for the block's renderer. */
    public static boolean isSceneDrawn(BlockPos pos) {
        return DiegesisClient.isSceneDrawn(pos);
    }

    /** Whether the station at {@code pos} leaves its block out of the world now, its scene showing it instead. */
    public static boolean isBlockHidden(BlockPos pos) {
        return DiegesisClient.station(pos).map(StationView::isBlockHidden).orElse(false);
    }
}

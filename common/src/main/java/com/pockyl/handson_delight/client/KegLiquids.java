package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.LiquidLook;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How the liquids of a keg look, each as what it is: a beer golden with a head of foam and bubbles rising, a stout nearly black
 * under a grey head, vodka clear, eggnog thick and creamy, grenadine glittering. Colours follow Brewin' and Chewin's own drinks;
 * the alpha is how clear the liquid is. Liquids nobody describes here take the colour of the drink poured from them.
 */
final class KegLiquids {
    /**
     * One liquid's look.
     *
     * @param color     ARGB, the alpha how clear it is
     * @param viscosity how thick it moves, 0 to 1
     * @param foam      how much of the surface its foam covers at rest, 0 to 1, and in which colour
     * @param fizz      bubbles rising through it per second
     * @param mottle    how cloudy and uneven its surface is, 0 to 1
     * @param glows     lit in the dark
     * @param sprite    what is drawn on it, tinted by the colour
     * @param rising    particles that leave its surface now and then (glitter, fumes), in {@code risingColor}, per second
     * @param brewed    it ferments under a yeast head: foam grows while it is made and settles brown as it is done
     */
    record Drink(int color, float viscosity, float foam, int foamColor, float fizz, float mottle, boolean glows, ResourceLocation sprite,
            ResourceLocation rising, int risingColor, float risingRate, boolean brewed) {
        static Drink of(int color) {
            return new Drink(color, 0.0F, 0.0F, 0, 0.0F, 0.0F, false, LiquidLook.LIQUID_SPRITE, null, 0, 0.0F, false);
        }

        Drink thick(float value) {
            return new Drink(color, value, foam, foamColor, fizz, mottle, glows, sprite, rising, risingColor, risingRate, brewed);
        }

        Drink foam(float coverage, int argb) {
            return new Drink(color, viscosity, coverage, argb, fizz, mottle, glows, sprite, rising, risingColor, risingRate, brewed);
        }

        Drink fizz(float perSecond) {
            return new Drink(color, viscosity, foam, foamColor, perSecond, mottle, glows, sprite, rising, risingColor, risingRate, brewed);
        }

        Drink cloudy(float amount) {
            return new Drink(color, viscosity, foam, foamColor, fizz, amount, glows, sprite, rising, risingColor, risingRate, brewed);
        }

        Drink glowing() {
            return new Drink(color, viscosity, foam, foamColor, fizz, mottle, true, sprite, rising, risingColor, risingRate, brewed);
        }

        Drink drawn(ResourceLocation value) {
            return new Drink(color, viscosity, foam, foamColor, fizz, mottle, glows, value, rising, risingColor, risingRate, brewed);
        }

        Drink giving(ResourceLocation particle, int argb, float perSecond) {
            return new Drink(color, viscosity, foam, foamColor, fizz, mottle, glows, sprite, particle, argb, perSecond, brewed);
        }

        Drink asBrew() {
            return new Drink(color, viscosity, foam, foamColor, fizz, mottle, glows, sprite, rising, risingColor, risingRate, true);
        }

        LiquidLook look() {
            return new LiquidLook(color, sprite, glows, viscosity);
        }
    }

    /** A head of beer foam, cream white, and the brown it settles to when a brew is done. */
    static final int HEAD = 0xF4FAF0D2;
    static final int SPENT_HEAD = 0xEEC4A878;

    private static final Map<ResourceLocation, Drink> DRINKS = new HashMap<>();

    static {
        String bnc = "brewinandchewin";
        drink(bnc, "beer", Drink.of(0xD0D49A3C).foam(0.55F, HEAD).fizz(1.6F).asBrew());
        drink(bnc, "strongroot_ale", Drink.of(0xE08A3438).foam(0.36F, 0xF2E8A49C).fizz(1.1F).asBrew());
        drink(bnc, "steel_toe_stout", Drink.of(0xF62E2629).foam(0.55F, 0xF4D2CAC9).fizz(0.7F).thick(0.12F).asBrew());
        drink(bnc, "kombucha", Drink.of(0xC8705E2C).foam(0.06F, 0xE8D8CBA0).fizz(1.2F).cloudy(0.35F).asBrew());
        drink(bnc, "mead", Drink.of(0xD8F2A21E).fizz(0.3F).thick(0.25F));
        drink(bnc, "vodka", Drink.of(0x40DDEEE9));
        drink(bnc, "rice_wine", Drink.of(0xA8E8E4BE).cloudy(0.25F));
        drink(bnc, "pale_jane", Drink.of(0x98E6D2BC).fizz(0.25F));
        drink(bnc, "saccharine_rum", Drink.of(0xD8A82E4C).thick(0.1F));
        drink(bnc, "red_rum", Drink.of(0xE8650A0E).thick(0.1F));
        drink(bnc, "salty_folly", Drink.of(0xD83E6A30).cloudy(0.3F).fizz(0.3F));
        drink(bnc, "egg_grog", Drink.of(0xF6F2E8C8).thick(0.45F).foam(0.18F, 0xF6FFFFFA).cloudy(0.15F));
        drink(bnc, "dread_nog", Drink.of(0xF422C2A2).thick(0.45F).foam(0.16F, 0xF4E1F5DD).cloudy(0.2F));
        drink(bnc, "bloody_mary", Drink.of(0xF8861C14).thick(0.35F).cloudy(0.3F));
        drink(bnc, "glittering_grenadine", Drink.of(0xDCE0702A).glowing().giving(DiegesisParticles.GLINT, 0xFFFFE6A0, 2.5F));
        drink(bnc, "withering_dross", Drink.of(0xFA221A15).thick(0.5F).cloudy(0.3F).giving(DiegesisParticles.SMOKE, 0xC0201814, 0.8F));
        // Deep amber, nearly opaque in a keg, slow, with an uneven gloss.
        drink(bnc, "honey", Drink.of(0xE6CC8418).thick(0.85F).cloudy(0.18F));
        drink(bnc, "flaxen_cheese", Drink.of(0xFFFFFFFF).thick(1.0F).drawn(ResourceLocation.tryBuild(bnc, "block/flaxen_cheese_still")));
        drink(bnc, "scarlet_cheese", Drink.of(0xFFFFFFFF).thick(1.0F).drawn(ResourceLocation.tryBuild(bnc, "block/scarlet_cheese_still")));
        Drink milk = Drink.of(0xFAF6F3EA).thick(0.1F);
        drink("minecraft", "milk", milk);
        drink(bnc, "milk", milk);
    }

    private KegLiquids() {
    }

    private static void drink(String namespace, String path, Drink drink) {
        DRINKS.put(ResourceLocation.tryBuild(namespace, path), drink);
    }

    /** Tells Diegesis how each of them looks in a tank. */
    static void register() {
        DRINKS.forEach((id, drink) -> DiegesisClient.registerLiquid(id, drink.look()));
    }

    static Optional<Drink> of(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return Optional.empty();
        }
        return Optional.ofNullable(DRINKS.get(BuiltInRegistries.FLUID.getKey(fluid)));
    }
}
